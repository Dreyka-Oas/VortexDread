package oas.dreyka.vortexdread.atmosphere.cpu;

import oas.dreyka.vortexdread.atmosphere.AtmosphereGrid;
import oas.dreyka.vortexdread.atmosphere.AtmosphereProfile;
import oas.dreyka.vortexdread.atmosphere.Thermodynamics;

/**
 * The force that makes warm damp air climb, Harris equation 4.
 *
 * <p>Air lighter than its surroundings goes up, heavier goes down, and vapour counts as lighter while
 * suspended droplets count as heavier. That second term is why a mature cloud stops growing and spreads:
 * the water it has condensed is now weighing the column down.
 *
 * <p>The force lands on horizontal faces, since that is where the vertical flow is kept, and a face has
 * two cells rather than one. Both get a say: the push through the face is the mean of what the cell below
 * and the cell above it weigh, against the mean of what the still air weighs at those two heights.
 */
public final class Buoyancy {

    private Buoyancy() {
    }

    public static void apply(AtmosphereGrid grid, AtmosphereProfile profile, float timeStep) {
        // Face 0 is the ground and there is no face above the highest row, so neither end takes a force.
        for (int y = 1; y < grid.sizeY; y++) {
            float ambient = 0.5f * (profile.ambientVirtual[y - 1] + profile.ambientVirtual[y]);
            int rowStart = grid.index(0, y, 0);
            int rowEnd = rowStart + grid.sizeX * grid.sizeZ;
            int span = grid.sizeX * grid.sizeZ;

            for (int index = rowStart; index < rowEnd; index++) {
                int under = index - span;
                float above = Thermodynamics.buoyancy(grid.potentialTemperature[index],
                        grid.vapour[index], grid.cloudWater[index], ambient);
                float below = Thermodynamics.buoyancy(grid.potentialTemperature[under],
                        grid.vapour[under], grid.cloudWater[under], ambient);
                grid.velocityY[index] += 0.5f * (above + below) * timeStep;
            }
        }
    }
}
