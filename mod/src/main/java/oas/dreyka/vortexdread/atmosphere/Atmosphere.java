package oas.dreyka.vortexdread.atmosphere;

import oas.dreyka.vortexdread.atmosphere.cpu.Advection;
import oas.dreyka.vortexdread.atmosphere.cpu.Boundaries;
import oas.dreyka.vortexdread.atmosphere.cpu.Buoyancy;
import oas.dreyka.vortexdread.atmosphere.cpu.PhaseChange;
import oas.dreyka.vortexdread.atmosphere.cpu.Precipitation;
import oas.dreyka.vortexdread.atmosphere.cpu.Projection;
import oas.dreyka.vortexdread.atmosphere.cpu.SurfaceForcing;

/**
 * One patch of simulated sky, and the stages that advance it.
 *
 * <p>The processor path, and the reference the card has to agree with. Nothing here knows about
 * Minecraft: it takes SI units, advances by seconds, and hands back a field of condensed water. What
 * that field looks like drawn across a sky is the renderer's business.
 *
 * <p>Harris also puts vorticity confinement between the forces and the projection, and it is not here.
 * It exists to put back the small scale rotation a backward traced advection smears away, and counting
 * faces smears away much less of it, so the term would be paying for a problem this solver does not have.
 * What it would bring instead is an energy source with no physical counterpart: the force it applies
 * creates the rotation it measures to decide how hard to push, which measures out as a field accelerating
 * past thirty metres a second and never settling. Sub-cell detail belongs to the renderer, where it costs
 * nothing and cannot feed back.
 *
 * <p>The order of the stages is not interchangeable. Advection has to run before the forces, or the
 * forces are applied to air that is about to be replaced. Everything that touches the velocity has to run
 * before the projection, the boundaries included, because the projection is what leaves the flow
 * conserving mass and whatever comes after it undoes that. Condensation comes after, on air whose position
 * and temperature are both settled, and rain after condensation, since only water that has already become
 * droplets can fall.
 */
public final class Atmosphere implements SkySolver {

    private final AtmosphereSettings settings;
    private final AtmosphereGrid grid;
    private final AtmosphereProfile profile;
    private final AtmosphereScratch scratch;
    private final SurfaceForcing forcing;

    private long stepsTaken;

    public Atmosphere(AtmosphereSettings settings, long seed) {
        this.settings = settings;
        this.grid = settings.newGrid();
        this.profile = settings.newProfile();
        this.scratch = new AtmosphereScratch(grid.cellCount());
        this.forcing = new SurfaceForcing(SurfaceDrive.of(settings, profile, seed));
        this.profile.reset(grid);
    }

    @Override
    public AtmosphereSettings settings() {
        return settings;
    }

    @Override
    public AtmosphereGrid grid() {
        return grid;
    }

    public AtmosphereProfile profile() {
        return profile;
    }

    @Override
    public long stepsTaken() {
        return stepsTaken;
    }

    /** The grid is already the live one here, so resuming is the count and nothing else. */
    @Override
    public void resumeAt(long step) {
        this.stepsTaken = step;
    }

    /** Simulated seconds since the first step. */
    @Override
    public float elapsedSeconds() {
        return stepsTaken * settings.timeStep();
    }

    @Override
    public String description() {
        return "processor";
    }

    /** Nothing to release: the arrays go when this does. Here so the two paths close the same way. */
    @Override
    public void close() {
    }

    /** Advances the sky by one time step. */
    @Override
    public void step() {
        float timeStep = settings.timeStep();

        forcing.apply(grid, elapsedSeconds(), timeStep);
        Advection.advect(grid, scratch, timeStep);
        Buoyancy.apply(grid, profile, timeStep);
        Boundaries.apply(grid, timeStep);
        Projection.apply(grid, scratch, settings.pressureIterations());
        PhaseChange.apply(grid, profile);
        Precipitation.apply(grid, timeStep);

        stepsTaken++;
    }

    @Override
    public void step(int count) {
        for (int i = 0; i < count; i++) {
            step();
        }
    }

    /** Total condensed water in the domain, kg per kg of air summed over every cell. */
    public double totalCloudWater() {
        double total = 0.0;
        for (float value : grid.cloudWater) {
            total += value;
        }
        return total;
    }

    /** Total water still airborne, both phases. The ground adds to it and rain takes from it. */
    public double totalWater() {
        double total = 0.0;
        for (int i = 0; i < grid.cellCount(); i++) {
            total += grid.vapour[i] + grid.cloudWater[i];
        }
        return total;
    }

    /** The highest cell row holding enough droplets to be worth drawing, or -1 when the sky is clear. */
    public int cloudTopRow(float threshold) {
        for (int y = grid.sizeY - 1; y >= 0; y--) {
            if (rowHoldsCloud(y, threshold)) {
                return y;
            }
        }
        return -1;
    }

    /** The lowest such row, which is the flat base a cumulus field shares. */
    public int cloudBaseRow(float threshold) {
        for (int y = 0; y < grid.sizeY; y++) {
            if (rowHoldsCloud(y, threshold)) {
                return y;
            }
        }
        return -1;
    }

    private boolean rowHoldsCloud(int y, float threshold) {
        int rowStart = grid.index(0, y, 0);
        int rowEnd = rowStart + grid.sizeX * grid.sizeZ;
        for (int index = rowStart; index < rowEnd; index++) {
            if (grid.cloudWater[index] > threshold) {
                return true;
            }
        }
        return false;
    }
}
