package com.bunker.survival.launcher;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.awt.GraphicsEnvironment;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThemedPopupMenuTest {

    @Test
    void showAndDismissLifecycle() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(),
                "Requires display for JWindow tray menu");

        AtomicBoolean clicked = new AtomicBoolean(false);
        ThemedPopupMenu menu = new ThemedPopupMenu();
        menu.addItem("Test", e -> clicked.set(true));

        runOnEdt(() -> menu.showAtScreen(100, 100));
        runOnEdt(() -> assertTrue(menu.isVisible(), "menu should be visible after show"));

        runOnEdt(menu::dismiss);
        runOnEdt(() -> assertFalse(menu.isVisible(), "menu should not be visible after dismiss"));

        runOnEdt(() -> menu.showAtScreen(120, 120));
        runOnEdt(() -> assertTrue(menu.isVisible(), "menu should show again"));
        runOnEdt(menu::dismiss);
        runOnEdt(() -> assertFalse(menu.isVisible(), "menu should dismiss again"));

        runOnEdt(menu::dispose);
        assertFalse(clicked.get());
    }

    @Test
    void dismissWhenNotShownIsSafe() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(),
                "Requires display for JWindow tray menu");

        ThemedPopupMenu menu = new ThemedPopupMenu();
        menu.addItem("X", e -> {});
        runOnEdt(menu::dismiss);
        runOnEdt(() -> assertFalse(menu.isVisible()));
        runOnEdt(menu::dispose);
    }

    private static void runOnEdt(Runnable action) throws Exception {
        if (java.awt.EventQueue.isDispatchThread()) {
            action.run();
            return;
        }
        var latch = new java.util.concurrent.CountDownLatch(1);
        AtomicBoolean error = new AtomicBoolean(false);
        javax.swing.SwingUtilities.invokeLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                error.set(true);
                throw t;
            } finally {
                latch.countDown();
            }
        });
        latch.await();
        if (error.get()) {
            throw new AssertionError("EDT action failed");
        }
    }
}
