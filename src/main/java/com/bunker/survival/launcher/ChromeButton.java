package com.bunker.survival.launcher;

import javax.swing.JButton;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** Title-bar chrome with a painted glyph (no Unicode that Windows L&F turns into "..."). */
final class ChromeButton extends JButton {
    enum Kind {
        MINIMIZE, CLOSE
    }

    private final Kind kind;

    ChromeButton(Kind kind) {
        this.kind = kind;
        setUI(new BasicButtonUI());
        setText("");
        setFocusable(false);
        setFocusPainted(false);
        setOpaque(true);
        setContentAreaFilled(true);
        setBorderPainted(false);
        setForeground(LauncherTheme.TEXT);
        setBackground(LauncherTheme.BG2);
        setToolTipText(kind == Kind.CLOSE ? "Свернуть в трей" : "Свернуть");
        Dimension hit = new Dimension(46, 32);
        setPreferredSize(hit);
        setMinimumSize(new Dimension(32, 32));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                setBackground(kind == Kind.CLOSE ? LauncherTheme.CLOSE_HOVER : LauncherTheme.PANEL_INNER);
                setForeground(LauncherTheme.TEXT);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                setBackground(LauncherTheme.BG2);
                setForeground(LauncherTheme.TEXT);
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(getForeground());
        g2.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int cx = getWidth() / 2;
        int cy = getHeight() / 2;
        int s = 5;
        if (kind == Kind.MINIMIZE) {
            g2.drawLine(cx - s, cy, cx + s, cy);
        } else {
            g2.drawLine(cx - s, cy - s, cx + s, cy + s);
            g2.drawLine(cx + s, cy - s, cx - s, cy + s);
        }
        g2.dispose();
    }
}
