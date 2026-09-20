package oas.dreyka.vortexdread.atmosphere.gpu;

import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_kernel;

import static org.jocl.CL.clEnqueueFillBuffer;
import static org.jocl.CL.clReleaseKernel;

/**
 * Making the flow divergence free, which is three kernels and a loop.
 *
 * <p>Its own class rather than a method beside the other stages because it is the only stage with an interior
 * shape: the other ten run once over the grid and this one runs sixty times over half of it, in an order that
 * has to be the processor's order exactly. Projection.java is the twin.
 */
final class PressureSolve implements AutoCloseable {

    private final SkyDevice device;
    private final SkyFields fields;
    private final long[] wholeGrid;

    private final cl_kernel measureDivergence;
    private final cl_kernel relaxPressure;
    private final cl_kernel subtractGradient;

    PressureSolve(SkyDevice device, SkyFields fields, long[] wholeGrid) {
        this.device = device;
        this.fields = fields;
        this.wholeGrid = wholeGrid;
        this.measureDivergence = device.kernel("measure_divergence");
        this.relaxPressure = device.kernel("relax_pressure");
        this.subtractGradient = device.kernel("subtract_gradient");
    }

    void run(int passes) {
        at(measureDivergence).with(fields.velocityX).with(fields.velocityY).with(fields.velocityZ)
                .with(fields.divergence).over(wholeGrid);
        // Starting from zero rather than from the previous step's answer. Carrying it over converges in
        // fewer passes and makes the step depend on how many steps came before it, which is a different
        // sky on a client that joined late.
        clEnqueueFillBuffer(device.queue(), fields.pressure, Pointer.to(new float[]{0.0f}),
                Sizeof.cl_float, 0, fields.fieldBytes, 0, null, null);

        // Half the cells per launch, and no synchronisation inside one: on this stencil no cell of a colour
        // touches another of its own, so the parallel sweep lands on the numbers the sequential one would.
        for (int pass = 0; pass < passes; pass++) {
            for (int colour = 0; colour < 2; colour++) {
                at(relaxPressure).with(fields.pressure).with(fields.divergence).with(colour)
                        .over(wholeGrid[0] / 2, wholeGrid[1], wholeGrid[2]);
            }
        }

        at(subtractGradient).with(fields.velocityX).with(fields.velocityY).with(fields.velocityZ)
                .with(fields.pressure).over(wholeGrid);
    }

    @Override
    public void close() {
        clReleaseKernel(measureDivergence);
        clReleaseKernel(relaxPressure);
        clReleaseKernel(subtractGradient);
    }

    private Dispatch at(cl_kernel kernel) {
        return new Dispatch(device.queue(), kernel);
    }
}
