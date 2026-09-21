#version 330

uniform sampler2D CloudSmall;

in vec2 texCoord;

out vec4 fragColor;

/**
 * The reduced sky stretched back over the screen.
 *
 * Straight bilinear, done by the sampler rather than by hand. What makes that enough here is what the
 * pass is drawing: the march already writes a premultiplied colour whose alpha is one minus the
 * transmittance, so the edge of a cloud is a ramp in alpha over several pixels and not a step. Filtering
 * a ramp gives a ramp. The case bilinear ruins is a hard silhouette, which is what a depth guided
 * upscale exists for, and this pass has no silhouette and no depth to guide anything with.
 *
 * Colour and alpha travel through the same filter on purpose. They are premultiplied, so they are two
 * halves of one quantity, and filtering them apart is what puts a dark fringe on a cloud edge.
 */
void main() {
    fragColor = texture(CloudSmall, texCoord);
}
