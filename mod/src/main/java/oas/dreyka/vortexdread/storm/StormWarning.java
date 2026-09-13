package oas.dreyka.vortexdread.storm;

import oas.dreyka.vortexdread.config.domain.StormConfig;
import oas.dreyka.vortexdread.entity.TornadoEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** What a player is told, and how little of it. */
public final class StormWarning {
    private StormWarning() {
    }

    /** The eight points a warning is given in, starting due east and turning toward south. */
    private static final String[] POINTS = {
            "east", "southeast", "south", "southwest", "west", "northwest", "north", "northeast"
    };

    /** Tells whoever is close enough that something is on the ground, and roughly where. */
    public static void announce(ServerLevel level, TornadoEntity tornado) {
        double radius = StormConfig.warningRadius;
        for (ServerPlayer player : level.players()) {
            double dx = tornado.getX() - player.getX();
            double dz = tornado.getZ() - player.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance > radius) {
                continue;
            }
            player.displayClientMessage(Component.translatable("vortexdread.warning.spotted",
                    Math.round(distance),
                    Component.translatable("vortexdread.direction." + bearing(dx, dz)))
                    .withStyle(ChatFormatting.GOLD), false);
        }
    }

    /** Which of the eight points a displacement falls in. */
    public static String bearing(double dx, double dz) {
        double angle = Math.atan2(dz, dx);
        int index = (int) Math.round(angle / (Math.PI / 4.0));
        return POINTS[Math.floorMod(index, POINTS.length)];
    }
}
