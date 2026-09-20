package oas.dreyka.vortexdread.atmosphere.gpu;

import java.util.ArrayList;
import java.util.List;

import oas.dreyka.vortexdread.atmosphere.AtmosphereGrid;
import oas.dreyka.vortexdread.atmosphere.AtmosphereProfile;

import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_command_queue;
import org.jocl.cl_context;
import org.jocl.cl_mem;

import static org.jocl.CL.CL_MEM_READ_ONLY;
import static org.jocl.CL.CL_MEM_READ_WRITE;
import static org.jocl.CL.CL_TRUE;
import static org.jocl.CL.clCreateBuffer;
import static org.jocl.CL.clEnqueueReadBuffer;
import static org.jocl.CL.clEnqueueWriteBuffer;
import static org.jocl.CL.clReleaseMemObject;

/**
 * The state as the card holds it: allocated once, and after that the fields stay there.
 *
 * <p>Uploading the grid every step and reading it back would cost more than the step. Six fields of four
 * hundred thousand cells is ten megabytes each way, and at twenty steps a second that is four hundred
 * megabytes a second across the bus to save a computation the card does in a couple of milliseconds. So the
 * simulation lives on the device between steps, and the only traffic is the handful of floats the slice count
 * needs, plus one download whenever something actually wants to look at the cloud.
 *
 * <p>The three per-row arrays go up once at construction. They are the still atmosphere the whole run is
 * measured against, they depend on nothing the simulation does, and the host computes them with the strict
 * library rather than the portable arithmetic, which is free because they are computed once.
 */
final class SkyFields implements AutoCloseable {

    final cl_mem velocityX;
    final cl_mem velocityY;
    final cl_mem velocityZ;
    final cl_mem potentialTemperature;
    final cl_mem vapour;
    final cl_mem cloudWater;

    final cl_mem tracedX;
    final cl_mem tracedY;
    final cl_mem tracedZ;
    final cl_mem advected;
    final cl_mem pressure;
    final cl_mem divergence;

    final cl_mem ambientVirtual;
    final cl_mem exner;
    final cl_mem pressureByRow;
    final cl_mem fastest;

    final long fieldBytes;

    private final cl_command_queue queue;
    private final List<cl_mem> owned = new ArrayList<>();

    SkyFields(SkyDevice device, AtmosphereGrid grid, AtmosphereProfile profile, int reductionGroups) {
        this.queue = device.queue();
        this.fieldBytes = (long) Sizeof.cl_float * grid.cellCount();
        cl_context context = device.context();

        this.velocityX = working(context, fieldBytes);
        this.velocityY = working(context, fieldBytes);
        this.velocityZ = working(context, fieldBytes);
        this.potentialTemperature = working(context, fieldBytes);
        this.vapour = working(context, fieldBytes);
        this.cloudWater = working(context, fieldBytes);

        this.tracedX = working(context, fieldBytes);
        this.tracedY = working(context, fieldBytes);
        this.tracedZ = working(context, fieldBytes);
        this.advected = working(context, fieldBytes);
        this.pressure = working(context, fieldBytes);
        this.divergence = working(context, fieldBytes);

        long rowBytes = (long) Sizeof.cl_float * profile.rows();
        this.ambientVirtual = fixed(context, rowBytes);
        this.exner = fixed(context, rowBytes);
        this.pressureByRow = fixed(context, rowBytes);
        this.fastest = working(context, (long) Sizeof.cl_float * reductionGroups);

        write(ambientVirtual, profile.ambientVirtual, rowBytes);
        write(exner, profile.exner, rowBytes);
        write(pressureByRow, profile.pressure, rowBytes);
    }

    /** Pushes a whole grid up, which happens once at the start and again whenever the sky is reset. */
    void upload(AtmosphereGrid grid) {
        write(velocityX, grid.velocityX, fieldBytes);
        write(velocityY, grid.velocityY, fieldBytes);
        write(velocityZ, grid.velocityZ, fieldBytes);
        write(potentialTemperature, grid.potentialTemperature, fieldBytes);
        write(vapour, grid.vapour, fieldBytes);
        write(cloudWater, grid.cloudWater, fieldBytes);
    }

    /** Pulls it back, for whoever wants to draw it, measure it or compare it against the processor path. */
    void download(AtmosphereGrid grid) {
        read(velocityX, grid.velocityX, fieldBytes);
        read(velocityY, grid.velocityY, fieldBytes);
        read(velocityZ, grid.velocityZ, fieldBytes);
        read(potentialTemperature, grid.potentialTemperature, fieldBytes);
        read(vapour, grid.vapour, fieldBytes);
        read(cloudWater, grid.cloudWater, fieldBytes);
    }

    void read(cl_mem buffer, float[] into, long bytes) {
        clEnqueueReadBuffer(queue, buffer, CL_TRUE, 0, bytes, Pointer.to(into), 0, null, null);
    }

    @Override
    public void close() {
        for (cl_mem buffer : owned) {
            clReleaseMemObject(buffer);
        }
        owned.clear();
    }

    private void write(cl_mem buffer, float[] from, long bytes) {
        clEnqueueWriteBuffer(queue, buffer, CL_TRUE, 0, bytes, Pointer.to(from), 0, null, null);
    }

    private cl_mem working(cl_context context, long bytes) {
        return keep(clCreateBuffer(context, CL_MEM_READ_WRITE, bytes, null, null));
    }

    private cl_mem fixed(cl_context context, long bytes) {
        return keep(clCreateBuffer(context, CL_MEM_READ_ONLY, bytes, null, null));
    }

    private cl_mem keep(cl_mem buffer) {
        owned.add(buffer);
        return buffer;
    }
}
