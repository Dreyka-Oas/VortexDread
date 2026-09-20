// Everything the flow carries, moved one step downstream. Advection.java, MomentumTransport.java and
// ScalarTransport.java, which say why the two kinds of field get two different schemes and why the same
// choice cannot serve both.

// One step back along a periodic axis, since the vertical helper takes coordinates and not indices.
inline int wrap_back(int coordinate, int size) {
    return coordinate == 0 ? size - 1 : coordinate - 1;
}

inline float vertical_at_side_face(global const float* vy, int x, int y, int z) {
    int back = wrap_back(x, SIZE_X);
    return 0.25f * (face_velocity_y(vy, x, y, z) + face_velocity_y(vy, x, y + 1, z)
            + face_velocity_y(vy, back, y, z) + face_velocity_y(vy, back, y + 1, z));
}

inline float lateral_at_side_face(global const float* vz, int x, int y, int z) {
    return 0.25f * (vz[cell_index(x, y, z)] + vz[wrapped_index(x, y, z + 1)]
            + vz[wrapped_index(x - 1, y, z)] + vz[wrapped_index(x - 1, y, z + 1)]);
}

inline float along_at_floor_face(global const float* vx, int x, int y, int z) {
    return 0.25f * (vx[cell_index(x, y, z)] + vx[wrapped_index(x + 1, y, z)]
            + vx[wrapped_index(x, y - 1, z)] + vx[wrapped_index(x + 1, y - 1, z)]);
}

inline float lateral_at_floor_face(global const float* vz, int x, int y, int z) {
    return 0.25f * (vz[cell_index(x, y, z)] + vz[wrapped_index(x, y, z + 1)]
            + vz[wrapped_index(x, y - 1, z)] + vz[wrapped_index(x, y - 1, z + 1)]);
}

inline float along_at_back_face(global const float* vx, int x, int y, int z) {
    return 0.25f * (vx[cell_index(x, y, z)] + vx[wrapped_index(x + 1, y, z)]
            + vx[wrapped_index(x, y, z - 1)] + vx[wrapped_index(x + 1, y, z - 1)]);
}

inline float vertical_at_back_face(global const float* vy, int x, int y, int z) {
    int back = wrap_back(z, SIZE_Z);
    return 0.25f * (face_velocity_y(vy, x, y, z) + face_velocity_y(vy, x, y + 1, z)
            + face_velocity_y(vy, x, y, back) + face_velocity_y(vy, x, y + 1, back));
}

// The flow carrying itself, traced backwards. Each component sits on its own face, so each traces from a
// point offset half a cell from the pressure lattice and the other two components are averaged onto it from
// the four faces around. The half cell then cancels against the array's own offset.
kernel void trace_momentum(global const float* vx, global const float* vy, global const float* vz,
        global float* outX, global float* outY, global float* outZ, const float courant) {
    int x = get_global_id(0);
    int z = get_global_id(1);
    int y = get_global_id(2);
    int index = cell_index(x, y, z);

    float sideVertical = vertical_at_side_face(vy, x, y, z);
    float sideLateral = lateral_at_side_face(vz, x, y, z);
    outX[index] = sample_periodic(vx, x - vx[index] * courant, y - sideVertical * courant,
            z - sideLateral * courant);

    if (y == 0) {
        outY[index] = 0.0f;
    } else {
        float floorAlong = along_at_floor_face(vx, x, y, z);
        float floorLateral = lateral_at_floor_face(vz, x, y, z);
        outY[index] = sample_periodic(vy, x - floorAlong * courant, y - vy[index] * courant,
                z - floorLateral * courant);
    }

    float backAlong = along_at_back_face(vx, x, y, z);
    float backVertical = vertical_at_back_face(vy, x, y, z);
    outZ[index] = sample_periodic(vz, x - backAlong * courant, y - backVertical * courant,
            z - vz[index] * courant);
}

// The flow through one of the two faces a cell owns along an axis, low side at offset zero.
inline float flow_at(global const float* vx, global const float* vy, global const float* vz,
        int axis, int x, int y, int z, int offset) {
    if (axis == 0) {
        return vx[wrapped_index(x + offset, y, z)];
    }
    if (axis == 1) {
        return face_velocity_y(vy, x, y + offset, z);
    }
    return vz[wrapped_index(x, y, z + offset)];
}

inline float field_at(global const float* field, int axis, int x, int y, int z, int offset) {
    int ox = axis == 0 ? offset : 0;
    int oy = axis == 1 ? offset : 0;
    int oz = axis == 2 ? offset : 0;
    return field[wrapped_index(x + ox, y + oy, z + oz)];
}

// The van Leer limiter: the harmonic mean of the two one sided differences, and zero wherever they disagree
// in sign, so an edge stays an edge instead of ringing into negative water on the dry side of a cloud.
inline float limited_slope(global const float* field, int axis, int x, int y, int z, int offset) {
    float here = field_at(field, axis, x, y, z, offset);
    float behind = here - field_at(field, axis, x, y, z, offset - 1);
    float ahead = field_at(field, axis, x, y, z, offset + 1) - here;
    float product = behind * ahead;
    if (product <= 0.0f) {
        return 0.0f;
    }
    return exact_divide(2.0f * product, behind + ahead);
}

inline float face_value(global const float* field, int axis, int x, int y, int z, int faceOffset,
        float flow, float courant) {
    bool rising = flow >= 0.0f;
    int upwind = rising ? faceOffset - 1 : faceOffset;
    float side = rising ? 0.5f : -0.5f;
    float travelled = fabs(flow) * courant;
    float slope = limited_slope(field, axis, x, y, z, upwind);
    return field_at(field, axis, x, y, z, upwind) + side * (1.0f - travelled) * slope;
}

// All six faces are settled against the same starting field and the cell is updated once. The last argument
// picks which of the two conservation properties this field wants: an exact total, which every field made of
// water needs, or a uniform field left exactly uniform, which is what potential temperature needs.
kernel void move_scalar(global const float* vx, global const float* vy, global const float* vz,
        global const float* field, global float* out, const float courant, const int conserveTotal) {
    int x = get_global_id(0);
    int z = get_global_id(1);
    int y = get_global_id(2);

    float leaving = 0.0f;
    float spread = 0.0f;
    for (int axis = 0; axis < 3; axis++) {
        float lowFlow = flow_at(vx, vy, vz, axis, x, y, z, 0);
        float highFlow = flow_at(vx, vy, vz, axis, x, y, z, 1);
        leaving += highFlow * face_value(field, axis, x, y, z, 1, highFlow, courant)
                - lowFlow * face_value(field, axis, x, y, z, 0, lowFlow, courant);
        spread += highFlow - lowFlow;
    }

    int index = cell_index(x, y, z);
    float carried = conserveTotal ? leaving : leaving - field[index] * spread;
    out[index] = field[index] - courant * carried;
}

// How fast the air is going in the cell that is emptying fastest, which is what decides how many slices the
// step gets cut into. The three components are added rather than taken separately, since air leaving
// diagonally empties its cell through two faces at once.
//
// The only reduction in the whole solver, and it is one a card is bad at, so it stops at one value per work
// group and the host takes the last max over a handful of floats. Taking a maximum is exact whatever the
// order, so the answer is the same number the processor path computes, to the bit.
kernel void reduce_fastest(global const float* vx, global const float* vy, global const float* vz,
        global float* partial, local float* shared) {
    int lane = get_local_id(0);
    float best = 0.0f;
    for (int i = get_global_id(0); i < CELL_COUNT; i += get_global_size(0)) {
        best = fmax(best, fabs(vx[i]) + fabs(vy[i]) + fabs(vz[i]));
    }

    shared[lane] = best;
    barrier(CLK_LOCAL_MEM_FENCE);
    for (int stride = get_local_size(0) / 2; stride > 0; stride >>= 1) {
        if (lane < stride) {
            shared[lane] = fmax(shared[lane], shared[lane + stride]);
        }
        barrier(CLK_LOCAL_MEM_FENCE);
    }

    if (lane == 0) {
        partial[get_group_id(0)] = shared[0];
    }
}
