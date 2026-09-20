package oas.dreyka.vortexdread.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import oas.dreyka.vortexdread.VortexDread;
import oas.dreyka.vortexdread.atmosphere.SkySnapshot;

/**
 * One step of the sky on its way to a client, packed.
 *
 * <p>The server is the only place the simulation runs. A client was meant to replay it from the seed, which
 * the bit-for-bit parity between the card and the processor made possible, and the reason that is not what
 * ships is a measurement: a packed field is three kilobytes on a fair afternoon and eighty-five on a forced
 * overcast one, against one step every ten seconds. Replaying costs a second copy of the physics, a
 * catch-up for anyone who joins an hour in, and a client whose driver refuses to build the kernel watching
 * different weather. {@link CloudField} holds the numbers.
 *
 * <p>Self describing on purpose. The sizes, the cell edge and the cadence travel with every field rather
 * than in a handshake, which costs about thirty bytes on a packet of thousands and removes the state
 * machine a handshake needs: no ordering between two payloads, nothing to resend when an operator reloads
 * the options, and a client knows the shape of the sky from the first packet it receives.
 *
 * <p>The block is packed once, when the payload is built, and not inside the encoder. That is the whole
 * reason this holds bytes rather than a snapshot: encoding runs once per recipient, so packing there would
 * spend those fifteen milliseconds again for every player on the server.
 */
public record SkyFieldPayload(long step, int sizeX, int sizeY, int sizeZ, float cellSize,
        int ticksPerStep, byte[] packed) implements CustomPacketPayload {

    /**
     * Widest grid a client will build out of a packet.
     *
     * <p>Not the option's own ceiling, since the option is read on the server. This is what stops a hostile
     * packet from asking the client for gigabytes before a single byte is inflated.
     */
    private static final int MAX_CELLS_PER_AXIS = 512;

    public static final Type<SkyFieldPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(VortexDread.ID, "sky_field"));

    public static final StreamCodec<FriendlyByteBuf, SkyFieldPayload> STREAM_CODEC =
            CustomPacketPayload.codec(SkyFieldPayload::write, SkyFieldPayload::read);

    public static void register() {
        PayloadTypeRegistry.playS2C().register(TYPE, STREAM_CODEC);
    }

    /** Packs the field, so this is called on the thread that can afford it rather than on the tick. */
    public static SkyFieldPayload of(SkySnapshot sky, int ticksPerStep) {
        return new SkyFieldPayload(sky.step, sky.sizeX, sky.sizeY, sky.sizeZ, sky.cellSize, ticksPerStep,
                CloudField.pack(sky.cloudWater));
    }

    public static void sendTo(ServerPlayer player, SkyFieldPayload field) {
        ServerPlayNetworking.send(player, field);
    }

    /** Unpacks what arrived, on the client, into the form the renderer already reads. */
    public SkySnapshot sky() {
        return SkySnapshot.adopting(step, sizeX, sizeY, sizeZ, cellSize,
                CloudField.unpack(packed, sizeX * sizeY * sizeZ));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeVarLong(step);
        buffer.writeVarInt(sizeX);
        buffer.writeVarInt(sizeY);
        buffer.writeVarInt(sizeZ);
        buffer.writeFloat(cellSize);
        buffer.writeVarInt(ticksPerStep);
        buffer.writeByteArray(packed);
    }

    // Every number is checked here rather than where it is used, because the sizes are what decide how much
    // memory the inflate will ask for, and that happens on the network thread.
    private static SkyFieldPayload read(FriendlyByteBuf buffer) {
        long step = buffer.readVarLong();
        int sizeX = axis(buffer.readVarInt());
        int sizeY = axis(buffer.readVarInt());
        int sizeZ = axis(buffer.readVarInt());
        float cellSize = buffer.readFloat();
        if (!Float.isFinite(cellSize) || cellSize <= 0.0f) {
            throw new IllegalArgumentException("a cell cannot be " + cellSize + " metres across");
        }
        int ticksPerStep = buffer.readVarInt();
        if (ticksPerStep < 1) {
            throw new IllegalArgumentException("a cadence of " + ticksPerStep + " ticks per step");
        }
        return new SkyFieldPayload(step, sizeX, sizeY, sizeZ, cellSize, ticksPerStep,
                buffer.readByteArray(CloudField.MAX_PACKED_BYTES));
    }

    private static int axis(int cells) {
        if (cells < 4 || cells > MAX_CELLS_PER_AXIS) {
            throw new IllegalArgumentException("an axis of " + cells + " cells is outside 4 to "
                    + MAX_CELLS_PER_AXIS);
        }
        return cells;
    }
}
