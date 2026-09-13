package oas.dreyka.vortexdread.compute;

import java.nio.charset.StandardCharsets;

import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_device_id;
import org.jocl.cl_kernel;

import static org.jocl.CL.CL_DEVICE_EXTENSIONS;
import static org.jocl.CL.CL_DEVICE_MAX_COMPUTE_UNITS;
import static org.jocl.CL.CL_DEVICE_MAX_WORK_GROUP_SIZE;
import static org.jocl.CL.CL_DEVICE_NAME;
import static org.jocl.CL.CL_KERNEL_PREFERRED_WORK_GROUP_SIZE_MULTIPLE;
import static org.jocl.CL.clGetDeviceInfo;
import static org.jocl.CL.clGetKernelWorkGroupInfo;

/**
 * The device queries the dispatcher needs to name a card and to size a dispatch for it.
 *
 * <p>Every one of them falls back rather than throwing, so a driver missing an optional parameter costs
 * a good default and not the whole card.
 */
final class GpuDeviceInfo {
    private GpuDeviceInfo() {
    }

    /** AMD extension: the board name a buyer would recognise, against CL_DEVICE_NAME's "gfx1200". */
    private static final int CL_DEVICE_BOARD_NAME_AMD = 0x4038;

    /** Work group size assumed when the device will not say. Legal everywhere. */
    private static final long DEFAULT_WORK_GROUP = 256L;

    /** Wavefront assumed when the device will not say: 32 on one vendor, 64 on the other, 64 is safe. */
    private static final long DEFAULT_MULTIPLE = 64L;

    static String name(cl_device_id device) {
        String board = queryString(device, CL_DEVICE_BOARD_NAME_AMD);
        if (board != null && !board.isEmpty()) {
            return board;
        }
        String name = queryString(device, CL_DEVICE_NAME);
        return name == null ? "GPU" : name;
    }

    /**
     * Whether this device can do double precision at all.
     *
     * <p>Asked before the program is built rather than discovered from its build log. The kernel solves
     * the same equation as the processor to the same tolerance, which single precision cannot hold over
     * Minecraft world coordinates, so a device without it is not a slower option, it is the wrong
     * answer. Some drivers hide the extension behind an environment switch on hardware that has the
     * silicon: on Mesa's rusticl that is RUSTICL_FEATURES, which the run tasks set.
     */
    static boolean hasDoublePrecision(cl_device_id device) {
        String extensions = queryString(device, CL_DEVICE_EXTENSIONS);
        return extensions != null && extensions.contains("cl_khr_fp64");
    }

    static long maxWorkGroupSize(cl_device_id device) {
        try {
            long[] out = new long[1];
            clGetDeviceInfo(device, CL_DEVICE_MAX_WORK_GROUP_SIZE, Sizeof.size_t, Pointer.to(out), null);
            return out[0] > 0 ? out[0] : DEFAULT_WORK_GROUP;
        } catch (Throwable ignored) {
            return DEFAULT_WORK_GROUP;
        }
    }

    static int computeUnits(cl_device_id device) {
        try {
            int[] out = new int[1];
            clGetDeviceInfo(device, CL_DEVICE_MAX_COMPUTE_UNITS, Sizeof.cl_uint, Pointer.to(out), null);
            return Math.max(0, out[0]);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /**
     * The wavefront this kernel wants its work group rounded to on this device.
     *
     * <p>Asked rather than written down. It is 64 on one vendor's parts and 32 on the other's, it moves
     * between generations, and it depends on the kernel's own register use, so the only number that is
     * right is the one the driver returns for this kernel on this device.
     */
    static long preferredMultiple(cl_kernel kernel, cl_device_id device) {
        try {
            long[] out = new long[1];
            clGetKernelWorkGroupInfo(kernel, device, CL_KERNEL_PREFERRED_WORK_GROUP_SIZE_MULTIPLE,
                    Sizeof.size_t, Pointer.to(out), null);
            return out[0] > 0 ? out[0] : DEFAULT_MULTIPLE;
        } catch (Throwable ignored) {
            return DEFAULT_MULTIPLE;
        }
    }

    private static String queryString(cl_device_id device, int parameter) {
        try {
            long[] size = new long[1];
            clGetDeviceInfo(device, parameter, 0, null, size);
            if (size[0] <= 0) {
                return null;
            }
            byte[] buffer = new byte[(int) size[0]];
            clGetDeviceInfo(device, parameter, buffer.length, Pointer.to(buffer), null);
            return new String(buffer, 0, Math.max(0, buffer.length - 1), StandardCharsets.UTF_8).trim();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
