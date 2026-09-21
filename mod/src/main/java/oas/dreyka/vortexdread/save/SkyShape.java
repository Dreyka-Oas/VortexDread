package oas.dreyka.vortexdread.save;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import oas.dreyka.vortexdread.atmosphere.AtmosphereSettings;

/**
 * What a saved sky has to agree with before a single cell of it is read back.
 *
 * <p>The three sizes are the obvious half: an admin who halves {@code cellsAcross} between two sessions
 * leaves a file whose cell count no longer matches, and a restore that trusted it would read one row's
 * water into another's. The rest is less obvious and matters as much. The still atmosphere every buoyancy
 * term is measured against is computed from the surface temperature, the mixed layer, the lapse rate and
 * the humidity, and a field saved inside one profile dropped into another is air that is out of balance
 * everywhere at once: it would start convecting on the first step for no reason a player could see.
 *
 * <p>Which is why this compares values rather than hashing them. A hash would say no at the same moments
 * and could say no at one more, and a collision here restores a sky into the wrong atmosphere.
 */
public record SkyShape(int sizeX, int sizeY, int sizeZ, float cellSize, float surfaceTemperature,
        float mixedLayerHeight, float lapseRate, float mixedLayerHumidity, float dryingHeight) {

    public static final Codec<SkyShape> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("sizeX").forGetter(SkyShape::sizeX),
            Codec.INT.fieldOf("sizeY").forGetter(SkyShape::sizeY),
            Codec.INT.fieldOf("sizeZ").forGetter(SkyShape::sizeZ),
            Codec.FLOAT.fieldOf("cellSize").forGetter(SkyShape::cellSize),
            Codec.FLOAT.fieldOf("surfaceTemperature").forGetter(SkyShape::surfaceTemperature),
            Codec.FLOAT.fieldOf("mixedLayerHeight").forGetter(SkyShape::mixedLayerHeight),
            Codec.FLOAT.fieldOf("lapseRate").forGetter(SkyShape::lapseRate),
            Codec.FLOAT.fieldOf("mixedLayerHumidity").forGetter(SkyShape::mixedLayerHumidity),
            Codec.FLOAT.fieldOf("dryingHeight").forGetter(SkyShape::dryingHeight)
    ).apply(i, SkyShape::new));

    public static SkyShape of(AtmosphereSettings settings) {
        return new SkyShape(settings.sizeX(), settings.sizeY(), settings.sizeZ(), settings.cellSize(),
                settings.surfaceTemperature(), settings.mixedLayerHeight(), settings.lapseRate(),
                settings.mixedLayerHumidity(), settings.dryingHeight());
    }

    public int cellCount() {
        return sizeX * sizeY * sizeZ;
    }
}
