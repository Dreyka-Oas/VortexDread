package oas.dreyka.vortexdread.client;

import oas.dreyka.vortexdread.atmosphere.SkyFeed;
import oas.dreyka.vortexdread.atmosphere.SkySnapshot;
import oas.dreyka.vortexdread.atmosphere.SkyView;
import oas.dreyka.vortexdread.weather.RainMap;
import oas.dreyka.vortexdread.weather.RainReport;

/**
 * The sky as this client knows it: the last two steps the server sent, and the clock between them.
 *
 * <p>Nothing here simulates. The server owns the physics and sends the field, so a client on an old laptop
 * with no usable driver watches the same clouds as the one that has a card, and neither can drift.
 *
 * <p>What this adds to the feed underneath is the client's own tick count, because a field arrives with
 * whatever latency the connection has and the tick it lands on is not the tick it was computed on. Timed
 * off the arrival, the blend always runs a full cadence and always finishes.
 */
public final class ClientSky {

    private final SkyFeed feed = new SkyFeed();
    private final RainReport rain = new RainReport();

    private long ticks;

    public void onTick() {
        ticks++;
    }

    /** Called on the client thread when a field arrives. */
    public void accept(SkySnapshot sky, int cadence) {
        feed.offer(sky, ticks, cadence);
        // Summed here rather than per frame: it is one pass over the field, which costs about what unpacking
        // it already cost, and the answer only changes when a new field lands.
        rain.take(sky);
    }

    /**
     * Where it rains, for the particles and the sound.
     *
     * <p>Built from the same field the server built its own from, so a client asking whether it rains on a
     * block gets the server's answer without a packet for it. That is what the field travelling whole buys:
     * the weather is not a state to synchronise, it is a reading of something both sides already hold.
     */
    public RainMap rain() {
        return rain.map();
    }

    public SkyView view(float partialTick) {
        return feed.view(ticks, partialTick);
    }

    /** Which step is on screen, or -1 before the first one, for the line a debug overlay prints. */
    public long step() {
        return feed.step();
    }

    /** Dropped when the connection goes, so a second world does not open on the first one's clouds. */
    public void forget() {
        feed.forget();
        rain.forget();
    }
}
