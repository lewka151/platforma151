package com.bunker.survival.launcher.saves;

import com.bunker.survival.launcher.TlsHttp;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Downloads games catalog from site API into AppData cache for GameSaveRegistry.
 */
public final class CatalogSyncService {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path cacheFile;
    private final Path versionFile;
    private final GameSaveRegistry registry;
    private final Consumer<String> log;
    private String apiBaseUrl;
    private String localVersion = "0";

    public CatalogSyncService(Path appDir, GameSaveRegistry registry, Consumer<String> log) {
        this.cacheFile = appDir.resolve("games-catalog.cache.json");
        this.versionFile = appDir.resolve("games-catalog.version");
        this.registry = registry;
        this.log = log != null ? log : s -> {};
        this.localVersion = readLocalVersion();
    }

    public void setApiBaseUrl(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        String u = url.trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        this.apiBaseUrl = u;
    }

    public String getLocalVersion() {
        return localVersion;
    }

    public Path getCacheFile() {
        return cacheFile;
    }

    private String readLocalVersion() {
        try {
            if (Files.isRegularFile(versionFile)) {
                String v = Files.readString(versionFile, StandardCharsets.UTF_8).trim();
                if (!v.isEmpty()) {
                    return v;
                }
            }
        } catch (Exception ignored) {
        }
        return "0";
    }

    /** Blocking fetch; returns true if catalog was updated. */
    public boolean syncNow() throws Exception {
        if (apiBaseUrl == null || apiBaseUrl.isBlank()) {
            throw new IllegalStateException("API URL не задан");
        }
        HttpClient http = TlsHttp.clientBuilder().connectTimeout(Duration.ofSeconds(8)).build();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(apiBaseUrl + "/api/saves/supported-games"))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + resp.statusCode());
        }
        JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
        JsonObject data = root.has("data") && root.get("data").isJsonObject()
                ? root.getAsJsonObject("data")
                : root;
        String remoteVersion = data.has("catalog_version") && !data.get("catalog_version").isJsonNull()
                ? data.get("catalog_version").getAsString()
                : "0";
        JsonArray items = data.has("items") && data.get("items").isJsonArray()
                ? data.getAsJsonArray("items")
                : new JsonArray();
        if (items.size() == 0) {
            log.accept("Каталог с сайта пуст — оставляем локальный");
            return false;
        }
        if (remoteVersion.equals(localVersion) && Files.isRegularFile(cacheFile)) {
            log.accept("Каталог игр актуален (" + remoteVersion + ", " + items.size() + ")");
            return false;
        }

        List<GameSaveRegistry.CatalogEntry> entries = new ArrayList<>();
        for (JsonElement el : items) {
            if (!el.isJsonObject()) {
                continue;
            }
            GameSaveRegistry.CatalogEntry entry = GSON.fromJson(el, GameSaveRegistry.CatalogEntry.class);
            if (entry != null && entry.key != null && !entry.key.isBlank()) {
                entries.add(entry);
            }
        }
        if (entries.isEmpty()) {
            return false;
        }

        Files.writeString(cacheFile, GSON.toJson(entries) + "\n", StandardCharsets.UTF_8);
        Files.writeString(versionFile, remoteVersion + "\n", StandardCharsets.UTF_8);
        localVersion = remoteVersion;
        registry.replaceCatalogFromEntries(entries);
        log.accept("Каталог игр обновлён: " + remoteVersion + " (" + entries.size() + ")");
        return true;
    }

    public void syncAsync() {
        new Thread(() -> {
            try {
                syncNow();
            } catch (Exception e) {
                log.accept("Каталог игр: " + e.getMessage());
            }
        }, "catalog-sync").start();
    }
}
