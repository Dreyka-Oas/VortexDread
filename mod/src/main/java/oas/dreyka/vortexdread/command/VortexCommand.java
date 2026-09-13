package oas.dreyka.vortexdread.command;

import oas.dreyka.vortexdread.VortexDread;
import oas.dreyka.vortexdread.compute.AdvectionCompute;
import oas.dreyka.vortexdread.config.domain.ComputeConfig;
import oas.dreyka.vortexdread.config.domain.StormConfig;
import oas.dreyka.vortexdread.entity.TornadoEntity;
import oas.dreyka.vortexdread.entity.VortexEntities;
import oas.dreyka.vortexdread.tornado.RatingRoll;
import oas.dreyka.vortexdread.wind.EfScale;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec2Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

/** Operator control over the weather this mod adds. */
public final class VortexCommand {
    private VortexCommand() {
    }

    /** How far around the caller a listing or a clear reaches, in blocks. */
    private static final double SEARCH_RADIUS = 8192.0;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // The same gate vanilla puts on /weather, since this is the same kind of decision.
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("vortex")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));

        root.then(Commands.literal("spawn")
                .executes(context -> spawn(context.getSource(), here(context.getSource()), null))
                .then(Commands.argument("rating", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            for (EfScale scale : EfScale.values()) {
                                builder.suggest(scale.name().toLowerCase(Locale.ROOT));
                            }
                            return builder.buildFuture();
                        })
                        .executes(context -> spawn(context.getSource(), here(context.getSource()),
                                parse(StringArgumentType.getString(context, "rating"))))
                        .then(Commands.argument("pos", Vec2Argument.vec2())
                                .executes(context -> spawn(context.getSource(),
                                        Vec2Argument.getVec2(context, "pos"),
                                        parse(StringArgumentType.getString(context, "rating")))))));

        root.then(Commands.literal("list").executes(context -> list(context.getSource())));
        root.then(Commands.literal("clear").executes(context -> clear(context.getSource())));
        root.then(Commands.literal("storms")
                .then(Commands.literal("on").executes(context -> storms(context.getSource(), true)))
                .then(Commands.literal("off").executes(context -> storms(context.getSource(), false))));
        root.then(Commands.literal("compute").executes(context -> compute(context.getSource())));

        dispatcher.register(root);
    }

    private static Vec2 here(CommandSourceStack source) {
        Vec3 position = source.getPosition();
        return new Vec2((float) position.x, (float) position.z);
    }

    private static EfScale parse(String written) {
        for (EfScale scale : EfScale.values()) {
            if (scale.name().equalsIgnoreCase(written)) {
                return scale;
            }
        }
        return null;
    }

    private static int spawn(CommandSourceStack source, Vec2 where, EfScale wanted) {
        ServerLevel level = source.getLevel();
        java.util.random.RandomGenerator random = oas.dreyka.vortexdread.VortexRandom.of(level.random);
        float peak = wanted == null
                ? RatingRoll.drawPeakWind(random, StormConfig.ratingBias)
                : RatingRoll.windInside(wanted, random);

        TornadoEntity tornado = TornadoEntity.spawn(level, where.x, where.y, peak);
        if (tornado == null) {
            return 0;
        }
        EfScale rating = EfScale.fromWind(peak);
        VortexDread.LOGGER.info("[VortexDread] tornado spawned: {} peak {} m/s at {} {} in {}",
                rating.name(), String.format(Locale.ROOT, "%.1f", peak),
                Math.round(tornado.getX()), Math.round(tornado.getZ()), level.dimension().identifier());
        source.sendSuccess(() -> Component.translatable("vortexdread.command.spawned",
                Component.translatable(rating.translationKey()),
                Math.round(tornado.getX()), Math.round(tornado.getY()), Math.round(tornado.getZ())), true);
        return 1;
    }

    private static int list(CommandSourceStack source) {
        List<TornadoEntity> alive = nearby(source);
        if (alive.isEmpty()) {
            source.sendFailure(Component.translatable("vortexdread.command.none"));
            return 0;
        }
        Vec3 from = source.getPosition();
        for (TornadoEntity tornado : alive) {
            // The peak rather than the wind of the moment, for the same reason a survey team rates a
            // tornado on its worst damage: a decaying EF5 still has an EF5's core, and calling it an
            // EF2 while it is thirty blocks wide reads as a bug to whoever asked.
            source.sendSuccess(() -> Component.translatable("vortexdread.command.listed",
                    Component.translatable(tornado.peakRating().translationKey()),
                    Component.translatable(tornado.stage().translationKey()),
                    Math.round(Math.sqrt(tornado.distanceToSqr(from))),
                    Math.round(tornado.coreRadius()),
                    Math.round(tornado.groundLoad() * 100.0f)), false);
        }
        return alive.size();
    }

    private static int clear(CommandSourceStack source) {
        List<TornadoEntity> alive = nearby(source);
        for (TornadoEntity tornado : alive) {
            tornado.discard();
        }
        int count = alive.size();
        source.sendSuccess(() -> Component.translatable("vortexdread.command.cleared", count), true);
        return count;
    }

    private static int storms(CommandSourceStack source, boolean on) {
        StormConfig.naturalTornadoes = on;
        oas.dreyka.vortexdread.config.ConfigIo.save();
        source.sendSuccess(() -> Component.translatable(
                on ? "vortexdread.command.storm_on" : "vortexdread.command.storm_off"), true);
        return 1;
    }

    /**
     * Where the advection is running and how much has gone each way.
     *
     * <p>The two counts are the point. A dispatcher that falls back on its own leaves a broken card path
     * looking exactly like a working one: the game stays playable, the log stays quiet, and the win is
     * gone. Two numbers that both move is the only thing that says which path a run actually took.
     */
    private static int compute(CommandSourceStack source) {
        String device = AdvectionCompute.deviceName();
        Component where;
        if (device != null) {
            where = Component.translatable("vortexdread.gpu.active", device);
        } else if (ComputeConfig.useGpu) {
            where = Component.translatable("vortexdread.gpu.unavailable");
        } else {
            where = Component.translatable("vortexdread.gpu.disabled");
        }
        long onCard = AdvectionCompute.steppedOnCard();
        long onProcessor = AdvectionCompute.steppedOnProcessor();
        source.sendSuccess(() -> Component.translatable("vortexdread.command.compute",
                where, onCard, onProcessor), false);
        return 1;
    }

    private static List<TornadoEntity> nearby(CommandSourceStack source) {
        Vec3 from = source.getPosition();
        AABB box = new AABB(from, from).inflate(SEARCH_RADIUS);
        return source.getLevel().getEntities(VortexEntities.TORNADO, box, tornado -> true);
    }
}
