package com.bunker.survival.launcher;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class LauncherPaths {
    public static final String LOCAL_GAME_VERSION_FALLBACK = "2.9";

    public final Path appDir;
    public final Path configPath;
    public final Path versionPath;
    public final Path gameDir;

    private LauncherPaths(Path appDir) {
        this.appDir = appDir;
        this.configPath = appDir.resolve("config.json");
        this.versionPath = appDir.resolve("version.txt");
        this.gameDir = appDir.resolve("game");
    }

    public static LauncherPaths resolve() {
        return resolve("BunkerSurvival", ".bunker-survival", true);
    }

    public static LauncherPaths resolvePlatforma() {
        return resolve("Platforma151", ".platforma151", false);
    }

    public static LauncherPaths resolve(String appDataFolder, String unixFolder, boolean withGameDir) {
        String appData = System.getenv("APPDATA");
        Path appDir;
        if (appData != null && !appData.isBlank()) {
            appDir = Path.of(appData, appDataFolder);
        } else {
            appDir = Path.of(System.getProperty("user.home"), unixFolder);
        }
        LauncherPaths paths = new LauncherPaths(appDir);
        try {
            Files.createDirectories(paths.appDir);
            if (withGameDir) {
                Files.createDirectories(paths.gameDir);
            }
        } catch (Exception ignored) {
        }
        return paths;
    }

    public static boolean isWindows() {
        String os = System.getProperty("os.name", "");
        return os.toLowerCase(Locale.ROOT).contains("win");
    }

    public Path selfJarDir() {
        try {
            var cs = com.bunker.survival.platforma.PlatformaApp.class.getProtectionDomain().getCodeSource();
            if (cs == null || cs.getLocation() == null) {
                return null;
            }
            Path p = Path.of(cs.getLocation().toURI());
            if (Files.isRegularFile(p)) {
                return p.getParent();
            }
            return p;
        } catch (Exception e) {
            return null;
        }
    }

    public Path installRoot() {
        try {
            Optional<String> cmd = ProcessHandle.current().info().command();
            if (cmd.isPresent()) {
                Path exe = Path.of(cmd.get()).toAbsolutePath().normalize();
                String name = exe.getFileName() != null
                        ? exe.getFileName().toString().toLowerCase(Locale.ROOT)
                        : "";
                if (name.endsWith(".exe") && !name.equals("java.exe") && !name.equals("javaw.exe")) {
                    Path dir = exe.getParent();
                    if (dir != null) {
                        return dir;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        Path self = selfJarDir();
        if (self != null && self.getFileName() != null
                && "app".equalsIgnoreCase(self.getFileName().toString())) {
            return self.getParent();
        }
        return self;
    }

    Path findProjectRoot() {
        Path cur = Path.of("").toAbsolutePath();
        for (int i = 0; i < 6 && cur != null; i++) {
            if (Files.isRegularFile(cur.resolve("gradlew.bat"))
                    && Files.isDirectory(cur.resolve("desktop"))) {
                return cur;
            }
            cur = cur.getParent();
        }
        Path start = installRoot();
        if (start == null) {
            start = selfJarDir();
        }
        if (start != null) {
            Path up = start;
            for (int i = 0; i < 8 && up != null; i++) {
                if (Files.isRegularFile(up.resolve("gradlew.bat"))) {
                    return up;
                }
                up = up.getParent();
            }
        }
        return null;
    }

    List<Path> gameJarCandidates() {
        List<Path> candidates = new ArrayList<>();
        Path self = selfJarDir();
        if (self != null) {
            candidates.add(self.resolve("bunker-survival.jar"));
            candidates.add(self.resolve("desktop").resolve("bunker-survival.jar"));
        }
        Path install = installRoot();
        if (install != null) {
            candidates.add(install.resolve("bunker-survival.jar"));
            candidates.add(install.resolve("app").resolve("bunker-survival.jar"));
        }
        candidates.add(gameDir.resolve("bunker-survival.jar"));
        Path root = findProjectRoot();
        if (root != null) {
            candidates.add(root.resolve("build").resolve("libs").resolve("bunker-survival.jar"));
            candidates.add(root.resolve("desktop").resolve("build").resolve("libs").resolve("bunker-survival.jar"));
            candidates.add(root.resolve("dist").resolve("BunkerSurvival").resolve("app").resolve("bunker-survival.jar"));
        }
        Path cwd = Path.of("").toAbsolutePath();
        candidates.add(cwd.resolve("bunker-survival.jar"));
        candidates.add(cwd.resolve("build").resolve("libs").resolve("bunker-survival.jar"));
        candidates.add(cwd.resolve("desktop").resolve("build").resolve("libs").resolve("bunker-survival.jar"));
        return candidates;
    }

    public Path resolveAutostartTarget() {
        return resolveAutostartTarget(
                List.of("BunkerLauncher.exe", "BunkerSurvival.exe", "Bunker.exe"),
                List.of("bunker-launcher.jar"),
                List.of("BunkerLauncher", "BunkerSurvival", "Bunker"));
    }

    public Path resolveAutostartTarget(List<String> exeNames, List<String> jarNames, List<String> distFolders) {
        Path install = installRoot();
        if (install != null) {
            Path found = firstExisting(install, exeNames, jarNames);
            if (found != null) {
                return found;
            }
        }
        Path self = selfJarDir();
        if (self != null) {
            Path found = firstExisting(self, exeNames, jarNames);
            if (found != null) {
                return found;
            }
        }
        Path root = findProjectRoot();
        if (root != null) {
            for (String folder : distFolders) {
                Path distDir = root.resolve("dist").resolve(folder);
                Path found = firstExisting(distDir, exeNames, jarNames);
                if (found != null) {
                    return found;
                }
            }
            for (String jarName : jarNames) {
                Path built = root.resolve("build").resolve("libs").resolve(jarName);
                if (Files.isRegularFile(built)) {
                    return built.toAbsolutePath().normalize();
                }
            }
        }
        return null;
    }

    private static Path firstExisting(Path dir, List<String> exeNames, List<String> jarNames) {
        if (dir == null) {
            return null;
        }
        for (String exeName : exeNames) {
            Path exe = dir.resolve(exeName);
            if (Files.isRegularFile(exe)) {
                return exe.toAbsolutePath().normalize();
            }
        }
        Path app = dir.resolve("app");
        for (String jarName : jarNames) {
            Path appJar = app.resolve(jarName);
            if (Files.isRegularFile(appJar)) {
                return appJar.toAbsolutePath().normalize();
            }
            Path jar = dir.resolve(jarName);
            if (Files.isRegularFile(jar)) {
                return jar.toAbsolutePath().normalize();
            }
        }
        return null;
    }

    static String findJava() {
        List<Path> candidates = new ArrayList<>();
        String home = System.getProperty("java.home");
        if (home != null && !home.isBlank()) {
            Path homePath = Path.of(home);
            candidates.add(homePath.resolve("bin").resolve("java.exe"));
            candidates.add(homePath.resolve("bin").resolve("javaw.exe"));
            candidates.add(homePath.resolve("bin").resolve("java"));
            if (homePath.getFileName() != null
                    && "runtime".equalsIgnoreCase(homePath.getFileName().toString())
                    && homePath.getParent() != null) {
                candidates.add(homePath.getParent().resolve("runtime").resolve("bin").resolve("java.exe"));
            }
        }
        String envJavaHome = System.getenv("JAVA_HOME");
        if (envJavaHome != null && !envJavaHome.isBlank()) {
            Path envPath = Path.of(envJavaHome);
            candidates.add(envPath.resolve("bin").resolve("java.exe"));
            candidates.add(envPath.resolve("bin").resolve("javaw.exe"));
            candidates.add(envPath.resolve("bin").resolve("java"));
        }
        try {
            Optional<String> cmd = ProcessHandle.current().info().command();
            if (cmd.isPresent()) {
                Path exe = Path.of(cmd.get()).toAbsolutePath().normalize();
                Path dir = exe.getParent();
                if (dir != null) {
                    candidates.add(dir.resolve("runtime").resolve("bin").resolve("java.exe"));
                    candidates.add(dir.resolve("runtime").resolve("bin").resolve("javaw.exe"));
                    candidates.add(dir.resolve("bin").resolve("java.exe"));
                    candidates.add(dir.resolve("bin").resolve("javaw.exe"));
                    if (dir.getParent() != null) {
                        candidates.add(dir.getParent().resolve("runtime").resolve("bin").resolve("java.exe"));
                    }
                }
            }
        } catch (Exception ignored) {
        }
        String userHome = System.getProperty("user.home");
        if (userHome != null && !userHome.isBlank()) {
            Path jdks = Path.of(userHome, ".jdks");
            if (Files.isDirectory(jdks)) {
                try (var stream = Files.newDirectoryStream(jdks)) {
                    for (Path jdk : stream) {
                        candidates.add(jdk.resolve("bin").resolve("java.exe"));
                    }
                } catch (Exception ignored) {
                }
            }
        }
        for (Path java : candidates) {
            if (java != null && Files.isRegularFile(java)) {
                return java.toAbsolutePath().toString();
            }
        }
        return "java";
    }
}
