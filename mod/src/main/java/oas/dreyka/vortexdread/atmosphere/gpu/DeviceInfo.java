package oas.dreyka.vortexdread.atmosphere.gpu;

import java.nio.charset.StandardCharsets;

import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_device_id;
import org.jocl.cl_kernel;

import static org.jocl.CL.CL_DEVICE_MAX_CLOCK_FREQUENCY;
import static org.jocl.CL.CL_DEVICE_MAX_COMPUTE_UNITS;
import static org.jocl.CL.CL_DEVICE_MAX_WORK_GROUP_SIZE;
import static org.jocl.CL.CL_DEVICE_NAME;
import static org.jocl.CL.CL_KERNEL_PREFERRED_WORK_GROUP_SIZE_MULTIPLE;
import static org.jocl.CL.clGetDeviceInfo;
import static org.jocl.CL.clGetKernelWorkGroupInfo;

/**
 * The device queries needed to name a card and to size a dispatch for it.
 *
 * <p>Every one of them falls back rather than throwing, so a driver missing an optional parameter costs a good
 * default and not the whole card.
 *
 * <p>Nothing here asks about double precision. The solver is single precision throughout, on both paths, and
 * the one place it needed better rounding than the card gives is handled inside the kernel in single precision,
 * so a device without the extension is not the wrong answer, it is simply a device.
 */
final class DeviceInfo {

    /** AMD extension: the board name a buyer would recognise, against CL_DEVICE_NAME's "gfx1200". */
    private static final int CL_DEVICE_BOARD_NAME_AMD = 0x4038;

    /** Work group size assumed when the device will not say. Legal everywhere. */
    private static final long DEFAULT_WORK_GROUP = 256L;

    /** Wavefront assumed when the device will not say: 32 on one vendor, 64 on the other, 64 is safe. */
    private static final long DEFAULT_MULTIPLE = 64L;

    private DeviceInfo() {
    }

    static String name(cl_device_id device) {
        String board = queryString(device, CL_DEVICE_BOARD_NAME_AMD);
        if (board != null && !board.isEmpty()) {
            return board;
        }
        String name = queryString(device, CL_DEVICE_NAME);
        return name == null ? "GPU" : name;
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
        return queryUnsigned(device, CL_DEVICE_MAX_COMPUTE_UNITS);
    }

    /** The other half of what the pick ranks on, since a compute unit is a unit of layout and not of speed. */
    static int clockMegahertz(cl_device_id device) {
        return queryUnsigned(device, CL_DEVICE_MAX_CLOCK_FREQUENCY);
    }

    private static int queryUnsigned(cl_device_id device, int parameter) {
        try {
            int[] out = new int[1];
            clGetDeviceInfo(device, parameter, Sizeof.cl_uint, Pointer.to(out), null);
            return Math.max(0, out[0]);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /**
     * The wavefront this kernel wants its work group rounded to on this device.
     *
     * <p>Asked rather than written down. It is 64 on one vendor's parts and 32 on the other's, it moves
     * between generations, and it depends on the kernel's own register use, so the only number that is right
     * is the one the driver returns for this kernel on this device.
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
