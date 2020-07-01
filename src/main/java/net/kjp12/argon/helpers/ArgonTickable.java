package net.kjp12.argon.helpers;

import net.kjp12.argon.TickingState;

public interface ArgonTickable {
    /**
     * This is the same as tick in normal tickables.
     * <p>
     * The real difference here is that this tick calculates as if x ticks happened.
     * <em>You must not tick AI the amount of times as passed through here.</em>
     * <p>
     * However, you may change the AI logic to account for the lack of ticks.
     */
    void argon$tick(int ticks);

    /**
     * Used for checking if the entity is safe to mutate blocks.
     * <p>
     * Note: Due to the unpredictable nature of mods adding entities and blocks, this is {@link TickingState#SYNC SYNC} by default.
     *
     * @return the state the entity is currently in, or {@link TickingState#SYNC}.
     */
    default TickingState argon$currentState() {
        return TickingState.SYNC;
    }
}
