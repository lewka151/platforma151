package com.bunker.survival.launcher;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** 8px edge/corner resize for an undecorated frame (no maximize). */
public final class EdgeResizer extends MouseAdapter {
    private static final int ZONE = 8;
    private final JFrame frame;
    private int cursorType = Cursor.DEFAULT_CURSOR;
    private Point startScreen;
    private Rectangle startBounds;

    public EdgeResizer(JFrame frame) {
        this.frame = frame;
    }

    public void install(JPanel host) {
        host.addMouseListener(this);
        host.addMouseMotionListener(this);
    }

    private int cursorAt(MouseEvent e) {
        Component c = e.getComponent();
        int w = c.getWidth();
        int h = c.getHeight();
        int x = e.getX();
        int y = e.getY();
        boolean north = y < ZONE;
        boolean south = y >= h - ZONE;
        boolean west = x < ZONE;
        boolean east = x >= w - ZONE;
        if (north && west) {
            return Cursor.NW_RESIZE_CURSOR;
        }
        if (north && east) {
            return Cursor.NE_RESIZE_CURSOR;
        }
        if (south && west) {
            return Cursor.SW_RESIZE_CURSOR;
        }
        if (south && east) {
            return Cursor.SE_RESIZE_CURSOR;
        }
        if (north) {
            return Cursor.N_RESIZE_CURSOR;
        }
        if (south) {
            return Cursor.S_RESIZE_CURSOR;
        }
        if (west) {
            return Cursor.W_RESIZE_CURSOR;
        }
        if (east) {
            return Cursor.E_RESIZE_CURSOR;
        }
        return Cursor.DEFAULT_CURSOR;
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        cursorType = cursorAt(e);
        e.getComponent().setCursor(Cursor.getPredefinedCursor(cursorType));
    }

    @Override
    public void mouseExited(MouseEvent e) {
        if (startBounds == null) {
            e.getComponent().setCursor(Cursor.getDefaultCursor());
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        cursorType = cursorAt(e);
        if (cursorType == Cursor.DEFAULT_CURSOR || !SwingUtilities.isLeftMouseButton(e)) {
            startBounds = null;
            return;
        }
        startScreen = e.getLocationOnScreen();
        startBounds = frame.getBounds();
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        startBounds = null;
        startScreen = null;
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (startBounds == null || startScreen == null || cursorType == Cursor.DEFAULT_CURSOR) {
            return;
        }
        Point now = e.getLocationOnScreen();
        int dx = now.x - startScreen.x;
        int dy = now.y - startScreen.y;
        int x = startBounds.x;
        int y = startBounds.y;
        int w = startBounds.width;
        int h = startBounds.height;
        int minW = Math.max(frame.getMinimumSize().width, 200);
        int minH = Math.max(frame.getMinimumSize().height, 200);
        boolean moveX = cursorType == Cursor.W_RESIZE_CURSOR
                || cursorType == Cursor.NW_RESIZE_CURSOR
                || cursorType == Cursor.SW_RESIZE_CURSOR;
        boolean moveY = cursorType == Cursor.N_RESIZE_CURSOR
                || cursorType == Cursor.NW_RESIZE_CURSOR
                || cursorType == Cursor.NE_RESIZE_CURSOR;
        boolean growX = cursorType == Cursor.E_RESIZE_CURSOR
                || cursorType == Cursor.NE_RESIZE_CURSOR
                || cursorType == Cursor.SE_RESIZE_CURSOR
                || moveX;
        boolean growY = cursorType == Cursor.S_RESIZE_CURSOR
                || cursorType == Cursor.SW_RESIZE_CURSOR
                || cursorType == Cursor.SE_RESIZE_CURSOR
                || moveY;
        if (moveX) {
            x += dx;
            w -= dx;
        } else if (growX) {
            w += dx;
        }
        if (moveY) {
            y += dy;
            h -= dy;
        } else if (growY) {
            h += dy;
        }
        if (w < minW) {
            if (moveX) {
                x = startBounds.x + startBounds.width - minW;
            }
            w = minW;
        }
        if (h < minH) {
            if (moveY) {
                y = startBounds.y + startBounds.height - minH;
            }
            h = minH;
        }
        frame.setBounds(x, y, w, h);
    }
}
