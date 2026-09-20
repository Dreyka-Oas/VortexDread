package oas.dreyka.vortexdread.atmosphere.gpu;

import oas.dreyka.vortexdread.atmosphere.AtmosphereGrid;
import oas.dreyka.vortexdread.atmosphere.SurfaceDrive;

import org.jocl.Sizeof;
import org.jocl.cl_kernel;
import org.jocl.cl_mem;

import static org.jocl.CL.clEnqueueCopyBuffer;
import static org.jocl.CL.clReleaseKernel;

/**
 * One method per stage of the step, each of them a kernel and the shape to run it over.
 *
 * <p>Nothing here decides anything. The order of the stages, how many slices the advection needs and how many
 * passes the pressure gets are the simulation's business and live next door; this is the layer that knows
 * which kernel that means and over how many work items.
 */
final class SkyStages implements AutoCloseable {

    private final SkyDevice device;
    private final SkyFields fields;
    private final long[] wholeGrid;
    private final int sizeY;
    private final int cellCount;
    private final int reductionLanes;
    private final int reductionGroups;
    private final float[] partial;

    private final cl_kernel forceSurface;
    private final cl_kernel traceMomentum;
    private final cl_kernel moveScalar;
    private final cl_kernel reduceFastest;
    private final cl_kernel applyBuoyancy;
    private final cl_kernel dampLid;
    private final cl_kernel changePhase;
    private final cl_kernel fallRain;
    private final PressureSolve pressure;

    SkyStages(SkyDevice device, SkyFields fields, AtmosphereGrid grid, int lanes, int groups) {
        this.device = device;
        this.fields = fields;
        this.wholeGrid = new long[]{grid.sizeX, grid.sizeZ, grid.sizeY};
        this.sizeY = grid.sizeY;
        this.cellCount = grid.cellCount();
        this.reductionLanes = lanes;
        this.reductionGroups = groups;
        this.partial = new float[groups];

        this.forceSurface = device.kernel("force_surface");
        this.traceMomentum = device.kernel("trace_momentum");
        this.moveScalar = device.kernel("move_scalar");
        this.reduceFastest = device.kernel("reduce_fastest");
        this.applyBuoyancy = device.kernel("apply_buoyancy");
        this.dampLid = device.kernel("damp_lid");
        this.changePhase = device.kernel("change_phase");
        this.fallRain = device.kernel("fall_rain");
        this.pressure = new PressureSolve(device, fields, wholeGrid);
    }

    void forceSurface(SurfaceDrive drive, float elapsedSeconds, float timeStep) {
        at(forceSurface).with(fields.potentialTemperature).with(fields.vapour).with(drive.seed())
                .with(drive.latticePerCell()).with(drive.time(elapsedSeconds))
                .with(drive.relaxation(timeStep)).with(drive.temperatureAmplitude())
                .with(drive.humidityAmplitude()).with(drive.surfaceTemperature())
                .with(drive.surfaceVapour())
                .over(wholeGrid[0], wholeGrid[1]);
    }

    void traceMomentum(float courant) {
        at(traceMomentum).with(fields.velocityX).with(fields.velocityY).with(fields.velocityZ)
                .with(fields.tracedX).with(fields.tracedY).with(fields.tracedZ).with(courant)
                .over(wholeGrid);
    }

    /** Moves one scalar and copies it back, which is what the processor path's array copy amounts to. */
    void moveScalar(cl_mem field, float courant, boolean conserveTotal) {
        at(moveScalar).with(fields.velocityX).with(fields.velocityY).with(fields.velocityZ).with(field)
                .with(fields.advected).with(courant).with(conserveTotal ? 1 : 0)
                .over(wholeGrid);
        copy(fields.advected, field);
    }

    /** The traced velocity waits in scratch until the three scalars have been moved by the old flow. */
    void adoptTracedVelocity() {
        copy(fields.tracedX, fields.velocityX);
        copy(fields.tracedY, fields.velocityY);
        copy(fields.tracedZ, fields.velocityZ);
    }

    float fastestFlow() {
        at(reduceFastest).with(fields.velocityX).with(fields.velocityY).with(fields.velocityZ)
                .with(fields.fastest).shared(reductionLanes)
                .inGroupsOf(new long[]{(long) reductionLanes * reductionGroups},
                        new long[]{reductionLanes});
        fields.read(fields.fastest, partial, (long) Sizeof.cl_float * reductionGroups);

        float fastest = 0.0f;
        for (float value : partial) {
            fastest = Math.max(fastest, value);
        }
        return fastest;
    }

    void applyBuoyancy(float timeStep) {
        at(applyBuoyancy).with(fields.velocityY).with(fields.potentialTemperature).with(fields.vapour)
                .with(fields.cloudWater).with(fields.ambientVirtual).with(timeStep)
                .over(wholeGrid[0], wholeGrid[1], sizeY - 1L);
    }

    void dampLid(int firstRow, float timeStep) {
        at(dampLid).with(fields.velocityX).with(fields.velocityY).with(fields.velocityZ).with(firstRow)
                .with(timeStep)
                .over(wholeGrid[0], wholeGrid[1], sizeY - (long) firstRow);
    }

    void project(int passes) {
        pressure.run(passes);
    }

    void changePhase() {
        at(changePhase).with(fields.potentialTemperature).with(fields.vapour).with(fields.cloudWater)
                .with(fields.exner).with(fields.pressureByRow).over(wholeGrid);
    }

    void fallRain(float share) {
        at(fallRain).with(fields.cloudWater).with(share).over(cellCount);
    }

    @Override
    public void close() {
        pressure.close();
        for (cl_kernel kernel : new cl_kernel[]{forceSurface, traceMomentum, moveScalar, reduceFastest,
                applyBuoyancy, dampLid, changePhase, fallRain}) {
            clReleaseKernel(kernel);
        }
    }

    private Dispatch at(cl_kernel kernel) {
        return new Dispatch(device.queue(), kernel);
    }

    private void copy(cl_mem from, cl_mem into) {
        clEnqueueCopyBuffer(device.queue(), from, into, 0, 0, fields.fieldBytes, 0, null, null);
    }
}
