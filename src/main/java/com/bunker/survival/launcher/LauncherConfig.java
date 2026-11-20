package com.bunker.survival.launcher;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Shared with game GameConfig fields so neither side wipes launcher keys. */
public final class LauncherConfig {
    public static final String DEFAULT_API_BASE = "https://platforma151.chickenkiller.com";
    static final String LEGACY_LAN_API_BASE = "http://192.168.1.54";
    static final String LEGACY_PUBLIC_IP_HTTP = "http://81.5.107.44";
    static final String LEGACY_PUBLIC_IP_HTTPS = "https://81.5.107.44";
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public String apiBaseUrl = DEFAULT_API_BASE;
    public int windowWidth = 960;
    public int windowHeight = 580;
    /** Screen location; -1 means center on next launch. */
    public int windowX = -1;
    public int windowY = -1;
    public boolean fullscreen = false;
    public boolean autoUpdate = true;
    public boolean startWithWindows = false;
    public double autosaveSeconds = 20.0;
    public transient boolean startWithWindowsExplicit;
    public transient boolean autoUpdateExplicit;

    public static String trimSlash(String url) {
        if (url == null) {
            return DEFAULT_API_BASE;
        }
        String u = url.trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        if (u.isEmpty()) {
            return DEFAULT_API_BASE;
        }
        if (u.equalsIgnoreCase(LEGACY_LAN_API_BASE)
                || u.equalsIgnoreCase(LEGACY_PUBLIC_IP_HTTP)
                || u.equalsIgnoreCase(LEGACY_PUBLIC_IP_HTTPS)) {
            return DEFAULT_API_BASE;
        }
        return u;
    }

    public static LauncherConfig load(Path configPath) {
        LauncherConfig cfg = new LauncherConfig();
        try {
            if (Files.isRegularFile(configPath)) {
                String json = Files.readString(configPath, StandardCharsets.UTF_8);
                LauncherConfig parsed = GSON.fromJson(json, LauncherConfig.class);
                if (parsed != null) {
                    cfg = parsed;
                    cfg.startWithWindowsExplicit = json.contains("\"startWithWindows\"");
                    cfg.autoUpdateExplicit = json.contains("\"autoUpdate\"");
                    if (!cfg.autoUpdateExplicit) {
                        cfg.autoUpdate = true;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        String before = cfg.apiBaseUrl;
        cfg.apiBaseUrl = trimSlash(cfg.apiBaseUrl);
        if (before == null || !cfg.apiBaseUrl.equals(before)) {
            try {
                Files.writeString(configPath, GSON.toJson(cfg) + "\n", StandardCharsets.UTF_8);
            } catch (Exception ignored) {
            }
        }
        return cfg;
    }

    public void save(Path configPath) throws Exception {
        Files.writeString(configPath, GSON.toJson(this) + "\n", StandardCharsets.UTF_8);
    }
}
