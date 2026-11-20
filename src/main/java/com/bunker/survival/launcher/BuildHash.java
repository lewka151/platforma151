package com.bunker.survival.launcher;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Reads/writes the short SHA-256 prefix written next to the app-image. */
public final class BuildHash {
    private BuildHash() {
    }

    public static String readLocal(LauncherPaths paths) {
        List<Path> candidates = new ArrayList<>();
        if (paths != null) {
            Path self = paths.selfJarDir();
            if (self != null) {
                candidates.add(self.resolve("build-hash.txt"));
            }
            Path install = paths.installRoot();
            if (install != null) {
                candidates.add(install.resolve("app").resolve("build-hash.txt"));
                candidates.add(install.resolve("build-hash.txt"));
            }
            candidates.add(paths.appDir.resolve("build-hash.txt"));
        }
        Path cwd = Path.of("").toAbsolutePath();
        candidates.add(cwd.resolve("build-hash.txt"));
        candidates.add(cwd.resolve("build").resolve("libs").resolve("build-hash.txt"));
        for (Path p : candidates) {
            if (p == null) {
                continue;
            }
            try {
                if (Files.isRegularFile(p)) {
                    String v = Files.readString(p, StandardCharsets.UTF_8).trim();
                    if (!v.isEmpty()) {
                        return v.split("\\s+")[0];
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /** Persist remote/local short hash so same-version checks do not loop on missing file. */
    public static void writeLocal(LauncherPaths paths, String hash) {
        if (paths == null || hash == null || hash.isBlank()) {
            return;
        }
        String shortHash = hash.trim().split("\\s+")[0];
        if (shortHash.isEmpty()) {
            return;
        }
        List<Path> targets = new ArrayList<>();
        Path install = paths.installRoot();
        if (install != null) {
            targets.add(install.resolve("build-hash.txt"));
            targets.add(install.resolve("app").resolve("build-hash.txt"));
        }
        Path self = paths.selfJarDir();
        if (self != null) {
            targets.add(self.resolve("build-hash.txt"));
        }
        targets.add(paths.appDir.resolve("build-hash.txt"));
        for (Path p : targets) {
            if (p == null) {
                continue;
            }
            try {
                Path parent = p.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(p, shortHash + "\n", StandardCharsets.UTF_8);
            } catch (Exception ignored) {
            }
        }
    }
}
