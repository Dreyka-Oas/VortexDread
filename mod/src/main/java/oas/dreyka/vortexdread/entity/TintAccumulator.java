package oas.dreyka.vortexdread.entity;

/**
 * The colour a funnel takes from what it has swallowed.
 *
 * <p>This is the detail that separates a tornado from a grey cone. One crossing a ploughed field turns
 * brown, one crossing grass turns green at the base, one over water stays white, and one that has just
 * taken a road turns almost black. Photographs of the same storm ten minutes apart do not match.
 *
 * <p>A running average with a ceiling on its weight, so the colour keeps following the ground the
 * funnel is on now rather than settling on the average of everything it has ever eaten.
 */
public final class TintAccumulator {

    /** Past this many samples the oldest material stops counting, which is what keeps it current. */
    private static final int MEMORY = 400;

    private double red;
    private double green;
    private double blue;
    private int weight;

    public TintAccumulator(int startingColour) {
        red = (startingColour >> 16) & 0xFF;
        green = (startingColour >> 8) & 0xFF;
        blue = startingColour & 0xFF;
        weight = 1;
    }

    /** Folds one more block colour in. */
    public void ingest(int rgb) {
        double r = (rgb >> 16) & 0xFF;
        double g = (rgb >> 8) & 0xFF;
        double b = rgb & 0xFF;
        int w = Math.min(weight, MEMORY);
        red = (red * w + r) / (w + 1);
        green = (green * w + g) / (w + 1);
        blue = (blue * w + b) / (w + 1);
        weight = w + 1;
    }

    /** The colour as one packed value, ready for the synced data and for the vertex stream. */
    public int packed() {
        return (clamp(red) << 16) | (clamp(green) << 8) | clamp(blue);
    }

    public int weight() {
        return weight;
    }

    /** Puts a saved colour back, so a tornado loaded from disk keeps the ground it was carrying. */
    public void restore(int packed, int savedWeight) {
        red = (packed >> 16) & 0xFF;
        green = (packed >> 8) & 0xFF;
        blue = packed & 0xFF;
        weight = Math.max(1, Math.min(savedWeight, MEMORY));
    }

    private static int clamp(double channel) {
        int v = (int) Math.round(channel);
        return v < 0 ? 0 : Math.min(v, 255);
    }
}
