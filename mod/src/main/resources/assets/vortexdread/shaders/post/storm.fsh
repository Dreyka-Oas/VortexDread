#version 330

#moj_import <minecraft:globals.glsl>

uniform sampler2D InSampler;
uniform sampler2D StateSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

in vec2 texCoord;

out vec4 fragColor;

// How far a pixel is dragged at the worst of it, as a fraction of the screen. Small on purpose: a warp
// anyone can measure reads as a broken driver, and one nobody can quite point at reads as air.
const float MAX_WARP = 0.011;

// What is left of the daylight standing under the wall cloud. Not zero: a tornado is dark, not night.
const float FLOOR_LIGHT = 0.42;

// Where the light ends up at full strength. Red goes first and blue follows it, which is the way round
// that reads as the storm rather than as a filter dropped over the screen.
const vec3 SKY_GREEN = vec3(0.74, 1.0, 0.66);

void main() {
    // Nearness, flash and grit, written into a one pixel texture by the client every frame, because a
    // post chain's own uniforms are fixed when the pack loads and this has to follow a storm.
    vec4 state = texture(StateSampler, vec2(0.5));
    float near = state.r;
    float flash = state.g;
    float grit = state.b;
    float green = state.a;

    vec2 uv = texCoord;
    vec2 centred = uv * 2.0 - 1.0;
    float edge = clamp(dot(centred, centred), 0.0, 1.0);

    if (near > 0.004) {
        // Light bends through air that is not the same density everywhere, and inside the inflow it is
        // nowhere near uniform. Two speeds crossed rather than one, so the pattern never repeats on a
        // beat the eye can lock onto, and weighted toward the edges of the frame where the flow is
        // crossing the view fastest.
        float t = GameTime * 2400.0;
        float dx = sin(uv.y * 41.0 + t * 6.1) * 0.6 + sin(uv.y * 17.0 - t * 3.7) * 0.4;
        float dy = cos(uv.x * 33.0 - t * 5.3) * 0.6 + cos(uv.x * 13.0 + t * 2.9) * 0.4;
        uv += vec2(dx, dy) * MAX_WARP * near * (0.35 + 0.65 * edge);
    }

    vec3 colour = texture(InSampler, clamp(uv, vec2(0.0005), vec2(0.9995))).rgb;

    // The light goes before the wind arrives. Everyone who has stood under one says the same thing, and
    // the darkening is not even: the middle of the frame keeps more of it than the corners do.
    colour *= mix(1.0, FLOOR_LIGHT, near * (0.55 + 0.45 * edge));

    // The colour a storm sky takes when it is deep enough to hail. Sunlight that has crossed kilometres
    // of water and ice comes out short of red, and what is left of it lands on the ground as that
    // green nobody who has seen it forgets. Scaled by the light already lost, so it arrives with the
    // darkening rather than before it, and left on a multiply: tinting toward a flat colour would wash
    // the landscape out where this only takes the red away.
    colour *= mix(vec3(1.0), SKY_GREEN, near * green);

    // Dust and rain crossing the view, drawn as a grain that moves rather than a texture that sits.
    if (grit > 0.004) {
        float streak = fract(sin(dot(vec2(uv.x * 260.0, uv.y * 90.0 + GameTime * 90000.0),
                                    vec2(12.9898, 78.233))) * 43758.5453);
        colour = mix(colour, colour * (0.55 + 0.9 * streak), grit * 0.5);
    }

    // Lightning inside the funnel lights the whole scene for a frame or two, and it lights it warm.
    // Added to what is there rather than mixed toward white, and weighted by it: a flash that pulls
    // every pixel the same distance toward one colour flattens the frame into a grey card, where a
    // real one leaves the lit side of the column brighter than the side turned away from the bolt.
    float bolt = clamp(flash, 0.0, 1.0);
    colour += vec3(1.0, 0.94, 0.82) * bolt * (0.18 + 0.62 * colour);

    fragColor = vec4(colour, 1.0);
}
