package oas.dreyka.vortexdread.config;

/**
 * What the sky looks like, decided by whoever is watching it.
 *
 * <p>Separate from {@link SkyConfig} because the two belong to different people. A server owner decides
 * what the weather is and every player on the server gets that weather; a player decides what their own
 * machine spends drawing it, and nobody else is affected by the answer. Nothing here travels over the
 * network and nothing here can make two players see different clouds, only the same clouds drawn better
 * or worse.
 *
 * <p>Both classes land in the same file, since one file per mod is what an operator expects to find.
 */
public final class LookConfig {

    private LookConfig() {
    }

    /**
     * The block height the simulation's floor is laid on.
     *
     * <p>The simulation counts metres off the ground it stands on and knows nothing about Minecraft, so
     * this is the one number that ties the two together. Sea level, because the modelled ground is the
     * ground: a cloud base a real afternoon puts at fourteen hundred metres lands fourteen hundred blocks
     * over the sea, which is where it belongs in a world where a player is a little under two blocks tall.
     *
     * <p>Lowering it brings the whole sky down and makes the clouds look bigger, which is the trade the
     * game's own clouds make at a hundred and ninety-two. It is a look, not a correction.
     */
    public static float groundLevelBlock = 62.0f;

    /**
     * How much light a unit of condensed water stops, per metre.
     *
     * <p>The field holds a mixing ratio, kilograms of water per kilogram of air, and a cumulus peaks
     * around a ten thousandth of that. Air being close to a kilogram the cubic metre, that is a tenth of a
     * gram of water in each one, and a cloud that thick dims what is behind it by about one part in a
     * hundred per metre. A hundred is that number divided back out of the mixing ratio.
     *
     * <p>Raising it gives a denser, whiter cloud with a harder edge, lowering it gives thin haze.
     */
    public static float cloudExtinction = 100.0f;

    /**
     * How far one ray follows the sky before giving up, metres.
     *
     * <p>A ray pointed at the horizon never leaves the layer, so something has to stop it, and what that
     * costs is the cloud past this distance simply not being there. Twelve kilometres is a long way further
     * than the terrain is drawn, so the missing part is behind the horizon on any normal render distance.
     */
    public static float cloudReachMetres = 12000.0f;

    /**
     * How long the first step towards the sun is, metres. Each of the six after it is twice the last.
     *
     * <p>Fifty covers three kilometres by the end, which is wider than a cumulus, while still counting
     * the water nearest the sample closely. That is the right way round: the water in the first hundred
     * metres decides most of how much sun gets through.
     */
    public static float cloudLightStepMetres = 50.0f;

    /**
     * How much of the powder term to apply, nought to one.
     *
     * <p>Powder is the correction for what plain absorption gets backwards. A real cloud's lit face
     * darkens towards its edge, because a thin edge holds too little water to scatter light back at the
     * eye; absorption alone makes exactly that edge the brightest part, and a cumulus ends up reading as
     * a cotton ball lit from inside. At nought the cloud goes back to that.
     */
    public static float cloudPowder = 0.5f;

    /**
     * How long one wave of the detail is, metres.
     *
     * <p>Not a fraction of a cell: at exactly a half or a third the detail's lattice lines up with
     * the simulation's and draws the grid it is here to break. And not much shorter than the march
     * samples either, which is what sets the floor. Sixty-four steps spread over a slant of two or
     * three kilometres land forty to fifty metres apart, so a wave under a hundred metres is being
     * sampled below twice a wave and comes back as sparkle rather than as shape. Ninety-six metres
     * is a wave and a half per cell, above that floor and dividing nothing.
     */
    public static float cloudDetailMetres = 96.0f;

    /**
     * How hard the detail eats into the field, nought to one.
     *
     * <p>Nothing about the simulation is wrong at sixty-four metres, it simply has no opinion under
     * that, and a lone wet cell drawn faithfully is a smooth ball the width of a football pitch. The
     * bite grows as the cell gets fainter, so a dense core keeps its shape and an edge breaks into
     * wisps. At nought the sky goes back to the grid it came on.
     *
     * <p>Under a half rather than over it. Past that the middle of the range stops being a range: a
     * cell holding a tenth of the peak goes from surviving where the noise is quiet to gone
     * everywhere else, and an erosion with no middle is the grid again with holes in it.
     */
    public static float cloudErosion = 0.45f;

    /** How bright the sun makes the face it reaches, against the game's own daylight colour. */
    public static float cloudSunLight = 1.0f;

    /**
     * How bright the rest of the sky makes the face the sun never reaches.
     *
     * <p>The shaded side of a real cloud is not black, because half the sky is a lamp pointed at it.
     * Below about a third the cloud reads as cut out of stone, above about two thirds it goes flat.
     */
    public static float cloudSkyLight = 0.45f;

    /**
     * How much of the light a droplet catches carries on forward, nought to just under one.
     *
     * <p>This is what puts the bright rim on a cloud with the sun behind it and what makes the sky
     * next to the sun glare. The honest figure for a water droplet is around eight tenths, which
     * multiplies the light by forty-five when you look straight at the sun through thin cloud and
     * blows the picture out. Six tenths keeps the effect and keeps the range.
     */
    public static float cloudForwardScatter = 0.6f;

    /**
     * How much comes straight back instead, as a negative pull.
     *
     * <p>Small and the other way round. It lights the cloud you look at with the sun over your own
     * shoulder, which a forward lobe alone leaves dull. At nought the pair collapses to one lobe
     * and that face goes flat again.
     */
    public static float cloudBackScatter = -0.15f;
}
