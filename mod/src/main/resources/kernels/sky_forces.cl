// What pushes the air around: the ground below, buoyancy inside, and the band under the lid that stops it
// bouncing. SurfaceForcing.java, Buoyancy.java and Boundaries.java.

#define HUMIDITY_OFFSET 37.0f
#define SPONGE_ROWS 4
#define SPONGE_TIME 20.0f

// The warm damp air entering at the ground, nudging the floor row and the one above it toward what the ground
// underneath them is doing. A flux and not a value: warm ground under air that is already that warm heats
// nothing, and writing the target straight in would make the surface an infinite reservoir.
//
// Two rows because the floor itself is held at rest by the sponge's counterpart at the other end, so a bubble
// seeded there alone would be flattened before buoyancy ever saw it.
kernel void force_surface(global float* theta, global float* vapour, const ulong seed,
        const float latticePerCell, const float time, const float relaxation,
        const float temperatureAmplitude, const float humidityAmplitude,
        const float surfaceTemperature, const float surfaceVapour) {
    int x = get_global_id(0);
    int z = get_global_id(1);

    float latticeX = x * latticePerCell;
    float latticeZ = z * latticePerCell;
    float warmth = noise_layered(seed, latticeX, latticeZ, time, 3);
    float damp = noise_layered(seed, latticeX + HUMIDITY_OFFSET, latticeZ + HUMIDITY_OFFSET, time, 2);

    float temperature = surfaceTemperature + temperatureAmplitude * warmth;
    float wanted = surfaceVapour * (1.0f + humidityAmplitude * damp);
    if (wanted < 0.0f) {
        wanted = 0.0f;
    }

    for (int y = 0; y < 2; y++) {
        int index = cell_index(x, y, z);
        theta[index] += (temperature - theta[index]) * relaxation;
        vapour[index] += (wanted - vapour[index]) * relaxation;
    }
}

// The force that makes warm damp air climb, Harris equation 4. Vapour lifts and condensed droplets weigh,
// which is what gives a mature cloud its flat anvil instead of letting the column climb without end.
//
// It lands on horizontal faces, and a face has two cells rather than one, so both get a say. Row 0 is the
// ground and there is no face above the highest row, so the launch starts one row up.
kernel void apply_buoyancy(global float* vy, global const float* theta, global const float* vapour,
        global const float* cloudWater, global const float* ambientVirtual, const float timeStep) {
    int x = get_global_id(0);
    int z = get_global_id(1);
    int y = ((int) get_global_id(2)) + 1;

    float ambient = 0.5f * (ambientVirtual[y - 1] + ambientVirtual[y]);
    int index = cell_index(x, y, z);
    int under = index - SLAB;

    float above = buoyancy_of(theta[index], vapour[index], cloudWater[index], ambient);
    float below = buoyancy_of(theta[under], vapour[under], cloudWater[under], ambient);
    vy[index] += 0.5f * (above + below) * timeStep;
}

// The band under the lid, where whatever motion arrives is bled off. A rigid ceiling sends every rising
// thermal back down as a wave, the wave meets the next thermal, and the two add.
//
// Motion and nothing else. Pulling the temperature and the water back toward the still profile up there as
// well releases heat nothing paid for, once per step, for ever.
kernel void damp_lid(global float* vx, global float* vy, global float* vz, const int firstRow,
        const float timeStep) {
    int x = get_global_id(0);
    int z = get_global_id(1);
    int y = ((int) get_global_id(2)) + firstRow;

    float depth = exact_divide(y - firstRow + 1, (float) SPONGE_ROWS);
    float share = fmin(1.0f, exact_divide(depth * depth * timeStep, SPONGE_TIME));

    int index = cell_index(x, y, z);
    vx[index] -= vx[index] * share;
    vy[index] -= vy[index] * share;
    vz[index] -= vz[index] * share;
}
