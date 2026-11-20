package com.bunker.survival.launcher.saves;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/**
 * Append-only sync/error log under appDir/logs (rotated when large).
 */
public final class SyncLog {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long MAX_BYTES = 3L * 1024L * 1024L;
    private static volatile Path logFile;
    private static volatile Consumer<String> uiSink;

    private SyncLog() {
    }

    public static void init(Path appDir, Consumer<String> uiLog) {
        uiSink = uiLog;
        if (appDir == null) {
            return;
        }
        try {
            Path dir = appDir.resolve("logs");
            Files.createDirectories(dir);
            logFile = dir.resolve("sync.log");
        } catch (Exception ignored) {
            logFile = null;
        }
    }

    public static void info(String message) {
        write("INFO", null, message, null);
    }

    public static void warn(String message) {
        write("WARN", null, message, null);
    }

    public static void error(String message) {
        write("ERROR", null, message, null);
    }

    public static void error(String gameKey, String message, Throwable t) {
        write("ERROR", gameKey, message, t);
    }

    public static void error(String message, Throwable t) {
        write("ERROR", null, message, t);
    }

    private static void write(String level, String gameKey, String message, Throwable t) {
        String msg = message != null ? message : "";
        String line = TS.format(LocalDateTime.now())
                + " [" + level + "]"
                + (gameKey != null && !gameKey.isBlank() ? " [" + gameKey + "]" : "")
                + " " + msg;
        if (t != null) {
            String detail = t.getClass().getSimpleName()
                    + (t.getMessage() != null ? ": " + t.getMessage() : "");
            line = line + " | " + detail;
        }
        Consumer<String> ui = uiSink;
        if (ui != null) {
            try {
                ui.accept(line.length() > 240 ? line.substring(0, 240) + "…" : line);
            } catch (Exception ignored) {
            }
        }
        Path file = logFile;
        if (file == null) {
            return;
        }
        synchronized (SyncLog.class) {
            try {
                rotateIfNeeded(file);
                StringBuilder sb = new StringBuilder(line).append('\n');
                if (t != null) {
                    StringWriter sw = new StringWriter();
                    t.printStackTrace(new PrintWriter(sw));
                    String stack = sw.toString();
                    // keep stack short in file
                    String[] lines = stack.split("\\R");
                    int limit = Math.min(lines.length, 12);
                    for (int i = 0; i < limit; i++) {
                        sb.append("  ").append(lines[i]).append('\n');
                    }
                }
                Files.writeString(file, sb.toString(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (Exception ignored) {
            }
        }
    }

    private static void rotateIfNeeded(Path file) {
        try {
            if (!Files.isRegularFile(file) || Files.size(file) < MAX_BYTES) {
                return;
            }
            Path bak = file.resolveSibling("sync.log.1");
            Files.deleteIfExists(bak);
            Files.move(file, bak);
        } catch (Exception ignored) {
        }
    }

    public static Path currentFile() {
        return logFile;
    }
}
