// How to read the cloud volume. Included by anything that marches through it.

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
    vec4 SunToward;       // xyz unit vector at the sun, w metres in the first step towards it
    vec4 SunLight;        // rgb the light falling on the cloud, w how much of the powder term
    vec4 SkyLight;        // rgb what lights the side the sun never reaches, w spare
    vec4 CloudBand;       // x the lowest metre holding water, y the highest, zw spare
};

// The two steps the server sent, each an atlas of tiles, four altitudes per texel.
uniform sampler2D CloudsFrom;
uniform sampler2D CloudsTo;

// Altitudes in one texel, which is the channel count of the image rather than a tuning number.
const float PER_TEXEL = 4.0;

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

/**
 * A cell coordinate warped so straight interpolation comes out curved.
 *
 * <p>Hardware filtering is linear, so the field it reconstructs has a kink at every cell boundary,
 * and on a grid of sixty-four metre cells those kinks draw a visible diagonal lattice across the
 * inside of every cloud. Bending the fractional part along a Hermite curve first costs a floor and
 * two multiplies and makes the reconstruction smooth at the boundary, which is where the lattice
 * came from. It is not a cubic filter and does not pretend to be one.
 */
vec3 curved(vec3 cells) {
    vec3 whole = floor(cells);
    vec3 part = cells - whole;
    return whole + part * part * (3.0 - 2.0 * part);
}

/** Condensed water at one point in the volume, blended between the two steps the client holds. */
float waterAt(vec3 metres) {
    // A cell holds the average over the box it covers, so its value belongs at the box's centre and
    // not at its corner. Half a cell on each axis, which is thirty-two metres of layer height.
    vec3 cells = curved(metres / CameraInVolume.w - 0.5);
    // Strictly under the top row, not at it: the row above is what the vertical blend reaches for, and
    // at the very top that row is a tile the atlas does not have. Empty air either way.
    if (cells.y < 0.0 || cells.y >= VolumeCells.y - 1.0) {
        return 0.0;
    }
    vec2 xz = mod(cells.xz, VolumeCells.xz);
    float low = floor(cells.y);
    float rise = cells.y - low;
    float tile = floor(low / PER_TEXEL);
    int channel = int(low) - int(tile) * int(PER_TEXEL);

    // Squared, because the packing stored the root. A linear eighth bit leaves the faint wisp around a
    // cumulus three or four levels to live in, and the filter then draws it as a lattice of dots.
    float older = levelAt(CloudsFrom, xz, tile, channel, rise);
    float newer = levelAt(CloudsTo, xz, tile, channel, rise);
    return mix(older * older * CameraUp.w, newer * newer * CameraLeft.w, CameraForward.w);
}
