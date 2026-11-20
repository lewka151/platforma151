package com.bunker.survival.launcher.auth;

import com.bunker.survival.launcher.TlsHttp;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class LauncherBrowserAuth {
    private LauncherBrowserAuth() {
    }

    public static void signIn(JFrame parent, LauncherAuthStore store, String apiBaseUrl, String tokenLabel,
                              Runnable onSuccess) {
        String base = DesktopAuthFlow.trimSlash(apiBaseUrl);
        if (base.isEmpty()) {
            JOptionPane.showMessageDialog(parent, "Укажите адрес сайта", "Вход", JOptionPane.WARNING_MESSAGE);
            return;
        }
        new SwingWorker<JsonObject, Void>() {
            @Override
            protected JsonObject doInBackground() throws Exception {
                try (DesktopAuthFlow flow = new DesktopAuthFlow()) {
                    String code = flow.startAndWait(base, 180);
                    return exchange(base, code, tokenLabel);
                }
            }

            @Override
            protected void done() {
                try {
                    JsonObject data = get();
                    String token = data.get("api_token").getAsString();
                    JsonObject u = data.getAsJsonObject("user");
                    int uid = u.get("id").getAsInt();
                    String uname = u.has("name") && !u.get("name").isJsonNull() ? u.get("name").getAsString() : "";
                    store.save(token, uid, uname);
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    String msg = cause.getMessage() != null ? cause.getMessage() : cause.toString();
                    if (msg.contains("TimeoutException") || msg.contains("timeout")) {
                        msg = "Время ожидания входа истекло";
                    }
                    JOptionPane.showMessageDialog(parent, msg, "Вход не выполнен", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    public static void openRegister(String apiBaseUrl) {
        String url = DesktopAuthFlow.trimSlash(apiBaseUrl) + "/desktop/register";
        try {
            DesktopAuthFlow.openBrowser(url);
        } catch (Exception ignored) {
        }
    }

    public static void revoke(LauncherAuthStore store, String apiBaseUrl) {
        String token = store.getToken();
        String base = DesktopAuthFlow.trimSlash(apiBaseUrl);
        if (token != null && !token.isBlank() && !base.isEmpty()) {
            try {
                HttpClient http = TlsHttp.clientBuilder().connectTimeout(Duration.ofSeconds(8)).build();
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(base + "/api/auth/desktop/revoke"))
                        .timeout(Duration.ofSeconds(10))
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json; charset=utf-8")
                        .POST(HttpRequest.BodyPublishers.ofString("{}", StandardCharsets.UTF_8))
                        .build();
                http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (Exception ignored) {
            }
        }
        store.clear();
    }

    private static JsonObject exchange(String apiBaseUrl, String code, String label) throws Exception {
        HttpClient http = TlsHttp.clientBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        JsonObject payload = new JsonObject();
        payload.addProperty("code", code);
        payload.addProperty("label", label != null && !label.isBlank() ? label : "desktop");
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(apiBaseUrl + "/api/auth/desktop/exchange"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonObject body = JsonParser.parseString(resp.body()).getAsJsonObject();
        if (resp.statusCode() < 200 || resp.statusCode() >= 300 || !body.has("ok") || !body.get("ok").getAsBoolean()) {
            String err = body.has("error") ? body.get("error").getAsString() : ("HTTP " + resp.statusCode());
            throw new RuntimeException(err);
        }
        return body.has("data") ? body.getAsJsonObject("data") : body;
    }
}
