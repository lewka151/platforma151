package com.bunker.survival.launcher.saves;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class GameSaveDefinition {
    private final String key;
    private String name;
    private String shortcutName;
    private final List<String> defaultCandidates = new ArrayList<>();
    private String customSavePath;
    private String exeOrIconPath;
    private boolean custom;
    private Path resolvedPath;
    private String settingsPath;
    private String installPath;
    private List<String> fileExtensions = new ArrayList<>();
    private List<String> includeGlobs = new ArrayList<>();
    private List<String> excludeGlobs = new ArrayList<>();
    private List<String> excludeDirs = new ArrayList<>();

    public GameSaveDefinition(String key, String name) {
        this.key = key;
        this.name = name;
    }

    public GameSaveDefinition(String key, String name, String shortcutName, List<String> candidates) {
        this.key = key;
        this.name = name;
        this.shortcutName = shortcutName;
        if (candidates != null) {
            this.defaultCandidates.addAll(candidates);
        }
    }

    public String getKey() {
        return key;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getShortcutName() {
        return shortcutName;
    }

    public void setShortcutName(String shortcutName) {
        this.shortcutName = shortcutName;
    }

    public List<String> getDefaultCandidates() {
        return Collections.unmodifiableList(defaultCandidates);
    }

    public void addCandidate(String path) {
        if (path != null && !path.isBlank()) {
            defaultCandidates.add(path);
        }
    }

    public String getCustomSavePath() {
        return customSavePath;
    }

    public void setCustomSavePath(String customSavePath) {
        this.customSavePath = customSavePath;
    }

    public String getExeOrIconPath() {
        return exeOrIconPath;
    }

    public void setExeOrIconPath(String exeOrIconPath) {
        this.exeOrIconPath = exeOrIconPath;
    }

    public String getSettingsPath() {
        return settingsPath;
    }

    public void setSettingsPath(String settingsPath) {
        this.settingsPath = settingsPath;
    }

    public String getInstallPath() {
        return installPath;
    }

    public void setInstallPath(String installPath) {
        this.installPath = installPath;
    }

    public boolean isCustom() {
        return custom;
    }

    public void setCustom(boolean custom) {
        this.custom = custom;
    }

    public Path getResolvedPath() {
        return resolvedPath;
    }

    public void setResolvedPath(Path resolvedPath) {
        this.resolvedPath = resolvedPath;
    }

    public List<String> getFileExtensions() {
        return fileExtensions;
    }

    public void setFileExtensions(List<String> fileExtensions) {
        this.fileExtensions = fileExtensions != null ? new ArrayList<>(fileExtensions) : new ArrayList<>();
    }

    public List<String> getIncludeGlobs() {
        return includeGlobs;
    }

    public void setIncludeGlobs(List<String> includeGlobs) {
        this.includeGlobs = includeGlobs != null ? new ArrayList<>(includeGlobs) : new ArrayList<>();
    }

    public List<String> getExcludeGlobs() {
        return excludeGlobs;
    }

    public void setExcludeGlobs(List<String> excludeGlobs) {
        this.excludeGlobs = excludeGlobs != null ? new ArrayList<>(excludeGlobs) : new ArrayList<>();
    }

    public List<String> getExcludeDirs() {
        return excludeDirs;
    }

    public void setExcludeDirs(List<String> excludeDirs) {
        this.excludeDirs = excludeDirs != null ? new ArrayList<>(excludeDirs) : new ArrayList<>();
    }

    public boolean hasFileFilters() {
        return (fileExtensions != null && !fileExtensions.isEmpty())
                || (includeGlobs != null && !includeGlobs.isEmpty())
                || (excludeGlobs != null && !excludeGlobs.isEmpty())
                || (excludeDirs != null && !excludeDirs.isEmpty());
    }

    public boolean isConfigured() {
        return resolvedPath != null || (customSavePath != null && !customSavePath.isBlank());
    }

    @Override
    public String toString() {
        return name + " (" + key + ")";
    }
}
