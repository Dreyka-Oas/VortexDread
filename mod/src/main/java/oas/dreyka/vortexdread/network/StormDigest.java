package oas.dreyka.vortexdread.network;

import oas.dreyka.vortexdread.entity.TornadoEntity;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Everything needed to draw one funnel, and nothing needed to simulate it.
 *
 * <p>The funnel is drawn from a handful of numbers, so a client can put a storm on screen without the
 * entity behind it existing there at all. That is what lets a tornado stand on a horizon a terrain
 * mod is drawing and the vanilla entity tracker is not: the wind, the damage and the debris stay on
 * the server where they belong, and the shape travels.
 *
 * @param id         the entity this came off, so an update replaces rather than duplicates
 * @param x          world position, absolute, since the camera moves between packets
 * @param groundY    height of the land under the foot
 * @param coreRadius core radius in blocks
 * @param height     ground to cloud base in blocks
 * @param descent    how far down out of the cloud the funnel has come, 0 to 1
 * @param tint       packed rgb of whatever it has picked up
 * @param groundLoad how much of the ground it currently has in the air, 0 to 1
 * @param flash      how lit it is by a bolt inside it right now
 * @param windShare  its current turn against the fastest it will ever turn
 */
public record StormDigest(
        int id,
        double x,
        double z,
        float groundY,
        float coreRadius,
        float height,
        float descent,
        int tint,
        float groundLoad,
        float flash,
        float windShare) {

    public static final StreamCodec<RegistryFriendlyByteBuf, StormDigest> CODEC = StreamCodec.of(
            StormDigest::write, StormDigest::read);

    /** Reads one off a tornado the client already has, so both paths describe a storm the same way. */
    public static StormDigest of(TornadoEntity tornado) {
        return of(tornado, 1.0f);
    }

    /**
     * The same, placed where the storm stands partway through the tick being drawn.
     *
     * <p>A tornado crosses four blocks a second, so a position taken at the tick boundary and held for
     * the three frames after it makes the column jump rather than travel. The entity renderer is handed
     * a partial tick for exactly this reason; anything drawing the same storm beside it has to use the
     * same one, or the two funnels sit a stride apart for most of every tick.
     */
    public static StormDigest of(TornadoEntity tornado, float partialTick) {
        var where = tornado.getPosition(partialTick);
        float peak = Math.max(1.0f, tornado.peakWind());
        return new StormDigest(
                tornado.getId(),
                where.x,
                where.z,
                (float) tornado.groundY(),
                tornado.coreRadius(),
                tornado.funnelHeight(),
                (float) tornado.descent(),
                tornado.tint(),
                tornado.groundLoad(),
                tornado.flash(),
                Math.min(1.0f, tornado.wind() / peak));
    }

    private static void write(RegistryFriendlyByteBuf buffer, StormDigest digest) {
        ByteBufCodecs.VAR_INT.encode(buffer, digest.id);
        buffer.writeDouble(digest.x);
        buffer.writeDouble(digest.z);
        buffer.writeFloat(digest.groundY);
        buffer.writeFloat(digest.coreRadius);
        buffer.writeFloat(digest.height);
        buffer.writeFloat(digest.descent);
        ByteBufCodecs.INT.encode(buffer, digest.tint);
        buffer.writeFloat(digest.groundLoad);
        buffer.writeFloat(digest.flash);
        buffer.writeFloat(digest.windShare);
    }

    private static StormDigest read(RegistryFriendlyByteBuf buffer) {
        return new StormDigest(
                ByteBufCodecs.VAR_INT.decode(buffer),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                ByteBufCodecs.INT.decode(buffer),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat());
    }
}
