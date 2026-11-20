package com.bunker.survival.platforma;

import com.bunker.survival.launcher.BuildHash;
import com.bunker.survival.launcher.ClientRelease;
import com.bunker.survival.launcher.DarkScrollBarUI;
import com.bunker.survival.launcher.EdgeResizer;
import com.bunker.survival.launcher.HudFramePanel;
import com.bunker.survival.launcher.LauncherConfig;
import com.bunker.survival.launcher.LauncherPaths;
import com.bunker.survival.launcher.LauncherTheme;
import com.bunker.survival.launcher.LauncherTray;
import com.bunker.survival.launcher.MetroBackdrop;
import com.bunker.survival.launcher.SiteButton;
import com.bunker.survival.launcher.SingleInstance;
import com.bunker.survival.launcher.TitleBar;
import com.bunker.survival.launcher.TlsHttp;
import com.bunker.survival.launcher.UpdateService;
import com.bunker.survival.launcher.WindowsAutostart;
import com.bunker.survival.launcher.auth.LauncherAuthStore;
import com.bunker.survival.launcher.auth.LauncherBrowserAuth;
import com.bunker.survival.launcher.saves.CatalogSyncService;
import com.bunker.survival.launcher.saves.CloudSaveSyncService;
import com.bunker.survival.launcher.saves.CustomGameStore;
import com.bunker.survival.launcher.saves.DesktopGamesScanner;
import com.bunker.survival.launcher.saves.GameSaveRegistry;
import com.bunker.survival.launcher.saves.SaveManagerDialog;
import com.bunker.survival.launcher.saves.SyncLog;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Platforma151 — облачные сохранения, трей и вход через сайт. Без запуска игры.
 */
public final class PlatformaApp {
    private static final int MIN_WINDOW_W = 880;
    private static final int MIN_WINDOW_H = 520;

    private final JFrame frame = new JFrame("Platforma151");
    private final JTextField urlField = new JTextField(28);
    private final JCheckBox autostartBox = new JCheckBox("Запускать при старте Windows");
    private final JTextArea log = new JTextArea(6, 52);
    private final AtomicBoolean applyingAutostartUi = new AtomicBoolean(false);
    private final AtomicBoolean updating = new AtomicBoolean(false);
    private final LauncherPaths paths;
    private final GameSaveRegistry saveRegistry;
    private final CloudSaveSyncService saveSyncService;
    private final CatalogSyncService catalogSyncService;
    private final LauncherAuthStore authStore;
    private final JLabel authStatusLabel = new JLabel("Вход не выполнен");
    private final JButton authCardBtn = new SiteButton("Войти", false);
    private final JCheckBox autoUpdateBox = new JCheckBox("Автообновление");
    private final JCheckBox autoSyncBox = new JCheckBox("Автосинхронизация (папки + обход по расписанию)");
    private final JCheckBox settingsArchiveBox = new JCheckBox("Сохранять настройки отдельным архивом");
    private final JSpinner syncIntervalSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 120, 1));
    private final JLabel settingsHashLabel = new JLabel(" ");
    private final JLabel shortcutsDirLabel = new JLabel(" ");
    private final JLabel nextUpdateLabel = new JLabel(" ");
    private final JLabel settingsArchivePathLabel = new JLabel(" ");
    private final JLabel gamesCountLabel = new JLabel("Игр в списке: 0");
    private final JTextField siteHostField = new JTextField(24);
    private final boolean startHiddenInTray;
    private TitleBar titleBar;
    private LauncherTray tray;
    private SaveManagerDialog savePanel;
    private CardLayout pagesLayout;
    private JPanel pagesPanel;
    private SingleInstance singleInstance;
    private Timer autoUpdateTimer;
    private Timer updateCountdownTimer;
    private long nextAutoUpdateAtMs;
    private Timer persistWindowTimer;

    public PlatformaApp(boolean startHiddenInTray) {
        this.startHiddenInTray = startHiddenInTray;
        this.paths = LauncherPaths.resolvePlatforma();
        this.authStore = new LauncherAuthStore(paths.appDir);
        this.saveRegistry = new GameSaveRegistry(paths.appDir);
        this.saveSyncService = new CloudSaveSyncService(
                paths.appDir,
                saveRegistry,
                this::append,
                msg -> {
                    if (tray != null && tray.isInstalled()) {
                        tray.showBalloon("Platforma151", msg);
                    }
                });
        this.catalogSyncService = new CatalogSyncService(paths.appDir, saveRegistry, this::append);
        this.catalogSyncService.setApiBaseUrl(loadApiUrl());
    }

    public static void main(String[] args) {
        Path appDir = LauncherPaths.resolvePlatforma().appDir;
        SingleInstance held = SingleInstance.acquire(appDir);
        if (held == null) {
            if (!SingleInstance.signalShow(appDir)) {
                System.err.println("Platforma151 already running");
            }
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(held::close, "platforma-instance-close"));
        boolean tray = Arrays.stream(args).anyMatch(a ->
                "--tray".equalsIgnoreCase(a) || "/tray".equalsIgnoreCase(a));
        SwingUtilities.invokeLater(() -> {
            LauncherTheme.installFontsAndDefaults();
            LauncherTheme.applyRussianFileChooserStrings();
            PlatformaApp app = new PlatformaApp(tray);
            app.singleInstance = held;
            held.setOnShow(() -> SwingUtilities.invokeLater(app::restoreFromTray));
            app.show();
        });
    }

    private void show() {
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.setUndecorated(true);
        frame.setResizable(true);
        frame.setMinimumSize(new Dimension(MIN_WINDOW_W, MIN_WINDOW_H));
        try {
            frame.setIconImage(LauncherTray.createIconImage("platforma151"));
        } catch (Exception ignored) {
        }

        JPanel shell = new MetroBackdrop();
        shell.setLayout(new BorderLayout());
        shell.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LauncherTheme.BORDER, 1),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        new EdgeResizer(frame).install(shell);

        titleBar = new TitleBar(
                frame,
                readLocalVersion(),
                () -> frame.setState(JFrame.ICONIFIED),
                this::requestClose);
        shell.add(titleBar, BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(12, 12));
        body.setOpaque(false);
        body.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        savePanel = new SaveManagerDialog(frame, saveRegistry, saveSyncService, urlField.getText());
        authStatusLabel.setFont(LauncherTheme.uiFont);
        savePanel.setAuthStatusLabel(authStatusLabel);
        savePanel.setAppInfo(readLocalVersion(), BuildHash.readLocal(paths), paths.appDir);
        savePanel.setHostSettings(() -> SwingUtilities.invokeLater(this::showSettingsPage));

        pagesLayout = new CardLayout();
        pagesPanel = new JPanel(pagesLayout);
        pagesPanel.setOpaque(false);
        pagesPanel.add(buildSavesPage(), "saves");
        pagesPanel.add(buildSettingsPage(), "settings");
        body.add(pagesPanel, BorderLayout.CENTER);
        shell.add(body, BorderLayout.CENTER);

        applySiteHostFromUrl(loadApiUrl());
        frame.setContentPane(shell);
        applySavedWindowSize();
        persistWindowTimer = new Timer(300, e -> persistWindowSize());
        persistWindowTimer.setRepeats(false);
        frame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                schedulePersistWindow();
            }

            @Override
            public void componentMoved(ComponentEvent e) {
                schedulePersistWindow();
            }
        });
        installTraySupport();
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                requestClose();
            }
        });

        boolean hide = startHiddenInTray && tray != null && tray.isInstalled();
        frame.setVisible(!hide);
        if (hide) {
            hideToTray();
            append("Запуск в трее (--tray / автозапуск).");
        }
        syncAutostartCheckbox();
        savePanel.selectFirstGameIfNone();

        refreshAuthUi();
        append("Platforma151 v" + readLocalVersion());
        append("Каталог данных: " + paths.appDir);
        append("Трей: " + (tray != null && tray.isInstalled() ? "активен (закрытие → в трей)" : "недоступен"));
        append("Автозапуск: " + (WindowsAutostart.isInstalled(WindowsAutostart.PLATFORMA_SHORTCUT) ? "включён (старт Windows)" : "выкл"));
        saveSyncService.setApiBaseUrl(loadApiUrl());
        saveSyncService.start();
        catalogSyncService.setApiBaseUrl(loadApiUrl());
        savePanel.reloadRegistryAsync();
        fetchRemoteBuildHash();
        startAutoUpdateSchedule();
        savePanel.startSettingsArchiveSchedule();
    }

    private void fetchRemoteBuildHash() {
        String base = LauncherConfig.trimSlash(urlField.getText());
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                try {
                    HttpClient http = TlsHttp.clientBuilder().connectTimeout(Duration.ofSeconds(5)).build();
                    ClientRelease release = UpdateService.fetchPlatformaRelease(http, base);
                    return release.platformaBuildHash;
                } catch (Exception e) {
                    SyncLog.warn("[update] remote hash: " + e.getMessage());
                    return null;
                }
            }

            @Override
            protected void done() {
                try {
                    String hash = get();
                    if (hash != null && !hash.isBlank() && savePanel != null) {
                        savePanel.setRemoteBuildHash(hash);
                        refreshSettingsHash();
                    }
                } catch (Exception ignored) {
                }
            }
        }.execute();
    }

    private JPanel buildPageHeader(JButton leftBtn, String titleText, JPanel rightPanel) {
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        left.add(leftBtn);
        header.add(left, BorderLayout.WEST);

        JLabel heading = new JLabel(titleText);
        heading.setForeground(LauncherTheme.TEXT);
        heading.setFont(LauncherTheme.uiFontTitle);
        heading.setHorizontalAlignment(JLabel.CENTER);
        header.add(heading, BorderLayout.CENTER);

        if (rightPanel != null) {
            header.add(rightPanel, BorderLayout.EAST);
        } else {
            // Keep left button position stable when right side is empty.
            JPanel spacer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            spacer.setOpaque(false);
            spacer.setPreferredSize(left.getPreferredSize());
            header.add(spacer, BorderLayout.EAST);
        }
        return header;
    }

    private JPanel buildSavesPage() {
        JPanel page = new JPanel(new BorderLayout(8, 8));
        page.setOpaque(false);

        JButton settingsBtn = new SiteButton("Настройки", false);
        settingsBtn.addActionListener(e -> showSettingsPage());

        page.add(buildPageHeader(settingsBtn, "Platforma151", null), BorderLayout.NORTH);
        page.add(savePanel, BorderLayout.CENTER);
        return page;
    }

    private JPanel buildSettingsPage() {
        JPanel page = new JPanel(new BorderLayout(8, 8));
        page.setOpaque(false);

        JButton backBtn = new SiteButton("Назад", false);
        backBtn.addActionListener(e -> showSavesPage());
        page.add(buildPageHeader(backBtn, "Настройки", null), BorderLayout.NORTH);

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setOpaque(true);
        form.setBackground(LauncherTheme.PANEL);
        form.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JLabel siteLbl = new JLabel("Сервер");
        siteLbl.setForeground(LauncherTheme.MUTED);
        siteLbl.setFont(LauncherTheme.uiFont);
        siteLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel siteRow = new JPanel(new BorderLayout(6, 0));
        siteRow.setOpaque(true);
        siteRow.setBackground(LauncherTheme.PANEL);
        siteRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        siteRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        JLabel httpsLbl = new JLabel("https://");
        httpsLbl.setForeground(LauncherTheme.MUTED);
        httpsLbl.setFont(LauncherTheme.uiFont);
        LauncherTheme.styleField(siteHostField);
        siteRow.add(httpsLbl, BorderLayout.WEST);
        siteRow.add(siteHostField, BorderLayout.CENTER);

        JPanel pingRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        pingRow.setOpaque(true);
        pingRow.setBackground(LauncherTheme.PANEL);
        pingRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton pingBtn = new SiteButton("Проверить связь", false);
        pingBtn.addActionListener(e -> checkConnection());
        JButton updBtn = new SiteButton("Проверить обновления", false);
        updBtn.addActionListener(e -> {
            persistSettingsFromUi();
            checkUpdates(false);
        });
        pingRow.add(pingBtn);
        pingRow.add(updBtn);

        LauncherTheme.styleCheck(autostartBox);
        autostartBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        autostartBox.addActionListener(e -> {
            if (applyingAutostartUi.get()) {
                return;
            }
            applyAutostartPreference(autostartBox.isSelected());
        });

        LauncherTheme.styleCheck(autoUpdateBox);
        autoUpdateBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        autoUpdateBox.addActionListener(e -> {
            persistSettingsFromUi();
            restartAutoUpdateSchedule();
        });
        nextUpdateLabel.setForeground(LauncherTheme.MUTED);
        nextUpdateLabel.setFont(LauncherTheme.uiFont);
        nextUpdateLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        LauncherTheme.styleCheck(autoSyncBox);
        autoSyncBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        autoSyncBox.addActionListener(e -> persistSettingsFromUi());

        JPanel intervalRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        intervalRow.setOpaque(true);
        intervalRow.setBackground(LauncherTheme.PANEL);
        intervalRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel intervalLbl = new JLabel("Интервал, мин:");
        intervalLbl.setForeground(LauncherTheme.TEXT);
        intervalLbl.setFont(LauncherTheme.uiFont);
        SpinnerNumberModel intervalModel = (SpinnerNumberModel) syncIntervalSpinner.getModel();
        syncIntervalSpinner.setVisible(false);
        JPanel stepper = LauncherTheme.horizontalStepper(intervalModel, this::persistSettingsFromUi);
        intervalRow.add(intervalLbl);
        intervalRow.add(stepper);

        settingsHashLabel.setFont(LauncherTheme.monoFont);
        settingsHashLabel.setForeground(LauncherTheme.MUTED);
        settingsHashLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        refreshSettingsHash();

        JPanel gamesRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        gamesRow.setOpaque(true);
        gamesRow.setBackground(LauncherTheme.PANEL);
        gamesRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton addGameBtn = new SiteButton("Добавить свою игру", false);
        addGameBtn.addActionListener(e -> {
            if (savePanel != null) {
                savePanel.showAddCustomGameDialog();
                refreshGamesCountLabel();
            }
        });
        JButton scanGamesBtn = new SiteButton("Сканировать игры", false);
        scanGamesBtn.addActionListener(e -> {
            catalogSyncService.setApiBaseUrl(composeApiUrlFromHostField());
            catalogSyncService.syncAsync();
            if (savePanel != null) {
                savePanel.scanSupportedGames();
                // Count updates after scan finishes asynchronously — poll shortly.
                Timer t = new Timer(800, ev -> refreshGamesCountLabel());
                t.setRepeats(false);
                t.start();
                Timer t2 = new Timer(3000, ev -> refreshGamesCountLabel());
                t2.setRepeats(false);
                t2.start();
            } else {
                refreshGamesCountLabel();
            }
        });
        gamesCountLabel.setForeground(LauncherTheme.MUTED);
        gamesCountLabel.setFont(LauncherTheme.uiFont);
        gamesRow.add(addGameBtn);
        gamesRow.add(scanGamesBtn);
        gamesRow.add(gamesCountLabel);

        LauncherTheme.styleCheck(settingsArchiveBox);
        settingsArchiveBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        settingsArchiveBox.addActionListener(e -> {
            persistSettingsFromUi();
            if (savePanel != null) {
                savePanel.startSettingsArchiveSchedule();
            }
        });
        settingsArchivePathLabel.setFont(LauncherTheme.uiFont);
        settingsArchivePathLabel.setForeground(LauncherTheme.TEXT);
        settingsArchivePathLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton archivePathBtn = new SiteButton("Папка архива настроек", false);
        archivePathBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        archivePathBtn.addActionListener(e -> chooseSettingsArchiveDir());

        form.add(siteLbl);
        form.add(Box.createVerticalStrut(4));
        form.add(siteRow);
        form.add(Box.createVerticalStrut(6));
        form.add(pingRow);
        form.add(Box.createVerticalStrut(14));
        form.add(autostartBox);
        form.add(Box.createVerticalStrut(8));
        form.add(autoUpdateBox);
        form.add(Box.createVerticalStrut(4));
        form.add(nextUpdateLabel);
        form.add(Box.createVerticalStrut(6));
        form.add(settingsHashLabel);
        form.add(Box.createVerticalStrut(14));
        form.add(autoSyncBox);
        form.add(Box.createVerticalStrut(6));
        form.add(intervalRow);
        form.add(Box.createVerticalStrut(14));
        form.add(gamesRow);
        form.add(Box.createVerticalStrut(14));
        form.add(settingsArchiveBox);
        form.add(Box.createVerticalStrut(4));
        form.add(settingsArchivePathLabel);
        form.add(Box.createVerticalStrut(6));
        form.add(archivePathBtn);

        JScrollPane formScroll = new JScrollPane(form);
        formScroll.setBorder(BorderFactory.createEmptyBorder());
        LauncherTheme.styleScroll(formScroll, LauncherTheme.PANEL);
        formScroll.setOpaque(true);
        formScroll.setBackground(LauncherTheme.PANEL);
        formScroll.getViewport().setOpaque(true);
        formScroll.getViewport().setBackground(LauncherTheme.PANEL);

        // Log takes remaining height: form NORTH, log CENTER
        JPanel settingsBody = new JPanel(new BorderLayout(0, 8));
        settingsBody.setOpaque(false);
        JPanel formCard = new HudFramePanel(false);
        formCard.setLayout(new BorderLayout());
        formCard.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        formCard.add(formScroll, BorderLayout.NORTH);
        settingsBody.add(formCard, BorderLayout.NORTH);
        settingsBody.add(buildLogPanel(), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(12, 0));
        bottom.setOpaque(false);
        bottom.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

        JPanel bottomActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        bottomActions.setOpaque(false);
        bottomActions.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LauncherTheme.BORDER, 1),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        JButton importShortcutsBtn = new SiteButton("Импорт папки ярлыков игр", false);
        importShortcutsBtn.addActionListener(e -> importShortcutsFolder());
        bottomActions.add(importShortcutsBtn);
        shortcutsDirLabel.setFont(LauncherTheme.uiFont);
        shortcutsDirLabel.setForeground(LauncherTheme.MUTED);
        bottomActions.add(shortcutsDirLabel);

        JButton dataBtn = new SiteButton("Папка данных", false);
        dataBtn.addActionListener(e -> openDataFolder());
        JButton logsBtn = new SiteButton("Открыть логи", false);
        logsBtn.addActionListener(e -> openLogsFolder());
        JButton accountBtn = new SiteButton("Аккаунт", true);
        accountBtn.addActionListener(e -> openAccountPage());
        authCardBtn.addActionListener(e -> openAuth());
        bottomActions.add(dataBtn);
        bottomActions.add(logsBtn);
        bottomActions.add(accountBtn);
        bottomActions.add(authCardBtn);

        bottom.add(bottomActions, BorderLayout.WEST);

        page.add(settingsBody, BorderLayout.CENTER);
        page.add(bottom, BorderLayout.SOUTH);
        return page;
    }

    private void showSettingsPage() {
        applySiteHostFromUrl(loadApiUrl());
        syncAutostartCheckbox();
        LauncherConfig cfg = LauncherConfig.load(paths.configPath);
        autoUpdateBox.setSelected(cfg.autoUpdate);
        CustomGameStore.SavesConfigData scfg = saveRegistry.getStore().loadConfig();
        autoSyncBox.setSelected(scfg.autoSyncEnabled);
        syncIntervalSpinner.setValue(Math.max(1, scfg.syncIntervalMinutes));
        settingsArchiveBox.setSelected(scfg.settingsArchiveEnabled);
        refreshSettingsArchivePathLabel();
        refreshSettingsHash();
        refreshShortcutsDirLabel();
        refreshNextUpdateLabel();
        refreshGamesCountLabel();
        pagesLayout.show(pagesPanel, "settings");
    }

    private void refreshGamesCountLabel() {
        int n = saveRegistry.getAllGames().size();
        gamesCountLabel.setText("Игр в списке: " + n);
    }

    private void showSavesPage() {
        persistSettingsFromUi();
        pagesLayout.show(pagesPanel, "saves");
    }

    private void persistSettingsFromUi() {
        CustomGameStore.SavesConfigData data = saveRegistry.getStore().loadConfig();
        data.autoSyncEnabled = autoSyncBox.isSelected();
        data.syncIntervalMinutes = (Integer) syncIntervalSpinner.getValue();
        data.settingsArchiveEnabled = settingsArchiveBox.isSelected();
        saveRegistry.getStore().saveConfig(data);
        String url = composeApiUrlFromHostField();
        applySiteHostFromUrl(url);
        LauncherConfig cfg = LauncherConfig.load(paths.configPath);
        cfg.apiBaseUrl = url.replace("\"", "");
        cfg.autoUpdate = autoUpdateBox.isSelected();
        cfg.startWithWindows = autostartBox.isSelected();
        copyWindowBounds(cfg);
        try {
            cfg.save(paths.configPath);
            saveSyncService.setApiBaseUrl(url);
            catalogSyncService.setApiBaseUrl(url);
            if (savePanel != null) {
                savePanel.setSiteBaseUrl(url);
            }
        } catch (Exception e) {
            append("Не удалось сохранить настройки: " + e.getMessage());
        }
    }

    private void applySiteHostFromUrl(String url) {
        String full = LauncherConfig.trimSlash(url);
        urlField.setText(full);
        String host = full;
        if (host.regionMatches(true, 0, "https://", 0, 8)) {
            host = host.substring(8);
        } else if (host.regionMatches(true, 0, "http://", 0, 7)) {
            host = host.substring(7);
        }
        siteHostField.setText(host);
    }

    private String composeApiUrlFromHostField() {
        String host = siteHostField.getText() != null ? siteHostField.getText().trim() : "";
        while (host.startsWith("/")) {
            host = host.substring(1);
        }
        if (host.regionMatches(true, 0, "https://", 0, 8) || host.regionMatches(true, 0, "http://", 0, 7)) {
            return LauncherConfig.trimSlash(host);
        }
        if (host.isEmpty()) {
            return LauncherConfig.DEFAULT_API_BASE;
        }
        return LauncherConfig.trimSlash("https://" + host);
    }

    private void startAutoUpdateSchedule() {
        stopAutoUpdateSchedule();
        if (!LauncherConfig.load(paths.configPath).autoUpdate) {
            refreshNextUpdateLabel();
            return;
        }
        checkUpdates(true);
        catalogSyncService.setApiBaseUrl(loadApiUrl());
        catalogSyncService.syncAsync();
        nextAutoUpdateAtMs = System.currentTimeMillis() + 5L * 60L * 60L * 1000L;
        autoUpdateTimer = new Timer((int) (5L * 60L * 60L * 1000L), e -> {
            if (LauncherConfig.load(paths.configPath).autoUpdate) {
                checkUpdates(true);
                catalogSyncService.setApiBaseUrl(loadApiUrl());
                catalogSyncService.syncAsync();
                nextAutoUpdateAtMs = System.currentTimeMillis() + 5L * 60L * 60L * 1000L;
            }
        });
        autoUpdateTimer.setRepeats(true);
        autoUpdateTimer.start();
        updateCountdownTimer = new Timer(1_000, e -> refreshNextUpdateLabel());
        updateCountdownTimer.setRepeats(true);
        updateCountdownTimer.start();
        refreshNextUpdateLabel();
    }

    private void stopAutoUpdateSchedule() {
        if (autoUpdateTimer != null) {
            autoUpdateTimer.stop();
            autoUpdateTimer = null;
        }
        if (updateCountdownTimer != null) {
            updateCountdownTimer.stop();
            updateCountdownTimer = null;
        }
        nextAutoUpdateAtMs = 0;
    }

    private void restartAutoUpdateSchedule() {
        startAutoUpdateSchedule();
    }

    private void refreshNextUpdateLabel() {
        if (!autoUpdateBox.isSelected() || nextAutoUpdateAtMs <= 0) {
            nextUpdateLabel.setText("Автообновление выключено");
            return;
        }
        long rem = Math.max(0L, nextAutoUpdateAtMs - System.currentTimeMillis());
        long totalSec = rem / 1000L;
        long hh = totalSec / 3600L;
        long mm = (totalSec % 3600L) / 60L;
        long ss = totalSec % 60L;
        nextUpdateLabel.setText(String.format("до следующей проверки: %02d:%02d:%02d", hh, mm, ss));
    }

    private void openLogsFolder() {
        try {
            Path dir = paths.appDir;
            Files.createDirectories(dir);
            Desktop.getDesktop().open(dir.toFile());
        } catch (Exception e) {
            append("Не удалось открыть логи: " + e.getMessage());
        }
    }

    private void chooseSettingsArchiveDir() {
        CustomGameStore.SavesConfigData data = saveRegistry.getStore().loadConfig();
        JFileChooser chooser = LauncherTheme.darkDirectoryChooser("Папка архива настроек");
        if (data.settingsArchiveDir != null && !data.settingsArchiveDir.isBlank()) {
            Path p = Path.of(data.settingsArchiveDir);
            if (Files.isDirectory(p)) {
                chooser.setCurrentDirectory(p.toFile());
            }
        }
        if (LauncherTheme.showOpenDialog(frame, chooser) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        data.settingsArchiveDir = chooser.getSelectedFile().getAbsolutePath();
        data.settingsArchiveEnabled = true;
        settingsArchiveBox.setSelected(true);
        saveRegistry.getStore().saveConfig(data);
        refreshSettingsArchivePathLabel();
        if (savePanel != null) {
            savePanel.startSettingsArchiveSchedule();
        }
    }

    private void refreshSettingsArchivePathLabel() {
        CustomGameStore.SavesConfigData data = saveRegistry.getStore().loadConfig();
        String p = data.settingsArchiveDir;
        if (p == null || p.isBlank()) {
            p = paths.appDir.resolve("settings-archive").toString();
        }
        settingsArchivePathLabel.setText(p);
    }

    private void refreshSettingsHash() {
        String hash = savePanel != null ? savePanel.displayedBuildHash() : BuildHash.readLocal(paths);
        String ver = savePanel != null ? savePanel.getAppVersion() : readLocalVersion();
        if (hash == null || hash.isBlank()) {
            hash = "—";
        }
        settingsHashLabel.setText("Сборка " + ver + " · " + hash + " · l151");
    }

    private void openAccountPage() {
        String base = loadApiUrl();
        String path = authStore.hasToken() ? "/profile" : "/login";
        try {
            Desktop.getDesktop().browse(URI.create(base + path));
        } catch (Exception e) {
            append("Не удалось открыть аккаунт: " + e.getMessage());
        }
    }

    private JPanel buildLogPanel() {
        log.setEditable(false);
        log.setOpaque(true);
        log.setBackground(LauncherTheme.SURFACE2);
        log.setForeground(LauncherTheme.LOG_FG);
        log.setCaretColor(LauncherTheme.TEXT);
        log.setFont(LauncherTheme.monoFont);
        log.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        JScrollPane scroll = new JScrollPane(log);
        scroll.setOpaque(false);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setOpaque(true);
        scroll.getViewport().setBackground(LauncherTheme.SURFACE2);
        scroll.setBackground(LauncherTheme.SURFACE2);
        scroll.getVerticalScrollBar().setUI(new DarkScrollBarUI());
        scroll.getHorizontalScrollBar().setUI(new DarkScrollBarUI());
        scroll.getVerticalScrollBar().setOpaque(true);
        scroll.getVerticalScrollBar().setBackground(LauncherTheme.SCROLL_TRACK);
        scroll.setPreferredSize(new Dimension(640, 160));

        JPanel logHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        logHeader.setOpaque(false);
        JLabel logTitle = new JLabel("Журнал");
        logTitle.setForeground(LauncherTheme.MUTED);
        logTitle.setFont(LauncherTheme.uiFontBold);
        JButton openLogsBtn = new SiteButton("Открыть логи", false);
        openLogsBtn.addActionListener(e -> openLogsFolder());
        logHeader.add(logTitle);
        logHeader.add(openLogsBtn);

        JPanel logCard = new HudFramePanel(false);
        logCard.setLayout(new BorderLayout(0, 8));
        logCard.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));
        logCard.add(logHeader, BorderLayout.NORTH);
        logCard.add(scroll, BorderLayout.CENTER);
        return logCard;
    }

    private void requestClose() {
        if (tray != null && tray.isInstalled()) {
            hideToTray();
        } else {
            exitFully();
        }
    }

    private void installTraySupport() {
        if (!LauncherTray.isSupported()) {
            return;
        }
        tray = LauncherTray.install(
                "Platforma151",
                "platforma151",
                this::restoreFromTray,
                null,
                null,
                () -> SwingUtilities.invokeLater(this::exitFully),
                this::append);
        if (tray == null) {
            append("SystemTray не удалось инициализировать — обычное закрытие.");
        }
    }

    private void hideToTray() {
        if (tray == null || !tray.isInstalled()) {
            append("Трей недоступен — окно остаётся открытым.");
            return;
        }
        persistWindowSize();
        frame.setVisible(false);
    }

    private void restoreFromTray() {
        SwingUtilities.invokeLater(() -> {
            frame.setVisible(true);
            frame.setState(JFrame.NORMAL);
            frame.setExtendedState(JFrame.NORMAL);
            frame.toFront();
            frame.requestFocus();
            if (savePanel != null) {
                savePanel.refreshCloudStatusAsync();
            }
        });
    }

    private void exitFully() {
        persistWindowSize();
        stopAutoUpdateSchedule();
        if (persistWindowTimer != null) {
            persistWindowTimer.stop();
            persistWindowTimer = null;
        }
        if (saveSyncService != null) {
            saveSyncService.stop();
        }
        if (tray != null) {
            tray.uninstall();
            tray = null;
        }
        if (singleInstance != null) {
            singleInstance.close();
            singleInstance = null;
        }
        frame.dispose();
        System.exit(0);
    }

    private void syncAutostartCheckbox() {
        boolean want = loadStartWithWindows();
        boolean have = LauncherPaths.isWindows() && WindowsAutostart.isRunValueSet(WindowsAutostart.PLATFORMA_RUN_VALUE);
        applyingAutostartUi.set(true);
        try {
            if (want && LauncherPaths.isWindows()) {
                try {
                    if (!have) {
                        installAutostartShortcut();
                        have = WindowsAutostart.isRunValueSet(WindowsAutostart.PLATFORMA_RUN_VALUE);
                        if (have) {
                            append("Автозапуск восстановлен (реестр Windows).");
                        }
                    }
                    if (!have) {
                        append("Автозапуск включён в настройках, но запись в реестре не создана.");
                        want = false;
                    }
                } catch (Exception e) {
                    append("Не удалось восстановить автозапуск: " + e.getMessage());
                    want = false;
                    have = false;
                }
            } else if (!want && (have || WindowsAutostart.isShortcutPresent(WindowsAutostart.PLATFORMA_SHORTCUT))) {
                try {
                    WindowsAutostart.remove(WindowsAutostart.PLATFORMA_SHORTCUT);
                    have = false;
                    append("Автозапуск выключен (запись удалена).");
                } catch (Exception e) {
                    append("Не удалось удалить автозапуск: " + e.getMessage());
                    want = true;
                }
            }
            autostartBox.setSelected(want && have);
            if (!LauncherPaths.isWindows()) {
                autostartBox.setEnabled(false);
                autostartBox.setToolTipText("Автозапуск доступен только на Windows");
            }
        } finally {
            applyingAutostartUi.set(false);
        }
    }

    private void applyAutostartPreference(boolean enable) {
        if (!LauncherPaths.isWindows()) {
            applyingAutostartUi.set(true);
            try {
                autostartBox.setSelected(false);
            } finally {
                applyingAutostartUi.set(false);
            }
            append("Автозапуск поддерживается только на Windows.");
            return;
        }
        try {
            if (enable) {
                installAutostartShortcut();
                append("Автозапуск включён (старт Windows).");
            } else {
                WindowsAutostart.remove(WindowsAutostart.PLATFORMA_SHORTCUT);
                append("Автозапуск выключен.");
            }
            saveConfig();
        } catch (Exception e) {
            append("Ошибка автозапуска: " + e.getMessage());
            applyingAutostartUi.set(true);
            try {
                autostartBox.setSelected(WindowsAutostart.isInstalled(WindowsAutostart.PLATFORMA_SHORTCUT));
            } finally {
                applyingAutostartUi.set(false);
            }
            saveConfig();
            JOptionPane.showMessageDialog(frame,
                    "Не удалось изменить автозапуск:\n" + e.getMessage(),
                    "Автозапуск", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void installAutostartShortcut() throws Exception {
        Path target = resolvePlatformaLaunchTarget();
        if (target == null) {
            throw new IllegalStateException("Не найден Platforma151.exe и platforma151.jar для автозапуска");
        }
        Path workDir = target.getParent();
        if (workDir != null && "app".equalsIgnoreCase(workDir.getFileName().toString())
                && workDir.getParent() != null) {
            Path exe = workDir.getParent().resolve("Platforma151.exe");
            if (Files.isRegularFile(exe)) {
                target = exe;
                workDir = exe.getParent();
            }
        }
        if (target.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".exe")) {
            WindowsAutostart.install(target, workDir, null, "--tray",
                    WindowsAutostart.PLATFORMA_SHORTCUT, "Platforma151");
        } else {
            WindowsAutostart.install(target, workDir, WindowsAutostart.findJavaw(), "--tray",
                    WindowsAutostart.PLATFORMA_SHORTCUT, "Platforma151");
        }
    }

    private Path resolvePlatformaLaunchTarget() {
        try {
            Optional<String> cmd = ProcessHandle.current().info().command();
            if (cmd.isPresent()) {
                Path exe = Path.of(cmd.get()).toAbsolutePath().normalize();
                String name = exe.getFileName() != null
                        ? exe.getFileName().toString().toLowerCase(Locale.ROOT)
                        : "";
                if (name.equals("platforma151.exe") && Files.isRegularFile(exe)) {
                    return exe;
                }
            }
        } catch (Exception ignored) {
        }
        String localApp = System.getenv("LOCALAPPDATA");
        if (localApp != null && !localApp.isBlank()) {
            Path installed = Path.of(localApp, "Programs", "Platforma151", "Platforma151.exe");
            if (Files.isRegularFile(installed)) {
                return installed.toAbsolutePath().normalize();
            }
        }
        return paths.resolveAutostartTarget(
                List.of("Platforma151.exe"),
                List.of("platforma151.jar"),
                List.of("Platforma151"));
    }

    private void append(String line) {
        SwingUtilities.invokeLater(() -> {
            log.append(line + "\n");
            log.setCaretPosition(log.getDocument().getLength());
        });
    }

    private String loadApiUrl() {
        return LauncherConfig.trimSlash(LauncherConfig.load(paths.configPath).apiBaseUrl);
    }

    private boolean loadStartWithWindows() {
        LauncherConfig cfg = LauncherConfig.load(paths.configPath);
        if (cfg.startWithWindowsExplicit) {
            return cfg.startWithWindows;
        }
        return LauncherPaths.isWindows() && WindowsAutostart.isRunValueSet(WindowsAutostart.PLATFORMA_RUN_VALUE);
    }

    private void saveConfig() {
        String url = LauncherConfig.trimSlash(urlField.getText());
        urlField.setText(url);
        LauncherConfig cfg = LauncherConfig.load(paths.configPath);
        cfg.apiBaseUrl = url.replace("\"", "");
        cfg.startWithWindows = autostartBox.isSelected();
        copyWindowBounds(cfg);
        try {
            cfg.save(paths.configPath);
            saveSyncService.setApiBaseUrl(url);
            catalogSyncService.setApiBaseUrl(url);
            if (savePanel != null) {
                savePanel.setSiteBaseUrl(url);
            }
            append("Конфиг сохранён: " + paths.configPath);
        } catch (Exception e) {
            append("Не удалось сохранить конфиг: " + e.getMessage());
        }
    }

    private void applySavedWindowSize() {
        LauncherConfig cfg = LauncherConfig.load(paths.configPath);
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int w = cfg.windowWidth > 0 ? cfg.windowWidth : 960;
        int h = cfg.windowHeight > 0 ? cfg.windowHeight : 580;
        w = Math.max(MIN_WINDOW_W, w);
        h = Math.max(MIN_WINDOW_H, h);
        w = Math.min(w, Math.max(MIN_WINDOW_W, screen.width));
        h = Math.min(h, Math.max(MIN_WINDOW_H, screen.height));
        frame.setSize(w, h);
        if (cfg.windowX >= 0 && cfg.windowY >= 0) {
            int x = Math.max(screen.x, Math.min(cfg.windowX, screen.x + screen.width - w));
            int y = Math.max(screen.y, Math.min(cfg.windowY, screen.y + screen.height - h));
            frame.setLocation(x, y);
        } else {
            frame.setLocationRelativeTo(null);
        }
    }

    private void schedulePersistWindow() {
        if (persistWindowTimer != null) {
            persistWindowTimer.restart();
        }
    }

    private void persistWindowSize() {
        if (!frame.isShowing()) {
            return;
        }
        int w = frame.getWidth();
        int h = frame.getHeight();
        if (w < MIN_WINDOW_W || h < MIN_WINDOW_H) {
            return;
        }
        LauncherConfig cfg = LauncherConfig.load(paths.configPath);
        copyWindowBounds(cfg);
        try {
            cfg.save(paths.configPath);
        } catch (Exception ignored) {
        }
    }

    private void copyWindowBounds(LauncherConfig cfg) {
        if (!frame.isShowing() && (frame.getWidth() < MIN_WINDOW_W || frame.getHeight() < MIN_WINDOW_H)) {
            return;
        }
        int w = frame.getWidth();
        int h = frame.getHeight();
        if (w < MIN_WINDOW_W || h < MIN_WINDOW_H) {
            return;
        }
        cfg.windowWidth = w;
        cfg.windowHeight = h;
        cfg.windowX = frame.getX();
        cfg.windowY = frame.getY();
    }

    private void checkUpdates(boolean silentIfUpToDate) {
        if (!updating.compareAndSet(false, true)) {
            if (!silentIfUpToDate) {
                append("Обновление уже выполняется…");
            }
            return;
        }
        String base = loadApiUrl();
        String localHash = BuildHash.readLocal(paths);
        String localVersion = readLocalVersion();
        append("Проверка обновлений: " + base + " (локально v" + localVersion
                + (localHash != null ? " / " + localHash : "") + ") …");
        SyncLog.info("[update] check v" + localVersion + (localHash != null ? " / " + localHash : ""));
        new SwingWorker<String, String>() {
            @Override
            protected String doInBackground() throws Exception {
                HttpClient http = TlsHttp.clientBuilder().connectTimeout(Duration.ofSeconds(8)).build();
                ClientRelease release = UpdateService.fetchPlatformaRelease(http, base);
                String remoteHash = release.platformaBuildHash;
                String remoteVersion = release.platformaVersion;
                if (remoteVersion == null || remoteVersion.isBlank()) {
                    // Do not use bunker game version for Platforma decisions.
                    remoteVersion = null;
                }
                if (remoteHash != null && !remoteHash.isBlank() && savePanel != null) {
                    SwingUtilities.invokeLater(() -> {
                        savePanel.setRemoteBuildHash(remoteHash);
                        refreshSettingsHash();
                    });
                }

                boolean bothHashesKnown = remoteHash != null && !remoteHash.isBlank()
                        && localHash != null && !localHash.isBlank();
                boolean hashMismatch = bothHashesKnown && !remoteHash.equalsIgnoreCase(localHash);
                boolean remoteNewer = remoteVersion != null
                        && UpdateService.isNewerVersion(remoteVersion, localVersion);
                boolean localNewer = remoteVersion != null
                        && UpdateService.compareVersions(remoteVersion, localVersion) < 0;
                boolean sameVersion = remoteVersion != null
                        && UpdateService.isSameVersion(remoteVersion, localVersion);

                // Primary gate: semantic version. Hash-only rebuild when BOTH hashes known and differ.
                // Missing local hash + same version must NOT force reinstall (Setup never embeds hash).
                boolean needUpdate;
                String reason;
                if (remoteVersion == null || remoteVersion.isBlank()) {
                    needUpdate = hashMismatch;
                    reason = needUpdate ? "сборка " + remoteHash : "нет platforma_version, hash совпал или локальный неизвестен";
                } else if (localNewer) {
                    needUpdate = false;
                    reason = "локальная v" + localVersion + " новее серверной v" + remoteVersion;
                } else if (remoteNewer) {
                    needUpdate = true;
                    reason = "сервер v" + remoteVersion + " > локальная v" + localVersion;
                } else if (sameVersion && hashMismatch) {
                    needUpdate = true;
                    reason = "та же v" + localVersion + ", другая сборка " + remoteHash;
                } else {
                    needUpdate = false;
                    reason = "актуально v" + localVersion
                            + (localHash != null ? " (" + localHash + ")" : "");
                }

                if (!needUpdate) {
                    if (remoteHash != null && !remoteHash.isBlank()) {
                        BuildHash.writeLocal(paths, remoteHash);
                    }
                    SyncLog.info("[update] up-to-date — " + reason);
                    return silentIfUpToDate
                            ? "Platforma151 актуальна — " + reason + "."
                            : "Обновлений нет — " + reason + ".";
                }
                SyncLog.info("[update] need update — " + reason);
                boolean installed = isInstalledAppImage();
                String relUrl = installed ? release.platformaInstallerUrl : release.platformaJarUrl;
                String sha = installed ? release.platformaInstallerSha256 : release.platformaJarSha256;
                long size = installed ? release.platformaInstallerSize : release.platformaJarSize;
                if (relUrl == null || relUrl.isBlank()) {
                    throw new IllegalStateException("На сервере нет файла обновления Platforma151");
                }
                String downloadUrl = relUrl.startsWith("http")
                        ? relUrl
                        : base + (relUrl.startsWith("/") ? relUrl : "/" + relUrl);
                Path destDir = paths.appDir.resolve("updates");
                Files.createDirectories(destDir);
                String fileName = installed ? "Platforma151-Setup.exe" : "platforma151.jar";
                Path partial = destDir.resolve(fileName + ".part");
                Path target = destDir.resolve(fileName);
                publish("Обновление: " + reason);
                publish("Скачивание: " + downloadUrl);
                UpdateService.downloadFile(http, downloadUrl, partial, size, this::publish);
                if (sha != null && !sha.isBlank()) {
                    String got = UpdateService.sha256Hex(partial);
                    if (!sha.equalsIgnoreCase(got)) {
                        Files.deleteIfExists(partial);
                        throw new IllegalStateException("SHA-256 не совпал");
                    }
                    publish("SHA-256 OK");
                }
                Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
                if (remoteHash != null && !remoteHash.isBlank()) {
                    BuildHash.writeLocal(paths, remoteHash);
                }
                if (installed) {
                    publish("Запуск установщика…");
                    new ProcessBuilder(target.toAbsolutePath().toString()).start();
                    String tag = remoteVersion != null ? ("v" + remoteVersion) : remoteHash;
                    return "Скачано обновление " + tag + ". Завершите работу и пройдите установщик.";
                }
                Path self = paths.selfJarDir();
                if (self != null) {
                    try {
                        Files.copy(target, self.resolve("platforma151.jar"), StandardCopyOption.REPLACE_EXISTING);
                    } catch (Exception e) {
                        publish("Не удалось заменить запущенный JAR: " + e.getMessage());
                    }
                }
                String tag = remoteVersion != null ? ("v" + remoteVersion) : remoteHash;
                return "Скачан platforma151.jar (" + tag + "). Перезапустите приложение.";
            }

            @Override
            protected void process(List<String> chunks) {
                for (String line : chunks) {
                    append(line);
                }
            }

            @Override
            protected void done() {
                updating.set(false);
                try {
                    String msg = get();
                    append(msg);
                    SyncLog.info("[update] " + msg);
                } catch (Exception e) {
                    String err = rootMessage(e);
                    append("Обновление не удалось: " + err);
                    SyncLog.error("[update] failed: " + err, e);
                }
            }
        }.execute();
    }

    private boolean isInstalledAppImage() {
        Path install = paths.installRoot();
        if (install == null) {
            return false;
        }
        return Files.isRegularFile(install.resolve("Platforma151.exe"));
    }

    private void checkConnection() {
        saveConfig();
        String base = LauncherConfig.trimSlash(urlField.getText());
        append("Проверка " + base + "/api/auth/captcha …");
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                HttpClient http = TlsHttp.clientBuilder().connectTimeout(Duration.ofSeconds(5)).build();
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(base + "/api/auth/captcha"))
                        .timeout(Duration.ofSeconds(8))
                        .header("Accept", "application/json")
                        .GET()
                        .build();
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                return "HTTP " + resp.statusCode() + " · " + resp.body().substring(0, Math.min(120, resp.body().length()));
            }

            @Override
            protected void done() {
                try {
                    append("OK: " + get());
                } catch (Exception e) {
                    append("Связь не удалась: " + rootMessage(e));
                }
            }
        }.execute();
    }

    private String readLocalVersion() {
        List<Path> candidates = new ArrayList<>();
        Path install = paths.installRoot();
        // Prefer install image over AppData so a stale AppData version.txt cannot force updates.
        if (isInstalledAppImage() && install != null) {
            candidates.add(install.resolve("version.txt"));
            candidates.add(install.resolve("app").resolve("version.txt"));
        }
        if (paths.selfJarDir() != null) {
            candidates.add(paths.selfJarDir().resolve("version.txt"));
        }
        if (install != null) {
            candidates.add(install.resolve("version.txt"));
            candidates.add(install.resolve("app").resolve("version.txt"));
        }
        candidates.add(paths.versionPath);
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
        return "1.0";
    }

    private void refreshAuthUi() {
        authStore.load();
        SwingUtilities.invokeLater(() -> {
            if (authStore.hasToken()) {
                String name = authStore.getUserName();
                if (name == null || name.isBlank()) {
                    name = "Аккаунт #" + authStore.getUserId();
                }
                authStatusLabel.setText(name + " (онлайн)");
                authStatusLabel.setForeground(new Color(0x60, 0xd0, 0x70));
                authCardBtn.setText("Выйти");
            } else {
                authStatusLabel.setText("Вход не выполнен");
                authStatusLabel.setForeground(LauncherTheme.MUTED);
                authCardBtn.setText("Войти");
            }
            if (savePanel != null) {
                savePanel.refreshCloudStatusAsync();
            }
        });
    }

    private void openAuth() {
        saveConfig();
        String base = LauncherConfig.trimSlash(urlField.getText());
        if (authStore.hasToken()) {
            int res = JOptionPane.showConfirmDialog(
                    frame,
                    "Вы авторизованы как: " + authStore.getUserName() + " (ID: " + authStore.getUserId() + ")\nВыйти из аккаунта на этом компьютере?",
                    "Управление аккаунтом",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);
            if (res == JOptionPane.YES_OPTION) {
                LauncherBrowserAuth.revoke(authStore, base);
                append("Вы вышли из аккаунта.");
                refreshAuthUi();
            }
            return;
        }
        LauncherBrowserAuth.signIn(frame, authStore, base, "platforma", () -> {
            append("Успешная авторизация: " + authStore.getUserName() + " (ID: " + authStore.getUserId() + ")");
            refreshAuthUi();
        });
    }

    private void openDataFolder() {
        try {
            Desktop.getDesktop().open(paths.appDir.toFile());
        } catch (Exception e) {
            append("Не удалось открыть папку: " + e.getMessage());
        }
    }

    private void refreshShortcutsDirLabel() {
        Path dir = saveRegistry.getShortcutsDir();
        if (dir == null) {
            dir = DesktopGamesScanner.defaultGamesDir();
        }
        shortcutsDirLabel.setText(dir != null ? dir.toString() : "(не задано)");
        shortcutsDirLabel.setToolTipText(shortcutsDirLabel.getText());
    }

    private void importShortcutsFolder() {
        JFileChooser chooser = LauncherTheme.darkDirectoryChooser("Импорт папки ярлыков игр");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        Path current = saveRegistry.getShortcutsDir();
        if (current == null) {
            current = DesktopGamesScanner.defaultGamesDir();
        }
        if (current != null && Files.isDirectory(current)) {
            chooser.setCurrentDirectory(current.toFile());
        }
        if (LauncherTheme.showOpenDialog(frame, chooser) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path chosen = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
        if (savePanel != null) {
            savePanel.importShortcutsFolder(chosen);
            showSavesPage();
        } else {
            saveRegistry.setShortcutsDir(chosen);
            append("Папка ярлыков: " + chosen);
        }
        refreshShortcutsDirLabel();
    }

    private static String rootMessage(Exception e) {
        Throwable t = e;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t.getMessage() != null ? t.getMessage() : e.toString();
    }
}
