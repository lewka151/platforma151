package com.bunker.survival.launcher;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class UpdateService {
    private UpdateService() {
    }

    /**
     * Single entry for Platforma update/manifest: version, build hash, installer/jar URLs.
     * Prefers bunker client manifest, then app client-release.
     */
    public static ClientRelease fetchPlatformaRelease(HttpClient http, String base) throws Exception {
        return fetchClientRelease(http, base);
    }

    public static ClientRelease fetchClientRelease(HttpClient http, String base) throws Exception {
        String[] paths = {
                "/api/bunker/client",
                "/api/app/client-release",
        };
        Exception last = null;
        for (String path : paths) {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(base + path))
                        .timeout(Duration.ofSeconds(12))
                        .header("Accept", "application/json")
                        .GET()
                        .build();
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                    return parseClientRelease(resp.body());
                }
                last = new IllegalStateException(path + " → HTTP " + resp.statusCode());
            } catch (Exception e) {
                last = e;
            }
        }
        throw last != null ? last : new IllegalStateException("Манифест клиента недоступен");
    }

    public static ClientRelease parseClientRelease(String body) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        JsonObject data = root;
        if (root.has("data") && root.get("data").isJsonObject()) {
            data = root.getAsJsonObject("data");
        }
        ClientRelease r = new ClientRelease();
        r.version = str(data, "version");
        r.url = str(data, "url");
        r.sha256 = str(data, "sha256");
        r.size = lng(data, "size");
        r.platformaJarUrl = str(data, "platforma_jar_url");
        r.platformaJarSize = lng(data, "platforma_jar_size");
        r.platformaJarSha256 = str(data, "platforma_jar_sha256");
        r.platformaInstallerUrl = str(data, "platforma_installer_url");
        r.platformaInstallerSize = lng(data, "platforma_installer_size");
        r.platformaInstallerSha256 = str(data, "platforma_installer_sha256");
        r.platformaBuildHash = str(data, "platforma_build_hash");
        r.platformaVersion = str(data, "platforma_version");
        if (r.platformaVersion == null) {
            // builds.platforma.version nested in /api/app/version style payloads
            if (data.has("builds") && data.get("builds").isJsonObject()) {
                JsonObject builds = data.getAsJsonObject("builds");
                if (builds.has("platforma") && builds.get("platforma").isJsonObject()) {
                    JsonObject p = builds.getAsJsonObject("platforma");
                    r.platformaVersion = str(p, "version");
                    if (r.platformaBuildHash == null) {
                        r.platformaBuildHash = str(p, "hash");
                    }
                }
            }
        }
        if ((r.version == null || r.version.isBlank()) && r.platformaBuildHash == null && r.url == null) {
            throw new IllegalStateException("Манифест без version");
        }
        return r;
    }

    private static String str(JsonObject o, String key) {
        if (!o.has(key) || o.get(key).isJsonNull()) {
            return null;
        }
        JsonElement el = o.get(key);
        if (el.isJsonPrimitive()) {
            String v = el.getAsString();
            return v.isBlank() ? null : v;
        }
        return null;
    }

    private static long lng(JsonObject o, String key) {
        if (!o.has(key) || o.get(key).isJsonNull()) {
            return 0;
        }
        try {
            return o.get(key).getAsLong();
        } catch (Exception e) {
            return 0;
        }
    }

    public static void downloadFile(HttpClient http, String url, Path dest, long expectedSize,
                             Consumer<String> log) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(10))
                .GET()
                .build();
        HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IllegalStateException("Скачивание HTTP " + resp.statusCode());
        }
        long total = expectedSize > 0 ? expectedSize : resp.headers().firstValueAsLong("Content-Length").orElse(-1);
        Files.createDirectories(dest.getParent());
        long read = 0;
        byte[] buf = new byte[64 * 1024];
        long nextLog = 0;
        try (InputStream in = resp.body(); OutputStream out = Files.newOutputStream(dest)) {
            int n;
            while ((n = in.read(buf)) >= 0) {
                out.write(buf, 0, n);
                read += n;
                if (total > 0) {
                    int pct = (int) Math.min(100, (read * 100) / total);
                    if (pct >= nextLog) {
                        log.accept("Загрузка… " + pct + "% (" + read + "/" + total + ")");
                        nextLog = pct + 10;
                    }
                } else if (read >= nextLog) {
                    log.accept("Загрузка… " + (read / 1024) + " КБ");
                    nextLog = read + 512 * 1024;
                }
            }
        }
        if (total > 0 && read != total) {
            log.accept("Предупреждение: размер " + read + " ≠ ожидалось " + total);
        }
        log.accept("Файл сохранён (" + read + " байт)");
    }

    public static String sha256Hex(Path file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) >= 0) {
                md.update(buf, 0, n);
            }
        }
        return HexFormat.of().formatHex(md.digest());
    }

    public static boolean isNewerVersion(String remote, String local) {
        if (remote == null || remote.isBlank()) {
            return false;
        }
        if (local == null || local.isBlank()) {
            return true;
        }
        return compareVersions(remote, local) > 0;
    }

    /** 0 = equal, &gt;0 remote newer, &lt;0 local newer. */
    public static int compareVersions(String a, String b) {
        int[] av = parseVersionParts(a != null ? a : "0");
        int[] bv = parseVersionParts(b != null ? b : "0");
        int n = Math.max(av.length, bv.length);
        for (int i = 0; i < n; i++) {
            int x = i < av.length ? av[i] : 0;
            int y = i < bv.length ? bv[i] : 0;
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        return 0;
    }

    public static boolean isSameVersion(String a, String b) {
        if (a == null || a.isBlank() || b == null || b.isBlank()) {
            return false;
        }
        return compareVersions(a, b) == 0;
    }

    private static int[] parseVersionParts(String v) {
        String cleaned = v.trim().toLowerCase(Locale.ROOT);
        if (cleaned.startsWith("v")) {
            cleaned = cleaned.substring(1);
        }
        String[] bits = cleaned.split("[^0-9]+");
        List<Integer> out = new ArrayList<>();
        for (String bit : bits) {
            if (bit.isEmpty()) {
                continue;
            }
            try {
                out.add(Integer.parseInt(bit));
            } catch (NumberFormatException ignored) {
            }
        }
        if (out.isEmpty()) {
            return new int[]{0};
        }
        int[] arr = new int[out.size()];
        for (int i = 0; i < out.size(); i++) {
            arr[i] = out.get(i);
        }
        return arr;
    }
}
