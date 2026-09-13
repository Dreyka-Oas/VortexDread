package oas.dreyka.vortexdread.storm;

import oas.dreyka.vortexdread.config.domain.StormConfig;
import oas.dreyka.vortexdread.config.domain.TornadoConfig;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;

/**
 * The lightning inside the cloud deck.
 *
 * <p>Vanilla only knows one kind of lightning: a bolt from the sky to a block, which sets fire to it
 * and kills whatever was standing there. Nearly all real lightning never touches the ground. It stays
 * in the cloud, and what a person on the ground sees is the whole underside of the storm lighting up
 * from inside, several times a minute, with no bolt visible at all.
 *
 * <p>That is what this puts in the sky. Visual-only bolts, placed at cloud height rather than on the
 * terrain, so nothing burns and nobody dies from weather they cannot see coming. The light itself is
 * the shader's work: Iris hands the cloud pass the bolt's world position, and the patched pass scatters
 * from that point through the cloud volume instead of multiplying the whole sky by a constant.
 */
public final class CloudLightning {
    private CloudLightning() {
    }

    /** How far above the player's own level the cloud deck sits, in blocks. */
    private static final double DECK_ABOVE_PLAYER = 96.0;

    /** How far around a player a flash may be placed, in blocks. Beyond this nobody would see it. */
    private static final double SPREAD = 420.0;

    /** Closest a flash is placed, so the deck never lights up directly overhead every time. */
    private static final double MINIMUM_SPREAD = 48.0;

    /**
     * Whether the deck is allowed to light itself at all.
     *
     * <p>Three switches rather than one, because they answer different people. A server owner who runs
     * another weather mod turns the whole rebuild off; a player who finds the sky too busy lowers the
     * rate; someone who wants vanilla bolts and nothing else clears the flag.
     */
    public static boolean deckLit() {
        return StormConfig.rebuildStorms && StormConfig.extraLightning
                && StormConfig.cloudFlashesPerMinute > 0;
    }

    /** One tick of the cloud deck's own lightning. */
    public static void tick(ServerLevel level) {
        if (!deckLit()) {
            return;
        }
        if (!level.isThundering() || level.players().isEmpty()) {
            return;
        }
        double chance = StormConfig.cloudFlashesPerMinute / (60.0 * 20.0);
        if (level.random.nextDouble() >= chance) {
            return;
        }
        ServerPlayer witness = level.players().get(level.random.nextInt(level.players().size()));
        flashNear(level, witness.getX(), witness.getY(), witness.getZ());
    }

    /** Puts one intra-cloud flash somewhere in the deck above a point on the ground. */
    public static void flashNear(ServerLevel level, double x, double y, double z) {
        double angle = level.random.nextDouble() * Math.PI * 2.0;
        double distance = MINIMUM_SPREAD + level.random.nextDouble() * (SPREAD - MINIMUM_SPREAD);
        double height = y + DECK_ABOVE_PLAYER
                + level.random.nextDouble() * (TornadoConfig.funnelHeightAbove(y) - DECK_ABOVE_PLAYER) * 0.5;
        flashAt(level, x + distance * Math.cos(angle), height, z + distance * Math.sin(angle));
    }

    /** Puts one flash at an exact place, which is how the funnel and the mesocyclone use it. */
    public static void flashAt(ServerLevel level, double x, double y, double z) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
        if (bolt == null) {
            return;
        }
        bolt.setVisualOnly(true);
        bolt.snapTo(x, y, z);
        level.addFreshEntity(bolt);
    }
}
