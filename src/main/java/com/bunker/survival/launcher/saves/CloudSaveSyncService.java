package com.bunker.survival.launcher.saves;

import com.bunker.survival.launcher.LauncherConfig;
import com.bunker.survival.launcher.TlsHttp;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class CloudSaveSyncService {
    public static final long QUOTA_BYTES = 262_144_000L;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long DEBOUNCE_MS = 30_000L;

    public static class GameSyncState {
        public String gameKey;
        public String sha256;
        public long lastMtime;
        public long lastSize;
        public String lastSyncedAt;
        public String remoteFileName;
        public long remoteFileSize;
        public String lastError;
    }

    public static final class QuotaInfo {
        public long used;
        public long limit = QUOTA_BYTES;
        public long remaining = QUOTA_BYTES;
        public String lastSyncedAt;

        public static QuotaInfo unknown() {
            QuotaInfo q = new QuotaInfo();
            q.used = -1;
            q.remaining = -1;
            return q;
        }

        public boolean known() {
            return used >= 0 && limit > 0;
        }
    }

    public static final class QuotaExceededException extends IOException {
        public final long used;
        public final long limit;
        public final long remaining;

        public QuotaExceededException(String message, long used, long limit, long remaining) {
            super(message);
            this.used = used;
            this.limit = limit;
            this.remaining = remaining;
        }
    }

    private final Path appDir;
    private final Path stateFile;
    private final Path authFile;
    private final GameSaveRegistry registry;
    private final Consumer<String> logger;
    private final Consumer<String> balloonNotifier;
    private final HttpClient http;
    private final Map<String, GameSyncState> stateMap = new ConcurrentHashMap<>();
    private final Map<WatchKey, String> watchKeys = new ConcurrentHashMap<>();
    private final Set<String> dirtyKeys = ConcurrentHashMap.newKeySet();
    private ScheduledExecutorService scheduler;
    private WatchService watchService;
    private volatile boolean running = false;
    private volatile long lastDirtyAt;
    private volatile long lastSilentSyncAt;
    private volatile String lastSilentCycleAt;
    private volatile Runnable onQuotaChanged;
    private volatile Set<String> remoteSaveKeys = Set.of();
    private String apiBaseUrl = LauncherConfig.DEFAULT_API_BASE;

    public CloudSaveSyncService(Path appDir, GameSaveRegistry registry, Consumer<String> logger, Consumer<String> balloonNotifier) {
        this.appDir = appDir;
        this.stateFile = appDir.resolve("cloud_saves_state.json");
        this.authFile = appDir.resolve("auth.json");
        this.registry = registry;
        this.logger = logger != null ? logger : s -> {};
        this.balloonNotifier = balloonNotifier != null ? balloonNotifier : s -> {};
        this.http = TlsHttp.clientBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
        loadState();
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

    public void setOnQuotaChanged(Runnable onQuotaChanged) {
        this.onQuotaChanged = onQuotaChanged;
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        SyncLog.init(appDir, this.logger);
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "CloudSave-Sync-Daemon");
            t.setDaemon(true);
            return t;
        });
        try {
            watchService = FileSystems.getDefault().newWatchService();
        } catch (Exception e) {
            SyncLog.error("WatchService недоступен", e);
            log("WatchService недоступен: " + e.getMessage());
        }
        refreshWatches();
        scheduler.scheduleWithFixedDelay(this::drainWatches, 2, 2, TimeUnit.SECONDS);
        scheduler.scheduleWithFixedDelay(this::flushDirtyDebounced, 5, 5, TimeUnit.SECONDS);
        // First full silent sync soon after start, then poll every 30s (gated by syncIntervalMinutes).
        scheduler.schedule(this::runSyncCycleSilently, 2, TimeUnit.SECONDS);
        scheduler.scheduleWithFixedDelay(this::runSyncCycleSilently, 30, 30, TimeUnit.SECONDS);
        SyncLog.info("Фоновый sync запущен (старт через 2с, далее по интервалу настроек).");
        log("Фоновый сервис облачных сохранений запущен (слежение за папками + обход по интервалу из настроек).");
    }

    public synchronized void stop() {
        running = false;
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        closeWatches();
        if (watchService != null) {
            try {
                watchService.close();
            } catch (Exception ignored) {
            }
            watchService = null;
        }
    }

    public synchronized void refreshWatches() {
        if (watchService == null) {
            return;
        }
        closeWatches();
        for (GameSaveDefinition def : registry.getAllGames()) {
            Path resolved = def.getResolvedPath();
            if (resolved == null) {
                continue;
            }
            Path dir = Files.isDirectory(resolved) ? resolved : resolved.getParent();
            if (dir == null || !Files.isDirectory(dir)) {
                continue;
            }
            try {
                WatchKey key = dir.register(
                        watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_DELETE,
                        StandardWatchEventKinds.ENTRY_MODIFY);
                watchKeys.put(key, def.getKey());
            } catch (Exception ignored) {
            }
        }
    }

    public GameSyncState getState(String gameKey) {
        return stateMap.get(gameKey);
    }

    public boolean isPending(String gameKey) {
        return gameKey != null && dirtyKeys.contains(gameKey);
    }

    /** True when the authenticated user has a cloud save for this game on the server. */
    public boolean hasRemoteSave(String gameKey) {
        return gameKey != null && !gameKey.isBlank() && remoteSaveKeys.contains(gameKey);
    }

    /** Refreshes the cached set of game keys present in the user's cloud saves. */
    public void refreshRemoteSaveKeys() {
        String token = loadAuthToken();
        if (token == null) {
            remoteSaveKeys = Set.of();
            return;
        }
        fetchRemoteStatus(token);
    }

    public Map<String, GameSyncState> getAllStates() {
        return new HashMap<>(stateMap);
    }

    public String latestLocalSyncAt() {
        String latest = lastSilentCycleAt;
        for (GameSyncState st : stateMap.values()) {
            if (st != null && st.lastSyncedAt != null && !st.lastSyncedAt.isBlank()) {
                if (latest == null || st.lastSyncedAt.compareTo(latest) > 0) {
                    latest = st.lastSyncedAt;
                }
            }
        }
        return latest;
    }

    public String latestSilentCycleAt() {
        return lastSilentCycleAt;
    }

    private synchronized void loadState() {
        if (!Files.isRegularFile(stateFile)) {
            return;
        }
        try {
            String json = Files.readString(stateFile, StandardCharsets.UTF_8);
            Type mapType = new TypeToken<Map<String, GameSyncState>>() {}.getType();
            Map<String, GameSyncState> loaded = GSON.fromJson(json, mapType);
            if (loaded != null) {
                stateMap.putAll(loaded);
            }
        } catch (Exception ignored) {
        }
    }

    private synchronized void saveState() {
        try {
            Files.writeString(stateFile, GSON.toJson(stateMap), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    public String loadAuthToken() {
        if (!Files.isRegularFile(authFile)) {
            return null;
        }
        try {
            String json = Files.readString(authFile, StandardCharsets.UTF_8);
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            if (obj.has("apiToken") && !obj.get("apiToken").isJsonNull()) {
                String token = obj.get("apiToken").getAsString();
                return token.isBlank() ? null : token;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void drainWatches() {
        if (watchService == null) {
            return;
        }
        WatchKey key;
        while ((key = watchService.poll()) != null) {
            String gameKey = watchKeys.get(key);
            for (WatchEvent<?> event : key.pollEvents()) {
                if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                    dirtyKeys.addAll(stateMap.keySet());
                    for (GameSaveDefinition def : registry.getAllGames()) {
                        dirtyKeys.add(def.getKey());
                    }
                    break;
                }
                if (gameKey != null) {
                    dirtyKeys.add(gameKey);
                }
            }
            if (!key.reset()) {
                watchKeys.remove(key);
            }
            lastDirtyAt = System.currentTimeMillis();
        }
    }

    private void flushDirtyDebounced() {
        if (dirtyKeys.isEmpty()) {
            return;
        }
        if (!registry.getStore().loadConfig().autoSyncEnabled) {
            dirtyKeys.clear();
            return;
        }
        if (System.currentTimeMillis() - lastDirtyAt < DEBOUNCE_MS) {
            return;
        }
        Set<String> keys = new HashSet<>(dirtyKeys);
        dirtyKeys.clear();
        String token = loadAuthToken();
        if (token == null) {
            return;
        }
        registry.reload();
        for (GameSaveDefinition def : registry.getAllGames()) {
            if (!keys.contains(def.getKey())) {
                continue;
            }
            try {
                syncGameIfChanged(def, token, false, null);
            } catch (QuotaExceededException q) {
                SyncLog.warn("Квота: " + q.getMessage());
                log("Квота: " + q.getMessage());
                markError(def.getKey(), q.getMessage());
            } catch (Exception e) {
                SyncLog.error(def.getKey(), "Ошибка синхронизации " + def.getName(), e);
                log("Ошибка синхронизации " + def.getName() + ": " + e.getMessage());
                markError(def.getKey(), e.getMessage());
            }
        }
        notifyQuota();
    }

    private void markError(String gameKey, String message) {
        if (gameKey == null) {
            return;
        }
        GameSyncState st = stateMap.computeIfAbsent(gameKey, k -> new GameSyncState());
        st.gameKey = gameKey;
        st.lastError = message != null ? message : "Ошибка";
        saveState();
    }

    private void runSyncCycleSilently() {
        if (!registry.getStore().loadConfig().autoSyncEnabled) {
            return;
        }
        int minutes = Math.max(1, registry.getStore().loadConfig().syncIntervalMinutes);
        long intervalMs = minutes * 60_000L;
        if (lastSilentSyncAt > 0 && System.currentTimeMillis() - lastSilentSyncAt < intervalMs) {
            return;
        }
        String token = loadAuthToken();
        if (token == null) {
            return;
        }
        lastSilentSyncAt = System.currentTimeMillis();
        lastSilentCycleAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        SyncLog.info("Тихий цикл sync начат");
        refreshWatches();
        registry.reload();
        List<GameSaveDefinition> games = registry.getAllGames();
        for (GameSaveDefinition def : games) {
            if (def.getResolvedPath() == null || !Files.exists(def.getResolvedPath())) {
                continue;
            }
            try {
                syncGameIfChanged(def, token, false, null);
            } catch (QuotaExceededException q) {
                SyncLog.warn("Квота [" + def.getKey() + "]: " + q.getMessage());
                log("Квота: " + q.getMessage());
            } catch (Exception e) {
                SyncLog.error(def.getKey(), "Ошибка синхронизации " + def.getName(), e);
                log("Ошибка синхронизации " + def.getName() + ": " + e.getMessage());
            }
        }
        try {
            postSyncHeartbeat(token);
        } catch (Exception e) {
            SyncLog.error("Heartbeat failed", e);
        }
        notifyQuota();
    }

    public boolean syncGameNow(GameSaveDefinition def, Consumer<String> progress) throws Exception {
        String token = loadAuthToken();
        if (token == null) {
            throw new IllegalStateException("Требуется авторизация в Platforma151 для сохранения в облако");
        }
        registry.reload();
        boolean updated = syncGameIfChanged(def, token, true, progress);
        notifyQuota();
        return updated;
    }

    public int syncAllNow(Consumer<String> progress) throws Exception {
        String token = loadAuthToken();
        if (token == null) {
            throw new IllegalStateException("Требуется авторизация в Platforma151 для сохранения в облако");
        }
        registry.reload();
        int synced = 0;
        List<String> errors = new java.util.ArrayList<>();
        List<GameSaveDefinition> games = new ArrayList<>();
        for (GameSaveDefinition def : registry.getAllGames()) {
            if (def.getResolvedPath() != null && Files.exists(def.getResolvedPath())) {
                games.add(def);
            }
        }
        int total = games.size();
        int index = 0;
        for (GameSaveDefinition def : games) {
            final int currentIndex = index;
            int pct = total <= 0 ? 100 : (currentIndex * 100) / total;
            if (progress != null) {
                progress.accept(pct + "\tПроверка: " + def.getName() + "...");
            }
            try {
                boolean updated = syncGameIfChanged(def, token, true, msg -> {
                    if (progress != null) {
                        int mid = total <= 0 ? 100 : Math.min(99, (currentIndex * 100) / total + 5);
                        progress.accept(mid + "\t" + msg);
                    }
                });
                if (updated) {
                    synced++;
                }
            } catch (QuotaExceededException q) {
                errors.add(def.getName() + ": " + q.getMessage());
                if (progress != null) {
                    progress.accept(pct + "\t" + q.getMessage());
                }
                break;
            } catch (Exception e) {
                errors.add(def.getName() + ": " + e.getMessage());
                if (progress != null) {
                    progress.accept(pct + "\tОшибка " + def.getName() + ": " + e.getMessage());
                }
            }
            index++;
            if (progress != null) {
                int donePct = total <= 0 ? 100 : (index * 100) / total;
                progress.accept(donePct + "\tГотово: " + def.getName());
            }
            com.bunker.survival.launcher.WorkThrottle.yieldBetweenBatches();
        }
        notifyQuota();
        if (!errors.isEmpty() && synced == 0) {
            throw new IOException(String.join("\n", errors));
        }
        if (!errors.isEmpty() && progress != null) {
            progress.accept("100\tГотово с ошибками: " + errors.size());
        } else if (progress != null) {
            progress.accept("100\tСинхронизация завершена");
        }
        return synced;
    }

    private boolean syncGameIfChanged(GameSaveDefinition def, String token, boolean forceUploadIfSame, Consumer<String> progress) throws Exception {
        Path savePath = def.getResolvedPath();
        if (savePath == null || !Files.exists(savePath)) {
            if (progress != null) {
                progress.accept("Папка сохранения не найдена для " + def.getName());
            }
            return false;
        }
        if (DesktopGamesScanner.isUnsafeSyncRoot(savePath)) {
            if (progress != null) {
                progress.accept("Пропуск (не папка сейва): " + def.getName());
            }
            log("[Сейвы] Отказ синка — путь похож на установку/систему: " + savePath);
            return false;
        }

        SavePacker.FolderFingerprint fp = SavePacker.fingerprint(def);
        GameSyncState currentState = stateMap.get(def.getKey());
        Map<String, RemoteSaveStatus> remote = fetchRemoteStatus(token);
        RemoteSaveStatus remoteItem = remote.get(def.getKey());

        boolean fingerprintSame = currentState != null
                && fp.latestMtime == currentState.lastMtime
                && fp.totalBytes == currentState.lastSize;
        boolean hashSame = currentState != null && currentState.sha256 != null && remoteItem != null
                && currentState.sha256.equalsIgnoreCase(remoteItem.hash);

        if (!forceUploadIfSame && fingerprintSame && hashSame) {
            return false;
        }
        if (!forceUploadIfSame && fingerprintSame && currentState != null && currentState.sha256 != null && remoteItem == null) {
            // local thinks it is synced but server has nothing — pack and upload
        } else if (!forceUploadIfSame && fingerprintSame && currentState != null && remoteItem != null && hashSame) {
            return false;
        } else if (!forceUploadIfSame && fingerprintSame && remoteItem == null && currentState != null && currentState.remoteFileSize > 0) {
            // keep going to re-upload
        } else if (!forceUploadIfSame && fingerprintSame && currentState != null && remoteItem != null) {
            if (currentState.sha256 != null && currentState.sha256.equalsIgnoreCase(remoteItem.hash)) {
                return false;
            }
        }

        SavePacker.SavePackResult packed = null;
        try {
            if (progress != null) {
                progress.accept("Архивация сейвов " + def.getName() + "...");
            }
            packed = SavePacker.pack(def);

            if (remoteItem != null && packed.getSha256().equalsIgnoreCase(remoteItem.hash) && !forceUploadIfSame) {
                rememberState(def, packed, remoteItem.size);
                return false;
            }

            long zipSize = Files.size(packed.getZipFile());
            QuotaInfo quota = fetchQuotaInfo(token);
            long oldSize = remoteItem != null ? remoteItem.size : 0;
            if (quota.known() && quota.used - oldSize + zipSize > quota.limit) {
                String msg = "Квота " + formatSize(quota.limit) + ": занято " + formatSize(quota.used)
                        + ", архив " + formatSize(zipSize)
                        + " не помещается (лимит " + formatSize(quota.limit) + ")";
                if (progress != null) {
                    progress.accept(msg);
                }
                throw new QuotaExceededException(msg, quota.used, quota.limit, quota.remaining);
            }

            if (progress != null) {
                progress.accept("Отправка на сервер: " + def.getName() + " (" + formatSize(zipSize) + ")...");
            }

            uploadToServer(def, packed, token);
            rememberState(def, packed, zipSize);

            String successMsg = "Облачные сейвы: " + def.getName() + " успешно синхронизирован";
            log("[Облако] " + successMsg + " (" + formatSize(packed.getTotalBytes()) + ")");

            if (progress != null) {
                progress.accept("Успешно сохранено на сервере!");
            }
            return true;
        } finally {
            if (packed != null) {
                packed.cleanup();
            }
        }
    }

    private void rememberState(GameSaveDefinition def, SavePacker.SavePackResult packed, long zipSize) {
        GameSyncState newState = new GameSyncState();
        newState.gameKey = def.getKey();
        newState.sha256 = packed.getSha256();
        newState.lastMtime = packed.getLatestMtime();
        newState.lastSize = packed.getTotalBytes();
        newState.lastSyncedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        newState.remoteFileName = def.getKey() + "_save.zip";
        newState.remoteFileSize = zipSize;
        newState.lastError = null;
        stateMap.put(def.getKey(), newState);
        saveState();
    }

    private void uploadToServer(GameSaveDefinition def, SavePacker.SavePackResult packed, String token) throws Exception {
        String uploadUrl = apiBaseUrl + "/api/saves/upload";
        String boundary = MultipartBody.newBoundary();
        Map<String, String> fields = new HashMap<>();
        fields.put("game_key", def.getKey());
        fields.put("game_name", def.getName());
        fields.put("local_path", def.getResolvedPath() != null ? def.getResolvedPath().toString() : "");
        fields.put("files_json", packed.getFilesJson());
        fields.put("computer_name", localComputerName());
        fields.put("os_user", System.getProperty("user.name", ""));
        JsonObject meta = new JsonObject();
        meta.addProperty("computer_name", localComputerName());
        meta.addProperty("os_user", System.getProperty("user.name", ""));
        meta.addProperty("os", System.getProperty("os.name", ""));
        if (def.getInstallPath() != null && !def.getInstallPath().isBlank()) {
            meta.addProperty("install_path", def.getInstallPath());
        }
        if (def.getSettingsPath() != null && !def.getSettingsPath().isBlank()) {
            meta.addProperty("settings_path", def.getSettingsPath());
        }
        fields.put("metadata", meta.toString());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uploadUrl))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Authorization", "Bearer " + token)
                .timeout(Duration.ofMinutes(30))
                .POST(MultipartBody.ofFileUpload(
                        fields, "file", packed.getZipFile(), def.getKey() + "_save.zip", "application/zip", boundary))
                .build();

        HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonObject body = parseBody(resp.body());
        if (resp.statusCode() == 413) {
            throw quotaFromBody(body);
        }
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("Сервер вернул ошибку при загрузке: " + errorMessage(body, resp.statusCode()));
        }
    }

    public QuotaInfo fetchQuotaInfo() {
        try {
            String token = loadAuthToken();
            if (token == null) {
                return QuotaInfo.unknown();
            }
            return fetchQuotaInfo(token);
        } catch (Exception e) {
            return QuotaInfo.unknown();
        }
    }

    public QuotaInfo fetchQuotaInfo(String token) throws Exception {
        JsonObject data = getJson(token, "/api/saves/list");
        QuotaInfo q = new QuotaInfo();
        q.used = longVal(data, "quota_used", data.has("total_size_bytes") ? data.get("total_size_bytes").getAsLong() : 0);
        q.limit = longVal(data, "quota_limit", QUOTA_BYTES);
        q.remaining = longVal(data, "quota_remaining", Math.max(0, q.limit - q.used));
        if (data.has("last_synced_at") && !data.get("last_synced_at").isJsonNull()) {
            q.lastSyncedAt = data.get("last_synced_at").getAsString();
        }
        String local = latestLocalSyncAt();
        if (q.lastSyncedAt == null || q.lastSyncedAt.isBlank()) {
            q.lastSyncedAt = local;
        } else if (local != null && !local.isBlank() && local.compareTo(q.lastSyncedAt) > 0) {
            q.lastSyncedAt = local;
        }
        return q;
    }

    private void postSyncHeartbeat(String token) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBaseUrl + "/api/saves/heartbeat"))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString("{}", StandardCharsets.UTF_8))
                    .build();
            http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            SyncLog.error("Heartbeat sync", e);
            log("Heartbeat sync: " + e.getMessage());
        }
    }

    private Map<String, RemoteSaveStatus> fetchRemoteStatus(String token) {
        Map<String, RemoteSaveStatus> out = new HashMap<>();
        try {
            JsonObject data = getJson(token, "/api/saves/status");
            JsonObject games = data;
            if (data.has("games") && data.get("games").isJsonObject()) {
                games = data.getAsJsonObject("games");
            }
            for (Map.Entry<String, JsonElement> e : games.entrySet()) {
                if (!e.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject o = e.getValue().getAsJsonObject();
                RemoteSaveStatus st = new RemoteSaveStatus();
                st.hash = o.has("hash") ? o.get("hash").getAsString() : (o.has("file_hash") ? o.get("file_hash").getAsString() : "");
                st.size = longVal(o, "size", longVal(o, "file_size", 0));
                out.put(e.getKey(), st);
            }
            remoteSaveKeys = Set.copyOf(out.keySet());
        } catch (Exception ignored) {
        }
        return out;
    }

    private JsonObject getJson(String token, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiBaseUrl + path))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();
        HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonObject body = parseBody(resp.body());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException(errorMessage(body, resp.statusCode()));
        }
        if (body.has("data") && body.get("data").isJsonObject()) {
            return body.getAsJsonObject("data");
        }
        return body;
    }

    /**
     * Downloads a ZIP of all cloud saves to {@code destZip} (site "download all").
     */
    public Path downloadAll(Path destZip, Consumer<String> progress) throws Exception {
        String token = loadAuthToken();
        if (token == null) {
            throw new IllegalStateException("Требуется авторизация для скачивания сохранений");
        }
        if (destZip == null) {
            throw new IllegalArgumentException("Не указан путь для сохранения ZIP");
        }
        if (progress != null) {
            progress.accept("15\tСкачивание всех сейвов…");
        }
        String dlUrl = apiBaseUrl + "/api/saves/download-all";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(dlUrl))
                .header("Authorization", "Bearer " + token)
                .timeout(Duration.ofMinutes(30))
                .GET()
                .build();
        HttpResponse<InputStream> resp = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("Ошибка при скачивании архива (HTTP " + resp.statusCode() + ")");
        }
        Path parent = destZip.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (InputStream is = resp.body()) {
            Files.copy(is, destZip, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        if (progress != null) {
            progress.accept("100\tАрхив сохранён: " + destZip.getFileName());
        }
        log("[Облако] Все сейвы сохранены в " + destZip);
        return destZip;
    }

    public void downloadAndRestore(GameSaveDefinition def, Consumer<String> progress) throws Exception {
        String token = loadAuthToken();
        if (token == null) {
            throw new IllegalStateException("Требуется авторизация для скачивания сохранения");
        }
        Path targetPath = def.getResolvedPath();
        if (targetPath == null) {
            Path installDir = SavePathDetector.installDirFromExe(def.getExeOrIconPath());
            if (def.getCustomSavePath() != null && !def.getCustomSavePath().isBlank()) {
                targetPath = SavePathDetector.resolveExisting(def.getCustomSavePath(), installDir);
            } else if (!def.getDefaultCandidates().isEmpty()) {
                targetPath = SavePathDetector.resolveExisting(def.getDefaultCandidates().get(0), installDir);
            }
            if (targetPath == null) {
                throw new IllegalStateException("Не указан целевой путь для сохранения игры");
            }
        }

        if (progress != null) {
            progress.accept("Скачивание архива с сервера...");
        }

        String dlUrl = apiBaseUrl + "/api/saves/download/" + def.getKey();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(dlUrl))
                .header("Authorization", "Bearer " + token)
                .timeout(Duration.ofMinutes(30))
                .GET()
                .build();

        HttpResponse<InputStream> resp = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("Ошибка при скачивании сохранения (HTTP " + resp.statusCode() + ")");
        }

        Path tempDl = Files.createTempFile("platforma_dl_" + def.getKey() + "_", ".zip");
        try {
            try (InputStream is = resp.body()) {
                Files.copy(is, tempDl, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            if (progress != null) {
                progress.accept("Распаковка и создание бэкапа существующего сейва...");
            }

            Path extractDir = Files.isDirectory(targetPath) || targetPath.toString().endsWith("\\") || targetPath.toString().endsWith("/")
                    ? targetPath
                    : (targetPath.getParent() != null ? targetPath.getParent() : targetPath);

            SavePacker.unpack(tempDl, extractDir, true);

            GameSyncState state = stateMap.computeIfAbsent(def.getKey(), k -> new GameSyncState());
            state.gameKey = def.getKey();
            state.lastSyncedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            saveState();

            if (progress != null) {
                progress.accept("Сохранение успешно восстановлено!");
            }
            log("[Облако] Сохранение " + def.getName() + " восстановлено из облака в " + extractDir);
        } finally {
            try {
                Files.deleteIfExists(tempDl);
            } catch (Exception ignored) {
            }
        }
    }

    private void closeWatches() {
        for (WatchKey key : watchKeys.keySet()) {
            try {
                key.cancel();
            } catch (Exception ignored) {
            }
        }
        watchKeys.clear();
    }

    private void notifyQuota() {
        Runnable cb = onQuotaChanged;
        if (cb != null) {
            cb.run();
        }
    }

    private static JsonObject parseBody(String raw) {
        try {
            return JsonParser.parseString(raw).getAsJsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    private static String errorMessage(JsonObject body, int status) {
        if (body.has("error") && !body.get("error").isJsonNull()) {
            return body.get("error").getAsString();
        }
        return "HTTP " + status;
    }

    private static QuotaExceededException quotaFromBody(JsonObject body) {
        long used = longVal(body, "quota_used", 0);
        long limit = longVal(body, "quota_limit", QUOTA_BYTES);
        long remaining = longVal(body, "quota_remaining", Math.max(0, limit - used));
        String msg = errorMessage(body, 413);
        return new QuotaExceededException(msg, used, limit, remaining);
    }

    private static long longVal(JsonObject o, String key, long fallback) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return o.get(key).getAsLong();
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String localComputerName() {
        try {
            String env = System.getenv("COMPUTERNAME");
            if (env != null && !env.isBlank()) {
                return env.trim();
            }
            env = System.getenv("HOSTNAME");
            if (env != null && !env.isBlank()) {
                return env.trim();
            }
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return System.getProperty("user.name", "unknown");
        }
    }

    public static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1048576) {
            return (Math.round(bytes / 1024.0 * 10.0) / 10.0) + " KB";
        }
        return (Math.round(bytes / 1048576.0 * 10.0) / 10.0) + " MB";
    }

    private void log(String msg) {
        logger.accept(msg);
    }

    private static final class RemoteSaveStatus {
        String hash = "";
        long size;
    }
}
