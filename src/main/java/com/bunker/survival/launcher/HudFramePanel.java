package com.bunker.survival.launcher;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** Card panel matching site .panel (radius 12). Border optional. */
public final class HudFramePanel extends JPanel {
    private final boolean drawBorder;

    public HudFramePanel() {
        this(true);
    }

    public HudFramePanel(boolean drawBorder) {
        this.drawBorder = drawBorder;
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        g2.setColor(LauncherTheme.PANEL);
        g2.fillRoundRect(1, 1, w - 2, h - 2, 12, 12);
        if (drawBorder) {
            g2.setStroke(new BasicStroke(1f));
            g2.setColor(LauncherTheme.BORDER);
            g2.drawRoundRect(1, 1, w - 3, h - 3, 12, 12);
        }
        g2.dispose();
        super.paintComponent(g);
    }
}
