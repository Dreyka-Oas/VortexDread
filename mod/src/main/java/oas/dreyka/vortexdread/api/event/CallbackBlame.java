package oas.dreyka.vortexdread.api.event;

import java.util.Set;

import oas.dreyka.vortexdread.VortexDread;

/**
 * Reports a listener's thrown exception once per listener class, so a broken addon writes one stack
 * trace for the life of the server rather than one per block a storm takes.
 *
 * <p>Each callback interface keeps its own set of already-blamed classes. The same addon failing in two
 * different callbacks is two bugs and gets reported twice.
 */
final class CallbackBlame {
    private CallbackBlame() {
    }

    /**
     * @param blamed   the calling interface's own already-reported set
     * @param listener the listener that threw
     * @param t        what it threw
     * @param tag      names the callback in the log line, for instance "block taken"
     * @param outcome  what happens now, for instance "the block is taken anyway"
     */
    static void report(Set<String> blamed, Object listener, Throwable t, String tag, String outcome) {
        String name = listener.getClass().getName();
        if (blamed.add(name)) {
            VortexDread.LOGGER.error("[VortexDread] " + tag + " listener {} threw, and is being reported "
                    + "once only; " + outcome, name, t);
        }
    }
}
