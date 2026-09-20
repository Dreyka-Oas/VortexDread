package oas.dreyka.vortexdread.atmosphere.gpu;

import java.util.ArrayList;
import java.util.List;

import org.jocl.cl_device_id;
import org.jocl.cl_platform_id;

import static org.jocl.CL.CL_DEVICE_TYPE_GPU;
import static org.jocl.CL.clGetDeviceIDs;
import static org.jocl.CL.clGetPlatformIDs;

/**
 * Everything the machine offers, and which one of them was taken.
 *
 * <p>Enumeration asks for GPU devices by type rather than for everything. That is not tidiness: Mesa's rusticl
 * rejects the all-devices constant outright, since its value is every bit set and the driver reads that as a
 * request for device types it has never heard of. Asking for the type that is actually wanted works on every
 * implementation, and it skips a processor advertising itself as an OpenCL device, which would run this
 * slower than the reference path already does.
 *
 * @param platform the platform the chosen device belongs to, which the context needs
 * @param device the chosen device
 * @param name its name as a person would recognise it
 * @param report that device and every one it was picked over, for the boot line
 */
record DeviceRoster(cl_platform_id platform, cl_device_id device, String name, String report) {

    static DeviceRoster gather(int wantedIndex) {
        List<cl_platform_id> platformOf = new ArrayList<>();
        List<cl_device_id> devices = new ArrayList<>();
        List<String> names = new ArrayList<>();
        walk(platformOf, devices, names);
        if (devices.isEmpty()) {
            throw new IllegalStateException("no OpenCL device of GPU type");
        }

        int[] units = new int[devices.size()];
        int[] megahertz = new int[devices.size()];
        for (int i = 0; i < devices.size(); i++) {
            units[i] = DeviceInfo.computeUnits(devices.get(i));
            megahertz[i] = DeviceInfo.clockMegahertz(devices.get(i));
        }

        int chosen = DevicePick.choose(names, units, megahertz, wantedIndex);
        return new DeviceRoster(platformOf.get(chosen), devices.get(chosen), names.get(chosen),
                describe(names, units, megahertz, chosen));
    }

    private static void walk(List<cl_platform_id> platformOf, List<cl_device_id> devices,
            List<String> names) {
        int[] platformCount = new int[1];
        clGetPlatformIDs(0, null, platformCount);
        if (platformCount[0] == 0) {
            throw new IllegalStateException("no OpenCL platform");
        }
        cl_platform_id[] platforms = new cl_platform_id[platformCount[0]];
        clGetPlatformIDs(platforms.length, platforms, null);

        for (cl_platform_id platform : platforms) {
            int[] deviceCount = new int[1];
            try {
                clGetDeviceIDs(platform, CL_DEVICE_TYPE_GPU, 0, null, deviceCount);
            } catch (Exception offeringNone) {
                continue;
            }
            if (deviceCount[0] == 0) {
                continue;
            }
            cl_device_id[] found = new cl_device_id[deviceCount[0]];
            clGetDeviceIDs(platform, CL_DEVICE_TYPE_GPU, found.length, found, null);
            for (cl_device_id one : found) {
                platformOf.add(platform);
                devices.add(one);
                names.add(DeviceInfo.name(one));
            }
        }
    }

    // Naming what was passed over, and the numbers it was passed over on, so an operator who disagrees with
    // the pick can read the index to put in the config off the same line.
    private static String describe(List<String> names, int[] units, int[] megahertz, int chosen) {
        StringBuilder line = new StringBuilder(entry(names, units, megahertz, chosen));
        for (int i = 0; i < names.size(); i++) {
            if (i != chosen) {
                line.append(", over ").append(entry(names, units, megahertz, i));
            }
        }
        return line.toString();
    }

    private static String entry(List<String> names, int[] units, int[] megahertz, int index) {
        return names.get(index) + " [" + index + "] " + units[index] + " units at " + megahertz[index]
                + " MHz";
    }
}
