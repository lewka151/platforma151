package com.bunker.survival.launcher;

import javax.swing.JButton;
import javax.swing.Timer;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

/**
 * Launcher button matching site CSS (.btn):
 * - ~150ms hover / ~80ms press transitions
 * - Press offset drawn inside bounds (no clip / "sinking")
 * - Fixed preferred height for consistent toolbars
 */
public final class SiteButton extends JButton {
    private static final int RADIUS = 10;
    private static final float PRESS_OFFSET_Y = 1.5f;
    /** Matches site --btn-min-h / btn-sm toolbar row. */
    public static final int PREFERRED_HEIGHT = 38;
    private static final int PAD_Y = 8;
    private static final int PAD_X = 14;
    /** ~150ms at 15ms ticks with lerp factor. */
    private static final float HOVER_SPEED = 0.22f;
    /** ~80ms press. */
    private static final float PRESS_SPEED = 0.35f;

    private final boolean primary;
    private float hoverProgress = 0f;
    private float pressProgress = 0f;
    private boolean isHovered = false;
    private boolean isPressed = false;

    private Timer animTimer;

    public SiteButton(String text, boolean primary) {
        super(text);
        this.primary = primary;

        setUI(new BasicButtonUI());
        setFocusPainted(false);
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFont(LauncherTheme.uiFontBold);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        // Bottom pad leaves room for press translate so parent FlowLayout does not clip.
        setBorder(javax.swing.BorderFactory.createEmptyBorder(PAD_Y, PAD_X, PAD_Y + 2, PAD_X));

        setupListeners();
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        d.height = PREFERRED_HEIGHT;
        return d;
    }

    @Override
    public Dimension getMinimumSize() {
        Dimension d = super.getMinimumSize();
        d.height = PREFERRED_HEIGHT;
        return d;
    }

    @Override
    public Dimension getMaximumSize() {
        Dimension d = super.getMaximumSize();
        d.height = PREFERRED_HEIGHT;
        return d;
    }

    private void setupListeners() {
        animTimer = new Timer(15, e -> updateAnimation());

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (isEnabled()) {
                    isHovered = true;
                    startAnimation();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                isHovered = false;
                isPressed = false;
                startAnimation();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (isEnabled() && e.getButton() == MouseEvent.BUTTON1) {
                    isPressed = true;
                    startAnimation();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                isPressed = false;
                startAnimation();
            }
        });
    }

    private void startAnimation() {
        if (!animTimer.isRunning()) {
            animTimer.start();
        }
    }

    private void updateAnimation() {
        float targetHover = isHovered && isEnabled() ? 1f : 0f;
        float targetPress = isPressed && isEnabled() ? 1f : 0f;

        boolean changed = false;

        if (Math.abs(hoverProgress - targetHover) > 0.01f) {
            hoverProgress += (targetHover - hoverProgress) * HOVER_SPEED;
            changed = true;
        } else {
            hoverProgress = targetHover;
        }

        if (Math.abs(pressProgress - targetPress) > 0.01f) {
            pressProgress += (targetPress - pressProgress) * PRESS_SPEED;
            changed = true;
        } else {
            pressProgress = targetPress;
        }

        if (changed) {
            repaint();
        } else if (hoverProgress == targetHover && pressProgress == targetPress) {
            animTimer.stop();
        }
    }

    private Color interpolateColor(Color c1, Color c2, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = (int) (c1.getRed() + t * (c2.getRed() - c1.getRed()));
        int g = (int) (c1.getGreen() + t * (c2.getGreen() - c1.getGreen()));
        int b = (int) (c1.getBlue() + t * (c2.getBlue() - c1.getBlue()));
        int a = (int) (c1.getAlpha() + t * (c2.getAlpha() - c1.getAlpha()));
        return new Color(r, g, b, a);
    }

    private Color getCurrentBg() {
        if (!isEnabled()) {
            return primary
                    ? new Color(0xa8, 0x97, 0x6a, 100)
                    : new Color(0x16, 0x1a, 0x21, 100);
        }
        if (primary) {
            Color normal = LauncherTheme.BTN_PRIMARY_BG;
            Color hover = LauncherTheme.ACCENT2;
            Color press = new Color(0x7a, 0x6b, 0x44);
            Color base = interpolateColor(normal, hover, hoverProgress);
            return interpolateColor(base, press, pressProgress);
        } else {
            Color normal = LauncherTheme.BTN_BG;
            Color hover = LauncherTheme.BTN_HOVER_BG;
            Color press = new Color(0x19, 0x1e, 0x27);
            Color base = interpolateColor(normal, hover, hoverProgress);
            return interpolateColor(base, press, pressProgress);
        }
    }

    private Color getCurrentBorder() {
        if (!isEnabled()) {
            return new Color(0x2a, 0x2e, 0x36, 100);
        }
        // Border only while pressed (site-like); hover changes fill only.
        if (pressProgress > 0.05f) {
            if (primary) {
                return interpolateColor(LauncherTheme.BTN_PRIMARY_BG, new Color(0x7a, 0x6b, 0x44), pressProgress);
            }
            return interpolateColor(LauncherTheme.BORDER, LauncherTheme.ACCENT2, pressProgress);
        }
        if (primary) {
            return getCurrentBg();
        }
        return LauncherTheme.BORDER;
    }

    private Color getCurrentFg() {
        if (!isEnabled()) {
            return primary ? new Color(0x14, 0x12, 0x0e, 100) : LauncherTheme.MUTED;
        }
        // Text color never changes on hover/press.
        return primary ? LauncherTheme.BTN_PRIMARY_FG : LauncherTheme.TEXT;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        float yOffset = pressProgress * PRESS_OFFSET_Y;
        // Draw inside bounds: shrink height by offset so press never clips below.
        float drawH = h - 1f - PRESS_OFFSET_Y;
        float drawY = 0.5f + yOffset;

        RoundRectangle2D.Float shape = new RoundRectangle2D.Float(0.5f, drawY, w - 1f, drawH, RADIUS, RADIUS);

        g2.setColor(getCurrentBg());
        g2.fill(shape);

        g2.setColor(getCurrentBorder());
        g2.draw(shape);

        String text = getText();
        if (text != null && !text.isEmpty()) {
            g2.setFont(getFont());
            g2.setColor(getCurrentFg());
            FontMetrics fm = g2.getFontMetrics();
            int textWidth = fm.stringWidth(text);
            int textHeight = fm.getAscent() - fm.getDescent();
            int x = (w - textWidth) / 2;
            int y = Math.round(drawY + (drawH + textHeight) / 2f) - 1;
            g2.drawString(text, x, y);
        }

        g2.dispose();
    }
}
