package oas.dreyka.vortexdread.client.sound;

import oas.dreyka.vortexdread.entity.TornadoEntity;
import oas.dreyka.vortexdread.sound.VortexSounds;
import oas.dreyka.vortexdread.wind.EfScale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * One half of a tornado's voice, following it for as long as it lives.
 *
 * <p>Two of these run per funnel, one on each loop, and the crossfade between them is the distance.
 * The game's own attenuation is switched off: linear fade over sixteen blocks times the volume is a
 * fine law for a pig and a useless one for something audible from a kilometre away. What replaces it is
 * spherical spreading, which is a genuine inverse law, plus a separate curve on the broadband loop
 * standing in for the air swallowing everything above a few hundred hertz. Near the funnel the rush
 * dominates; a long way out only the low end has survived the trip.
 *
 * <p>The source is not the entity's own position either. A tornado is a column two hundred blocks tall,
 * so the nearest point of the column is what the ear should be pointed at: standing under the wall cloud
 * with the funnel a long way off, the sound comes from the funnel and not from overhead.
 */
public class TornadoRoar extends AbstractTickableSoundInstance {

    /** Past this, in blocks, the funnel is silent. Real ones carry further; a game has a horizon. */
    private static final float RANGE = 900.0f;

    /** Inside this, in blocks, the sound no longer grows. Standing in the core is not twice as loud. */
    private static final float NEAR = 45.0f;

    /** How fast the broadband loop is eaten by distance, in blocks. Roughly what damp air does. */
    private static final float AIR_ABSORPTION = 260.0f;

    /** How fast the gain follows a change, per tick. Slow, so a funnel does not snap into earshot. */
    private static final float GLIDE = 0.06f;

    /** Loudest either loop is allowed to be, before the player's own weather slider. */
    private static final float CEILING = 1.6f;

    private final TornadoEntity tornado;
    private final boolean broadband;
    private float gain;

    private TornadoRoar(TornadoEntity tornado, SoundEvent event, boolean broadband) {
        super(event, SoundSource.WEATHER, RandomSource.create());
        this.tornado = tornado;
        this.broadband = broadband;
        this.looping = true;
        this.delay = 0;
        this.attenuation = Attenuation.NONE;
        this.volume = 0.0f;
        this.pitch = 1.0f;
        follow();
    }

    /** The close loop, everything from the low end up to the inflow rush. */
    public static TornadoRoar rush(TornadoEntity tornado) {
        return new TornadoRoar(tornado, VortexSounds.TORNADO_ROAR, true);
    }

    /** The far loop, the part of the spectrum a kilometre of air leaves alone. */
    public static TornadoRoar rumble(TornadoEntity tornado) {
        return new TornadoRoar(tornado, VortexSounds.TORNADO_RUMBLE, false);
    }

    /** How loud this loop currently is, which is what the log line at the far end reports. */
    public float gain() {
        return gain;
    }

    @Override
    public void tick() {
        if (tornado.isRemoved()) {
            stop();
            return;
        }
        follow();

        float distance = distanceToColumn();
        float wanted = distance >= RANGE ? 0.0f : strength() * spreading(distance) * share(distance);
        gain += (wanted - gain) * GLIDE;
        volume = Math.min(CEILING, gain);

        // A bigger funnel sounds lower, which is the same reason a big bell does. The shift is small on
        // purpose: pitched far enough to hear, never far enough to sound like a slowed-down recording.
        float scale = tornado.coreRadius() / 30.0f;
        pitch = broadband ? 1.06f - 0.22f * Math.min(1.0f, scale) : 0.94f - 0.12f * Math.min(1.0f, scale);
    }

    @Override
    public boolean canPlaySound() {
        return !tornado.isRemoved();
    }

    /**
     * Yes, even though this starts at nothing.
     *
     * <p>The engine drops a sound whose opening volume is zero and never ticks it again, which for a
     * loop that is supposed to glide up out of silence means it is never heard at all. A funnel comes
     * into earshot long before it is loud, so the loop has to be running by then.
     */
    @Override
    public boolean canStartSilent() {
        return true;
    }

    /** Puts the source on the nearest point of the column rather than on the entity's feet. */
    private void follow() {
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera().position();
        double top = tornado.groundY() + tornado.funnelHeight() * tornado.descent();
        x = tornado.getX();
        z = tornado.getZ();
        y = Math.max(tornado.groundY(), Math.min(top, camera.y));
    }

    private float distanceToColumn() {
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera().position();
        double dx = camera.x - x;
        double dy = camera.y - y;
        double dz = camera.z - z;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Acoustic output against the gust, steeper than linear.
     *
     * <p>The power a turbulent flow radiates climbs far faster than its speed does, so an EF5 is not
     * one grade louder than an EF4, it is another thing entirely. The cube root at the end is the ear,
     * which hears power on a curve of its own.
     */
    private float strength() {
        float ratio = tornado.wind() / EfScale.EF0.minWind();
        if (ratio <= 0.0f) {
            return 0.0f;
        }
        return (float) Math.cbrt(ratio * ratio * ratio * ratio) * tornado.descent();
    }

    /** Inverse distance, held flat inside the core so the middle of it is not infinitely loud. */
    private float spreading(float distance) {
        return NEAR / Math.max(NEAR, distance);
    }

    /** How much of this loop belongs at this distance. The two shares add up to one. */
    private float share(float distance) {
        float high = (float) Math.exp(-distance / AIR_ABSORPTION);
        return broadband ? high : 1.0f - high;
    }
}
