package com.bunker.survival.launcher;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/**
 * AWT system tray for the Swing launcher (hide on close, restore on single left-click).
 * Right-click opens a themed Swing context menu matching the app chrome.
 */
public final class LauncherTray {
    private final SystemTray systemTray;
    private final TrayIcon trayIcon;
    private final ThemedPopupMenu popup;
    private boolean installed;

    private LauncherTray(SystemTray systemTray, TrayIcon trayIcon, ThemedPopupMenu popup) {
        this.systemTray = systemTray;
        this.trayIcon = trayIcon;
        this.popup = popup;
    }

    public static boolean isSupported() {
        return SystemTray.isSupported();
    }

    static LauncherTray install(Runnable onOpen, Runnable onLaunchGame, Runnable onOpenSaves, Runnable onExit,
                                Consumer<String> log) {
        return install("Bunker", "bunker", onOpen, onLaunchGame, onOpenSaves, onExit, log);
    }

    public static LauncherTray install(String tooltip, String iconStem, Runnable onOpen, Runnable onLaunchGame,
                                       Runnable onOpenSaves, Runnable onExit, Consumer<String> log) {
        if (!SystemTray.isSupported()) {
            return null;
        }
        try {
            SystemTray tray = SystemTray.getSystemTray();
            ThemedPopupMenu menu = buildMenu(onOpen, onLaunchGame, onOpenSaves, onExit);
            Image image = createIconImage(iconStem);
            // No AWT PopupMenu — right-click is handled with themed Swing menu.
            TrayIcon icon = new TrayIcon(image, tooltip != null ? tooltip : "Bunker");
            icon.setImageAutoSize(true);
            icon.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 1) {
                        if (menu.isVisible()) {
                            menu.dismiss();
                            return;
                        }
                        if (onOpen != null) {
                            onOpen.run();
                        }
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (e.getButton() == MouseEvent.BUTTON3 || e.isPopupTrigger()) {
                        showTrayMenu(menu, e);
                    }
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    if (e.getButton() == MouseEvent.BUTTON1 && menu.isVisible()) {
                        menu.dismiss();
                        return;
                    }
                    if (e.isPopupTrigger()) {
                        showTrayMenu(menu, e);
                    }
                }
            });
            tray.add(icon);
            LauncherTray lt = new LauncherTray(tray, icon, menu);
            lt.installed = true;
            return lt;
        } catch (AWTException e) {
            if (log != null) {
                log.accept("Трей недоступен: " + e.getMessage());
            }
            return null;
        }
    }

    private static void showTrayMenu(ThemedPopupMenu menu, MouseEvent e) {
        SwingUtilities.invokeLater(() -> {
            if (menu.isVisible()) {
                menu.dismiss();
                return;
            }
            menu.dismiss();
            int x;
            int y;
            try {
                x = e.getXOnScreen();
                y = e.getYOnScreen();
            } catch (Exception ex) {
                x = e.getX();
                y = e.getY();
            }
            menu.showAtScreen(x, y);
        });
    }

    private static ThemedPopupMenu buildMenu(Runnable onOpen, Runnable onLaunchGame, Runnable onOpenSaves,
                                             Runnable onExit) {
        ThemedPopupMenu menu = new ThemedPopupMenu();
        menu.addItem("Открыть", e -> {
            if (onOpen != null) {
                onOpen.run();
            }
        });
        if (onLaunchGame != null) {
            menu.addItem("Запустить", e -> onLaunchGame.run());
        }
        if (onOpenSaves != null) {
            menu.addItem("Облачные сейвы", e -> onOpenSaves.run());
        }
        menu.addMenuSeparator();
        menu.addItem("Выход", e -> {
            if (onExit != null) {
                onExit.run();
            }
        });
        return menu;
    }

    public boolean isInstalled() {
        return installed;
    }

    public void uninstall() {
        if (!installed) {
            return;
        }
        try {
            if (popup != null) {
                popup.dispose();
            }
            systemTray.remove(trayIcon);
        } catch (Exception ignored) {
        }
        installed = false;
    }

    public void showBalloon(String caption, String text) {
        if (!installed) {
            return;
        }
        try {
            trayIcon.displayMessage(caption, text, TrayIcon.MessageType.INFO);
        } catch (Exception ignored) {
        }
    }

    static Image createIconImage() {
        return createIconImage("bunker");
    }

    public static Image createIconImage(String stem) {
        String iconStem = (stem == null || stem.isBlank()) ? "bunker" : stem;
        if ("platforma151".equalsIgnoreCase(iconStem)) {
            return createPlatformaPlIcon(32);
        }
        try {
            Path ico = findIconFile(iconStem + ".ico");
            if (ico != null) {
                Path png32 = ico.getParent() != null ? ico.getParent().resolve(iconStem + "_32.png") : null;
                if (png32 != null && Files.isRegularFile(png32)) {
                    return javax.imageio.ImageIO.read(png32.toFile());
                }
                java.awt.Toolkit tk = java.awt.Toolkit.getDefaultToolkit();
                Image img = tk.getImage(ico.toUri().toURL());
                if (img != null) {
                    return img;
                }
            }
        } catch (Exception ignored) {
        }
        int s = 32;
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(0x0a0b0e));
            g.fillRoundRect(0, 0, s, s, 6, 6);
            g.setColor(new Color(0xe8c247));
            g.fillRect(4, 4, s - 8, 3);
            g.setColor(new Color(0x4a3f2a));
            g.drawRoundRect(1, 1, s - 3, s - 3, 5, 5);
            g.setColor(new Color(0x2a241c));
            g.fillRect(10, 12, 12, 14);
            g.setColor(new Color(0xe8c247));
            g.drawRect(10, 12, 12, 14);
            g.fillOval(18, 17, 3, 3);
        } finally {
            g.dispose();
        }
        return img;
    }

    private static Image createPlatformaPlIcon(int s) {
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(new Color(0x0a0b0e));
            g.fillRoundRect(0, 0, s, s, 6, 6);
            g.setColor(Color.WHITE);
            g.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, Math.max(12, s * 14 / 32)));
            java.awt.FontMetrics fm = g.getFontMetrics();
            String text = "Pl";
            int x = (s - fm.stringWidth(text)) / 2;
            int y = (s - fm.getHeight()) / 2 + fm.getAscent();
            g.drawString(text, x, y);
        } finally {
            g.dispose();
        }
        return img;
    }

    private static Path findIconFile(String fileName) {
        List<Path> candidates = new ArrayList<>();
        try {
            Optional<String> cmd = ProcessHandle.current().info().command();
            if (cmd.isPresent()) {
                Path exe = Path.of(cmd.get()).toAbsolutePath().normalize();
                Path dir = exe.getParent();
                if (dir != null) {
                    candidates.add(dir.resolve("assets").resolve("icons").resolve(fileName));
                    candidates.add(dir.resolve("app").resolve("assets").resolve("icons").resolve(fileName));
                    if (dir.getParent() != null) {
                        candidates.add(dir.getParent().resolve("assets").resolve("icons").resolve(fileName));
                    }
                }
            }
        } catch (Exception ignored) {
        }
        candidates.add(Path.of("assets", "icons", fileName).toAbsolutePath());
        String appData = System.getenv("APPDATA");
        if (appData != null) {
            candidates.add(Path.of(appData, "Platforma151", "icons", fileName));
            candidates.add(Path.of(appData, "BunkerSurvival", "icons", fileName));
        }
        for (Path p : candidates) {
            if (p != null && Files.isRegularFile(p)) {
                return p;
            }
        }
        return null;
    }
}
