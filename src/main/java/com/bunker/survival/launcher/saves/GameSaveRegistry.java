package com.bunker.survival.launcher.saves;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GameSaveRegistry {
    private static final Gson GSON = new GsonBuilder().create();
    private final CustomGameStore store;
    private Path shortcutsDir;
    private final Map<String, GameSaveDefinition> definitions = new LinkedHashMap<>();
    private final List<CatalogEntry> catalog = new ArrayList<>();

    public static final class CatalogEntry {
        public String key;
        public String name;
        public String shortcut;
        public List<String> paths;
        /** Optional: only pack files with these extensions (e.g. ".sl2", ".sav"). */
        public List<String> extensions;
        /** Optional: relative glob patterns under the save root (e.g. "*.ess", "SaveData/*"). */
        public List<String> include;
        /** Optional: relative globs to exclude (for example star-star slash star.log). */
        public List<String> exclude;
        /** Optional: directory names to exclude anywhere under the save root. */
        public List<String> exclude_dirs;
        /** Optional: settings/config path templates. */
        public List<String> settings_paths;
    }

    public GameSaveRegistry(Path appDir) {
        this.store = new CustomGameStore(appDir);
        this.shortcutsDir = resolveConfiguredShortcutsDir(store.loadConfig().shortcutsDir);
        loadCatalog(appDir.resolve("games-catalog.cache.json"));
        // Path resolution is deferred to reload() so the window can appear immediately.
    }

    private static Path resolveConfiguredShortcutsDir(String configured) {
        if (configured != null && !configured.isBlank()) {
            try {
                Path p = Path.of(configured.trim());
                if (Files.isDirectory(p)) {
                    return p.toAbsolutePath().normalize();
                }
            } catch (Exception ignored) {
            }
        }
        Path defaultDesktopGames = DesktopGamesScanner.defaultGamesDir();
        return Files.isDirectory(defaultDesktopGames) ? defaultDesktopGames : null;
    }

    public synchronized void reload() {
        definitions.clear();
        scanCatalogIntoList();
        applyCustomOverridesAndGames();
        resolveAllPaths();
        dropUnresolvedCatalogGames();
        dedupeAndPrune();
        applyHiddenGameKeys();
    }

    /** Replace in-memory catalog from remote/cache entries and rebuild definitions. */
    public synchronized void replaceCatalogFromEntries(List<CatalogEntry> entries) {
        catalog.clear();
        if (entries != null) {
            for (CatalogEntry entry : entries) {
                if (entry != null && entry.key != null && !entry.key.isBlank()) {
                    catalog.add(entry);
                }
            }
        }
        reload();
    }

    public synchronized int catalogSize() {
        return catalog.size();
    }

    /** Re-scan typical save folders. Returns how many catalog games were found. */
    public synchronized int scanSupportedGames() {
        reload();
        int found = 0;
        for (GameSaveDefinition def : definitions.values()) {
            if (!def.isCustom() && def.getResolvedPath() != null) {
                found++;
            }
        }
        return found;
    }

    public Path getShortcutsDir() {
        return shortcutsDir;
    }

    public synchronized void setShortcutsDir(Path dir) {
        if (dir != null && Files.isDirectory(dir)) {
            this.shortcutsDir = dir.toAbsolutePath().normalize();
        } else {
            this.shortcutsDir = null;
        }
        CustomGameStore.SavesConfigData cfg = store.loadConfig();
        cfg.shortcutsDir = this.shortcutsDir != null ? this.shortcutsDir.toString() : null;
        store.saveConfig(cfg);
    }

    public CustomGameStore getStore() {
        return store;
    }

    private void loadCatalog(Path cacheFile) {
        catalog.clear();
        if (cacheFile != null && Files.isRegularFile(cacheFile)) {
            try {
                String json = Files.readString(cacheFile, StandardCharsets.UTF_8);
                JsonElement root = JsonParser.parseString(json);
                if (loadCatalogArray(root)) {
                    return;
                }
            } catch (Exception ignored) {
            }
        }
        try (InputStream in = GameSaveRegistry.class.getResourceAsStream("/games-catalog.json")) {
            if (in == null) {
                return;
            }
            JsonElement root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            loadCatalogArray(root);
        } catch (Exception ignored) {
        }
    }

    private boolean loadCatalogArray(JsonElement root) {
        if (root == null) {
            return false;
        }
        JsonArray arr;
        if (root.isJsonArray()) {
            arr = root.getAsJsonArray();
        } else if (root.isJsonObject() && root.getAsJsonObject().has("items")) {
            arr = root.getAsJsonObject().getAsJsonArray("items");
        } else {
            return false;
        }
        int before = catalog.size();
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) {
                continue;
            }
            CatalogEntry entry = GSON.fromJson(el, CatalogEntry.class);
            if (entry != null && entry.key != null && !entry.key.isBlank()) {
                catalog.add(entry);
            }
        }
        return catalog.size() > before;
    }

    private void scanCatalogIntoList() {
        Set<String> hidden = hiddenGameKeys();
        for (CatalogEntry entry : catalog) {
            if (hidden.contains(entry.key)) {
                continue;
            }
            GameSaveDefinition def = new GameSaveDefinition(
                    entry.key,
                    entry.name != null ? entry.name : entry.key,
                    entry.shortcut,
                    entry.paths);
            def.setCustom(false);
            def.setFileExtensions(entry.extensions);
            def.setIncludeGlobs(entry.include);
            def.setExcludeGlobs(entry.exclude);
            def.setExcludeDirs(entry.exclude_dirs);
            definitions.put(entry.key, def);
        }
    }

    private void dropUnresolvedCatalogGames() {
        List<String> drop = new ArrayList<>();
        for (GameSaveDefinition def : definitions.values()) {
            if (!def.isCustom() && def.getResolvedPath() == null) {
                drop.add(def.getKey());
            }
        }
        for (String key : drop) {
            definitions.remove(key);
        }
    }

    private void applyCustomOverridesAndGames() {
        CustomGameStore.SavesConfigData cfg = store.loadConfig();
        if (cfg.pathOverrides != null) {
            for (Map.Entry<String, String> entry : cfg.pathOverrides.entrySet()) {
                GameSaveDefinition def = definitions.get(entry.getKey());
                if (def != null) {
                    def.setCustomSavePath(entry.getValue());
                }
            }
        }

        List<CustomGameStore.CustomGameEntry> customGames = store.loadCustomGames();
        for (CustomGameStore.CustomGameEntry cge : customGames) {
            if (cge.key == null || cge.key.isBlank()) {
                continue;
            }
            if (DesktopGamesScanner.isBlacklistedGame(cge.name, cge.savePath, cge.installPath)) {
                continue;
            }
            GameSaveDefinition def = new GameSaveDefinition(cge.key, cge.name != null ? cge.name : cge.key);
            def.setCustom(true);
            def.setCustomSavePath(cge.savePath);
            def.setExeOrIconPath(cge.exePath);
            def.setInstallPath(cge.installPath != null && !cge.installPath.isBlank()
                    ? cge.installPath
                    : (cge.exePath != null ? parentDirOf(cge.exePath) : null));
            def.setShortcutName(cge.shortcutName);
            def.setSettingsPath(cge.settingsPath);
            definitions.put(cge.key, def);
        }
    }

    private static String parentDirOf(String filePath) {
        try {
            Path p = Path.of(filePath);
            Path parent = p.getParent();
            return parent != null ? parent.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void resolveAllPaths() {
        for (GameSaveDefinition def : definitions.values()) {
            Path p = SavePathDetector.resolveSavePath(def);
            def.setResolvedPath(p);
        }
    }

    public synchronized List<CatalogEntry> getCatalogEntries() {
        return Collections.unmodifiableList(new ArrayList<>(catalog));
    }

    public synchronized CatalogEntry findCatalogByShortcutOrName(String shortcutFileName, String displayName) {
        String shortLower = shortcutFileName != null ? shortcutFileName.toLowerCase(java.util.Locale.ROOT).trim() : "";
        String nameLower = displayName != null ? displayName.toLowerCase(java.util.Locale.ROOT).trim() : "";
        String shortStem = shortLower;
        if (shortStem.endsWith(".lnk") || shortStem.endsWith(".url")) {
            shortStem = shortStem.substring(0, shortStem.length() - 4);
        }
        for (CatalogEntry e : catalog) {
            if (e.shortcut != null && !e.shortcut.isBlank()
                    && e.shortcut.toLowerCase(java.util.Locale.ROOT).equals(shortLower)) {
                return e;
            }
        }
        for (CatalogEntry e : catalog) {
            String n = e.name != null ? e.name.toLowerCase(java.util.Locale.ROOT).trim() : "";
            String k = e.key != null ? e.key.toLowerCase(java.util.Locale.ROOT).trim() : "";
            if (!n.isEmpty() && (n.equals(nameLower) || n.equals(shortStem))) {
                return e;
            }
            if (!k.isEmpty() && (k.equals(nameLower) || k.equals(shortStem))) {
                return e;
            }
        }
        return null;
    }

    /** First existing save path from catalog path templates, or null. */
    public static Path resolveCatalogSavePath(CatalogEntry entry) {
        return resolveCatalogSavePath(entry, null);
    }

    public static Path resolveCatalogSavePath(CatalogEntry entry, Path installDir) {
        if (entry == null || entry.paths == null) {
            return null;
        }
        for (String raw : entry.paths) {
            Path p = SavePathDetector.resolveExisting(raw, installDir);
            if (p != null) {
                return p;
            }
        }
        return null;
    }

    public synchronized List<GameSaveDefinition> getAllGames() {
        return Collections.unmodifiableList(new ArrayList<>(definitions.values()));
    }

    public synchronized GameSaveDefinition getGame(String key) {
        return definitions.get(key);
    }

    public synchronized void updatePathOverride(String key, String customPath) {
        GameSaveDefinition def = definitions.get(key);
        if (def == null) {
            return;
        }
        def.setCustomSavePath(customPath);
        def.setResolvedPath(SavePathDetector.resolveSavePath(def));

        CustomGameStore.SavesConfigData cfg = store.loadConfig();
        if (cfg.pathOverrides == null) {
            cfg.pathOverrides = new java.util.HashMap<>();
        }
        if (customPath == null || customPath.isBlank()) {
            cfg.pathOverrides.remove(key);
        } else {
            cfg.pathOverrides.put(key, customPath);
        }
        store.saveConfig(cfg);
    }

    public synchronized void addOrUpdateCustomGame(String rawKey, String name, String savePath, String exePath, String shortcut) {
        addOrUpdateCustomGame(rawKey, name, savePath, exePath, shortcut, null, null);
    }

    public synchronized void addOrUpdateCustomGame(String rawKey, String name, String savePath, String exePath, String shortcut, String settingsPath) {
        addOrUpdateCustomGame(rawKey, name, savePath, exePath, shortcut, settingsPath, null);
    }

    public synchronized void addOrUpdateCustomGame(String rawKey, String name, String savePath, String exePath,
                                                   String shortcut, String settingsPath, String installPath) {
        if (DesktopGamesScanner.isBlacklistedGame(name, savePath, installPath != null ? installPath : parentDirOf(exePath))) {
            return;
        }
        String key = rawKey;
        if (key == null || key.isBlank()) {
            key = "custom_" + System.currentTimeMillis();
        }
        String effectiveKey = key.trim().toLowerCase().replaceAll("[^a-z0-9_\\-]", "_");
        if (isGameHidden(effectiveKey)) {
            return;
        }

        String normName = normalizeNameKey(name);
        String normSave = normalizePathKey(savePath);
        String normInstall = normalizePathKey(installPath != null ? installPath : parentDirOf(exePath));

        GameSaveDefinition mergeTarget = definitions.get(effectiveKey);
        for (GameSaveDefinition existing : definitions.values()) {
            if (existing.getKey().equals(effectiveKey)) {
                continue;
            }
            String es = normalizePathKey(existing.getCustomSavePath() != null
                    ? existing.getCustomSavePath()
                    : (existing.getResolvedPath() != null ? existing.getResolvedPath().toString() : null));
            String ei = normalizePathKey(existing.getInstallPath());
            String en = normalizeNameKey(existing.getName());
            boolean sameName = normName != null && normName.equals(en);
            boolean sameSave = normSave != null && normSave.equals(es);
            boolean sameInstall = normInstall != null && normInstall.equals(ei);
            if (sameName || sameSave || sameInstall) {
                mergeTarget = preferDefinition(mergeTarget, existing);
                effectiveKey = mergeTarget.getKey();
                break;
            }
        }

        GameSaveDefinition def = definitions.get(effectiveKey);
        if (def == null) {
            def = new GameSaveDefinition(effectiveKey, name != null ? name : effectiveKey);
            def.setCustom(true);
            definitions.put(effectiveKey, def);
        } else if (name != null && !name.isBlank()) {
            def.setName(name);
        }
        if (savePath != null && !savePath.isBlank()) {
            def.setCustomSavePath(savePath);
        }
        if (exePath != null && !exePath.isBlank()) {
            def.setExeOrIconPath(exePath);
        }
        String resolvedInstall = installPath != null && !installPath.isBlank() ? installPath : parentDirOf(exePath);
        if (resolvedInstall != null && !resolvedInstall.isBlank()) {
            def.setInstallPath(resolvedInstall);
        }
        if (shortcut != null && !shortcut.isBlank()) {
            def.setShortcutName(shortcut);
        }
        if (settingsPath != null) {
            def.setSettingsPath(settingsPath.isBlank() ? null : settingsPath);
        } else if (def.getSettingsPath() == null && def.getCustomSavePath() != null && !def.getCustomSavePath().isBlank()) {
            def.setSettingsPath(def.getCustomSavePath());
        }
        def.setResolvedPath(SavePathDetector.resolveSavePath(def));

        persistCustomGamesFromDefinitions();
    }

    /** Drop blacklist + duplicates by name/save/install; rewrite custom_games.json. */
    public synchronized void dedupeAndPrune() {
        List<String> removeKeys = new ArrayList<>();
        for (GameSaveDefinition def : definitions.values()) {
            String save = def.getCustomSavePath() != null
                    ? def.getCustomSavePath()
                    : (def.getResolvedPath() != null ? def.getResolvedPath().toString() : null);
            if (DesktopGamesScanner.isBlacklistedGame(def.getName(), save, def.getInstallPath())) {
                removeKeys.add(def.getKey());
            }
        }
        for (String key : removeKeys) {
            definitions.remove(key);
        }

        Map<String, GameSaveDefinition> byName = new LinkedHashMap<>();
        Map<String, GameSaveDefinition> bySave = new LinkedHashMap<>();
        Map<String, GameSaveDefinition> byInstall = new LinkedHashMap<>();
        List<String> drop = new ArrayList<>();

        for (GameSaveDefinition def : new ArrayList<>(definitions.values())) {
            String n = normalizeNameKey(def.getName());
            String s = normalizePathKey(def.getCustomSavePath() != null
                    ? def.getCustomSavePath()
                    : (def.getResolvedPath() != null ? def.getResolvedPath().toString() : null));
            String i = normalizePathKey(def.getInstallPath());

            GameSaveDefinition conflict = null;
            if (n != null && byName.containsKey(n)) {
                conflict = byName.get(n);
            } else if (s != null && bySave.containsKey(s)) {
                conflict = bySave.get(s);
            } else if (i != null && byInstall.containsKey(i)) {
                conflict = byInstall.get(i);
            }
            if (conflict != null) {
                GameSaveDefinition keep = preferDefinition(conflict, def);
                GameSaveDefinition lose = keep == conflict ? def : conflict;
                drop.add(lose.getKey());
                mergePathsInto(keep, lose);
                if (keep != conflict) {
                    // Replace maps so keep wins under previous keys.
                    if (n != null) {
                        byName.put(n, keep);
                    }
                    if (s != null) {
                        bySave.put(s, keep);
                    }
                    if (i != null) {
                        byInstall.put(i, keep);
                    }
                }
                continue;
            }
            if (n != null) {
                byName.put(n, def);
            }
            if (s != null) {
                bySave.put(s, def);
            }
            if (i != null) {
                byInstall.put(i, def);
            }
        }
        for (String key : drop) {
            definitions.remove(key);
        }
        persistCustomGamesFromDefinitions();
    }

    private void persistCustomGamesFromDefinitions() {
        List<CustomGameStore.CustomGameEntry> filtered = new ArrayList<>();
        for (GameSaveDefinition def : definitions.values()) {
            boolean hasInstall = def.getInstallPath() != null && !def.getInstallPath().isBlank();
            if (!def.isCustom() && !hasInstall) {
                continue;
            }
            if (DesktopGamesScanner.isBlacklistedGame(def.getName(),
                    def.getCustomSavePath() != null ? def.getCustomSavePath()
                            : (def.getResolvedPath() != null ? def.getResolvedPath().toString() : null),
                    def.getInstallPath())) {
                continue;
            }
            CustomGameStore.CustomGameEntry entry = new CustomGameStore.CustomGameEntry();
            entry.key = def.getKey();
            entry.name = def.getName();
            entry.savePath = def.getCustomSavePath() != null
                    ? def.getCustomSavePath()
                    : (def.getResolvedPath() != null ? def.getResolvedPath().toString() : null);
            entry.exePath = def.getExeOrIconPath();
            entry.installPath = def.getInstallPath();
            entry.shortcutName = def.getShortcutName();
            entry.settingsPath = def.getSettingsPath();
            filtered.add(entry);
            if (hasInstall) {
                def.setCustom(true);
            }
        }
        store.saveCustomGames(filtered);
    }

    private static GameSaveDefinition preferDefinition(GameSaveDefinition a, GameSaveDefinition b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        int sa = scoreDefinition(a);
        int sb = scoreDefinition(b);
        if (sb > sa) {
            return b;
        }
        return a;
    }

    private static int scoreDefinition(GameSaveDefinition d) {
        int score = 0;
        if (d.getResolvedPath() != null || (d.getCustomSavePath() != null && !d.getCustomSavePath().isBlank())) {
            score += 4;
        }
        if (d.getInstallPath() != null && !d.getInstallPath().isBlank()) {
            score += 2;
        }
        if (d.getExeOrIconPath() != null && !d.getExeOrIconPath().isBlank()) {
            score += 1;
        }
        if (!d.isCustom()) {
            score += 1; // prefer catalog key stability when equal richness
        }
        return score;
    }

    private static void mergePathsInto(GameSaveDefinition keep, GameSaveDefinition from) {
        if ((keep.getCustomSavePath() == null || keep.getCustomSavePath().isBlank())
                && from.getCustomSavePath() != null && !from.getCustomSavePath().isBlank()) {
            keep.setCustomSavePath(from.getCustomSavePath());
        }
        if ((keep.getInstallPath() == null || keep.getInstallPath().isBlank())
                && from.getInstallPath() != null && !from.getInstallPath().isBlank()) {
            keep.setInstallPath(from.getInstallPath());
        }
        if ((keep.getExeOrIconPath() == null || keep.getExeOrIconPath().isBlank())
                && from.getExeOrIconPath() != null && !from.getExeOrIconPath().isBlank()) {
            keep.setExeOrIconPath(from.getExeOrIconPath());
        }
        if ((keep.getSettingsPath() == null || keep.getSettingsPath().isBlank())
                && from.getSettingsPath() != null && !from.getSettingsPath().isBlank()) {
            keep.setSettingsPath(from.getSettingsPath());
        }
        if ((keep.getShortcutName() == null || keep.getShortcutName().isBlank())
                && from.getShortcutName() != null && !from.getShortcutName().isBlank()) {
            keep.setShortcutName(from.getShortcutName());
        }
    }

    private static String normalizeNameKey(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return name.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static String normalizePathKey(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        try {
            return Path.of(path.trim()).toAbsolutePath().normalize().toString().toLowerCase(java.util.Locale.ROOT);
        } catch (Exception e) {
            return path.trim().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private Set<String> hiddenGameKeys() {
        CustomGameStore.SavesConfigData cfg = store.loadConfig();
        if (cfg.hiddenGameKeys == null || cfg.hiddenGameKeys.isEmpty()) {
            return Set.of();
        }
        return cfg.hiddenGameKeys;
    }

    private boolean isGameHidden(String key) {
        return key != null && !key.isBlank() && hiddenGameKeys().contains(key);
    }

    private void applyHiddenGameKeys() {
        for (String key : hiddenGameKeys()) {
            definitions.remove(key);
        }
    }

    public synchronized void removeGameFromList(String key) {
        final String effectiveKey = key != null ? key.trim() : "";
        if (effectiveKey.isEmpty()) {
            return;
        }
        boolean inCustom = store.loadCustomGames().stream()
                .anyMatch(e -> effectiveKey.equalsIgnoreCase(e.key));
        if (inCustom) {
            removeCustomGame(effectiveKey);
            return;
        }
        CustomGameStore.SavesConfigData cfg = store.loadConfig();
        if (cfg.hiddenGameKeys == null) {
            cfg.hiddenGameKeys = new HashSet<>();
        }
        cfg.hiddenGameKeys.add(effectiveKey);
        store.saveConfig(cfg);
        definitions.remove(effectiveKey);
    }

    public synchronized void removeCustomGame(String key) {
        final String effectiveKey = key != null ? key.trim() : "";
        definitions.remove(effectiveKey);
        List<CustomGameStore.CustomGameEntry> list = store.loadCustomGames();
        list.removeIf(e -> effectiveKey.equalsIgnoreCase(e.key));
        store.saveCustomGames(list);

        CustomGameStore.SavesConfigData cfg = store.loadConfig();
        if (cfg.pathOverrides != null) {
            cfg.pathOverrides.remove(effectiveKey);
            store.saveConfig(cfg);
        }
    }
}
