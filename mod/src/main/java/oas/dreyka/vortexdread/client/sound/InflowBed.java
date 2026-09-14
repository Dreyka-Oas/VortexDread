package oas.dreyka.vortexdread.client.sound;

import oas.dreyka.vortexdread.entity.TornadoEntity;
import oas.dreyka.vortexdread.sound.VortexSounds;
import oas.dreyka.vortexdread.wind.EfScale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * The wind under the storm, played at the ear rather than at anything in the world.
 *
 * <p>Air feeding a mesocyclone arrives from kilometres away, and a person standing in it hears the storm
 * long before there is a column to look at. The roar loops cannot carry that: they are positional, they
 * start at the funnel and they are weighted by how far down it has come, so a supercell that has not
 * dropped anything yet is silent under them. This is the other half, and it is the half that makes an
 * approach frightening rather than sudden.
 *
 * <p>Placed at the listener with no attenuation, because there is no point in the world it comes from.
 * Wind is around the head from every side at once, and putting it on the funnel's own position would
 * have the player turn away from the storm and hear it go quiet.
 *
 * <p>One instance for the whole client. Several funnels feed it and the strongest wins, which is what
 * happens anyway: two winds arriving at one ear are one wind.
 */
public class InflowBed extends AbstractTickableSoundInstance {

    /** How far a storm is heard as wind, in blocks. Well past the range the funnel itself carries. */
    private static final float REACH = 1400.0f;

    /** Inside this, in blocks, the bed no longer grows: the inflow is a field, not a point. */
    private static final float NEAR = 120.0f;

    /**
     * How fast the level follows a change, per tick.
     *
     * <p>A third of what the roar uses. A gust front takes minutes to arrive, so a bed that tracks the
     * distance closely turns every camera move into a swell and gives the whole thing away.
     */
    private static final float GLIDE = 0.02f;

    /** Loudest the bed is allowed to be, before the player's own weather slider. */
    private static final float CEILING = 0.9f;

    private float gain;
    private boolean ended;

    public InflowBed() {
        super(VortexSounds.STORM_INFLOW, SoundSource.WEATHER, RandomSource.create());
        this.looping = true;
        this.delay = 0;
        this.attenuation = Attenuation.NONE;
        this.relative = true;
        this.x = 0.0;
        this.y = 0.0;
        this.z = 0.0;
        this.volume = 0.0f;
        this.pitch = 1.0f;
    }

    /** How loud the bed currently is, which is what the log line reports. */
    public float gain() {
        return gain;
    }

    /** Whether the client should stop holding a source for it. */
    public void end() {
        ended = true;
    }

    @Override
    public void tick() {
        Minecraft client = Minecraft.getInstance();
        if (ended || client.level == null) {
            stop();
            return;
        }

        float wanted = strongest(client);
        gain += (wanted - gain) * GLIDE;
        volume = Math.min(CEILING, gain);

        // Rising with the level, because wind gets brighter as it gets faster: the same air through the
        // same trees moves more of its energy up the spectrum the harder it is pushed.
        pitch = 0.88f + 0.24f * Math.min(1.0f, gain);
    }

    @Override
    public boolean canPlaySound() {
        return !ended;
    }

    /**
     * Yes, because it opens at nothing.
     *
     * <p>The engine drops a sound whose first volume is zero and never ticks it again. A bed that is
     * meant to climb out of silence over a minute has to be running through the whole of that minute.
     */
    @Override
    public boolean canStartSilent() {
        return true;
    }

    /** What the nearest storm asks for, ignoring how far its funnel has come down. */
    private float strongest(Minecraft client) {
        var camera = client.gameRenderer.getMainCamera().position();
        float best = 0.0f;
        for (var entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof TornadoEntity tornado)) {
                continue;
            }
            double dx = camera.x - tornado.getX();
            double dz = camera.z - tornado.getZ();
            float distance = (float) Math.sqrt(dx * dx + dz * dz);
            if (distance >= REACH) {
                continue;
            }
            // Peak rather than current wind, and no descent term anywhere. What is feeding the storm is
            // there before the funnel is, and it is what the storm is going to be that decides how hard
            // the air is moving now.
            float scale = Math.min(1.0f, tornado.peakWind() / EfScale.EF5.minWind());
            float spread = NEAR / Math.max(NEAR, distance);
            float edge = 1.0f - distance / REACH;
            best = Math.max(best, (0.3f + 0.7f * scale) * spread * edge);
        }
        return best;
    }
}
