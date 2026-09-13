#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;

out vec3 rayTarget;
out vec4 funnelTint;
out vec4 lightMapColor;

// Constant across the whole prism: the axis is at the same place for every one of its corners, so the
// fragment stage can reconstruct it from a single vertex without a uniform.
flat out vec2 axisXZ;
flat out float groundY;
flat out float coreRadius;
flat out float funnelHeight;
flat out float windFraction;
flat out float descent;
flat out float groundLoad;

// How far under the ground the funnel stands on the lofted dust is allowed to run, as a multiple of the
// core radius. Kept in step with the fragment program and with TornadoRenderer, which sizes the prism
// from the same number: a tornado on a ridge throws dust out over the valley beside it, and a volume
// that stops at the funnel's own ground level cuts that off along a dead straight line in mid air.
const float RING_DROP = 0.9;

// How far over the cloud base the prism reaches, as a fraction of the funnel's height, so the lowered
// base has room to thin out rather than ending on the lid. Matched in the fragment program, where it
// is the product of the depth of the lowering and how far it climbs back.
const float WALL_LOFT = 0.19;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    rayTarget = Position;
    funnelTint = Color;
    lightMapColor = texelFetch(Sampler2, UV2 / 16, 0);

    // The prism is written around the funnel's own axis, so subtracting a corner's offset from its
    // position gives that axis back wherever the camera happens to be. The normal carries no lighting
    // here: its sign says which end of the box this corner sits at, which is what turns a position into
    // a height above the ground the column stands on.
    axisXZ = Position.xz - UV0;
    coreRadius = float(UV1.x) / 64.0;
    funnelHeight = float(UV1.y) / 64.0;
    groundLoad = Normal.x;
    windFraction = abs(Normal.y);
    descent = Normal.z;
    groundY = Position.y
            - (Normal.y > 0.0 ? funnelHeight * (1.0 + WALL_LOFT) : -coreRadius * RING_DROP);
}
