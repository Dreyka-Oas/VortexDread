package oas.dreyka.vortexdread.atmosphere.gpu;

import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_command_queue;
import org.jocl.cl_kernel;
import org.jocl.cl_mem;

import static org.jocl.CL.clEnqueueNDRangeKernel;
import static org.jocl.CL.clSetKernelArg;

/**
 * One kernel launch, written as the arguments in order and then the shape of the grid to run it over.
 *
 * <p>Plumbing, and it exists because the alternative reads badly. Setting nine arguments through the C
 * interface is nine lines of index bookkeeping, and an index counted wrong does not fail: the driver takes
 * whatever is in the slot, the kernel reads a buffer as a float, and the sky quietly comes out wrong. Counting
 * the slots here means no stage has to.
 */
final class Dispatch {

    private final cl_command_queue queue;
    private final cl_kernel kernel;
    private int slot;

    Dispatch(cl_command_queue queue, cl_kernel kernel) {
        this.queue = queue;
        this.kernel = kernel;
    }

    Dispatch with(cl_mem buffer) {
        clSetKernelArg(kernel, slot++, Sizeof.cl_mem, Pointer.to(buffer));
        return this;
    }

    Dispatch with(int value) {
        clSetKernelArg(kernel, slot++, Sizeof.cl_int, Pointer.to(new int[]{value}));
        return this;
    }

    Dispatch with(float value) {
        clSetKernelArg(kernel, slot++, Sizeof.cl_float, Pointer.to(new float[]{value}));
        return this;
    }

    Dispatch with(long value) {
        clSetKernelArg(kernel, slot++, Sizeof.cl_ulong, Pointer.to(new long[]{value}));
        return this;
    }

    /** Scratch the work group shares, sized in floats. Passed as a size with no pointer behind it. */
    Dispatch shared(int floats) {
        clSetKernelArg(kernel, slot++, (long) Sizeof.cl_float * floats, null);
        return this;
    }

    /** Runs it, letting the driver choose how to split the range into work groups. */
    void over(long... sizes) {
        clEnqueueNDRangeKernel(queue, kernel, sizes.length, null, sizes, null, 0, null, null);
    }

    /** Runs it with the split spelled out, which only the reduction needs, since it shares memory. */
    void inGroupsOf(long[] sizes, long[] group) {
        clEnqueueNDRangeKernel(queue, kernel, sizes.length, null, sizes, group, 0, null, null);
    }
}
