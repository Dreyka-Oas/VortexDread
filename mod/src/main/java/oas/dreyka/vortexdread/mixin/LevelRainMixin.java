package oas.dreyka.vortexdread.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import oas.dreyka.vortexdread.weather.Rain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Rain that falls where the water is, rather than everywhere at once.
 *
 * <p>The game holds one boolean for a world thirty million blocks wide. Standing in sunshine while rain
 * lands on your head is not a bug in that design, it is the design: there is nowhere for a position to
 * enter the answer. So a farm grows, a fire goes out and a cauldron fills on the strength of a storm four
 * kilometres away that the player cannot even see.
 *
 * <p>The simulation already answers the real question. It carries condensed water per cell and knows the
 * depth of it over any column, so the honest answer per block was sitting there unasked.
 *
 * <p>This method rather than {@code isRaining}, and the choice matters. Every position-dependent effect in
 * the game funnels through here already, cauldrons, crops, fire, mob spawning, so one hook covers them
 * all and none of them needs to know this mod exists. Redirecting the global switch instead, the way the
 * mod this idea came from had to, means forcing it true and then filtering every caller by hand.
 *
 * <p>The game's own conditions are kept rather than replaced. Sky has to be visible, the block has to be
 * the top one, and the biome has to be one where water falls rather than snow or nothing: a desert stays
 * dry under a cloud that is raining on the forest beside it, which is what the biome is for.
 */
@Mixin(Level.class)
public abstract class LevelRainMixin {

    @Inject(method = "isRainingAt", at = @At("HEAD"), cancellable = true)
    private void vortexdread$rainOnlyUnderTheCloud(BlockPos pos, CallbackInfoReturnable<Boolean> answer) {
        Level level = (Level) (Object) this;
        if (Rain.simulated(level) && !Rain.over(level, pos)) {
            answer.setReturnValue(false);
        }
    }
}
