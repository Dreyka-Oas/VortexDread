package oas.dreyka.vortexdread.atmosphere.gpu;

import oas.dreyka.vortexdread.atmosphere.AtmosphereGrid;
import oas.dreyka.vortexdread.atmosphere.AtmosphereProfile;
import oas.dreyka.vortexdread.atmosphere.AtmosphereSettings;
import oas.dreyka.vortexdread.atmosphere.SkySolver;
import oas.dreyka.vortexdread.atmosphere.SurfaceDrive;
import oas.dreyka.vortexdread.atmosphere.cpu.Advection;
import oas.dreyka.vortexdread.atmosphere.cpu.Boundaries;
import oas.dreyka.vortexdread.atmosphere.cpu.Precipitation;

import static org.jocl.CL.clFinish;

/**
 * The same patch of sky as {@link oas.dreyka.vortexdread.atmosphere.Atmosphere}, stepped on the card.
 *
 * <p>The processor path is the reference and this has to agree with it, not approximately but to the bit. The
 * three rules that make that possible are worth stating together, because each of them looks like a small
 * choice and none of them is. Every transcendental goes through the portable exponential rather than the
 * library. The program is built with no flag that reorders arithmetic. And what is left is a red-black solve
 * whose parallel sweep happens to land on exactly the numbers the sequential one does, because no cell of a
 * colour touches another of its own.
 *
 * <p>The state stays on the device between steps. Only two things cross the bus: a handful of floats every
 * step, for the one reduction the slicing needs, and a full download whenever something wants to look at the
 * result. The grid handed back by {@link #grid()} is a copy of what the card holds as of the last step, and
 * it is fetched when asked for rather than after every step, so a run nobody watches costs nothing extra.
 */
public final class GpuAtmosphere implements SkySolver {

    /** Work items per group in the reduction. A power of two, since the tree halves the lanes each round. */
    private static final int REDUCTION_LANES = 256;

    /** Groups in the reduction. Enough to fill any card, few enough that the host finishes it in a glance. */
    private static final int REDUCTION_GROUPS = 64;

    private final AtmosphereSettings settings;
    private final AtmosphereGrid grid;
    private final AtmosphereProfile profile;
    private final SurfaceDrive drive;
    private final SkyDevice device;
    private final SkyFields fields;
    private final SkyStages stages;

    private long stepsTaken;
    private boolean hostCopyIsStale;

    public GpuAtmosphere(AtmosphereSettings settings, long seed, int wantedDeviceIndex) {
        this.settings = settings;
        this.grid = settings.newGrid();
        this.profile = settings.newProfile();
        this.drive = SurfaceDrive.of(settings, profile, seed);
        this.profile.reset(grid);

        this.device = new SkyDevice(grid, wantedDeviceIndex);
        // Rounded down to a power of two rather than merely capped: the reduction halves its lanes each
        // round, and a group of 384 would drop the top third of itself on the first halving.
        int lanes = Integer.highestOneBit((int) Math.min(REDUCTION_LANES, device.maxWorkGroup()));
        this.fields = new SkyFields(device, grid, profile, REDUCTION_GROUPS);
        this.stages = new SkyStages(device, fields, grid, lanes, REDUCTION_GROUPS);
        this.fields.upload(grid);
    }

    @Override
    public String description() {
        return device.report();
    }

    public String deviceName() {
        return device.deviceName();
    }

    @Override
    public AtmosphereSettings settings() {
        return settings;
    }

    public AtmosphereProfile profile() {
        return profile;
    }

    @Override
    public long stepsTaken() {
        return stepsTaken;
    }

    @Override
    public float elapsedSeconds() {
        return stepsTaken * settings.timeStep();
    }

    /** The state as the card last left it. Fetched on the first ask after a step and cached until the next. */
    @Override
    public AtmosphereGrid grid() {
        if (hostCopyIsStale) {
            fields.download(grid);
            hostCopyIsStale = false;
        }
        return grid;
    }

    /**
     * Advances the sky by one time step.
     *
     * <p>The same order as the processor path, for the same reasons: advection before the forces, or the
     * forces land on air that is about to be replaced; everything touching the velocity before the
     * projection, the boundaries included, since the projection is what leaves the flow conserving mass and
     * whatever comes after it undoes that; condensation on air whose position and temperature are both
     * settled; and rain last, since only water that has already become droplets can fall.
     */
    @Override
    public void step() {
        float timeStep = settings.timeStep();

        stages.forceSurface(drive, elapsedSeconds(), timeStep);
        advect(timeStep);
        stages.applyBuoyancy(timeStep);
        stages.dampLid(Boundaries.firstSpongeRow(grid.sizeY), timeStep);
        stages.project(settings.pressureIterations());
        stages.changePhase();
        stages.fallRain(Precipitation.shareFor(timeStep));

        stepsTaken++;
        hostCopyIsStale = true;
    }

    @Override
    public void step(int count) {
        for (int i = 0; i < count; i++) {
            step();
        }
    }

    /** Waits for everything queued to have actually run, which only a measurement needs. */
    public void settle() {
        clFinish(device.queue());
    }

    @Override
    public void close() {
        stages.close();
        fields.close();
        device.close();
    }

    private void advect(float timeStep) {
        int slices = Advection.slicesFor(stages.fastestFlow(), timeStep, grid.cellSize);
        float courant = timeStep / (slices * grid.cellSize);

        for (int slice = 0; slice < slices; slice++) {
            stages.traceMomentum(courant);
            stages.moveScalar(fields.potentialTemperature, courant, false);
            stages.moveScalar(fields.vapour, courant, true);
            stages.moveScalar(fields.cloudWater, courant, true);
            stages.adoptTracedVelocity();
        }
    }
}
