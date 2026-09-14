package oas.dreyka.vortexdread.client.render;

/**
 * Marks a lightning bolt that lives inside the cloud deck rather than striking the ground.
 *
 * <p>The mod needs the bolt entity to exist because that is where Iris reads
 * {@code lightningBoltPosition} from, and that position is what the patched cloud pass scatters
 * light from. What it does not need is the entity's own geometry: vanilla draws a bolt as a stack of
 * flat quads, which is a fair picture of a channel touching the ground and a very poor one of a
 * discharge buried in a cloud, where nothing of the channel is visible at all.
 *
 * <p>Carried on the render state rather than on the entity, since only the renderer asks and a render
 * state never crosses the network.
 */
public interface CloudFlash {
    boolean vortexdread$insideTheDeck();

    void vortexdread$insideTheDeck(boolean inside);
}
