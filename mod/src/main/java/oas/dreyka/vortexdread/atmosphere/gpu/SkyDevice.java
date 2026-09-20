package oas.dreyka.vortexdread.atmosphere.gpu;

import java.nio.charset.StandardCharsets;

import oas.dreyka.vortexdread.atmosphere.AtmosphereGrid;

import org.jocl.Pointer;
import org.jocl.cl_command_queue;
import org.jocl.cl_context;
import org.jocl.cl_context_properties;
import org.jocl.cl_device_id;
import org.jocl.cl_kernel;
import org.jocl.cl_program;
import org.jocl.cl_queue_properties;

import static org.jocl.CL.CL_CONTEXT_PLATFORM;
import static org.jocl.CL.CL_PROGRAM_BUILD_LOG;
import static org.jocl.CL.clBuildProgram;
import static org.jocl.CL.clCreateCommandQueueWithProperties;
import static org.jocl.CL.clCreateContext;
import static org.jocl.CL.clCreateKernel;
import static org.jocl.CL.clCreateProgramWithSource;
import static org.jocl.CL.clGetProgramBuildInfo;
import static org.jocl.CL.clReleaseCommandQueue;
import static org.jocl.CL.clReleaseContext;
import static org.jocl.CL.clReleaseProgram;
import static org.jocl.CL.setExceptionsEnabled;

/**
 * The card the sky runs on: the context, the queue and the built program.
 *
 * <p>Construction throws on anything it does not like, which is the whole contract. The caller degrades to the
 * processor path and the sky looks the same, so the card is never load bearing.
 */
public final class SkyDevice implements AutoCloseable {

    private final DeviceRoster roster;
    private final cl_context context;
    private final cl_command_queue queue;
    private final cl_program program;
    private final long maxWorkGroup;

    public SkyDevice(AtmosphereGrid grid, int wantedIndex) {
        // A JOCL-wide switch rather than a per-context one. JOCL is shaded into this jar, so this reaches this
        // mod's copy and no other mod's.
        setExceptionsEnabled(true);

        this.roster = DeviceRoster.gather(wantedIndex);
        cl_context_properties properties = new cl_context_properties();
        properties.addProperty(CL_CONTEXT_PLATFORM, roster.platform());
        this.context = clCreateContext(properties, 1, new cl_device_id[]{roster.device()}, null, null, null);
        this.queue = clCreateCommandQueueWithProperties(context, roster.device(), new cl_queue_properties(),
                null);
        this.program = build(grid);
        this.maxWorkGroup = DeviceInfo.maxWorkGroupSize(roster.device());
    }

    /** The picked device, and beside it every one it was picked over. This is the boot line. */
    public String report() {
        return roster.report();
    }

    public String deviceName() {
        return roster.name();
    }

    cl_command_queue queue() {
        return queue;
    }

    cl_context context() {
        return context;
    }

    cl_device_id handle() {
        return roster.device();
    }

    long maxWorkGroup() {
        return maxWorkGroup;
    }

    cl_kernel kernel(String name) {
        return clCreateKernel(program, name, null);
    }

    @Override
    public void close() {
        clReleaseProgram(program);
        clReleaseCommandQueue(queue);
        clReleaseContext(context);
    }

    private cl_program build(AtmosphereGrid grid) {
        cl_program built = clCreateProgramWithSource(context, 1, new String[]{KernelSource.read()}, null,
                null);
        try {
            clBuildProgram(built, 1, new cl_device_id[]{roster.device()}, KernelSource.buildOptions(grid),
                    null, null);
        } catch (RuntimeException refused) {
            // The log rather than the status code. A kernel that will not compile is a line and a column, and
            // CL_BUILD_PROGRAM_FAILURE on its own sends whoever reads it back to the source with nothing.
            throw new IllegalStateException("the sky kernels did not build: " + buildLog(built), refused);
        }
        return built;
    }

    private String buildLog(cl_program built) {
        try {
            long[] size = new long[1];
            clGetProgramBuildInfo(built, roster.device(), CL_PROGRAM_BUILD_LOG, 0, null, size);
            byte[] buffer = new byte[(int) Math.max(1, size[0])];
            clGetProgramBuildInfo(built, roster.device(), CL_PROGRAM_BUILD_LOG, buffer.length,
                    Pointer.to(buffer), null);
            return new String(buffer, StandardCharsets.UTF_8).trim();
        } catch (Throwable saying) {
            return "the driver would not say";
        }
    }
}
