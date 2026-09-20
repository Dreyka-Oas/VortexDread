package oas.dreyka.vortexdread.client;

import oas.dreyka.vortexdread.atmosphere.SkyFeed;
import oas.dreyka.vortexdread.atmosphere.SkySnapshot;
import oas.dreyka.vortexdread.atmosphere.SkyView;

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

    private long ticks;

    public void onTick() {
        ticks++;
    }

    /** Called on the client thread when a field arrives. */
    public void accept(SkySnapshot sky, int cadence) {
        feed.offer(sky, ticks, cadence);
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
    }
}
