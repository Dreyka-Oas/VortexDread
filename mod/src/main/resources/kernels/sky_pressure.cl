// Making the flow conserve mass, Harris equations 11 and 12. Projection.java says why the differences span
// one cell rather than two, and what a mismatched Laplacian leaves behind on a staggered grid.

#define OVERSHOOT 1.8f
#define INVERSE_SPAN (1.0f / CELL_SIZE)

// How much more air leaves each cell than enters it, per second. Summed over the domain this comes to zero
// whatever the field looks like, which is the condition for the Poisson problem below to have a solution at
// all: the horizontal terms wrap and cancel, and the two walls pass nothing.
kernel void measure_divergence(global const float* vx, global const float* vy, global const float* vz,
        global float* divergence) {
    int x = get_global_id(0);
    int z = get_global_id(1);
    int y = get_global_id(2);
    int index = cell_index(x, y, z);

    float flow = vx[wrapped_index(x + 1, y, z)] - vx[index]
            + face_velocity_y(vy, x, y + 1, z) - face_velocity_y(vy, x, y, z)
            + vz[wrapped_index(x, y, z + 1)] - vz[index];
    divergence[index] = flow * INVERSE_SPAN;
}

// One colour of one pass over the Poisson equation, and the reason the whole solver is a card problem rather
// than a processor one. No cell on this stencil touches another of its own parity, so every cell of a colour
// can be updated at once while still reading the newest values the other colour left: the sequential sweep
// the processor does and this one launch land on the same numbers, bit for bit, with nothing to synchronise
// inside the pass.
//
// Only the cells of the colour are launched rather than launching all of them and sending half home. The
// horizontal sizes are even, so wrapping preserves parity and every row holds exactly half its cells.
kernel void relax_pressure(global float* pressure, global const float* divergence, const int colour) {
    int z = get_global_id(1);
    int y = get_global_id(2);
    int x = 2 * ((int) get_global_id(0)) + ((y + z + colour) & 1);

    float neighbours = pressure[wrapped_index(x + 1, y, z)] + pressure[wrapped_index(x - 1, y, z)]
            + pressure[wrapped_index(x, y + 1, z)] + pressure[wrapped_index(x, y - 1, z)]
            + pressure[wrapped_index(x, y, z + 1)] + pressure[wrapped_index(x, y, z - 1)];

    int index = cell_index(x, y, z);
    float wanted = exact_divide(neighbours - (CELL_SIZE * CELL_SIZE) * divergence[index], 6.0f);
    pressure[index] += OVERSHOOT * (wanted - pressure[index]);
}

// Takes the pressure difference across each face off the flow through it. The two walls are skipped rather
// than corrected: nothing crosses them, so there is nothing to take away.
kernel void subtract_gradient(global float* vx, global float* vy, global float* vz,
        global const float* pressure) {
    int x = get_global_id(0);
    int z = get_global_id(1);
    int y = get_global_id(2);
    int index = cell_index(x, y, z);
    float here = pressure[index];

    vx[index] -= (here - pressure[wrapped_index(x - 1, y, z)]) * INVERSE_SPAN;
    vz[index] -= (here - pressure[wrapped_index(x, y, z - 1)]) * INVERSE_SPAN;
    if (y > 0) {
        vy[index] -= (here - pressure[cell_index(x, y - 1, z)]) * INVERSE_SPAN;
    }
}
