package net.kjp12.argon;

public enum TickingState {
    /**
     * This entity is inactive. It only persists in memory.
     * This will never be checked for activity.
     * <i>It must bump its own priority to at minimum {@link #SLEEP} to be ticked.</i>
     * <p>
     * For: Signs, Chests, Permanent projectiles
     */
    FROZEN(0),
    /**
     * This entity is inactive. It's ticked every 300 ticks, or 15 seconds.
     * Time dilation will be applied.
     * AI mustn't be ticked 300 times in inactive state.
     * However, the AI's randomness can be adjusted for the delay.
     * <em>Attacking AI must not be ticked more than once.</em>
     * <em>If the AI is attacking, bump the priority to {@link #CRITICAL}.</em>
     * <em>If the AI is mutating blocks, bump the priority to {@link #ACTIVE}.</em>
     * <em>Any attempt to mutate blocks must be immediately off-loaded to the main thread.</em>
     * <p>
     * For: Furnaces and projectiles.
     */
    SLEEP(0),
    /**
     * This entity is inactive. It's ticked every 20 ticks, or 1 second.
     * Time dilation will be applied.
     * AI mustn't be ticked 20 times in inactive state.
     * However, the AI can be adjusted for the delay.
     * <em>Attacking AI must not be ticked more than once.</em>
     * <em>If the AI is attacking, bump the priority to {@link #CRITICAL}.</em>
     * <em>If the AI is mutating blocks, bump the priority to {@link #ACTIVE}.<em/>
     * <em>Any attempt to mutate blocks must be immediately off-loaded to the main thread.</em>
     * <p>
     * For: Still mobs.
     */
    INACTIVE(0),
    /**
     * This entity is active, but at a slower tickrate. It is ticked at around the same speed as hoppers.
     * This is primarily intended for hoppers and anything else that may interface with hoppers.
     */
    HOPPER(0),
    /**
     * This entity is active. It's ticked every tick, or 1/20th of a second.
     * Time dilation will only be applied if the server lags behind.
     * Attacking AI should be moved to {@link #CRITICAL}.
     * <p>
     * For: Actively walking mobs, hoppers (safe if the other tile entity is concurrent.)
     */
    ACTIVE(0),
    /**
     * This entity is active and requires syncing to the world thread. It's ticked every tick, or 1/20th of a second.
     * If the AI is done mutating blocks or moving, bump the priority to {@link #INACTIVE}.
     * <p>
     * Note: This is the default state of entities and tile entities as it cannot be guaranteed that they won't
     * mutate blocks in the world. This may cause initial lag as the server will suddenly offload all entities
     * into the queue on load-in.
     * <p>
     * For: Chunk-mutating entities and blocks.
     */
    SYNC(1),
    /**
     * This entity is active and ticked independently from the main server thread.
     * If the AI is done attacking players, bump the priority to {@link #INACTIVE}.
     * <p>
     * For: Active PvE fights, Active Projectiles
     */
    CRITICAL(2);
    /**
     * This is more of a descriptor of which thread it's on.
     * To avoid issues with concurrency, the ticking state of
     * the block or entity you're about to mutate should
     * be the same lock as you. However, most items will be retrofitted for concurrent use to avoid self-destruction.
     */
    public final byte lock;

    TickingState(int lock) {
        this.lock = (byte) lock;
    }
}
