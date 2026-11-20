package com.bunker.survival.launcher.saves;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Scans %USERPROFILE%\\Desktop\\Games for non-Steam games.
 * Supports direct folders and .lnk / .url shortcuts to install folders.
 */
public final class DesktopGamesScanner {
    private DesktopGamesScanner() {
    }

    public static Path defaultGamesDir() {
        String userProfile = System.getenv("USERPROFILE");
        if (userProfile == null || userProfile.isBlank()) {
            userProfile = System.getProperty("user.home");
        }
        return Path.of(userProfile, "Desktop", "Games");
    }

    public static final class ScanResult {
        public final int added;
        public final int updated;
        public final int skippedSteam;
        public final int skippedUnresolved;
        public final Path root;

        public ScanResult(int added, int updated, int skippedSteam, int skippedUnresolved, Path root) {
            this.added = added;
            this.updated = updated;
            this.skippedSteam = skippedSteam;
            this.skippedUnresolved = skippedUnresolved;
            this.root = root;
        }

        public int totalTouched() {
            return added + updated;
        }
    }

    public static ScanResult scanAndRegister(GameSaveRegistry registry) throws IOException {
        return scanAllKnownRoots(registry, null);
    }

    public static ScanResult scanAllKnownRoots(GameSaveRegistry registry, java.util.function.Consumer<String> progress) throws IOException {
        int added = 0;
        int updated = 0;
        int skippedSteam = 0;
        int skippedUnresolved = 0;
        Path last = defaultGamesDir();
        for (Path root : collectScanRoots(registry)) {
            if (progress != null) {
                progress.accept("Скан: " + root);
            }
            boolean allowSteam = root.toString().toLowerCase(Locale.ROOT).replace('/', '\\').contains("\\steamapps\\common");
            ScanResult r = scanAndRegister(registry, root, allowSteam);
            added += r.added;
            updated += r.updated;
            skippedSteam += r.skippedSteam;
            skippedUnresolved += r.skippedUnresolved;
            last = r.root != null ? r.root : last;
            com.bunker.survival.launcher.WorkThrottle.yieldBetweenBatches();
        }
        return new ScanResult(added, updated, skippedSteam, skippedUnresolved, last);
    }

    private static List<Path> collectScanRoots(GameSaveRegistry registry) {
        List<Path> roots = new ArrayList<>();
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
        java.util.function.Consumer<Path> add = p -> {
            if (p == null) {
                return;
            }
            try {
                Path n = p.toAbsolutePath().normalize();
                if (Files.isDirectory(n) && seen.add(n.toString().toLowerCase(Locale.ROOT))) {
                    roots.add(n);
                }
            } catch (Exception ignored) {
            }
        };
        if (registry != null && registry.getShortcutsDir() != null) {
            add.accept(registry.getShortcutsDir());
        }
        add.accept(defaultGamesDir());
        File[] drives = File.listRoots();
        if (drives != null) {
            for (File drive : drives) {
                Path root = drive.toPath();
                add.accept(root.resolve("Games"));
                add.accept(root.resolve("Steam").resolve("steamapps").resolve("common"));
                add.accept(root.resolve("Program Files").resolve("Epic Games"));
                add.accept(root.resolve("Program Files (x86)").resolve("Epic Games"));
                add.accept(root.resolve("Program Files"));
                add.accept(root.resolve("Program Files (x86)"));
                // common Steam library locations
                add.accept(root.resolve("Program Files (x86)").resolve("Steam").resolve("steamapps").resolve("common"));
                add.accept(root.resolve("Program Files").resolve("Steam").resolve("steamapps").resolve("common"));
            }
        }
        return roots;
    }

    public static ScanResult scanAndRegister(GameSaveRegistry registry, Path root) throws IOException {
        return scanAndRegister(registry, root, false);
    }

    public static ScanResult scanAndRegister(GameSaveRegistry registry, Path root, boolean allowSteamInstalls) throws IOException {
        if (root == null || !Files.isDirectory(root)) {
            return new ScanResult(0, 0, 0, 0, root != null ? root : defaultGamesDir());
        }
        root = root.toAbsolutePath().normalize();

        int added = 0;
        int updated = 0;
        int skippedSteam = 0;
        int skippedUnresolved = 0;

        List<Path> entries = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(root)) {
            for (Path p : ds) {
                String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                if (Files.isDirectory(p)) {
                    entries.add(p);
                } else if (Files.isRegularFile(p) && (name.endsWith(".lnk") || name.endsWith(".url"))) {
                    entries.add(p);
                }
            }
        }
        entries.sort(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)));

        for (Path entry : entries) {
            String fileName = entry.getFileName().toString();
            String stem = stripShortcutExt(fileName);

            if (stem.equalsIgnoreCase("Steam") || stem.equalsIgnoreCase("Steam Client")
                    || isBlacklistedGame(stem, null, null)) {
                skippedSteam++;
                continue;
            }

            Path gameDir;
            String exeHint;
            String shortcutName;

            if (Files.isDirectory(entry)) {
                if (!allowSteamInstalls && looksLikeSteam(entry)) {
                    skippedSteam++;
                    continue;
                }
                gameDir = entry.toAbsolutePath().normalize();
                exeHint = findExeHint(gameDir);
                shortcutName = stem + ".lnk";
            } else {
                ResolvedShortcut resolved = resolveShortcut(entry);
                if (resolved == null) {
                    skippedUnresolved++;
                    continue;
                }
                if (resolved.steam && !allowSteamInstalls) {
                    skippedSteam++;
                    continue;
                }
                gameDir = resolved.gameDir;
                exeHint = resolved.exePath;
                shortcutName = fileName;
                if (gameDir == null || !Files.isDirectory(gameDir)) {
                    skippedUnresolved++;
                    continue;
                }
                if (!allowSteamInstalls && (looksLikeSteam(gameDir) || isSteamPath(gameDir.toString()))) {
                    skippedSteam++;
                    continue;
                }
            }

            // Never use the install folder as savePath — only known catalog save folders.
            GameSaveRegistry.CatalogEntry cat = registry.findCatalogByShortcutOrName(shortcutName, stem);
            if (cat == null && Files.isDirectory(entry)) {
                cat = registry.findCatalogByShortcutOrName(stem + ".lnk", stem);
            }
            if (cat == null) {
                skippedUnresolved++;
                continue;
            }
            Path savePath = GameSaveRegistry.resolveCatalogSavePath(cat, gameDir);
            if (savePath != null && (isUnsafeSyncRoot(savePath) || (gameDir != null && savePath.equals(gameDir)))) {
                savePath = null;
            }

            String key = cat.key;
            String displayName = cat.name != null && !cat.name.isBlank() ? cat.name : stem;
            String saveStr = savePath != null ? savePath.toString() : null;
            String installStr = gameDir != null ? gameDir.toString() : null;
            if (isBlacklistedGame(displayName, saveStr, installStr) || isBlacklistedGame(stem, saveStr, installStr)) {
                skippedUnresolved++;
                continue;
            }
            boolean existed = registry.getGame(key) != null;
            registry.addOrUpdateCustomGame(key, displayName, saveStr, exeHint, shortcutName, saveStr, installStr);
            if (existed) {
                updated++;
            } else {
                added++;
            }
            if (savePath == null) {
                skippedUnresolved++;
            }
        }
        return new ScanResult(added, updated, skippedSteam, skippedUnresolved, root);
    }

    /** Non-game Steam / system folders that must never appear in the list. */
    public static boolean isBlacklistedGame(String name, String savePath, String installPath) {
        if (name != null) {
            String n = name.trim().toLowerCase(Locale.ROOT);
            if (n.equals("steam controller configs")
                    || n.contains("steam controller config")
                    || n.equals("steamworks shared")
                    || n.equals("steamworks redistributables")
                    || n.equals("proton")
                    || n.startsWith("proton ")
                    || n.equals("steam linux runtime")
                    || n.contains("steam linux runtime")) {
                return true;
            }
        }
        return isBlacklistedPath(savePath) || isBlacklistedPath(installPath);
    }

    private static boolean isBlacklistedPath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String p = path.toLowerCase(Locale.ROOT).replace('/', '\\');
        return p.contains("\\steam controller configs")
                || p.contains("\\steamworks shared")
                || p.contains("\\steamworks redistributables");
    }

    /** Reject obvious install / system trees as sync roots. */
    static boolean isUnsafeSyncRoot(Path path) {
        if (path == null) {
            return true;
        }
        String p = path.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT).replace('/', '\\');
        if (p.matches("^[a-z]:\\\\?$") || p.endsWith(":\\windows") || p.contains("\\windows\\")
                || p.contains("\\system32") || p.contains("\\program files")
                || p.contains("\\steamapps\\common\\")) {
            return true;
        }
        try {
            if (Files.isDirectory(path)) {
                if (Files.isRegularFile(path.resolve("steam_api.dll"))
                        || Files.isRegularFile(path.resolve("steam_api64.dll"))
                        || Files.isRegularFile(path.resolve("steam_appid.txt"))) {
                    return true;
                }
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(path, "*.exe")) {
                    int exeCount = 0;
                    for (Path ignored : ds) {
                        exeCount++;
                        if (exeCount >= 2) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    static final class ResolvedShortcut {
        final Path gameDir;
        final String exePath;
        final boolean steam;

        ResolvedShortcut(Path gameDir, String exePath, boolean steam) {
            this.gameDir = gameDir;
            this.exePath = exePath;
            this.steam = steam;
        }
    }

    static ResolvedShortcut resolveShortcut(Path shortcut) {
        String name = shortcut.getFileName().toString().toLowerCase(Locale.ROOT);
        try {
            if (name.endsWith(".url")) {
                return resolveUrl(shortcut);
            }
            if (name.endsWith(".lnk")) {
                return resolveLnk(shortcut);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    static ResolvedShortcut resolveUrl(Path urlFile) throws IOException {
        String text = Files.readString(urlFile);
        String target = null;
        for (String line : text.split("\\R")) {
            String t = line.trim();
            if (t.regionMatches(true, 0, "URL=", 0, 4)) {
                target = t.substring(4).trim();
                break;
            }
        }
        if (target == null || target.isBlank()) {
            return null;
        }
        String lower = target.toLowerCase(Locale.ROOT);
        if (lower.startsWith("steam://") || lower.contains("steampowered.com")) {
            return new ResolvedShortcut(null, null, true);
        }
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("mailto:")) {
            return null;
        }
        if (lower.startsWith("file:///")) {
            target = Path.of(java.net.URI.create(target)).toString();
        }
        return fromTarget(target, null);
    }

    static ResolvedShortcut resolveLnk(Path lnk) throws Exception {
        String lnkEsc = psSingleQuoted(lnk.toAbsolutePath().normalize().toString());
        String script = "$ws = New-Object -ComObject WScript.Shell; "
                + "$s = $ws.CreateShortcut(" + lnkEsc + "); "
                + "Write-Output $s.TargetPath; "
                + "Write-Output $s.WorkingDirectory;";
        ProcessBuilder pb = new ProcessBuilder(
                "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", script);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = p.waitFor();
        if (code != 0) {
            return null;
        }
        String[] lines = out.split("\\R");
        String target = lines.length > 0 ? lines[0].trim() : "";
        String workDir = lines.length > 1 ? lines[1].trim() : "";
        if (target.isBlank() && workDir.isBlank()) {
            return null;
        }
        return fromTarget(target, workDir.isBlank() ? null : workDir);
    }

    static ResolvedShortcut fromTarget(String target, String workingDir) {
        if (target != null && isSteamPath(target)) {
            return new ResolvedShortcut(null, null, true);
        }
        Path targetPath = null;
        if (target != null && !target.isBlank()) {
            try {
                targetPath = Path.of(target).toAbsolutePath().normalize();
            } catch (Exception ignored) {
            }
        }
        Path workPath = null;
        if (workingDir != null && !workingDir.isBlank()) {
            try {
                workPath = Path.of(workingDir).toAbsolutePath().normalize();
            } catch (Exception ignored) {
            }
        }

        if (targetPath != null && Files.isRegularFile(targetPath)) {
            String n = targetPath.getFileName().toString().toLowerCase(Locale.ROOT);
            if (n.equals("steam.exe")) {
                return new ResolvedShortcut(null, null, true);
            }
            Path parent = targetPath.getParent();
            if (parent != null && Files.isDirectory(parent)) {
                String exe = n.endsWith(".exe") ? targetPath.toString() : findExeHint(parent);
                return new ResolvedShortcut(parent, exe, isSteamPath(parent.toString()));
            }
        }
        if (targetPath != null && Files.isDirectory(targetPath)) {
            return new ResolvedShortcut(targetPath, findExeHint(targetPath), isSteamPath(targetPath.toString()));
        }
        if (workPath != null && Files.isDirectory(workPath)) {
            String exe = null;
            if (targetPath != null && Files.isRegularFile(targetPath)
                    && targetPath.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".exe")) {
                exe = targetPath.toString();
            } else {
                exe = findExeHint(workPath);
            }
            return new ResolvedShortcut(workPath, exe, isSteamPath(workPath.toString()));
        }
        return null;
    }

    static boolean isSteamPath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String p = path.toLowerCase(Locale.ROOT).replace('/', '\\');
        return p.contains("\\steam.exe")
                || p.endsWith("steam.exe")
                || p.contains("\\steamapps\\")
                || p.contains("\\steam\\steamapps");
    }

    static String stripShortcutExt(String fileName) {
        String n = fileName;
        String lower = n.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".lnk") || lower.endsWith(".url")) {
            return n.substring(0, n.length() - 4);
        }
        return n;
    }

    static boolean looksLikeSteam(Path dir) {
        String name = dir.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.equals("steam") || name.equals("steamapps") || name.contains("steamapps")) {
            return true;
        }
        if (Files.isRegularFile(dir.resolve("steam_api.dll"))
                || Files.isRegularFile(dir.resolve("steam_api64.dll"))
                || Files.isRegularFile(dir.resolve("steam_appid.txt"))) {
            return true;
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.url")) {
            for (Path url : ds) {
                try {
                    String text = Files.readString(url);
                    if (text.toLowerCase(Locale.ROOT).contains("steam://")) {
                        return true;
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        try (Stream<Path> walk = Files.walk(dir, 2)) {
            return walk.anyMatch(p -> {
                String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                return n.equals("steam_api.dll") || n.equals("steam_api64.dll") || n.equals("steam_appid.txt");
            });
        } catch (Exception e) {
            return false;
        }
    }

    static String findExeHint(Path dir) {
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.exe")) {
            Path best = null;
            for (Path p : ds) {
                String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                if (n.contains("unins") || n.contains("setup") || n.contains("crash")) {
                    continue;
                }
                best = p;
                break;
            }
            return best != null ? best.toAbsolutePath().toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    static String sanitize(String name) {
        String s = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]+", "_");
        s = s.replaceAll("_+", "_");
        if (s.startsWith("_")) {
            s = s.substring(1);
        }
        if (s.endsWith("_")) {
            s = s.substring(0, s.length() - 1);
        }
        if (s.length() > 48) {
            s = s.substring(0, 48);
        }
        return s.isBlank() ? "game" : s;
    }

    private static String psSingleQuoted(String s) {
        return "'" + (s == null ? "" : s.replace("'", "''")) + "'";
    }
}
