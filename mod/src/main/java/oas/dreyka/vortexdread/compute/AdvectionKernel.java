package oas.dreyka.vortexdread.compute;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import oas.dreyka.vortexdread.VortexDread;
import oas.dreyka.vortexdread.config.domain.ComputeConfig;
import oas.dreyka.vortexdread.wind.VortexParameters;

import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_command_queue;
import org.jocl.cl_context;
import org.jocl.cl_context_properties;
import org.jocl.cl_device_id;
import org.jocl.cl_kernel;
import org.jocl.cl_mem;
import org.jocl.cl_platform_id;
import org.jocl.cl_program;
import org.jocl.cl_queue_properties;

import static org.jocl.CL.CL_CONTEXT_PLATFORM;
import static org.jocl.CL.CL_DEVICE_TYPE_GPU;
import static org.jocl.CL.CL_MEM_COPY_HOST_PTR;
import static org.jocl.CL.CL_MEM_READ_ONLY;
import static org.jocl.CL.CL_MEM_READ_WRITE;
import static org.jocl.CL.CL_TRUE;
import static org.jocl.CL.clBuildProgram;
import static org.jocl.CL.clCreateBuffer;
import static org.jocl.CL.clCreateCommandQueueWithProperties;
import static org.jocl.CL.clCreateContext;
import static org.jocl.CL.clCreateKernel;
import static org.jocl.CL.clCreateProgramWithSource;
import static org.jocl.CL.clEnqueueNDRangeKernel;
import static org.jocl.CL.clEnqueueReadBuffer;
import static org.jocl.CL.clGetDeviceIDs;
import static org.jocl.CL.clGetPlatformIDs;
import static org.jocl.CL.clReleaseMemObject;
import static org.jocl.CL.clSetKernelArg;
import static org.jocl.CL.setExceptionsEnabled;

/**
 * The OpenCL side of the debris advection: device pick, context, and one dispatch per batch.
 *
 * <p>Construction throws on anything it does not like, which is the whole contract: the caller degrades
 * to the processor and the game plays the same, so the card is never load bearing.
 *
 * <p>The four native handles live for the process. This is one allocation per launch, the driver
 * reclaims it at exit, and releasing it on world close would both re-pay the device enumeration and the
 * program build on the next world and hand a freed context to a dispatch already in flight.
 */
final class AdvectionKernel {

    /** Where the packaged kernel source sits in the jar. The build writes it from the .cl beside it. */
    private static final String SOURCE = "/kernels/debris_advect.clx";

    private final cl_context context;
    private final cl_command_queue queue;
    private final cl_program program;
    private final cl_kernel kernel;
    private final cl_kernel probe;
    private final cl_kernel mathProbe;
    private final String deviceName;
    private final long maxWorkGroup;
    private final long preferredMultiple;

    AdvectionKernel() {
        // A JOCL-wide switch rather than a per-context one. JOCL is shaded into this jar, so this reaches
        // this mod's copy and no other mod's.
        setExceptionsEnabled(true);

        int[] platformCount = new int[1];
        clGetPlatformIDs(0, null, platformCount);
        if (platformCount[0] == 0) {
            throw new IllegalStateException("no OpenCL platform");
        }
        cl_platform_id[] platforms = new cl_platform_id[platformCount[0]];
        clGetPlatformIDs(platforms.length, platforms, null);

        List<cl_platform_id> platformOf = new ArrayList<>();
        List<cl_device_id> devices = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (cl_platform_id platform : platforms) {
            int[] deviceCount = new int[1];
            try {
                clGetDeviceIDs(platform, CL_DEVICE_TYPE_GPU, 0, null, deviceCount);
            } catch (Exception ignored) {
                continue;
            }
            if (deviceCount[0] == 0) {
                continue;
            }
            cl_device_id[] found = new cl_device_id[deviceCount[0]];
            clGetDeviceIDs(platform, CL_DEVICE_TYPE_GPU, found.length, found, null);
            for (cl_device_id device : found) {
                if (!GpuDeviceInfo.hasDoublePrecision(device)) {
                    // Dropped here rather than left to fail the program build, because a build log full
                    // of "use of type double requires cl_khr_fp64" reads as a broken kernel and is not.
                    VortexDread.LOGGER.info("[VortexDread] skipping {}: no double precision",
                            GpuDeviceInfo.name(device));
                    continue;
                }
                platformOf.add(platform);
                devices.add(device);
                names.add(GpuDeviceInfo.name(device));
            }
        }
        if (devices.isEmpty()) {
            throw new IllegalStateException("no OpenCL device with double precision");
        }

        int[] units = new int[devices.size()];
        for (int i = 0; i < devices.size(); i++) {
            units[i] = GpuDeviceInfo.computeUnits(devices.get(i));
            // Logged beside the name because the width is what separates two cards from the same vendor,
            // and a pick nobody can read is a pick nobody can argue with.
            VortexDread.LOGGER.info("[VortexDread] device[{}] = {} ({} compute units)",
                    i, names.get(i), units[i]);
        }

        int chosen = GpuDevicePick.choose(names, units, ComputeConfig.gpuDeviceIndex);
        cl_device_id device = devices.get(chosen);

        cl_context_properties properties = new cl_context_properties();
        properties.addProperty(CL_CONTEXT_PLATFORM, platformOf.get(chosen));
        this.context = clCreateContext(properties, 1, new cl_device_id[]{device}, null, null, null);
        this.queue = clCreateCommandQueueWithProperties(context, device, new cl_queue_properties(), null);
        // No fast math option. It reorders the arithmetic, the card stops agreeing with the processor,
        // and agreement between the two paths is not for sale.
        this.program = clCreateProgramWithSource(context, 1, new String[]{source()}, null, null);
        clBuildProgram(program, 0, null, null, null, null);
        this.kernel = clCreateKernel(program, "advect", null);
        this.probe = clCreateKernel(program, "probe", null);
        this.mathProbe = clCreateKernel(program, "math_probe", null);
        this.deviceName = names.get(chosen);
        this.maxWorkGroup = GpuDeviceInfo.maxWorkGroupSize(device);
        this.preferredMultiple = GpuDeviceInfo.preferredMultiple(kernel, device);
    }

    String deviceName() {
        return deviceName;
    }

    /**
     * One tick for a batch of pieces, written back into the arrays it was handed.
     *
     * @param count how many pieces of the arrays are live, three doubles each in position and velocity
     */
    void advect(VortexParameters p, double phase, double dt,
                double[] position, double[] velocity, double[] terminal, int count) {
        long positionBytes = (long) Sizeof.cl_double * count * 3;
        long terminalBytes = (long) Sizeof.cl_double * count;

        cl_mem positionBuffer = null;
        cl_mem velocityBuffer = null;
        cl_mem terminalBuffer = null;
        try {
            positionBuffer = clCreateBuffer(context, CL_MEM_READ_WRITE | CL_MEM_COPY_HOST_PTR,
                    positionBytes, Pointer.to(position), null);
            velocityBuffer = clCreateBuffer(context, CL_MEM_READ_WRITE | CL_MEM_COPY_HOST_PTR,
                    positionBytes, Pointer.to(velocity), null);
            terminalBuffer = clCreateBuffer(context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                    terminalBytes, Pointer.to(terminal), null);

            int argument = 0;
            clSetKernelArg(kernel, argument++, Sizeof.cl_int, Pointer.to(new int[]{count}));
            clSetKernelArg(kernel, argument++, Sizeof.cl_mem, Pointer.to(positionBuffer));
            clSetKernelArg(kernel, argument++, Sizeof.cl_mem, Pointer.to(velocityBuffer));
            clSetKernelArg(kernel, argument++, Sizeof.cl_mem, Pointer.to(terminalBuffer));
            argument = scalar(argument, p.centerX());
            argument = scalar(argument, p.centerZ());
            argument = scalar(argument, p.groundY());
            argument = scalar(argument, p.cloudBaseY());
            argument = scalar(argument, p.coreRadius());
            argument = scalar(argument, p.peakWind());
            argument = scalar(argument, p.translationX());
            argument = scalar(argument, p.translationZ());
            argument = scalar(argument, p.turbulence());
            argument = scalar(argument, phase);
            scalar(argument, dt);

            long local = localSize();
            long global = ((count + local - 1) / local) * local;
            clEnqueueNDRangeKernel(queue, kernel, 1, null, new long[]{global}, new long[]{local},
                    0, null, null);

            // Read into scratch and copy over only once both halves are back. A dispatch that dies between
            // the two reads would otherwise leave the caller holding new positions against old velocities,
            // which the fallback cannot undo because it has no way to know it happened.
            double[] newPosition = new double[count * 3];
            double[] newVelocity = new double[count * 3];
            clEnqueueReadBuffer(queue, positionBuffer, CL_TRUE, 0, positionBytes,
                    Pointer.to(newPosition), 0, null, null);
            clEnqueueReadBuffer(queue, velocityBuffer, CL_TRUE, 0, positionBytes,
                    Pointer.to(newVelocity), 0, null, null);
            System.arraycopy(newPosition, 0, position, 0, count * 3);
            System.arraycopy(newVelocity, 0, velocity, 0, count * 3);
        } finally {
            release(positionBuffer);
            release(velocityBuffer);
            release(terminalBuffer);
        }
    }

    /** The hand-written functions on each input, seven doubles out per one in. */
    void mathProbe(double[] in, double[] out, int count) {
        cl_mem inBuffer = null;
        cl_mem outBuffer = null;
        try {
            inBuffer = clCreateBuffer(context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                    (long) Sizeof.cl_double * count, Pointer.to(in), null);
            outBuffer = clCreateBuffer(context, CL_MEM_READ_WRITE,
                    (long) Sizeof.cl_double * count * 7, null, null);
            clSetKernelArg(mathProbe, 0, Sizeof.cl_int, Pointer.to(new int[]{count}));
            clSetKernelArg(mathProbe, 1, Sizeof.cl_mem, Pointer.to(inBuffer));
            clSetKernelArg(mathProbe, 2, Sizeof.cl_mem, Pointer.to(outBuffer));

            long local = localSize();
            long global = ((count + local - 1) / local) * local;
            clEnqueueNDRangeKernel(queue, mathProbe, 1, null, new long[]{global}, new long[]{local},
                    0, null, null);
            clEnqueueReadBuffer(queue, outBuffer, CL_TRUE, 0, (long) Sizeof.cl_double * count * 7,
                    Pointer.to(out), 0, null, null);
        } finally {
            release(inBuffer);
            release(outBuffer);
        }
    }

    /**
     * The wind field at a batch of points, without the integrator.
     *
     * <p>Built for the parity test. The game never calls it: a tornado samples the field through the
     * advection, and a second path into it would be a second thing to keep in step.
     */
    void probe(VortexParameters p, double phase, double[] point, double[] out, int count) {
        long bytes = (long) Sizeof.cl_double * count * 3;
        cl_mem pointBuffer = null;
        cl_mem outBuffer = null;
        try {
            pointBuffer = clCreateBuffer(context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                    bytes, Pointer.to(point), null);
            outBuffer = clCreateBuffer(context, CL_MEM_READ_WRITE, bytes, null, null);

            int argument = 0;
            clSetKernelArg(probe, argument++, Sizeof.cl_int, Pointer.to(new int[]{count}));
            clSetKernelArg(probe, argument++, Sizeof.cl_mem, Pointer.to(pointBuffer));
            clSetKernelArg(probe, argument++, Sizeof.cl_mem, Pointer.to(outBuffer));
            for (double value : new double[]{p.centerX(), p.centerZ(), p.groundY(), p.cloudBaseY(),
                    p.coreRadius(), p.peakWind(), p.translationX(), p.translationZ(), p.turbulence(),
                    phase}) {
                clSetKernelArg(probe, argument++, Sizeof.cl_double, Pointer.to(new double[]{value}));
            }

            long local = localSize();
            long global = ((count + local - 1) / local) * local;
            clEnqueueNDRangeKernel(queue, probe, 1, null, new long[]{global}, new long[]{local},
                    0, null, null);
            clEnqueueReadBuffer(queue, outBuffer, CL_TRUE, 0, bytes, Pointer.to(out), 0, null, null);
        } finally {
            release(pointBuffer);
            release(outBuffer);
        }
    }

    private int scalar(int index, double value) {
        clSetKernelArg(kernel, index, Sizeof.cl_double, Pointer.to(new double[]{value}));
        return index + 1;
    }

    /**
     * Work group size, rounded to the wavefront the driver named for this kernel on this device.
     *
     * <p>A configured value wins when it is legal, since an operator with a misbehaving driver needs a
     * way out, and is ignored when it is not, because an illegal local size fails the whole dispatch.
     */
    private long localSize() {
        int wanted = ComputeConfig.gpuWorkgroupSize;
        if (wanted > 0 && wanted <= maxWorkGroup) {
            return wanted;
        }
        long size = preferredMultiple;
        while (size * 2 <= maxWorkGroup && size * 2 <= 256L) {
            size *= 2;
        }
        return Math.max(1L, Math.min(size, maxWorkGroup));
    }

    private static void release(cl_mem buffer) {
        if (buffer != null) {
            clReleaseMemObject(buffer);
        }
    }

    private static String source() {
        try (var in = AdvectionKernel.class.getResourceAsStream(SOURCE)) {
            if (in == null) {
                throw new IllegalStateException("kernel resource missing: " + SOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("kernel load failed", e);
        }
    }
}
