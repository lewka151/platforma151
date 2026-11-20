package com.bunker.survival.launcher.saves;

import com.bunker.survival.launcher.WorkThrottle;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class SavePacker {
    private static final Gson GSON = new GsonBuilder().create();

    private SavePacker() {
    }

    public static final class SaveFileInfo {
        public String name;
        public long size;
        public long mtime;

        public SaveFileInfo(String name, long size, long mtime) {
            this.name = name;
            this.size = size;
            this.mtime = mtime;
        }
    }

    public static final class SavePackResult {
        private final Path zipFile;
        private final String sha256;
        private final long totalBytes;
        private final long latestMtime;
        private final List<SaveFileInfo> files;
        private final String filesJson;

        public SavePackResult(Path zipFile, String sha256, long totalBytes, long latestMtime, List<SaveFileInfo> files) {
            this.zipFile = zipFile;
            this.sha256 = sha256;
            this.totalBytes = totalBytes;
            this.latestMtime = latestMtime;
            this.files = files != null ? files : Collections.emptyList();
            this.filesJson = GSON.toJson(this.files);
        }

        public Path getZipFile() {
            return zipFile;
        }

        public String getSha256() {
            return sha256;
        }

        public long getTotalBytes() {
            return totalBytes;
        }

        public long getLatestMtime() {
            return latestMtime;
        }

        public List<SaveFileInfo> getFiles() {
            return files;
        }

        public String getFilesJson() {
            return filesJson;
        }

        public void cleanup() {
            if (zipFile != null) {
                try {
                    Files.deleteIfExists(zipFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    public static final class FolderFingerprint {
        public final long totalBytes;
        public final long latestMtime;

        public FolderFingerprint(long totalBytes, long latestMtime) {
            this.totalBytes = totalBytes;
            this.latestMtime = latestMtime;
        }
    }

    public static FolderFingerprint fingerprint(Path resolved) throws IOException {
        return fingerprint(resolved, null);
    }

    public static FolderFingerprint fingerprint(GameSaveDefinition def) throws IOException {
        if (def == null) {
            throw new IOException("Игра не задана");
        }
        return fingerprint(def.getResolvedPath(), def);
    }

    public static FolderFingerprint fingerprint(Path resolved, GameSaveDefinition def) throws IOException {
        if (resolved == null || !Files.exists(resolved)) {
            throw new IOException("Папка сохранений не существует: " + (resolved != null ? resolved : "не настроено"));
        }
        long totalSize = 0;
        long latestMtime = 0;
        if (Files.isRegularFile(resolved)) {
            totalSize = Files.size(resolved);
            latestMtime = Files.getLastModifiedTime(resolved).toMillis() / 1000;
            return new FolderFingerprint(totalSize, latestMtime);
        }
        for (Path p : collectSaveFiles(resolved, def)) {
            totalSize += Files.size(p);
            latestMtime = Math.max(latestMtime, Files.getLastModifiedTime(p).toMillis() / 1000);
        }
        return new FolderFingerprint(totalSize, latestMtime);
    }

    /** Collect files under a save root applying catalog filters (extensions / include globs). */
    public static List<Path> collectSaveFiles(Path baseDir, GameSaveDefinition def) throws IOException {
        List<Path> allFiles = new ArrayList<>();
        final int maxDepth = 6;
        final int maxFiles = 2000;
        try (var stream = Files.walk(baseDir, maxDepth)) {
            stream.filter(Files::isRegularFile).forEach(allFiles::add);
        }
        if (allFiles.size() > maxFiles) {
            throw new IOException("Слишком много файлов для сейва (" + allFiles.size()
                    + "). Укажите папку сохранений, а не установку игры.");
        }
        List<Path> filtered = new ArrayList<>();
        for (Path p : allFiles) {
            String relName = baseDir.relativize(p).toString().replace('\\', '/');
            if (!isAllowedSaveRelative(relName, def)) {
                continue;
            }
            filtered.add(p);
        }
        return filtered;
    }

    static boolean isAllowedSaveRelative(String relName, GameSaveDefinition def) {
        if (relName == null || relName.isBlank()) {
            return false;
        }
        String norm = relName.replace('\\', '/');
        String lower = norm.toLowerCase(Locale.ROOT);

        if (isGloballyExcluded(lower)) {
            return false;
        }
        if (def != null && isGameExcluded(lower, def)) {
            return false;
        }

        boolean hasExt = def != null && def.getFileExtensions() != null && !def.getFileExtensions().isEmpty();
        boolean hasInc = def != null && def.getIncludeGlobs() != null && !def.getIncludeGlobs().isEmpty();
        if (!hasExt && !hasInc) {
            return true;
        }
        if (hasExt) {
            for (String ext : def.getFileExtensions()) {
                if (ext == null || ext.isBlank()) {
                    continue;
                }
                String e = ext.trim().toLowerCase(Locale.ROOT);
                if (!e.startsWith(".")) {
                    e = "." + e;
                }
                if (lower.endsWith(e)) {
                    return true;
                }
            }
        }
        if (hasInc) {
            for (String raw : def.getIncludeGlobs()) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                String pattern = raw.trim().replace('\\', '/');
                if (pattern.equals("*") || pattern.equals("**") || pattern.equals("**/*")) {
                    return true;
                }
                try {
                    PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
                    if (matcher.matches(Path.of(norm))) {
                        return true;
                    }
                    if (!pattern.contains("/") && !pattern.startsWith("**/")) {
                        PathMatcher deep = FileSystems.getDefault().getPathMatcher("glob:**/" + pattern);
                        if (deep.matches(Path.of(norm))) {
                            return true;
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return false;
    }

    /** Always-on blacklist for junk under save roots. */
    static boolean isGloballyExcluded(String lowerRelPath) {
        if (lowerRelPath == null || lowerRelPath.isBlank()) {
            return true;
        }
        lowerRelPath = lowerRelPath.replace('\\', '/').toLowerCase(Locale.ROOT);
        String name = lowerRelPath;
        int slash = lowerRelPath.lastIndexOf('/');
        if (slash >= 0) {
            name = lowerRelPath.substring(slash + 1);
        }
        if (name.equals("thumbs.db") || name.equals("desktop.ini") || name.equals(".ds_store")) {
            return true;
        }
        if (name.endsWith(".exe") || name.endsWith(".dll") || name.endsWith(".so")
                || name.endsWith(".log") || name.endsWith(".tmp") || name.endsWith(".dmp")
                || name.endsWith(".pdb") || name.endsWith(".bak") || name.endsWith(".old")) {
            return true;
        }
        String[] bannedDirs = {
                "debug", "logs", "log", "cache", "caches", "temp", "tmp",
                "crashdumps", "crash_dumps", "crash reports", "crashreports",
                "__pycache__", ".git", ".svn", "shadercache", "shader_cache"
        };
        String[] parts = lowerRelPath.split("/");
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            for (String banned : bannedDirs) {
                if (part.equals(banned)) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean isGameExcluded(String lowerRelPath, GameSaveDefinition def) {
        if (lowerRelPath == null) {
            return true;
        }
        lowerRelPath = lowerRelPath.replace('\\', '/').toLowerCase(Locale.ROOT);
        if (def.getExcludeDirs() != null) {
            String[] parts = lowerRelPath.split("/");
            for (String dir : def.getExcludeDirs()) {
                if (dir == null || dir.isBlank()) {
                    continue;
                }
                String d = dir.trim().toLowerCase(Locale.ROOT).replace('\\', '/');
                while (d.startsWith("./")) {
                    d = d.substring(2);
                }
                if (d.endsWith("/")) {
                    d = d.substring(0, d.length() - 1);
                }
                for (String part : parts) {
                    if (part.equals(d)) {
                        return true;
                    }
                }
                if (lowerRelPath.equals(d) || lowerRelPath.startsWith(d + "/")) {
                    return true;
                }
            }
        }
        if (def.getExcludeGlobs() != null) {
            for (String raw : def.getExcludeGlobs()) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                String pattern = raw.trim().replace('\\', '/').toLowerCase(Locale.ROOT);
                try {
                    PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
                    if (matcher.matches(Path.of(lowerRelPath))) {
                        return true;
                    }
                    // bare filename / folder patterns also match nested paths
                    if (!pattern.startsWith("**/")) {
                        PathMatcher deep = FileSystems.getDefault().getPathMatcher("glob:**/" + pattern);
                        if (deep.matches(Path.of(lowerRelPath))) {
                            return true;
                        }
                    }
                    // directory-style: Screenshots/** and **/Screenshots/**
                    if (pattern.endsWith("/**")) {
                        String prefix = pattern.substring(0, pattern.length() - 3);
                        if (prefix.startsWith("**/")) {
                            prefix = prefix.substring(3);
                        }
                        if (lowerRelPath.equals(prefix) || lowerRelPath.startsWith(prefix + "/")) {
                            return true;
                        }
                        if (lowerRelPath.contains("/" + prefix + "/")) {
                            return true;
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return false;
    }

    public static SavePackResult pack(GameSaveDefinition def) throws IOException {
        Path resolved = def.getResolvedPath();
        if (resolved == null || !Files.exists(resolved)) {
            throw new IOException("Папка сохранений не существует: " + (resolved != null ? resolved : "не настроено"));
        }
        if (DesktopGamesScanner.isUnsafeSyncRoot(resolved)) {
            throw new IOException("Отказ: путь похож на папку игры/системы, а не на сохранения: " + resolved);
        }

        Path tempZip = Files.createTempFile("bunker_save_" + def.getKey() + "_", ".zip");
        List<SaveFileInfo> fileList = new ArrayList<>();
        long totalSize = 0;
        long latestMtime = 0;

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (FileOutputStream fos = new FileOutputStream(tempZip.toFile());
                 BufferedOutputStream bos = new BufferedOutputStream(fos);
                 ZipOutputStream zos = new ZipOutputStream(bos)) {

                zos.setLevel(Deflater.BEST_COMPRESSION);

                if (Files.isRegularFile(resolved)) {
                    // Single file save (e.g. DARKSII0000.sl2)
                    String name = resolved.getFileName().toString();
                    long size = Files.size(resolved);
                    long mtime = Files.getLastModifiedTime(resolved).toMillis() / 1000;
                    totalSize += size;
                    latestMtime = Math.max(latestMtime, mtime);
                    fileList.add(new SaveFileInfo(name, size, mtime));

                    ZipEntry ze = new ZipEntry(name);
                    ze.setTime(Files.getLastModifiedTime(resolved).toMillis());
                    zos.putNextEntry(ze);
                    try (InputStream fis = Files.newInputStream(resolved)) {
                        copyThrottled(fis, zos);
                    }
                    zos.closeEntry();
                } else {
                    Path baseDir = resolved;
                    final long maxTotalBytes = 500L * 1024L * 1024L; // 500 MB
                    List<Path> allFiles = collectSaveFiles(baseDir, def);
                    if (allFiles.isEmpty()) {
                        throw new IOException("В папке сохранения нет подходящих файлов: " + baseDir
                                + (def.hasFileFilters() ? " (фильтр расширений/include)" : ""));
                    }

                    for (Path p : allFiles) {
                        String relName = baseDir.relativize(p).toString().replace('\\', '/');
                        long size = Files.size(p);
                        totalSize += size;
                        if (totalSize > maxTotalBytes) {
                            throw new IOException("Размер архива сейва слишком большой — похоже на файлы игры, не сохранения.");
                        }
                        long mtime = Files.getLastModifiedTime(p).toMillis() / 1000;
                        latestMtime = Math.max(latestMtime, mtime);
                        fileList.add(new SaveFileInfo(relName, size, mtime));

                        ZipEntry ze = new ZipEntry(relName);
                        ze.setTime(Files.getLastModifiedTime(p).toMillis());
                        zos.putNextEntry(ze);
                        try (InputStream fis = Files.newInputStream(p)) {
                            copyThrottled(fis, zos);
                        }
                        zos.closeEntry();
                        WorkThrottle.yieldBetweenUnits();
                    }
                }
            }

            // Compute SHA256 of the generated ZIP
            try (InputStream is = new BufferedInputStream(Files.newInputStream(tempZip));
                 DigestInputStream dis = new DigestInputStream(is, md)) {
                byte[] buf = new byte[8192];
                while (dis.read(buf) != -1) {
                    // hashing
                }
            }
            String sha256 = HexFormat.of().formatHex(md.digest());

            return new SavePackResult(tempZip, sha256, totalSize, latestMtime, fileList);
        } catch (Exception e) {
            try {
                Files.deleteIfExists(tempZip);
            } catch (Exception ignored) {
            }
            if (e instanceof IOException ioe) {
                throw ioe;
            }
            throw new IOException("Ошибка при архивации сохранения: " + e.getMessage(), e);
        }
    }

    private static void copyThrottled(InputStream in, ZipOutputStream out) throws IOException {
        byte[] buf = new byte[4096];
        int n;
        int chunks = 0;
        while ((n = in.read(buf)) >= 0) {
            if (n == 0) {
                continue;
            }
            out.write(buf, 0, n);
            chunks++;
            if ((chunks & 7) == 0) {
                WorkThrottle.yieldWhileStreaming();
            }
        }
    }

    public static void unpack(Path zipFile, Path targetDir, boolean backupExisting) throws IOException {
        if (!Files.isRegularFile(zipFile)) {
            throw new IOException("ZIP-архив не найден: " + zipFile);
        }

        if (backupExisting && Files.exists(targetDir)) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            Path backupPath = targetDir.getParent().resolve(targetDir.getFileName().toString() + "_backup_" + timestamp);
            if (Files.isDirectory(targetDir)) {
                copyDirectory(targetDir, backupPath);
            } else if (Files.isRegularFile(targetDir)) {
                Files.copy(targetDir, backupPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        Files.createDirectories(targetDir);

        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zipFile)))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.contains("..")) {
                    continue; // Path traversal security protection
                }
                Path outPath = targetDir.resolve(name);
                if (entry.isDirectory()) {
                    Files.createDirectories(outPath);
                } else {
                    if (outPath.getParent() != null) {
                        Files.createDirectories(outPath.getParent());
                    }
                    try (FileOutputStream fos = new FileOutputStream(outPath.toFile());
                         BufferedOutputStream bos = new BufferedOutputStream(fos)) {
                        zis.transferTo(bos);
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        Files.createDirectories(target);
        try (var stream = Files.walk(source)) {
            stream.forEach(src -> {
                try {
                    Path dest = target.resolve(source.relativize(src));
                    if (Files.isDirectory(src)) {
                        Files.createDirectories(dest);
                    } else {
                        Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException ignored) {
                }
            });
        }
    }
}
