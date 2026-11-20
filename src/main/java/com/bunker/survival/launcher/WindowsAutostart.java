package com.bunker.survival.launcher;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Registers the launcher to start with Windows.
 *
 * Windows 11 often ignores or silently disables {@code Startup} folder shortcuts
 * ({@code StartupApproved}). HKCU Run plus an explicit enabled StartupApproved
 * value is what Settings → Apps → Startup actually honors.
 */
public final class WindowsAutostart {
    public static final String BUNKER_SHORTCUT = "Bunker.lnk";
    public static final String BUNKER_LEGACY_SHORTCUT = "Bunker Survival.lnk";
    public static final String PLATFORMA_SHORTCUT = "Platforma151.lnk";
    public static final String PLATFORMA_RUN_VALUE = "Platforma151";
    static final String SHORTCUT_NAME = BUNKER_SHORTCUT;

    private static final String RUN_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    private static final String APPROVED_RUN_KEY =
            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\StartupApproved\\Run";
    private static final String APPROVED_STARTUP_KEY =
            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\StartupApproved\\StartupFolder";
    /** Enabled flag used by Explorer StartupApproved (12-byte REG_BINARY). */
    private static final String APPROVED_ENABLED_HEX = "020000000000000000000000";

    private WindowsAutostart() {
    }

    public static Path startupFolder() {
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isBlank()) {
            throw new IllegalStateException("APPDATA не задан");
        }
        return Path.of(appData, "Microsoft", "Windows", "Start Menu", "Programs", "Startup");
    }

    public static Path shortcutPath() {
        return shortcutPath(SHORTCUT_NAME);
    }

    public static Path shortcutPath(String shortcutName) {
        return startupFolder().resolve(shortcutName);
    }

    public static boolean isInstalled() {
        return isInstalled(BUNKER_SHORTCUT) || isInstalled(BUNKER_LEGACY_SHORTCUT);
    }

    public static boolean isInstalled(String shortcutName) {
        if (PLATFORMA_SHORTCUT.equals(shortcutName)) {
            return isRunValueSet(PLATFORMA_RUN_VALUE) || isShortcutPresent(shortcutName);
        }
        return isShortcutPresent(shortcutName);
    }

    public static boolean isRunValueSet(String valueName) {
        if (valueName == null || valueName.isBlank() || !LauncherPaths.isWindows()) {
            return false;
        }
        try {
            ExecResult r = run(regExe(), List.of("query", RUN_KEY, "/v", valueName));
            return r.exitCode == 0 && r.output.toLowerCase(Locale.ROOT).contains(valueName.toLowerCase(Locale.ROOT));
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isShortcutPresent(String shortcutName) {
        try {
            return Files.isRegularFile(shortcutPath(shortcutName));
        } catch (Exception e) {
            return false;
        }
    }

    public static void remove() throws Exception {
        Files.deleteIfExists(shortcutPath(BUNKER_SHORTCUT));
        Files.deleteIfExists(shortcutPath(BUNKER_LEGACY_SHORTCUT));
        disableStartupApproved(APPROVED_STARTUP_KEY, BUNKER_SHORTCUT);
        disableStartupApproved(APPROVED_STARTUP_KEY, BUNKER_LEGACY_SHORTCUT);
    }

    public static void remove(String shortcutName) throws Exception {
        Files.deleteIfExists(shortcutPath(shortcutName));
        disableStartupApproved(APPROVED_STARTUP_KEY, shortcutName);
        if (PLATFORMA_SHORTCUT.equals(shortcutName)) {
            removeRunValue(PLATFORMA_RUN_VALUE);
        }
    }

    /**
     * Creates/updates the Startup shortcut.
     *
     * @param targetExeOrJar absolute path to BunkerSurvival.exe or bunker-launcher.jar
     * @param workingDir     WorkingDirectory for the shortcut
     * @param javaOrJavaw    used only when target is a JAR (may be null → find javaw)
     * @param extraArgs      e.g. {@code --tray}
     */
    public static void install(Path targetExeOrJar, Path workingDir, Path javaOrJavaw, String extraArgs)
            throws Exception {
        install(targetExeOrJar, workingDir, javaOrJavaw, extraArgs, BUNKER_SHORTCUT, "Bunker");
    }

    public static void install(Path targetExeOrJar, Path workingDir, Path javaOrJavaw, String extraArgs,
                               String shortcutName, String description)
            throws Exception {
        LaunchSpec spec = launchSpec(targetExeOrJar, javaOrJavaw, extraArgs);
        Path work = workingDir != null ? workingDir : targetExeOrJar.getParent();
        if (work == null) {
            work = Path.of(".").toAbsolutePath();
        }

        if (PLATFORMA_SHORTCUT.equals(shortcutName)) {
            registerRunValue(PLATFORMA_RUN_VALUE, spec.commandLine());
            enableStartupApproved(APPROVED_RUN_KEY, PLATFORMA_RUN_VALUE);
            Files.deleteIfExists(shortcutPath(shortcutName));
            disableStartupApproved(APPROVED_STARTUP_KEY, shortcutName);
            return;
        }

        Path startup = startupFolder();
        Files.createDirectories(startup);
        Path lnk = shortcutPath(shortcutName);
        String iconLocation = iconLocationFor(targetExeOrJar, spec.targetPath);
        createShortcut(lnk, spec.targetPath, spec.arguments, work.toAbsolutePath().toString(),
                iconLocation, description);
        enableStartupApproved(APPROVED_STARTUP_KEY, shortcutName);
    }

    static LaunchSpec launchSpec(Path targetExeOrJar, Path javaOrJavaw, String extraArgs) {
        if (targetExeOrJar == null || !Files.exists(targetExeOrJar)) {
            throw new IllegalStateException("Цель ярлыка не найдена: " + targetExeOrJar);
        }
        String name = targetExeOrJar.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".exe")) {
            String args = extraArgs == null ? "" : extraArgs.trim();
            return new LaunchSpec(targetExeOrJar.toAbsolutePath().toString(), args);
        }
        if (name.endsWith(".jar")) {
            Path javaw = javaOrJavaw != null ? javaOrJavaw : findJavaw();
            if (javaw == null || !Files.isRegularFile(javaw)) {
                throw new IllegalStateException("javaw.exe не найден для автозапуска JAR");
            }
            String arguments = "-jar \"" + targetExeOrJar.toAbsolutePath() + "\"";
            if (extraArgs != null && !extraArgs.isBlank()) {
                arguments += " " + extraArgs.trim();
            }
            return new LaunchSpec(javaw.toAbsolutePath().toString(), arguments);
        }
        throw new IllegalStateException("Неподдерживаемая цель ярлыка: " + targetExeOrJar);
    }

    /** Prefer EXE icon; for JAR targets use javaw + index 0 (Windows still shows generic). */
    private static String iconLocationFor(Path targetExeOrJar, String targetPath) {
        String name = targetExeOrJar.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".exe")) {
            return targetExeOrJar.toAbsolutePath() + ",0";
        }
        Path parent = targetExeOrJar.getParent();
        if (parent != null) {
            for (String exeName : List.of("Platforma151.exe")) {
                Path exe = parent.resolve(exeName);
                if (Files.isRegularFile(exe)) {
                    return exe.toAbsolutePath() + ",0";
                }
                if (parent.getParent() != null) {
                    Path exeUp = parent.getParent().resolve(exeName);
                    if (Files.isRegularFile(exeUp)) {
                        return exeUp.toAbsolutePath() + ",0";
                    }
                }
            }
        }
        return targetPath + ",0";
    }

    static void registerRunValue(String valueName, String commandLine) throws Exception {
        if (valueName == null || valueName.isBlank()) {
            throw new IllegalStateException("Пустое имя автозапуска");
        }
        if (commandLine == null || commandLine.isBlank()) {
            throw new IllegalStateException("Пустая команда автозапуска");
        }
        ExecResult r = run(regExe(), List.of("add", RUN_KEY, "/v", valueName, "/t", "REG_SZ", "/d", commandLine, "/f"));
        if (r.exitCode != 0) {
            throw new IllegalStateException("Не удалось записать автозапуск в реестр: " + r.output.trim());
        }
    }

    static void removeRunValue(String valueName) throws Exception {
        if (valueName == null || valueName.isBlank()) {
            return;
        }
        run(regExe(), List.of("delete", RUN_KEY, "/v", valueName, "/f"));
        run(regExe(), List.of("delete", APPROVED_RUN_KEY, "/v", valueName, "/f"));
    }

    static void enableStartupApproved(String key, String valueName) throws Exception {
        run(regExe(), List.of("add", key, "/f"));
        ExecResult r = run(regExe(), List.of(
                "add", key, "/v", valueName, "/t", "REG_BINARY", "/d", APPROVED_ENABLED_HEX, "/f"));
        if (r.exitCode != 0) {
            throw new IllegalStateException("Не удалось включить автозапуск в Windows: " + r.output.trim());
        }
    }

    static void disableStartupApproved(String key, String valueName) {
        try {
            run(regExe(), List.of("delete", key, "/v", valueName, "/f"));
        } catch (Exception ignored) {
        }
    }

    private static void createShortcut(Path lnk, String targetPath, String arguments, String workingDir,
                                       String iconLocation, String description)
            throws Exception {
        String lnkEsc = psSingleQuoted(lnk.toAbsolutePath().toString());
        String targetEsc = psSingleQuoted(targetPath);
        String argsEsc = psSingleQuoted(arguments == null ? "" : arguments);
        String workEsc = psSingleQuoted(workingDir);
        String iconEsc = psSingleQuoted(iconLocation == null ? targetPath + ",0" : iconLocation);
        String script = "$ws = New-Object -ComObject WScript.Shell; "
                + "$s = $ws.CreateShortcut(" + lnkEsc + "); "
                + "$s.TargetPath = " + targetEsc + "; "
                + "$s.Arguments = " + argsEsc + "; "
                + "$s.WorkingDirectory = " + workEsc + "; "
                + "$s.IconLocation = " + iconEsc + "; "
                + "$s.Description = " + psSingleQuoted(description == null || description.isBlank() ? "Bunker" : description) + "; "
                + "$s.Save();";

        ExecResult r = run(powershellExe(), List.of(
                "-NoProfile", "-STA", "-ExecutionPolicy", "Bypass", "-Command", script));
        if (r.exitCode != 0 || !Files.isRegularFile(lnk)) {
            throw new IllegalStateException("Не удалось создать ярлык автозапуска (код "
                    + r.exitCode + "): " + r.output.trim());
        }
    }

    /** Escape for PowerShell single-quoted string ('' = literal '). */
    static String psSingleQuoted(String s) {
        return "'" + (s == null ? "" : s.replace("'", "''")) + "'";
    }

    public static Path findJavaw() {
        List<Path> candidates = new ArrayList<>();
        String home = System.getProperty("java.home");
        if (home != null && !home.isBlank()) {
            Path homePath = Path.of(home);
            candidates.add(homePath.resolve("bin").resolve("javaw.exe"));
            candidates.add(homePath.resolve("bin").resolve("java.exe"));
        }
        try {
            Optional<String> cmd = ProcessHandle.current().info().command();
            if (cmd.isPresent()) {
                Path exe = Path.of(cmd.get()).toAbsolutePath().normalize();
                Path dir = exe.getParent();
                if (dir != null) {
                    candidates.add(dir.resolve("runtime").resolve("bin").resolve("javaw.exe"));
                    candidates.add(dir.resolve("bin").resolve("javaw.exe"));
                    if (dir.getParent() != null) {
                        candidates.add(dir.getParent().resolve("runtime").resolve("bin").resolve("javaw.exe"));
                    }
                }
            }
        } catch (Exception ignored) {
        }
        for (Path p : candidates) {
            if (p != null && Files.isRegularFile(p)) {
                return p;
            }
        }
        return null;
    }

    private static Path regExe() {
        return system32("reg.exe");
    }

    private static Path powershellExe() {
        Path ps = Path.of(windowsRoot(), "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
        if (Files.isRegularFile(ps)) {
            return ps;
        }
        return Path.of("powershell.exe");
    }

    private static Path system32(String exe) {
        Path p = Path.of(windowsRoot(), "System32", exe);
        if (Files.isRegularFile(p)) {
            return p;
        }
        return Path.of(exe);
    }

    private static String windowsRoot() {
        String root = System.getenv("SystemRoot");
        if (root == null || root.isBlank()) {
            root = "C:\\Windows";
        }
        return root;
    }

    private static ExecResult run(Path exe, List<String> args) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(exe.toString());
        cmd.addAll(args);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = p.waitFor();
        return new ExecResult(code, out);
    }

    static final class LaunchSpec {
        final String targetPath;
        final String arguments;

        LaunchSpec(String targetPath, String arguments) {
            this.targetPath = targetPath;
            this.arguments = arguments == null ? "" : arguments;
        }

        String commandLine() {
            String quoted = quoteWin(targetPath);
            if (arguments.isBlank()) {
                return quoted;
            }
            return quoted + " " + arguments;
        }
    }

    static String quoteWin(String path) {
        if (path == null) {
            return "\"\"";
        }
        if (path.startsWith("\"") && path.endsWith("\"")) {
            return path;
        }
        return "\"" + path + "\"";
    }

    private record ExecResult(int exitCode, String output) {
    }
}
