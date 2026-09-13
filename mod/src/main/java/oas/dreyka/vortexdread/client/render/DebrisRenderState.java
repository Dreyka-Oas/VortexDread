package oas.dreyka.vortexdread.client.render;

import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

/** A block torn out of the ground, plus the tumble it picked up on the way up. */
public class DebrisRenderState extends EntityRenderState {
    public final MovingBlockRenderState block = new MovingBlockRenderState();
    public float spinYaw;
    public float spinPitch;
    public float spinRoll;
}
