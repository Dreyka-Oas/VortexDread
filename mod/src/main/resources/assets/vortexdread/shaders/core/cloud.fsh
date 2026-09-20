#version 330

#moj_import <minecraft:projection.glsl>

// The two steps the server sent, each an atlas of tiles, four altitudes per texel.
uniform sampler2D CloudsFrom;
uniform sampler2D CloudsTo;

// Everything in vec4 slots on purpose. A vec3 followed by a float is laid out one way by the std140
// rule and another way by whatever fills the buffer, and the mismatch shows up as a working shader
// on one driver and a black sky on the next. Sixteen byte members cannot be got wrong.
layout(std140) uniform CloudSky {
    vec4 CameraForward;   // xyz look direction, w how far between the two steps this frame sits
    vec4 CameraUp;        // xyz up, w peak of the older field
    vec4 CameraLeft;      // xyz left, w peak of the newer field
    vec4 CameraInVolume;  // xyz camera inside the volume in metres, w cell edge in metres
    vec4 VolumeCells;     // xyz cells per axis, w extinction per unit of water per metre
    vec4 AtlasShape;      // tiles across, tile width, tile height, metres to march at most
};

in vec2 texCoord;

out vec4 fragColor;

// Altitudes in one texel, which is the channel count of the image rather than a tuning number.
const float PER_TEXEL = 4.0;

// Deliberately coarse, and deliberately fixed. A step of a couple of hundred metres draws a cloud with
// visible slabs in it, which is the point at this stage: it says the field arrived and where it is. The
// step size, the jitter and the quality levels are the pass that comes after this one.
const int STEPS = 64;

/** How big the image is, worked out from the shape rather than sent, since it follows from it. */
vec2 atlasSize() {
    float tiles = ceil(VolumeCells.y / PER_TEXEL);
    return vec2(AtlasShape.x * AtlasShape.y, ceil(tiles / AtlasShape.x) * AtlasShape.z);
}

/** One tile, read at a fractional cell position so the hardware does the horizontal filtering. */
vec4 tileAt(sampler2D field, float tile, vec2 xz) {
    float across = AtlasShape.x;
    vec2 corner = vec2(mod(tile, across) * AtlasShape.y, floor(tile / across) * AtlasShape.z);
    // One texel in for the border, half a texel further to land on the centre of the cell at zero.
    return texture(field, (corner + vec2(1.5) + xz) / atlasSize());
}

/**
 * One field at one point, with the vertical blend done by hand.
 *
 * <p>Three times in four the altitude above is the next channel of the texel already fetched, which is
 * the whole reason four of them share one. The fourth time it is the first channel of the tile above.
 */
float levelAt(sampler2D field, vec2 xz, float tile, int channel, float rise) {
    vec4 here = tileAt(field, tile, xz);
    float above;
    if (channel < 3) {
        above = here[channel + 1];
    } else {
        above = tileAt(field, tile + 1.0, xz).r;
    }
    return mix(here[channel], above, rise);
}

/** Condensed water at one point in the volume, blended between the two steps the client holds. */
float waterAt(vec3 metres) {
    float y = metres.y / CameraInVolume.w;
    // Strictly under the top row, not at it: the row above is what the vertical blend reaches for, and
    // at the very top that row is a tile the atlas does not have. Empty air either way.
    if (y < 0.0 || y >= VolumeCells.y - 1.0) {
        return 0.0;
    }
    vec2 xz = mod(metres.xz / CameraInVolume.w, VolumeCells.xz);
    float low = floor(y);
    float rise = y - low;
    float tile = floor(low / PER_TEXEL);
    int channel = int(low) - int(tile) * int(PER_TEXEL);

    float older = levelAt(CloudsFrom, xz, tile, channel, rise) * CameraUp.w;
    float newer = levelAt(CloudsTo, xz, tile, channel, rise) * CameraLeft.w;
    return mix(older, newer, CameraForward.w);
}

void main() {
    float cellSize = CameraInVolume.w;
    float ceilingY = VolumeCells.y * cellSize;
    float camY = CameraInVolume.y;

    // The reciprocal of a perspective matrix's diagonal is the tangent of half the angle on that axis,
    // so the ray needs no field of view uniform of its own. Right is the other way from left.
    vec2 ndc = texCoord * 2.0 - 1.0;
    vec3 ray = normalize(CameraForward.xyz
            - ndc.x * CameraLeft.xyz / ProjMat[0][0]
            + ndc.y * CameraUp.xyz / ProjMat[1][1]);

    // Both horizontal axes tile, so the only faces the ray can cross are the floor and the ceiling.
    float enter = 0.0;
    float leave = AtlasShape.w;
    if (abs(ray.y) > 1.0e-4) {
        float toFloor = -camY / ray.y;
        float toCeiling = (ceilingY - camY) / ray.y;
        enter = max(0.0, min(toFloor, toCeiling));
        leave = min(leave, max(toFloor, toCeiling));
    } else if (camY < 0.0 || camY > ceilingY) {
        discard;
    }
    if (leave <= enter) {
        discard;
    }

    // Beer and Lambert, and nothing else yet: how much of the sky behind survives the water in front.
    // Light, its scattering and the erosion under a cell all come later, and each one needs this right
    // first, because every one of them is a factor on a number this loop produces.
    float span = (leave - enter) / float(STEPS);
    float transmittance = 1.0;
    for (int step = 0; step < STEPS; step++) {
        vec3 at = CameraInVolume.xyz + ray * (enter + (float(step) + 0.5) * span);
        float water = waterAt(at);
        if (water > 0.0) {
            transmittance *= exp(-water * VolumeCells.w * span);
        }
    }

    fragColor = vec4(1.0, 1.0, 1.0, 1.0 - transmittance);
}
