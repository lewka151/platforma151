package com.bunker.survival.launcher.saves;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class SavePathDetector {
    private SavePathDetector() {
    }

    public static String expandEnv(String rawPath) {
        return expandEnv(rawPath, null);
    }

    /**
     * Expand env placeholders. {@code %INSTALL%} / {@code %GAMEDIR%} become {@code installDir}
     * when provided (absolute path to the game install root).
     */
    public static String expandEnv(String rawPath, Path installDir) {
        if (rawPath == null || rawPath.isBlank()) {
            return "";
        }
        String res = rawPath;
        String userProfile = System.getenv("USERPROFILE");
        if (userProfile == null || userProfile.isBlank()) {
            userProfile = System.getProperty("user.home");
        }
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isBlank()) {
            appData = userProfile + "\\AppData\\Roaming";
        }
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData == null || localAppData.isBlank()) {
            localAppData = userProfile + "\\AppData\\Local";
        }
        String progData = System.getenv("ProgramData");
        if (progData == null || progData.isBlank()) {
            progData = "C:\\ProgramData";
        }

        res = replaceIgnoreCase(res, "%USERPROFILE%", userProfile);
        res = replaceIgnoreCase(res, "%APPDATA%", appData);
        res = replaceIgnoreCase(res, "%LOCALAPPDATA%", localAppData);
        res = replaceIgnoreCase(res, "%PROGRAMDATA%", progData);
        res = replaceIgnoreCase(res, "%DOCUMENTS%", userProfile + "\\Documents");
        res = replaceIgnoreCase(res, "%SAVEDGAMES%", userProfile + "\\Saved Games");

        if (installDir != null) {
            String install = installDir.toAbsolutePath().normalize().toString();
            res = replaceIgnoreCase(res, "%INSTALL%", install);
            res = replaceIgnoreCase(res, "%GAMEDIR%", install);
        }

        return res;
    }

    private static String replaceIgnoreCase(String text, String search, String replacement) {
        if (text == null || search == null || replacement == null) {
            return text;
        }
        String lowerText = text.toLowerCase(Locale.ROOT);
        String lowerSearch = search.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        int prev = 0;
        int idx;
        while ((idx = lowerText.indexOf(lowerSearch, prev)) != -1) {
            sb.append(text, prev, idx);
            sb.append(replacement);
            prev = idx + search.length();
        }
        sb.append(text.substring(prev));
        return sb.toString();
    }

    /**
     * Expand env (+ optional install dir), resolve a single {@code *} path segment to the
     * best existing child (newest by nested file mtime), return path if it exists.
     */
    public static Path resolveExisting(String rawPath, Path installDir) {
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }
        // Paths that still need %INSTALL% without an install dir cannot resolve yet.
        String lower = rawPath.toLowerCase(Locale.ROOT);
        if (installDir == null && (lower.contains("%install%") || lower.contains("%gamedir%"))) {
            return null;
        }
        String expanded = expandEnv(rawPath.trim(), installDir);
        if (expanded.isBlank()) {
            return null;
        }
        expanded = expanded.replace('/', '\\');
        Path resolved = resolveWildcardSegment(expanded);
        if (resolved != null && Files.exists(resolved)) {
            return resolved.toAbsolutePath().normalize();
        }
        return null;
    }

    /**
     * Supports at most one {@code *} as a full path segment, e.g.
     * {@code C:\Users\x\Games\Age of Empires DE\Users\*\Saved Games}.
     */
    static Path resolveWildcardSegment(String expanded) {
        if (expanded == null || expanded.isBlank()) {
            return null;
        }
        if (!expanded.contains("*")) {
            return Path.of(expanded);
        }
        String norm = expanded.replace('/', '\\');
        String marker = "\\*\\";
        int star = norm.indexOf(marker);
        if (star < 0) {
            // trailing \* or leading *
            if (norm.endsWith("\\*")) {
                Path parent = Path.of(norm.substring(0, norm.length() - 2));
                return pickBestChild(parent, null);
            }
            return null;
        }
        Path parent = Path.of(norm.substring(0, star));
        String suffix = norm.substring(star + marker.length());
        return pickBestChild(parent, suffix);
    }

    private static Path pickBestChild(Path parent, String suffixAfterChild) {
        if (parent == null || !Files.isDirectory(parent)) {
            return null;
        }
        List<Path> candidates = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(parent)) {
            for (Path child : ds) {
                if (!Files.isDirectory(child)) {
                    continue;
                }
                Path full = (suffixAfterChild == null || suffixAfterChild.isBlank())
                        ? child
                        : child.resolve(suffixAfterChild);
                if (Files.exists(full)) {
                    candidates.add(full);
                }
            }
        } catch (IOException e) {
            return null;
        }
        if (candidates.isEmpty()) {
            return null;
        }
        candidates.sort(Comparator
                .comparingLong(SavePathDetector::newestFileMtime).reversed()
                .thenComparing(p -> p.toString().toLowerCase(Locale.ROOT)));
        return candidates.get(0);
    }

    private static long newestFileMtime(Path root) {
        try {
            return Files.getLastModifiedTime(root).toMillis();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    /** Derive install root from an exe path (parent of .exe; walk up one more if under bin\x64). */
    public static Path installDirFromExe(String exeOrIconPath) {
        if (exeOrIconPath == null || exeOrIconPath.isBlank()) {
            return null;
        }
        try {
            Path exe = Path.of(exeOrIconPath).toAbsolutePath().normalize();
            Path dir = Files.isDirectory(exe) ? exe : exe.getParent();
            if (dir == null) {
                return null;
            }
            // Witcher-style ...\bin\x64\witcher3.exe → install is two levels up
            String name = dir.getFileName() != null ? dir.getFileName().toString().toLowerCase(Locale.ROOT) : "";
            if (("x64".equals(name) || "x64_dx12".equals(name) || "win64".equals(name))
                    && dir.getParent() != null) {
                Path bin = dir.getParent();
                String binName = bin.getFileName() != null ? bin.getFileName().toString().toLowerCase(Locale.ROOT) : "";
                if ("bin".equals(binName) && bin.getParent() != null) {
                    return bin.getParent();
                }
            }
            return dir;
        } catch (Exception e) {
            return null;
        }
    }

    public static Path resolveSavePath(GameSaveDefinition def) {
        Path installDir = installDirFromExe(def != null ? def.getExeOrIconPath() : null);

        // 1. Custom path explicitly configured
        if (def != null && def.getCustomSavePath() != null && !def.getCustomSavePath().isBlank()) {
            Path p = resolveExisting(def.getCustomSavePath().trim(), installDir);
            if (p != null) {
                return p;
            }
            // Custom without INSTALL may still be a plain path
            String expanded = expandEnv(def.getCustomSavePath().trim(), installDir);
            if (!expanded.isBlank() && !expanded.contains("*")) {
                Path plain = Path.of(expanded);
                if (Files.exists(plain)) {
                    return plain.toAbsolutePath().normalize();
                }
            }
        }

        // 2. Default candidates
        if (def != null) {
            for (String cand : def.getDefaultCandidates()) {
                Path p = resolveExisting(cand.trim(), installDir);
                if (p != null) {
                    return p;
                }
            }
        }

        return null;
    }

    public static List<File> listFilesInSave(Path savePath) {
        return listFilesInSave(savePath, null);
    }

    public static List<File> listFilesInSave(Path savePath, GameSaveDefinition def) {
        List<File> list = new ArrayList<>();
        if (savePath == null || !Files.exists(savePath)) {
            return list;
        }
        if (Files.isRegularFile(savePath)) {
            list.add(savePath.toFile());
            return list;
        }
        try {
            List<Path> files = SavePacker.collectSaveFiles(savePath, def);
            final int maxFiles = 300;
            for (Path p : files) {
                list.add(p.toFile());
                if (list.size() >= maxFiles) {
                    break;
                }
            }
        } catch (Exception ignored) {
        }
        return list;
    }
}

