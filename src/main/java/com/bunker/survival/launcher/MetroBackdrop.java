package com.bunker.survival.launcher;

import javax.swing.JPanel;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;

public final class MetroBackdrop extends JPanel {
    public MetroBackdrop() {
        setOpaque(true);
        setBackground(LauncherTheme.BG);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        int w = getWidth();
        int h = getHeight();
        g2.setPaint(new GradientPaint(0, 0, LauncherTheme.BG, 0, h, LauncherTheme.BG2));
        g2.fillRect(0, 0, w, h);
        g2.dispose();
    }
}
