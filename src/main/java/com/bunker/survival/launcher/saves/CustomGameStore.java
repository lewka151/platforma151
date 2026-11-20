package com.bunker.survival.launcher.saves;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CustomGameStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static class CustomGameEntry {
        public String key;
        public String name;
        public String savePath;
        public String exePath;
        public String installPath;
        public String shortcutName;
        /** Optional folder/file for game settings backup (ini/cfg). */
        public String settingsPath;
    }

    public static class SavesConfigData {
        public boolean autoSyncEnabled = true;
        public int syncIntervalMinutes = 5;
        public int splitDividerLocation = 360;
        public Map<String, String> pathOverrides = new HashMap<>();
        /** Optional folder with .lnk/.url game shortcuts (default Desktop\\Games). */
        public String shortcutsDir;
        public boolean settingsArchiveEnabled = false;
        public String settingsArchiveDir;
        public String settingsArchiveLastHash;
        public long settingsArchiveLastDay;
        /** Game keys hidden from the launcher list (catalog entries removed by user). */
        public Set<String> hiddenGameKeys = new HashSet<>();
    }

    private final Path configDir;
    private final Path customGamesFile;
    private final Path savesConfigFile;

    public CustomGameStore(Path configDir) {
        this.configDir = configDir;
        this.customGamesFile = configDir.resolve("custom_games.json");
        this.savesConfigFile = configDir.resolve("saves_config.json");
        try {
            Files.createDirectories(configDir);
        } catch (Exception ignored) {
        }
    }

    public Path getConfigDir() {
        return configDir;
    }

    public List<CustomGameEntry> loadCustomGames() {
        if (!Files.isRegularFile(customGamesFile)) {
            return new ArrayList<>();
        }
        try {
            String json = Files.readString(customGamesFile, StandardCharsets.UTF_8);
            Type listType = new TypeToken<List<CustomGameEntry>>() {}.getType();
            List<CustomGameEntry> list = GSON.fromJson(json, listType);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public void saveCustomGames(List<CustomGameEntry> entries) {
        try {
            Files.writeString(customGamesFile, GSON.toJson(entries), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    public SavesConfigData loadConfig() {
        if (!Files.isRegularFile(savesConfigFile)) {
            return new SavesConfigData();
        }
        try {
            String json = Files.readString(savesConfigFile, StandardCharsets.UTF_8);
            SavesConfigData cfg = GSON.fromJson(json, SavesConfigData.class);
            return cfg != null ? cfg : new SavesConfigData();
        } catch (Exception e) {
            return new SavesConfigData();
        }
    }

    public void saveConfig(SavesConfigData data) {
        try {
            Files.writeString(savesConfigFile, GSON.toJson(data), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }
}
