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
}
