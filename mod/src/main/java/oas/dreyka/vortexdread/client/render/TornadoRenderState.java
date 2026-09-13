package oas.dreyka.vortexdread.client.render;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

/** Everything the funnel program needs, pulled off the entity once per frame. */
public class TornadoRenderState extends EntityRenderState {
    public float coreRadius = 6.0f;
    public float funnelHeight = 150.0f;
    public float descent;
    public float groundLoad;
    public float windFraction;
    public float flash;
    public int tint = 0x9A9490;
}
