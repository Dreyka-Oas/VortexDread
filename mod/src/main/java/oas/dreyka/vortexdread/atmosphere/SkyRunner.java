package oas.dreyka.vortexdread.atmosphere;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The sky advancing on its own thread, at the pace of the clock.
 *
 * <p>One thread, and it is the only one that ever touches the solver. That is not caution about the processor
 * path, whose arrays would survive being read from anywhere, it is what a command queue requires: the card is
 * opened, fed and released on the same thread for its whole life.
 *
 * <p>Off the tick because of what a step costs on a machine with no card. Measured on the shipped grid, the
 * processor takes three hundred milliseconds for one step, six times the fifty a tick is allowed, so a sky
 * stepped inline would stall the server for a third of a second every time it advanced. The card takes two,
 * and it goes to the same thread anyway rather than being a second code path nobody exercises.
 *
 * <p>Nothing here knows about Minecraft. It counts ticks handed to it and steps when enough have gone by,
 * which is what keeps the cadence a number rather than a wall clock: the pace holds whether the sky is on
 * a card or on a processor, and the same count means the same thing on a server that is running behind.
 */
public final class SkyRunner implements AutoCloseable {

    /** How long close() waits for the card's buffers to come back before giving up on them. */
    private static final long SHUTDOWN_SECONDS = 5L;

    private final ExecutorService worker;
    private final int ticksPerStep;
    private final Consumer<SkySnapshot> afterStep;
    private final AtomicReference<String> unreadNotice = new AtomicReference<>();
    private final SkyFeed feed = new SkyFeed();

    private volatile SkySolver solver;
    private Future<?> inFlight;
    private long ticks;

    // The tick a step was launched on rather than the tick it landed on: that one is a multiple of the
    // cadence on every machine, where the landing moves with the hardware, and a renderer that paced itself
    // off the landing would run a different clock on every box. Volatile because it is written here on the
    // server thread and read by the worker once the step returns.
    private volatile long ticksAtLastStep;

    public SkyRunner(Supplier<SkySolver> open, int ticksPerStep) {
        this(open, ticksPerStep, snapshot -> {
        });
    }

    /**
     * @param open builds the solver, on the worker thread. It has to answer rather than throw: the choice
     *     between the card and the processor, and the fallback when the card refuses, belong to the caller,
     *     since only the caller can say which one an operator asked for
     * @param ticksPerStep server ticks between two steps
     * @param afterStep run on the worker with each published step, for work that would otherwise land on
     *     the tick. Packing a field for the network is fifteen milliseconds on an overcast sky, a third of
     *     the budget for a tick, and the worker has just gone idle. It runs after publication rather than
     *     instead of it, so a hook that throws stops the sky in the same way a step that throws does
     */
    public SkyRunner(Supplier<SkySolver> open, int ticksPerStep, Consumer<SkySnapshot> afterStep) {
        this.ticksPerStep = Math.max(1, ticksPerStep);
        this.afterStep = afterStep;
        this.worker = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "VortexDread sky");
            thread.setDaemon(true);
            return thread;
        });
        this.inFlight = worker.submit(() -> {
            SkySolver opened = open.get();
            solver = opened;
            unreadNotice.set(opened.description());
        });
    }

    /** Called once per server tick. Enqueues a step when one is due and the last one has finished. */
    public void onTick() {
        ticks++;
        if (!finished()) {
            // A step still running when the next is due. Unreachable on either path at the shipped settings,
            // so rather than queueing up a backlog the sky quietly runs slow and the clock keeps its meaning.
            return;
        }
        SkySolver sky = solver;
        if (sky == null || ticks % ticksPerStep != 0) {
            return;
        }
        ticksAtLastStep = ticks;
        inFlight = worker.submit(() -> {
            sky.step();
            publish(sky);
        });
    }

    /** The two most recent steps and where between them this frame sits, or null before the second one. */
    public SkyView view(long tick, float partialTick) {
        return feed.view(tick, partialTick);
    }

    /**
     * The line an operator needs to see, once, or null when there is nothing new.
     *
     * <p>Handed over rather than logged here so the caller logs it from the server thread. A line printed off
     * a worker is a line that can land in the middle of another, and this is the one line that says whether
     * the card was reached.
     */
    public String takeNotice() {
        return unreadNotice.getAndSet(null);
    }

    public long stepsTaken() {
        SkySolver sky = solver;
        return sky == null ? 0L : sky.stepsTaken();
    }

    @Override
    public void close() {
        worker.submit(() -> {
            SkySolver sky = solver;
            solver = null;
            if (sky != null) {
                sky.close();
            }
        });
        worker.shutdown();
        try {
            worker.awaitTermination(SHUTDOWN_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    // Only ever called from the worker, right after the step it describes. The copy is taken here rather
    // than by the feed because this is the last moment the grid is still the state that was just computed.
    private void publish(SkySolver sky) {
        SkySnapshot published = SkySnapshot.of(sky.grid(), sky.stepsTaken());
        feed.offer(published, ticksAtLastStep, ticksPerStep);
        afterStep.accept(published);
    }

    // A task that threw leaves the future done and the solver as it was, so without this the sky would go
    // quiet and nothing would say why. Reported through the same notice the boot line uses.
    private boolean finished() {
        if (inFlight == null) {
            return true;
        }
        if (!inFlight.isDone()) {
            return false;
        }
        try {
            inFlight.get();
        } catch (ExecutionException failed) {
            solver = null;
            unreadNotice.set("the sky stopped: " + failed.getCause());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        inFlight = null;
        return true;
    }
}
