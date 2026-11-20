package com.bunker.survival.launcher.auth;

import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Opens the site auth page and captures a one-time code via localhost redirect.
 */
public class DesktopAuthFlow implements AutoCloseable {
    private HttpServer server;
    private final AtomicReference<String> codeRef = new AtomicReference<>();
    private CompletableFuture<String> future;

    public String startAndWait(String apiBaseUrl, long timeoutSeconds) throws Exception {
        future = new CompletableFuture<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/callback", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            String code = extractParam(query, "code");
            String body = "<!DOCTYPE html><html lang=ru><body style='font-family:sans-serif;background:#0c0e12;color:#e6e4df;padding:2rem'>"
                    + "<h1>Можно вернуться в приложение</h1><p>Окно можно закрыть.</p></body></html>";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
            if (code != null && !code.isBlank()) {
                codeRef.set(code);
                future.complete(code);
            }
        });
        server.setExecutor(null);
        server.start();

        String redirect = "http://127.0.0.1:" + port + "/callback";
        String url = trimSlash(apiBaseUrl) + "/desktop/auth?redirect="
                + URLEncoder.encode(redirect, StandardCharsets.UTF_8);
        openBrowser(url);

        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } finally {
            close();
        }
    }

    private static String extractParam(String query, String key) {
        if (query == null) {
            return null;
        }
        for (String part : query.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) {
                return java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    static String trimSlash(String url) {
        if (url == null) {
            return "";
        }
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    static void openBrowser(String url) throws IOException {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI.create(url));
            return;
        }
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", "", url});
        } else if (os.contains("mac")) {
            Runtime.getRuntime().exec(new String[]{"open", url});
        } else {
            Runtime.getRuntime().exec(new String[]{"xdg-open", url});
        }
    }

    @Override
    public void close() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }
}
