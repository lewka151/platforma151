package com.bunker.survival.launcher.saves;

import javax.swing.Timer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Local daily zip of game settings paths (separate from cloud GameSettingsSyncService).
 */
public final class SettingsArchiveService {
    private final GameSaveRegistry registry;
    private final Path defaultArchiveDir;
    private Timer dailyTimer;
    private final java.util.function.Consumer<String> log;

    public SettingsArchiveService(GameSaveRegistry registry, Path defaultArchiveDir, java.util.function.Consumer<String> log) {
        this.registry = registry;
        this.defaultArchiveDir = defaultArchiveDir;
        this.log = log != null ? log : s -> {};
    }

    public void start() {
        stop();
        CustomGameStore.SavesConfigData cfg = registry.getStore().loadConfig();
        if (!cfg.settingsArchiveEnabled) {
            return;
        }
        runOnceAsync();
        dailyTimer = new Timer(60 * 60 * 1000, e -> runOnceAsync());
        dailyTimer.setRepeats(true);
        dailyTimer.start();
    }

    public void stop() {
        if (dailyTimer != null) {
            dailyTimer.stop();
            dailyTimer = null;
        }
    }

    private void runOnceAsync() {
        new Thread(() -> {
            try {
                maybeRebuild();
            } catch (Exception e) {
                log.accept("Архив настроек: " + e.getMessage());
            }
        }, "settings-archive").start();
    }

    public synchronized void maybeRebuild() throws Exception {
        CustomGameStore.SavesConfigData cfg = registry.getStore().loadConfig();
        if (!cfg.settingsArchiveEnabled) {
            return;
        }
        long today = LocalDate.now().toEpochDay();
        Path dir = resolveArchiveDir(cfg);
        Files.createDirectories(dir);
        Path zip = dir.resolve("game-settings.zip");
        Path tmp = dir.resolve("game-settings.tmp.zip");

        String contentHash = hashSettingsContent();
        if (contentHash.equals(cfg.settingsArchiveLastHash)
                && cfg.settingsArchiveLastDay == today
                && Files.isRegularFile(zip)) {
            return;
        }

        if (Files.exists(tmp)) {
            Files.delete(tmp);
        }
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tmp))) {
            zos.setLevel(java.util.zip.Deflater.BEST_COMPRESSION);
            for (GameSaveDefinition def : registry.getAllGames()) {
                String settings = def.getSettingsPath();
                if (settings == null || settings.isBlank()) {
                    continue;
                }
                Path src = Path.of(settings);
                if (!Files.exists(src)) {
                    continue;
                }
                String prefix = def.getKey() + "/";
                if (Files.isRegularFile(src)) {
                    putFile(zos, src, prefix + src.getFileName());
                } else if (Files.isDirectory(src)) {
                    Files.walkFileTree(src, new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            String rel = src.relativize(file).toString().replace('\\', '/');
                            putFile(zos, file, prefix + rel);
                            return FileVisitResult.CONTINUE;
                        }
                    });
                }
            }
        }
        Files.move(tmp, zip, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        cfg.settingsArchiveLastHash = contentHash;
        cfg.settingsArchiveLastDay = today;
        registry.getStore().saveConfig(cfg);
        log.accept("Архив настроек обновлён: " + zip);
    }

    private Path resolveArchiveDir(CustomGameStore.SavesConfigData cfg) {
        if (cfg.settingsArchiveDir != null && !cfg.settingsArchiveDir.isBlank()) {
            return Path.of(cfg.settingsArchiveDir);
        }
        return defaultArchiveDir;
    }

    private String hashSettingsContent() throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        for (GameSaveDefinition def : registry.getAllGames()) {
            String settings = def.getSettingsPath();
            if (settings == null || settings.isBlank()) {
                continue;
            }
            Path src = Path.of(settings);
            if (!Files.exists(src)) {
                continue;
            }
            md.update(def.getKey().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if (Files.isRegularFile(src)) {
                digestFile(md, src);
            } else if (Files.isDirectory(src)) {
                try (var stream = Files.walk(src)) {
                    List<Path> files = stream.filter(Files::isRegularFile).sorted().toList();
                    for (Path f : files) {
                        md.update(src.relativize(f).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        digestFile(md, f);
                    }
                }
            }
        }
        return HexFormat.of().formatHex(md.digest());
    }

    private static void digestFile(MessageDigest md, Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file);
             DigestInputStream din = new DigestInputStream(in, md)) {
            din.transferTo(OutputStream.nullOutputStream());
        }
    }

    private static void putFile(ZipOutputStream zos, Path file, String entryName) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        zos.putNextEntry(entry);
        try (InputStream in = Files.newInputStream(file)) {
            in.transferTo(zos);
        }
        zos.closeEntry();
    }
}
