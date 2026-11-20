package com.bunker.survival.launcher.saves;

import com.bunker.survival.launcher.LauncherConfig;
import com.bunker.survival.launcher.TlsHttp;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Packs typical game settings files and syncs them via /api/settings/*. */
public final class GameSettingsSyncService {
    private static final Set<String> NAME_MATCH = Set.of(
            "config.json", "settings.json", "options.txt", "options.ini", "config.ini",
            "settings.ini", "user.cfg", "graphics.cfg", "gameuser.settings"
    );
    private static final Set<String> EXT_MATCH = Set.of(".ini", ".cfg");

    private final Path authFile;
    private final HttpClient http;
    private final Consumer<String> logger;
    private String apiBaseUrl = LauncherConfig.DEFAULT_API_BASE;

    public GameSettingsSyncService(Path appDir, Consumer<String> logger) {
        this.authFile = appDir.resolve("auth.json");
        this.logger = logger != null ? logger : s -> {};
        this.http = TlsHttp.clientBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    }

    public void setApiBaseUrl(String url) {
        if (url != null && !url.isBlank()) {
            String u = url.trim();
            while (u.endsWith("/")) {
                u = u.substring(0, u.length() - 1);
            }
            this.apiBaseUrl = u;
        }
    }

    public void uploadSettings(GameSaveDefinition def, Consumer<String> progress) throws Exception {
        String token = loadAuthToken();
        if (token == null) {
            throw new IllegalStateException("Требуется авторизация");
        }
        Path root = resolveSettingsRoot(def);
        if (root == null || !Files.exists(root)) {
            throw new IOException("Папка настроек не найдена");
        }
        List<Path> files = collectSettingsFiles(root);
        if (DesktopGamesScanner.isUnsafeSyncRoot(root) && files.size() > 80) {
            throw new IOException("Отказ: слишком много файлов — укажите папку настроек, не установку игры");
        }
        if (files.isEmpty()) {
            throw new IOException("Не найдены файлы настроек (*.ini, *.cfg, config.json…)");
        }
        if (progress != null) {
            progress.accept("Архивация настроек (" + files.size() + ")…");
        }
        Path zip = Files.createTempFile("platforma_settings_", ".zip");
        String filesJson;
        try {
            filesJson = packFiles(root, files, zip);
            if (progress != null) {
                progress.accept("Отправка настроек…");
            }
            upload(def, zip, filesJson, token);
            log("[Настройки] " + def.getName() + " загружены (" + Files.size(zip) + " байт)");
        } finally {
            try {
                Files.deleteIfExists(zip);
            } catch (Exception ignored) {
            }
        }
    }

    public void restoreSettings(GameSaveDefinition def, Consumer<String> progress) throws Exception {
        String token = loadAuthToken();
        if (token == null) {
            throw new IllegalStateException("Требуется авторизация");
        }
        Path root = resolveSettingsRoot(def);
        if (root == null) {
            throw new IOException("Папка настроек не настроена");
        }
        Files.createDirectories(root);
        if (progress != null) {
            progress.accept("Скачивание настроек…");
        }
        Path temp = Files.createTempFile("platforma_settings_dl_", ".zip");
        try {
            download(def.getKey(), token, temp);
            if (progress != null) {
                progress.accept("Распаковка…");
            }
            unpack(temp, root);
            log("[Настройки] " + def.getName() + " восстановлены в " + root);
        } finally {
            try {
                Files.deleteIfExists(temp);
            } catch (Exception ignored) {
            }
        }
    }

    public static Path resolveSettingsRoot(GameSaveDefinition def) {
        if (def == null) {
            return null;
        }
        if (def.getSettingsPath() != null && !def.getSettingsPath().isBlank()) {
            return Path.of(def.getSettingsPath());
        }
        return def.getResolvedPath();
    }

    static List<Path> collectSettingsFiles(Path root) throws IOException {
        List<Path> out = new ArrayList<>();
        if (Files.isRegularFile(root)) {
            if (isSettingsFile(root)) {
                out.add(root);
            }
            return out;
        }
        try (var walk = Files.walk(root, 3)) {
            walk.filter(Files::isRegularFile)
                    .filter(GameSettingsSyncService::isSettingsFile)
                    .forEach(out::add);
        }
        return out;
    }

    static boolean isSettingsFile(Path p) {
        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
        if (NAME_MATCH.contains(name)) {
            return true;
        }
        for (String ext : EXT_MATCH) {
            if (name.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    private static String packFiles(Path root, List<Path> files, Path zip) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        List<Map<String, Object>> meta = new ArrayList<>();
        try (FileOutputStream fos = new FileOutputStream(zip.toFile());
             BufferedOutputStream bos = new BufferedOutputStream(fos);
             ZipOutputStream zos = new ZipOutputStream(bos)) {
            zos.setLevel(Deflater.BEST_COMPRESSION);
            Path base = Files.isDirectory(root) ? root : root.getParent();
            for (Path p : files) {
                String rel = (base != null ? base.relativize(p) : p.getFileName()).toString().replace('\\', '/');
                ZipEntry ze = new ZipEntry(rel);
                ze.setTime(Files.getLastModifiedTime(p).toMillis());
                zos.putNextEntry(ze);
                byte[] buf = Files.readAllBytes(p);
                zos.write(buf);
                md.update(buf);
                zos.closeEntry();
                Map<String, Object> m = new HashMap<>();
                m.put("name", rel);
                m.put("size", buf.length);
                meta.add(m);
                com.bunker.survival.launcher.WorkThrottle.yieldBetweenUnits();
            }
        }
        return new com.google.gson.Gson().toJson(meta);
    }

    private void upload(GameSaveDefinition def, Path zip, String filesJson, String token) throws Exception {
        String boundary = MultipartBody.newBoundary();
        Map<String, String> fields = new HashMap<>();
        fields.put("game_key", def.getKey());
        fields.put("game_name", def.getName());
        Path root = resolveSettingsRoot(def);
        fields.put("local_path", root != null ? root.toString() : "");
        fields.put("files_json", filesJson);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiBaseUrl + "/api/settings/upload"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Authorization", "Bearer " + token)
                .timeout(Duration.ofMinutes(15))
                .POST(MultipartBody.ofFileUpload(
                        fields, "file", zip, def.getKey() + "_settings.zip", "application/zip", boundary))
                .build();
        HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            String err = resp.body();
            try {
                JsonObject o = JsonParser.parseString(err).getAsJsonObject();
                if (o.has("error")) {
                    err = o.get("error").getAsString();
                }
            } catch (Exception ignored) {
            }
            throw new IOException("Сервер: " + err);
        }
    }

    private void download(String gameKey, String token, Path dest) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiBaseUrl + "/api/settings/download/" + java.net.URLEncoder.encode(gameKey, StandardCharsets.UTF_8)))
                .header("Authorization", "Bearer " + token)
                .timeout(Duration.ofMinutes(15))
                .GET()
                .build();
        HttpResponse<InputStream> resp = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("Ошибка скачивания настроек HTTP " + resp.statusCode());
        }
        try (InputStream in = resp.body()) {
            Files.copy(in, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void unpack(Path zip, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (e.isDirectory()) {
                    continue;
                }
                Path out = targetDir.resolve(e.getName()).normalize();
                if (!out.startsWith(targetDir.normalize())) {
                    throw new IOException("Некорректный путь в архиве: " + e.getName());
                }
                Files.createDirectories(out.getParent());
                Files.copy(zis, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private String loadAuthToken() {
        try {
            if (!Files.isRegularFile(authFile)) {
                return null;
            }
            String json = Files.readString(authFile, StandardCharsets.UTF_8);
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            if (o.has("apiToken") && !o.get("apiToken").isJsonNull()) {
                String t = o.get("apiToken").getAsString();
                if (t != null && !t.isBlank()) {
                    return t;
                }
            }
            if (o.has("token") && !o.get("token").isJsonNull()) {
                String t = o.get("token").getAsString();
                return t != null && !t.isBlank() ? t : null;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void log(String msg) {
        logger.accept(msg);
    }
}
