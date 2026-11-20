package com.bunker.survival.launcher.saves;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.filechooser.FileSystemView;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class GameIconExtractor {
    private static final Map<String, ImageIcon> CACHE = new ConcurrentHashMap<>();
    private static final int DEFAULT_SIZE = 36;

    private GameIconExtractor() {
    }

    public static ImageIcon getIcon(GameSaveDefinition def, Path shortcutDir, int size) {
        String cacheKey = def.getKey() + "_" + size;
        ImageIcon cached = CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        Image img = tryExtract(def, shortcutDir);
        if (img == null) {
            img = generatePlaceholder(def.getName(), size);
        } else {
            img = scaleImage(img, size, size);
        }

        ImageIcon icon = new ImageIcon(img);
        CACHE.put(cacheKey, icon);
        return icon;
    }

    public static ImageIcon getIcon(GameSaveDefinition def, Path shortcutDir) {
        return getIcon(def, shortcutDir, DEFAULT_SIZE);
    }

    private static Image tryExtract(GameSaveDefinition def, Path shortcutDir) {
        // 1. Try explicit exe or icon path
        if (def.getExeOrIconPath() != null && !def.getExeOrIconPath().isBlank()) {
            File f = new File(def.getExeOrIconPath());
            if (f.exists()) {
                Image img = extractFromFile(f);
                if (img != null) {
                    return img;
                }
            }
        }

        // 2. Try shortcut file in shortcutDir (e.g. C:\Users\lewka\Desktop\Games)
        if (shortcutDir != null && def.getShortcutName() != null && !def.getShortcutName().isBlank()) {
            Path lnk = shortcutDir.resolve(def.getShortcutName());
            if (Files.exists(lnk)) {
                Image img = extractFromFile(lnk.toFile());
                if (img != null) {
                    return img;
                }
            }
        }

        // 3. Try resolved path itself (if it's an exe or directory with an icon)
        if (def.getResolvedPath() != null) {
            File f = def.getResolvedPath().toFile();
            if (f.exists()) {
                Image img = extractFromFile(f);
                if (img != null) {
                    return img;
                }
            }
        }

        return null;
    }

    private static Image extractFromFile(File file) {
        String name = file.getName().toLowerCase();
        if (!(name.endsWith(".exe") || name.endsWith(".lnk") || name.endsWith(".ico")
                || name.endsWith(".url") || name.endsWith(".dll"))) {
            return null;
        }
        try {
            FileSystemView fsv = FileSystemView.getFileSystemView();
            Icon sysIcon = fsv.getSystemIcon(file);
            if (sysIcon != null) {
                return iconToImage(sysIcon);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Image iconToImage(Icon icon) {
        if (icon instanceof ImageIcon ii) {
            return ii.getImage();
        }
        int w = Math.max(1, icon.getIconWidth());
        int h = Math.max(1, icon.getIconHeight());
        BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = bi.createGraphics();
        try {
            icon.paintIcon(null, g, 0, 0);
        } finally {
            g.dispose();
        }
        return bi;
    }

    private static BufferedImage scaleImage(Image src, int width, int height) {
        BufferedImage dest = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dest.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(src, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return dest;
    }

    public static BufferedImage generatePlaceholder(String name, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // Background with subtle rounded corners
            g.setColor(new Color(0x1a, 0x1e, 0x24));
            g.fillRoundRect(1, 1, size - 2, size - 2, 8, 8);

            // Border
            g.setColor(new Color(0xa8, 0x97, 0x6a, 180));
            g.drawRoundRect(1, 1, size - 3, size - 3, 8, 8);

            // Initials
            String initials = getInitials(name);
            g.setColor(new Color(0xe6, 0xe4, 0xdf));
            int fontSize = size <= 32 ? 11 : 13;
            g.setFont(new Font("Segoe UI", Font.BOLD, fontSize));

            FontMetrics fm = g.getFontMetrics();
            int strW = fm.stringWidth(initials);
            int strH = fm.getAscent();
            int x = (size - strW) / 2;
            int y = (size + strH) / 2 - 2;
            g.drawString(initials, x, y);
        } finally {
            g.dispose();
        }
        return img;
    }

    private static String getInitials(String name) {
        if (name == null || name.isBlank()) {
            return "GM";
        }
        String[] parts = name.trim().split("[\\s\\-_/]+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!p.isEmpty() && Character.isLetterOrDigit(p.charAt(0))) {
                sb.append(Character.toUpperCase(p.charAt(0)));
                if (sb.length() >= 3) {
                    break;
                }
            }
        }
        if (sb.length() == 0) {
            return name.substring(0, Math.min(2, name.length())).toUpperCase();
        }
        return sb.toString();
    }
}
