package oas.dreyka.vortexdread.client.sound;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import oas.dreyka.vortexdread.VortexDread;
import oas.dreyka.vortexdread.entity.TornadoEntity;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

/**
 * Which funnels are currently making a noise on this client.
 *
 * <p>Held as a map rather than found by walking the level every tick: the loader already tells the
 * client when an entity arrives and when it goes, and a lookup that the game maintains for free beats
 * one paid for twenty times a second.
 *
 * <p>A funnel past the audible range has its loops stopped rather than played at zero. An OpenAL source
 * held open for a storm nobody can hear is a source the rest of the game cannot use, and on a busy
 * server that is how a mod quietly eats the sound budget.
 *
 * <p>The wind bed is the exception and is held for as long as any storm exists, because its own reach is
 * several times the roar's and it takes a minute to climb. Stopping and restarting it on a threshold
 * would put a swell at the threshold instead of at the storm.
 */
public final class StormAudio {
    private StormAudio() {
    }

    /** Past this, in blocks, the pair is stopped. Slightly over the roar's own range, so it can fade. */
    private static final double EARSHOT = 960.0;

    /** Ticks after a funnel comes into earshot before its settled level is worth reporting. */
    private static final long SETTLE_TICKS = 60L;

    /** How much the distance has to change before the level is worth saying again, as a ratio. */
    private static final double WORTH_SAYING = 0.3;

    private static final Map<Integer, Voice> VOICES = new HashMap<>();

    /** The one wind bed, held here so it survives between ticks and dies with the level. */
    private static InflowBed bed;

    private static final class Voice {
        private final TornadoRoar rush;
        private final TornadoRoar rumble;
        private long settleAt;
        private double reportedAt = -1.0;

        private Voice(TornadoEntity tornado, long now) {
            this.rush = TornadoRoar.rush(tornado);
            this.rumble = TornadoRoar.rumble(tornado);
            this.settleAt = now + SETTLE_TICKS;
        }

        /** Whether the level has settled on a distance that is no longer the one last reported. */
        private boolean worthSaying(long now, double distance) {
            if (now < settleAt) {
                return false;
            }
            return reportedAt < 0.0
                    || Math.abs(distance - reportedAt) > reportedAt * WORTH_SAYING;
        }
    }

    public static void register() {
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (entity instanceof TornadoEntity) {
                silence(entity.getId());
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(StormAudio::tick);
    }

    private static void tick(Minecraft client) {
        if (client.level == null) {
            VOICES.clear();
            if (bed != null) {
                bed.end();
                bed = null;
            }
            return;
        }
        long now = client.level.getGameTime();
        var camera = client.gameRenderer.getMainCamera().position();

        boolean anyStorm = false;
        for (var entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof TornadoEntity tornado)) {
                continue;
            }
            anyStorm = true;
            boolean close = tornado.distanceToSqr(camera.x, tornado.getY(), camera.z) < EARSHOT * EARSHOT;
            Voice voice = VOICES.get(tornado.getId());
            if (voice == null && close) {
                start(tornado, now);
            } else if (voice != null && !close) {
                silence(tornado.getId());
            }
        }

        // The bed opens as soon as a storm exists anywhere in the level, whatever its distance, because
        // its own reach is what decides whether it is audible and it needs the climb to be running
        // before it is. It closes only when the last one is gone.
        if (anyStorm && bed == null) {
            bed = new InflowBed();
            client.getSoundManager().play(bed);
        } else if (!anyStorm && bed != null) {
            bed.end();
            bed = null;
        }

        for (Iterator<Map.Entry<Integer, Voice>> it = VOICES.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Voice> entry = it.next();
            Voice voice = entry.getValue();
            if (voice.rush.isStopped() && voice.rumble.isStopped()) {
                it.remove();
                continue;
            }
            report(client, now, entry.getKey(), voice);
        }
    }

    private static void start(TornadoEntity tornado, long now) {
        Voice voice = new Voice(tornado, now);
        Minecraft client = Minecraft.getInstance();
        client.getSoundManager().play(voice.rush);
        client.getSoundManager().play(voice.rumble);
        VOICES.put(tornado.getId(), voice);
    }

    private static void silence(int entityId) {
        Voice voice = VOICES.remove(entityId);
        if (voice == null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        client.getSoundManager().stop(voice.rush);
        client.getSoundManager().stop(voice.rumble);
    }

    /**
     * One line per audible funnel whenever it has settled at a distance it has not been reported at.
     *
     * <p>The two shares are the point of the line. The broadband rush and the low rumble are one sound
     * split by frequency because air does not carry the two the same way, and a line taken at one
     * distance says nothing about whether that split is working. Several, at distances that differ,
     * say it in one look.
     */
    private static void report(Minecraft client, long now, int entityId, Voice voice) {
        if (client.level == null) {
            return;
        }
        var camera = client.gameRenderer.getMainCamera().position();
        var entity = client.level.getEntity(entityId);
        if (entity == null) {
            return;
        }
        double distance = Math.sqrt(entity.distanceToSqr(camera.x, entity.getY(), camera.z));
        if (!voice.worthSaying(now, distance)) {
            return;
        }
        voice.reportedAt = distance;
        voice.settleAt = now + SETTLE_TICKS;
        VortexDread.LOGGER.info("[VortexDread] roar at {} blocks: rush {}, rumble {}, inflow {}",
                Math.round(distance),
                String.format(java.util.Locale.ROOT, "%.3f", voice.rush.gain()),
                String.format(java.util.Locale.ROOT, "%.3f", voice.rumble.gain()),
                String.format(java.util.Locale.ROOT, "%.3f", bed == null ? 0.0f : bed.gain()));
    }
}
