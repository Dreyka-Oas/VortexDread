package oas.dreyka.vortexdread.config.domain;

/** Where the advection maths runs. Read on the server and on the client alike. */
public final class ComputeConfig {
    private ComputeConfig() {
    }

    /**
     * Use the OpenCL device for debris advection and for the volume sampling. On by default: with no
     * device present the dispatcher finds nothing and the processor path runs, with no visible
     * difference beyond how many debris the budget allows. A kill switch, not a feature flag.
     */
    public static boolean useGpu = true;

    /** Index into the flat list of OpenCL devices. Negative picks automatically. Read once at boot. */
    public static int gpuDeviceIndex = -1;

    /** Work group size. Zero lets the driver decide, which is right unless a device misbehaves. */
    public static int gpuWorkgroupSize = 0;

    /** Threads in the processor advection pool. Zero means cores minus two, floored at one. */
    public static int advectionThreads = 0;

    /** Debris handed to one dispatch. Smaller batches hide latency, larger ones amortise the upload. */
    public static int advectionBatch = 4096;
}
