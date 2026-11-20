package com.bunker.survival.launcher;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;

public final class DarkScrollBarUI extends BasicScrollBarUI {
    @Override
    protected void configureScrollBarColors() {
        thumbColor = LauncherTheme.SCROLL_THUMB;
        thumbDarkShadowColor = LauncherTheme.SCROLL_THUMB;
        thumbHighlightColor = LauncherTheme.SCROLL_THUMB_HOVER;
        thumbLightShadowColor = LauncherTheme.SCROLL_THUMB;
        trackColor = LauncherTheme.SCROLL_TRACK;
        trackHighlightColor = LauncherTheme.SCROLL_TRACK;
    }

    @Override
    protected JButton createDecreaseButton(int orientation) {
        return hiddenButton();
    }

    @Override
    protected JButton createIncreaseButton(int orientation) {
        return hiddenButton();
    }

    private static JButton hiddenButton() {
        JButton b = new JButton();
        b.setPreferredSize(new Dimension(0, 0));
        b.setMinimumSize(new Dimension(0, 0));
        b.setMaximumSize(new Dimension(0, 0));
        b.setOpaque(false);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        return b;
    }

    @Override
    protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
        g.setColor(LauncherTheme.SCROLL_TRACK);
        g.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height);
    }

    @Override
    protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
        if (thumbBounds.isEmpty() || !scrollbar.isEnabled()) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(isThumbRollover() ? LauncherTheme.SCROLL_THUMB_HOVER : LauncherTheme.SCROLL_THUMB);
        int pad = 3;
        g2.fillRoundRect(thumbBounds.x + pad, thumbBounds.y + pad,
                Math.max(4, thumbBounds.width - pad * 2),
                Math.max(4, thumbBounds.height - pad * 2), 6, 6);
        g2.dispose();
    }
}
