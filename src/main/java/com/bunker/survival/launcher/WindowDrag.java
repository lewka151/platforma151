package com.bunker.survival.launcher;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** Drag an undecorated frame by pressing and moving a component. */
public final class WindowDrag {
    private WindowDrag() {
    }

    public static void install(JFrame frame, Component... components) {
        if (frame == null || components == null) {
            return;
        }
        MouseAdapter drag = new MouseAdapter() {
            private Point grab;

            @Override
            public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e) || e.getClickCount() >= 2) {
                    grab = null;
                    return;
                }
                Point loc = frame.getLocationOnScreen();
                grab = new Point(e.getXOnScreen() - loc.x, e.getYOnScreen() - loc.y);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                grab = null;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (grab == null) {
                    return;
                }
                frame.setLocation(e.getXOnScreen() - grab.x, e.getYOnScreen() - grab.y);
            }
        };
        for (Component c : components) {
            if (c == null) {
                continue;
            }
            c.addMouseListener(drag);
            c.addMouseMotionListener(drag);
        }
    }
}
