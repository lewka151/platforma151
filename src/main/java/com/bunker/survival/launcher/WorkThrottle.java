package com.bunker.survival.launcher;

/**
 * Cooperative throttling for long scan/pack/sync work so the desktop stays responsive.
 */
public final class WorkThrottle {
    private WorkThrottle() {
    }

    /** Pause between heavy units (files, games, scan roots). */
    public static void yieldBetweenUnits() {
        try {
            Thread.sleep(35L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Thread.yield();
    }

    /** Slightly longer pause between larger batches (full game sync, disk roots). */
    public static void yieldBetweenBatches() {
        try {
            Thread.sleep(80L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Thread.yield();
    }

    /** Tiny pause while streaming bytes into a zip. */
    public static void yieldWhileStreaming() {
        try {
            Thread.sleep(2L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
