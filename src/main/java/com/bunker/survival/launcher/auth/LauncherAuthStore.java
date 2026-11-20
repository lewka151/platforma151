package com.bunker.survival.launcher.auth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LauncherAuthStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static class AuthData {
        public String apiToken;
        public Integer userId;
        public String userName;
    }

    private final Path authFile;
    private AuthData data = new AuthData();

    public LauncherAuthStore(Path appDir) {
        this.authFile = appDir.resolve("auth.json");
        load();
    }

    public synchronized void load() {
        if (!Files.isRegularFile(authFile)) {
            data = new AuthData();
            return;
        }
        try {
            String json = Files.readString(authFile, StandardCharsets.UTF_8);
            AuthData loaded = GSON.fromJson(json, AuthData.class);
            data = loaded != null ? loaded : new AuthData();
        } catch (Exception e) {
            data = new AuthData();
        }
    }

    public synchronized void save(String token, Integer userId, String userName) {
        data.apiToken = token;
        data.userId = userId;
        data.userName = userName;
        try {
            Files.writeString(authFile, GSON.toJson(data), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    public synchronized void clear() {
        data = new AuthData();
        try {
            Files.deleteIfExists(authFile);
        } catch (IOException ignored) {
        }
    }

    public synchronized boolean hasToken() {
        return data.apiToken != null && !data.apiToken.isBlank();
    }

    public synchronized String getToken() {
        return data.apiToken;
    }

    public synchronized String getUserName() {
        return data.userName != null ? data.userName : "";
    }

    public synchronized Integer getUserId() {
        return data.userId;
    }
}
