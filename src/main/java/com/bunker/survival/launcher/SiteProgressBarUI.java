package com.bunker.survival.launcher;

import javax.swing.JComponent;
import javax.swing.Timer;
import javax.swing.plaf.basic.BasicProgressBarUI;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * Thin continuous quota / busy bar matching site .quota-bar:
 * rounded track + overflow clip so a partial fill has a flat right edge.
 * Indeterminate mode uses a sliding accent segment.
 */
public final class SiteProgressBarUI extends BasicProgressBarUI {
    private static final int BAR_HEIGHT = 10;
    private static final float TRACK_RADIUS = BAR_HEIGHT;

    private Timer animTimer;
    private float animPhase;
    private PropertyChangeListener indeterminateListener;

    @Override
    public Dimension getPreferredSize(JComponent c) {
        Dimension d = super.getPreferredSize(c);
        d.height = BAR_HEIGHT;
        return d;
    }

    @Override
    public Dimension getMinimumSize(JComponent c) {
        Dimension d = super.getMinimumSize(c);
        d.height = BAR_HEIGHT;
        return d;
    }

    @Override
    public Dimension getMaximumSize(JComponent c) {
        Dimension d = super.getMaximumSize(c);
        d.height = BAR_HEIGHT;
        return d;
    }

    @Override
    protected void installListeners() {
        super.installListeners();
        indeterminateListener = this::onIndeterminateChanged;
        progressBar.addPropertyChangeListener("indeterminate", indeterminateListener);
        syncAnimTimer();
    }

    @Override
    protected void uninstallListeners() {
        stopAnimTimer();
        if (indeterminateListener != null) {
            progressBar.removePropertyChangeListener("indeterminate", indeterminateListener);
            indeterminateListener = null;
        }
        super.uninstallListeners();
    }

    private void onIndeterminateChanged(PropertyChangeEvent evt) {
        syncAnimTimer();
    }

    private void syncAnimTimer() {
        if (progressBar != null && progressBar.isIndeterminate() && progressBar.isShowing()) {
            startAnimTimer();
        } else {
            stopAnimTimer();
        }
    }

    private void startAnimTimer() {
        if (animTimer != null && animTimer.isRunning()) {
            return;
        }
        animTimer = new Timer(16, e -> {
            animPhase += 0.045f;
            if (animPhase > 1f) {
                animPhase -= 1f;
            }
            if (progressBar != null) {
                progressBar.repaint();
            }
        });
        animTimer.start();
    }

    private void stopAnimTimer() {
        if (animTimer != null) {
            animTimer.stop();
            animTimer = null;
        }
    }

    @Override
    protected void paintDeterminate(Graphics g, JComponent c) {
        if (!(g instanceof Graphics2D)) {
            super.paintDeterminate(g, c);
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = c.getWidth();
        int h = Math.min(BAR_HEIGHT, c.getHeight());
        int y = Math.max(0, (c.getHeight() - h) / 2);

        RoundRectangle2D.Float track = new RoundRectangle2D.Float(0, y, w, h, TRACK_RADIUS, TRACK_RADIUS);
        g2.setColor(LauncherTheme.SURFACE2);
        g2.fill(track);

        int amount = getAmountFull(c.getInsets(), w, h);
        if (amount > 0) {
            g2.setClip(track);
            float fillW = Math.min(w, Math.max(1, amount));
            boolean danger = progressBar.getPercentComplete() > 0.9;
            Rectangle2D.Float fill = new Rectangle2D.Float(0, y, fillW, h);
            if (danger) {
                g2.setColor(LauncherTheme.CLOSE_HOVER);
                g2.fill(fill);
            } else {
                g2.setPaint(new java.awt.GradientPaint(
                        0, y, LauncherTheme.ACCENT2,
                        fillW, y, LauncherTheme.ACCENT));
                g2.fill(fill);
            }
        }
        g2.dispose();
    }

    @Override
    protected void paintIndeterminate(Graphics g, JComponent c) {
        if (!(g instanceof Graphics2D)) {
            super.paintIndeterminate(g, c);
            return;
        }
        syncAnimTimer();
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = c.getWidth();
        int h = Math.min(BAR_HEIGHT, c.getHeight());
        int y = Math.max(0, (c.getHeight() - h) / 2);

        RoundRectangle2D.Float track = new RoundRectangle2D.Float(0, y, w, h, TRACK_RADIUS, TRACK_RADIUS);
        g2.setColor(LauncherTheme.SURFACE2);
        g2.fill(track);

        if (w > 4) {
            g2.setClip(track);
            float segW = Math.max(28f, w * 0.32f);
            float travel = w + segW;
            float x = animPhase * travel - segW;
            g2.setPaint(new java.awt.GradientPaint(
                    x, y, LauncherTheme.ACCENT2,
                    x + segW, y, LauncherTheme.ACCENT));
            g2.fill(new Rectangle2D.Float(x, y, segW, h));
        }
        g2.dispose();
    }
}
