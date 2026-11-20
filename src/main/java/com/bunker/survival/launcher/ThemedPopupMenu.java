package com.bunker.survival.launcher;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.InputMap;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JWindow;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Tray / context menu as always-on-top undecorated windows (menu + full-screen backdrop).
 * Closes on backdrop click, outside click, Escape, or focus loss; disposes all windows on dismiss.
 */
public final class ThemedPopupMenu {
    private static final int ITEM_H = 32;
    private static final int MENU_MIN_W = 187;
    private static final int CORNER = 8;
    private static final int OUTSIDE_LISTEN_DELAY_MS = 100;
    /** Nearly invisible but opaque — avoids Windows compositor ghost layers. */
    private static final Color BACKDROP_COLOR = new Color(0, 0, 0, 1);

    private final List<MenuEntry> entries = new ArrayList<>();
    private JWindow menuWindow;
    private JWindow backdropWindow;
    private WindowAdapter menuFocusListener;
    private AWTEventListener outsideClickListener;
    private Timer armOutsideTimer;
    private boolean open;
    private boolean dismissArmed;

    private sealed interface MenuEntry permits ActionEntry, SeparatorEntry {
    }

    private record ActionEntry(String text, ActionListener action) implements MenuEntry {
    }

    private record SeparatorEntry() implements MenuEntry {
    }

    public ThemedMenuItemProxy addItem(String text, ActionListener action) {
        entries.add(new ActionEntry(text, action));
        return new ThemedMenuItemProxy();
    }

    public void addMenuSeparator() {
        entries.add(new SeparatorEntry());
    }

    /** Compatibility stub — items are rebuilt on each show. */
    public static final class ThemedMenuItemProxy {
    }

    public boolean isVisible() {
        return open && menuWindow != null && menuWindow.isShowing();
    }

    public void dismiss() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::dismiss);
            return;
        }
        open = false;
        dismissArmed = false;
        if (armOutsideTimer != null) {
            armOutsideTimer.stop();
            armOutsideTimer = null;
        }
        uninstallOutsideClickListener();
        disposeMenuWindow();
        disposeBackdropWindow();
    }

    /** Permanently release tray menu window resources (app shutdown). */
    public void dispose() {
        dismiss();
    }

    /** Show near screen coordinates (tray right-click). */
    public void showAtScreen(int screenX, int screenY) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> showAtScreen(screenX, screenY));
            return;
        }
        dismiss();

        backdropWindow = createBackdropWindow();
        menuWindow = createMenuWindow(screenX, screenY);

        final JWindow menuWin = menuWindow;
        final JWindow backdropWin = backdropWindow;

        open = true;
        dismissArmed = false;

        backdropWin.setVisible(true);
        menuWin.setVisible(true);
        menuWin.toFront();
        try {
            menuWin.requestFocus();
        } catch (Exception ignored) {
        }

        armOutsideTimer = new Timer(OUTSIDE_LISTEN_DELAY_MS, e -> {
            armOutsideTimer = null;
            if (open && menuWindow == menuWin) {
                dismissArmed = true;
                installOutsideClickListener(menuWin);
            }
        });
        armOutsideTimer.setRepeats(false);
        armOutsideTimer.start();
    }

    private JWindow createBackdropWindow() {
        Rectangle screen = screenBoundsAt(0, 0);
        JWindow backdrop = new JWindow();
        backdrop.setType(Window.Type.POPUP);
        backdrop.setAlwaysOnTop(true);
        backdrop.setBackground(BACKDROP_COLOR);
        backdrop.setBounds(screen);
        JPanel catcher = new JPanel();
        catcher.setOpaque(true);
        catcher.setBackground(BACKDROP_COLOR);
        MouseAdapter dismissOnClick = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (dismissArmed) {
                    dismiss();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (dismissArmed) {
                    dismiss();
                }
            }
        };
        catcher.addMouseListener(dismissOnClick);
        backdrop.setContentPane(catcher);
        return backdrop;
    }

    private JWindow createMenuWindow(int screenX, int screenY) {
        JWindow win = new JWindow();
        win.setType(Window.Type.POPUP);
        win.setAlwaysOnTop(true);
        win.setFocusableWindowState(true);
        win.setBackground(LauncherTheme.PANEL);

        MenuPanel panel = new MenuPanel();
        for (MenuEntry entry : entries) {
            if (entry instanceof SeparatorEntry) {
                panel.add(createSeparator());
            } else if (entry instanceof ActionEntry ae) {
                panel.add(createItem(ae.text(), e -> {
                    dismiss();
                    if (ae.action() != null) {
                        ae.action().actionPerformed(e);
                    }
                }));
            }
        }
        win.setContentPane(panel);
        win.pack();

        Point loc = clampToScreen(screenX, screenY, win.getWidth(), win.getHeight());
        win.setLocation(loc);

        menuFocusListener = new WindowAdapter() {
            @Override
            public void windowLostFocus(WindowEvent e) {
                SwingUtilities.invokeLater(() -> {
                    if (menuWindow == win && open) {
                        dismiss();
                    }
                });
            }
        };
        win.addWindowFocusListener(menuFocusListener);

        InputMap inputMap = win.getRootPane().getInputMap(JRootPane.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = win.getRootPane().getActionMap();
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "dismissMenu");
        actionMap.put("dismissMenu", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                dismiss();
            }
        });

        return win;
    }

    private void disposeMenuWindow() {
        if (menuWindow == null) {
            return;
        }
        try {
            if (menuFocusListener != null) {
                menuWindow.removeWindowFocusListener(menuFocusListener);
                menuFocusListener = null;
            }
            menuWindow.getRootPane().getInputMap(JRootPane.WHEN_IN_FOCUSED_WINDOW).clear();
            menuWindow.getRootPane().getActionMap().clear();
            menuWindow.setAlwaysOnTop(false);
            menuWindow.getContentPane().removeAll();
            menuWindow.setVisible(false);
            menuWindow.dispose();
        } catch (Exception ignored) {
        }
        menuWindow = null;
    }

    private void disposeBackdropWindow() {
        if (backdropWindow == null) {
            return;
        }
        try {
            backdropWindow.setAlwaysOnTop(false);
            backdropWindow.getContentPane().removeAll();
            backdropWindow.setVisible(false);
            backdropWindow.dispose();
        } catch (Exception ignored) {
        }
        backdropWindow = null;
    }

    private static Rectangle screenBoundsAt(int x, int y) {
        Rectangle bounds = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration().getBounds();
        try {
            for (var device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
                Rectangle b = device.getDefaultConfiguration().getBounds();
                if (b.contains(x, y)) {
                    bounds = b;
                    break;
                }
            }
        } catch (Exception ignored) {
        }
        return bounds;
    }

    private static Point clampToScreen(int x, int y, int w, int h) {
        Rectangle bounds = screenBoundsAt(x, y);
        int nx = x;
        int ny = y;
        if (ny + h > bounds.y + bounds.height) {
            ny = y - h;
        }
        if (nx + w > bounds.x + bounds.width) {
            nx = bounds.x + bounds.width - w;
        }
        if (nx < bounds.x) {
            nx = bounds.x;
        }
        if (ny < bounds.y) {
            ny = bounds.y;
        }
        return new Point(nx, ny);
    }

    private void installOutsideClickListener(JWindow win) {
        uninstallOutsideClickListener();
        outsideClickListener = event -> {
            if (!(event instanceof MouseEvent me)) {
                return;
            }
            int id = me.getID();
            if (id != MouseEvent.MOUSE_PRESSED && id != MouseEvent.MOUSE_RELEASED) {
                return;
            }
            if (!open || !dismissArmed || menuWindow != win || !win.isShowing()) {
                return;
            }
            Point p;
            try {
                p = me.getLocationOnScreen();
            } catch (Exception ex) {
                return;
            }
            Rectangle bounds = win.getBounds();
            if (bounds.contains(p)) {
                return;
            }
            SwingUtilities.invokeLater(this::dismiss);
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(outsideClickListener, AWTEvent.MOUSE_EVENT_MASK);
    }

    private void uninstallOutsideClickListener() {
        if (outsideClickListener != null) {
            Toolkit.getDefaultToolkit().removeAWTEventListener(outsideClickListener);
            outsideClickListener = null;
        }
    }

    private static JPanel createSeparator() {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.setBorder(BorderFactory.createEmptyBorder(3, 12, 3, 12));
        JPanel line = new JPanel();
        line.setOpaque(true);
        line.setBackground(LauncherTheme.BORDER);
        line.setPreferredSize(new Dimension(10, 1));
        wrap.add(line, BorderLayout.CENTER);
        wrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, 8));
        return wrap;
    }

    private MenuItemRow createItem(String text, ActionListener action) {
        MenuItemRow row = new MenuItemRow(text);
        row.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    action.actionPerformed(null);
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                row.setHover(true);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                row.setHover(false);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    row.setPressed(true);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                row.setPressed(false);
            }
        });
        return row;
    }

    private static Font menuFont() {
        Font base = LauncherTheme.uiFont != null ? LauncherTheme.uiFont : new Font("Segoe UI", Font.PLAIN, 13);
        return base.deriveFont(Font.PLAIN, 12.5f);
    }

    private static final class MenuPanel extends JPanel {
        MenuPanel() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setOpaque(true);
            setBackground(LauncherTheme.PANEL);
            setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            d.width = Math.max(d.width, MENU_MIN_W);
            return d;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                g2.setColor(LauncherTheme.PANEL);
                g2.fill(new RoundRectangle2D.Float(0, 0, w, h, CORNER, CORNER));
                g2.setColor(LauncherTheme.BORDER);
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, CORNER, CORNER));
                Color accent = LauncherTheme.ACCENT;
                g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 55));
                g2.draw(new RoundRectangle2D.Float(1.5f, 1.5f, w - 3f, h - 3f, CORNER - 1, CORNER - 1));
            } finally {
                g2.dispose();
            }
        }
    }

    private static final class MenuItemRow extends JPanel {
        private final JLabel label;
        private boolean hovering;
        private boolean pressed;

        MenuItemRow(String text) {
            setLayout(new BorderLayout());
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 14));
            setPreferredSize(new Dimension(MENU_MIN_W - 12, ITEM_H));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, ITEM_H));
            label = new JLabel(text);
            label.setFont(menuFont());
            label.setForeground(LauncherTheme.TEXT);
            label.setOpaque(false);
            add(label, BorderLayout.CENTER);
        }

        void setHover(boolean on) {
            hovering = on;
            updateVisual();
        }

        void setPressed(boolean on) {
            pressed = on;
            updateVisual();
        }

        private void updateVisual() {
            boolean active = hovering || pressed;
            label.setForeground(active ? LauncherTheme.ACCENT : LauncherTheme.TEXT);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (!hovering && !pressed) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                g2.setColor(LauncherTheme.BTN_HOVER_BG);
                g2.fill(new RoundRectangle2D.Float(2, 2, w - 4, h - 4, 7, 7));
                Color accent = LauncherTheme.ACCENT;
                g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), pressed ? 220 : 160));
                g2.fill(new RoundRectangle2D.Float(4, 7, 3, h - 14, 2, 2));
            } finally {
                g2.dispose();
            }
        }
    }
}
