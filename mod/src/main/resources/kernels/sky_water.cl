// Where the cloud comes from and where it goes. PhaseChange.java and Precipitation.java.

#define AUTOCONVERSION_THRESHOLD 1.0e-3f

// Harris equations 7, 8 and 13. Every cell asks how much vapour it could hold at its own temperature and
// pressure and gives up the excess as droplets, which is the only place in the whole simulation a cloud is
// created. It is also where the loop closes: condensing releases latent heat, the heat lightens the parcel,
// buoyancy lifts it, and lifting it cools it into condensing more.
//
// The excess is divided by how far the target moves while it is being chased, which Harris leaves out. Handed
// over whole it overshoots near the ground by a factor close to three, the cell ends drier than saturated, the
// next step evaporates some back and cools it, and the two take turns with a larger swing each time.
//
// The Exner function and the pressure are one value per altitude row, computed on the host once and uploaded.
// That keeps the one function here whose exponent is not a power of two off the card entirely.
kernel void change_phase(global float* theta, global float* vapour, global float* cloudWater,
        global const float* exnerByRow, global const float* pressureByRow) {
    int x = get_global_id(0);
    int z = get_global_id(1);
    int y = get_global_id(2);

    float exner = exnerByRow[y];
    float pressure = pressureByRow[y];
    int index = cell_index(x, y, z);

    float potentialTemperature = theta[index];
    float celsius = potentialTemperature * exner - KELVIN_OFFSET;
    float saturation = saturation_mixing_ratio(celsius, pressure);
    float slope = saturation_slope(celsius, saturation);

    // A limiter can undershoot a cell that had almost no droplets in it by a rounding error, and the line
    // below would read a negative count as air owing water and drain the vapour to pay for it.
    float droplets = fmax(0.0f, cloudWater[index]);
    float chase = 1.0f + LATENT_HEAT / HEAT_CAPACITY * slope;
    float change = fmin(exact_divide(saturation - vapour[index], chase), droplets);

    vapour[index] += change;
    cloudWater[index] = droplets - change;
    theta[index] = potentialTemperature
            + exact_divide(-LATENT_HEAT * change, HEAT_CAPACITY * exner);
}

// Droplets that grew heavy enough to fall, taken out of the air, after Kessler 1969. Without this the domain
// has no way to lose water: every gram that condenses warms it by two and a half degrees and never cools it
// back, and a run that looked correct for twenty minutes reaches a hundred degrees.
//
// The share taken in one step arrives already capped by the host, since an explicit rate stepped past its own
// time constant removes more than there is.
kernel void fall_rain(global float* cloudWater, const float share) {
    int index = get_global_id(0);
    float excess = cloudWater[index] - AUTOCONVERSION_THRESHOLD;
    if (excess > 0.0f) {
        cloudWater[index] -= excess * share;
    }
}
