package com.bunker.survival.launcher.saves;

import com.bunker.survival.launcher.DarkScrollBarUI;
import com.bunker.survival.launcher.HudFramePanel;
import com.bunker.survival.launcher.LauncherConfig;
import com.bunker.survival.launcher.LauncherTheme;
import com.bunker.survival.launcher.MetroBackdrop;
import com.bunker.survival.launcher.SiteButton;
import com.bunker.survival.launcher.SiteProgressBarUI;
import com.bunker.survival.launcher.WindowDrag;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SaveManagerDialog extends JPanel {
    private final JFrame parent;
    private final GameSaveRegistry registry;
    private final CloudSaveSyncService syncService;
    private final GameSettingsSyncService settingsSyncService;
    private String siteBaseUrl;

    private final DefaultListModel<GameSaveDefinition> listModel = new DefaultListModel<>();
    private final JList<GameSaveDefinition> gameList = new JList<>(listModel);
    private final JTextField searchField = new JTextField(16);

    // Detail panel components
    private final JLabel gameTitleLabel = new JLabel("Выберите игру");
    private final JLabel gameIconLabel = new JLabel();
    private final JLabel gameTypeBadge = new JLabel();
    private final JTextField pathField = new JTextField();
    private final JTextField installPathField = new JTextField();
    private final JTextField settingsPathField = new JTextField();
    private final JButton browsePathBtn = new SiteButton("Обзор…", false);
    private final JButton browseSettingsBtn = new SiteButton("Конфиги…", false);
    private final JButton openExplorerBtn = new SiteButton("В папку", false);
    private final JButton resetPathBtn = new SiteButton("Сброс", false);

    private final JLabel syncStatusLabel = new JLabel("Статус: неизвестно");
    private final JLabel syncDateLabel = new JLabel("Последняя синхронизация: —");
    private final JLabel shaLabel = new JLabel("SHA-256: —");

    private final DefaultTableModel filesTableModel = new DefaultTableModel(new Object[]{"Имя файла", "Размер", "Дата изменения"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable filesTable = new JTable(filesTableModel);

    private final JButton openInBrowserBtn = new SiteButton("В браузере", false);
    private final JButton syncGameBtn = new SiteButton("Синхронизировать в облако", false);
    private final JButton restoreGameBtn = new SiteButton("Восстановить из облака", false);
    private final JButton launchGameBtn = new SiteButton("Запустить игру", true);
    private final JButton uploadSettingsBtn = new SiteButton("Сохранить настройки", false);
    private final JButton restoreSettingsBtn = new SiteButton("Восстановить настройки", false);
    private final JButton deleteCustomGameBtn = new SiteButton("Удалить", false);

    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JLabel progressPercentLabel = new JLabel(" ");
    private final JLabel progressLabel = new JLabel("Готово");
    private final JLabel gamesCountFooter = new JLabel("Всего: 0 · Синхронизировано: 0");
    private final JLabel quotaLabel = new JLabel("Квота: —");
    private final JLabel lastSyncGlobalLabel = new JLabel("Последняя синхронизация: —");
    private final JProgressBar quotaBar = new JProgressBar(0, 1000);
    private final JLabel buildHashLabel = new JLabel(" ");
    private JSplitPane splitPane;
    private String appVersion = "1.0";
    private String localBuildHash;
    private String remoteBuildHash;
    private Path appDataDir;
    private SettingsArchiveService settingsArchiveService;
    private Timer searchDebounceTimer;
    private String cachedFilterQuery;
    private List<GameSaveDefinition> cachedFilterResult = List.of();
    private final Map<String, StatusKind> statusCache = new ConcurrentHashMap<>();
    private int cachedSyncedCount = -1;
    private int cachedStatusTotal = -1;
    private boolean cloudStatusRunning;
    private boolean cloudStatusQueued;
    private int filesTableLoadSeq;
    private JLabel authStatusLabel;
    private JPanel topBarAuthSlot;

    public SaveManagerDialog(JFrame parent, GameSaveRegistry registry, CloudSaveSyncService syncService, String siteBaseUrl) {
        this.parent = parent;
        this.registry = registry;
        this.syncService = syncService;
        this.settingsSyncService = new GameSettingsSyncService(registry.getStore().getConfigDir(), s -> {});
        this.siteBaseUrl = siteBaseUrl != null ? siteBaseUrl : LauncherConfig.DEFAULT_API_BASE;
        this.settingsSyncService.setApiBaseUrl(this.siteBaseUrl);
        setOpaque(false);
        setLayout(new BorderLayout());
        initUi();
        filterList();
        syncService.setOnQuotaChanged(() -> SwingUtilities.invokeLater(this::refreshCloudStatusAsync));
    }

    public void setSiteBaseUrl(String url) {
        if (url != null && !url.isBlank()) {
            this.siteBaseUrl = url;
            settingsSyncService.setApiBaseUrl(url);
        }
    }

    /** Auth status label shown in the top bar (left-aligned with action buttons). */
    public void setAuthStatusLabel(JLabel label) {
        this.authStatusLabel = label;
        if (topBarAuthSlot != null && label != null) {
            topBarAuthSlot.removeAll();
            label.setAlignmentX(Component.LEFT_ALIGNMENT);
            topBarAuthSlot.add(label);
            topBarAuthSlot.revalidate();
            topBarAuthSlot.repaint();
        }
    }

    public void setAppInfo(String version, String buildHash, Path appDir) {
        if (version != null && !version.isBlank()) {
            this.appVersion = version;
        }
        this.localBuildHash = buildHash;
        this.appDataDir = appDir;
        refreshBuildHashLabel();
        if (appDir != null) {
            this.settingsArchiveService = new SettingsArchiveService(
                    registry, appDir.resolve("settings-archive"), msg -> setBusy(false, msg));
        }
    }

    public void startSettingsArchiveSchedule() {
        if (settingsArchiveService != null) {
            settingsArchiveService.start();
        }
    }

    public void showAddCustomGameDialog() {
        openAddCustomGameDialog();
    }

    public interface HostSettings {
        void showSettings();
    }

    private HostSettings hostSettings;

    public void setHostSettings(HostSettings hostSettings) {
        this.hostSettings = hostSettings;
    }

    public void setRemoteBuildHash(String hash) {
        this.remoteBuildHash = hash;
        refreshBuildHashLabel();
    }

    private void refreshBuildHashLabel() {
        String hash = localBuildHash;
        if (hash == null || hash.isBlank()) {
            hash = remoteBuildHash;
        }
        if (hash == null || hash.isBlank()) {
            hash = "—";
        }
        buildHashLabel.setText("Platforma151 " + hash);
    }

    public String displayedBuildHash() {
        String hash = localBuildHash;
        if (hash == null || hash.isBlank()) {
            hash = remoteBuildHash;
        }
        if (hash == null || hash.isBlank()) {
            return "—";
        }
        return hash;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public Path getAppDataDir() {
        return appDataDir;
    }

    private void openSettings() {
        if (hostSettings != null) {
            hostSettings.showSettings();
        }
    }

    public void refreshQuota() {
        applyQuotaInfo(syncService.fetchQuotaInfo());
    }

    /** Quota + remote-save keys off the EDT so the window can appear immediately. */
    public void refreshCloudStatusAsync() {
        if (cloudStatusRunning) {
            cloudStatusQueued = true;
            return;
        }
        cloudStatusRunning = true;
        new SwingWorker<CloudSaveSyncService.QuotaInfo, Void>() {
            @Override
            protected CloudSaveSyncService.QuotaInfo doInBackground() {
                syncService.refreshRemoteSaveKeys();
                return syncService.fetchQuotaInfo();
            }

            @Override
            protected void done() {
                CloudSaveSyncService.QuotaInfo q = CloudSaveSyncService.QuotaInfo.unknown();
                try {
                    CloudSaveSyncService.QuotaInfo got = get();
                    if (got != null) {
                        q = got;
                    }
                } catch (Exception ignored) {
                }
                applyQuotaInfo(q);
                GameSaveDefinition sel = gameList.getSelectedValue();
                if (sel != null) {
                    openInBrowserBtn.setEnabled(syncService.hasRemoteSave(sel.getKey()));
                }
                invalidateSearchAndStatusCache();
                gameList.repaint();
                updateGamesCountFooter();
                cloudStatusRunning = false;
                if (cloudStatusQueued) {
                    cloudStatusQueued = false;
                    refreshCloudStatusAsync();
                }
            }
        }.execute();
    }

    public void reloadRegistryAsync() {
        progressBar.setIndeterminate(true);
        progressLabel.setText("Загрузка списка игр…");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                registry.reload();
                return null;
            }

            @Override
            protected void done() {
                progressBar.setIndeterminate(false);
                progressLabel.setText("Готово");
                try {
                    get();
                } catch (Exception ignored) {
                }
                filterList();
                selectFirstGameIfNone();
                syncService.refreshWatches();
            }
        }.execute();
    }

    public void selectFirstGameIfNone() {
        if (!listModel.isEmpty() && gameList.getSelectedIndex() < 0) {
            gameList.setSelectedIndex(0);
        }
    }

    private void applyQuotaInfo(CloudSaveSyncService.QuotaInfo q) {
        String last = q.lastSyncedAt != null && !q.lastSyncedAt.isBlank()
                ? q.lastSyncedAt
                : syncService.latestLocalSyncAt();
        lastSyncGlobalLabel.setText("Последняя синхронизация: " + (last != null ? last : "—"));
        if (!q.known()) {
            quotaLabel.setText("Квота: войдите, чтобы увидеть лимит");
            quotaBar.setValue(0);
            quotaBar.setToolTipText(null);
            return;
        }
        quotaLabel.setText("Квота: " + CloudSaveSyncService.formatSize(q.used)
                + " / " + CloudSaveSyncService.formatSize(q.limit));
        int permille = q.limit > 0 ? (int) Math.min(1000, Math.round(q.used * 1000.0 / q.limit)) : 0;
        quotaBar.setValue(permille);
        quotaBar.setToolTipText(q.remaining + " байт свободно");
    }

    private void initUi() {
        JPanel root = new MetroBackdrop();
        root.setLayout(new BorderLayout(10, 10));
        root.setBorder(BorderFactory.createEmptyBorder(4, 2, 2, 2));

        JPanel north = new JPanel(new BorderLayout(8, 8));
        north.setOpaque(false);
        north.add(buildTopBar(), BorderLayout.CENTER);
        north.add(buildQuotaBar(), BorderLayout.SOUTH);
        root.add(north, BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildLeftPanel(), buildRightPanel());
        CustomGameStore.SavesConfigData cfg = registry.getStore().loadConfig();
        int divider = cfg.splitDividerLocation > 80 ? cfg.splitDividerLocation : 360;
        split.setContinuousLayout(true);
        LauncherTheme.styleSplitInvisibleResizable(split);
        split.setDividerLocation(divider);
        split.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, evt -> {
            CustomGameStore.SavesConfigData data = registry.getStore().loadConfig();
            data.splitDividerLocation = split.getDividerLocation();
            registry.getStore().saveConfig(data);
        });
        this.splitPane = split;
        root.add(split, BorderLayout.CENTER);

        JPanel bottomBar = buildBottomBar();
        root.add(bottomBar, BorderLayout.SOUTH);

        add(root, BorderLayout.CENTER);
    }

    private JPanel buildQuotaBar() {
        JPanel bar = new JPanel(new BorderLayout(10, 4));
        bar.setOpaque(false);
        quotaLabel.setFont(LauncherTheme.uiFontBold);
        quotaLabel.setForeground(LauncherTheme.TEXT);
        lastSyncGlobalLabel.setFont(LauncherTheme.uiFont);
        lastSyncGlobalLabel.setForeground(LauncherTheme.MUTED);
        quotaBar.setUI(new SiteProgressBarUI());
        quotaBar.setStringPainted(false);
        quotaBar.setOpaque(false);
        quotaBar.setBorderPainted(false);
        quotaBar.setPreferredSize(new Dimension(200, 10));
        JPanel labels = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        labels.setOpaque(false);
        labels.add(quotaLabel);
        labels.add(lastSyncGlobalLabel);
        bar.add(labels, BorderLayout.WEST);
        bar.add(quotaBar, BorderLayout.CENTER);
        return bar;
    }

    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout(12, 4));
        bar.setOpaque(false);

        JPanel west = new JPanel();
        west.setLayout(new BoxLayout(west, BoxLayout.Y_AXIS));
        west.setOpaque(false);

        JLabel subheader = new JLabel("Резервные копии сейвов");
        subheader.setFont(LauncherTheme.uiFont);
        subheader.setForeground(LauncherTheme.MUTED);
        subheader.setAlignmentX(Component.LEFT_ALIGNMENT);
        west.add(subheader);

        topBarAuthSlot = new JPanel();
        topBarAuthSlot.setLayout(new BoxLayout(topBarAuthSlot, BoxLayout.Y_AXIS));
        topBarAuthSlot.setOpaque(false);
        topBarAuthSlot.setAlignmentX(Component.LEFT_ALIGNMENT);
        west.add(Box.createVerticalStrut(2));
        west.add(topBarAuthSlot);
        if (authStatusLabel != null) {
            authStatusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            topBarAuthSlot.add(authStatusLabel);
        }

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        actions.setOpaque(false);

        JButton syncAllBtn = new SiteButton("Синхронизировать все", false);
        syncAllBtn.addActionListener(e -> performSyncAll());

        JButton saveAllBtn = new SiteButton("Сохранить все", false);
        saveAllBtn.addActionListener(e -> performDownloadAll());

        JButton openSiteBtn = new SiteButton("Открыть на сайте", false);
        openSiteBtn.addActionListener(e -> openWebsiteSaves());

        JButton scanBtn = new SiteButton("Сканировать игры", false);
        scanBtn.setToolTipText("Каталог + диски (Games, Steam, Program Files, Epic) и ярлыки");
        scanBtn.addActionListener(e -> scanSupportedGames());

        actions.add(scanBtn);
        actions.add(saveAllBtn);
        actions.add(syncAllBtn);
        actions.add(openSiteBtn);

        bar.add(west, BorderLayout.NORTH);
        bar.add(actions, BorderLayout.CENTER);

        return bar;
    }

    private JPanel buildLeftPanel() {
        JPanel left = new HudFramePanel(false);
        left.setLayout(new BorderLayout(8, 8));
        left.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel searchWrap = new JPanel(new BorderLayout(6, 6));
        searchWrap.setOpaque(false);
        JLabel searchLbl = new JLabel("Поиск:");
        searchLbl.setFont(LauncherTheme.uiFontBold);
        searchLbl.setForeground(LauncherTheme.MUTED);
        searchWrap.add(searchLbl, BorderLayout.WEST);

        searchField.setFont(LauncherTheme.uiFont);
        LauncherTheme.styleField(searchField);
        searchField.addCaretListener(e -> scheduleFilterList());
        searchWrap.add(searchField, BorderLayout.CENTER);
        left.add(searchWrap, BorderLayout.NORTH);

        gameList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        LauncherTheme.styleList(gameList);
        gameList.setCellRenderer(new GameListCellRenderer());
        gameList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onGameSelected(gameList.getSelectedValue());
            }
        });

        JScrollPane scroll = new JScrollPane(gameList);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        LauncherTheme.styleScroll(scroll);
        left.add(scroll, BorderLayout.CENTER);

        gamesCountFooter.setFont(LauncherTheme.uiFont);
        gamesCountFooter.setForeground(LauncherTheme.MUTED);
        gamesCountFooter.setBorder(BorderFactory.createEmptyBorder(4, 2, 0, 2));
        left.add(gamesCountFooter, BorderLayout.SOUTH);

        WindowDrag.install(parent, left, searchWrap);
        return left;
    }

    private JPanel buildRightPanel() {
        JPanel right = new HudFramePanel(false);
        right.setLayout(new BorderLayout(10, 10));
        right.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));

        // Details top
        JPanel topDetails = new JPanel(new BorderLayout(12, 8));
        topDetails.setOpaque(false);

        JPanel headerRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        headerRow.setOpaque(false);
        gameIconLabel.setPreferredSize(new Dimension(48, 48));
        headerRow.add(gameIconLabel);

        JPanel titleMeta = new JPanel();
        titleMeta.setLayout(new BoxLayout(titleMeta, BoxLayout.Y_AXIS));
        titleMeta.setOpaque(false);
        gameTitleLabel.setFont(LauncherTheme.uiFontTitle);
        gameTitleLabel.setForeground(LauncherTheme.TEXT);

        gameTypeBadge.setFont(LauncherTheme.uiFontBold);
        gameTypeBadge.setForeground(LauncherTheme.ACCENT);

        titleMeta.add(gameTitleLabel);
        titleMeta.add(gameTypeBadge);
        headerRow.add(titleMeta);
        topDetails.add(headerRow, BorderLayout.NORTH);

        // Path form
        JPanel pathPanel = new JPanel(new GridBagLayout());
        pathPanel.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.fill = GridBagConstraints.HORIZONTAL;

        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 0;
        JLabel pathLbl = new JLabel("Папка сейва:");
        pathLbl.setFont(LauncherTheme.uiFontBold);
        pathLbl.setForeground(LauncherTheme.MUTED);
        pathPanel.add(pathLbl, c);

        c.gridx = 1;
        c.weightx = 1.0;
        pathField.setFont(LauncherTheme.monoFont);
        LauncherTheme.styleField(pathField);
        pathField.setEditable(false);
        pathPanel.add(pathField, c);

        c.gridx = 2;
        c.weightx = 0;
        JPanel pathBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        pathBtns.setOpaque(false);
        browsePathBtn.addActionListener(e -> browseCustomPath());
        openExplorerBtn.addActionListener(e -> openInExplorer());
        resetPathBtn.addActionListener(e -> resetToDefaultPath());
        pathBtns.add(browsePathBtn);
        pathBtns.add(openExplorerBtn);
        pathBtns.add(resetPathBtn);
        pathPanel.add(pathBtns, c);

        c.gridx = 0;
        c.gridy = 1;
        c.weightx = 0;
        JLabel installLbl = new JLabel("Папка игры:");
        installLbl.setFont(LauncherTheme.uiFontBold);
        installLbl.setForeground(LauncherTheme.MUTED);
        pathPanel.add(installLbl, c);
        c.gridx = 1;
        c.gridwidth = 2;
        c.weightx = 1;
        installPathField.setFont(LauncherTheme.monoFont);
        LauncherTheme.styleField(installPathField);
        installPathField.setEditable(false);
        pathPanel.add(installPathField, c);

        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 1;
        c.weightx = 0;
        JLabel settingsLbl = new JLabel("Конфиги:");
        settingsLbl.setFont(LauncherTheme.uiFontBold);
        settingsLbl.setForeground(LauncherTheme.MUTED);
        pathPanel.add(settingsLbl, c);
        c.gridx = 1;
        c.weightx = 1;
        settingsPathField.setFont(LauncherTheme.monoFont);
        LauncherTheme.styleField(settingsPathField);
        settingsPathField.setEditable(false);
        pathPanel.add(settingsPathField, c);
        c.gridx = 2;
        c.weightx = 0;
        browseSettingsBtn.addActionListener(e -> browseSettingsPaths());
        pathPanel.add(browseSettingsBtn, c);

        // Status metadata
        c.gridx = 0;
        c.gridy = 3;
        c.gridwidth = 3;
        JPanel statusBox = new JPanel();
        statusBox.setLayout(new BoxLayout(statusBox, BoxLayout.Y_AXIS));
        statusBox.setOpaque(false);
        syncStatusLabel.setFont(LauncherTheme.uiFont);
        syncStatusLabel.setForeground(LauncherTheme.TEXT);
        syncStatusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        syncDateLabel.setFont(LauncherTheme.uiFont);
        syncDateLabel.setForeground(LauncherTheme.MUTED);
        syncDateLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        shaLabel.setFont(LauncherTheme.monoFont);
        shaLabel.setForeground(LauncherTheme.MUTED);
        shaLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        statusBox.add(syncStatusLabel);
        statusBox.add(syncDateLabel);
        statusBox.add(shaLabel);
        pathPanel.add(statusBox, c);

        topDetails.add(pathPanel, BorderLayout.CENTER);
        right.add(topDetails, BorderLayout.NORTH);

        // Center files table
        JPanel tablePanel = new JPanel(new BorderLayout(4, 4));
        tablePanel.setOpaque(false);
        JLabel tableTitle = new JLabel("Файлы в сохранении:");
        tableTitle.setFont(LauncherTheme.uiFontBold);
        tableTitle.setForeground(LauncherTheme.MUTED);
        tablePanel.add(tableTitle, BorderLayout.NORTH);

        filesTable.setFont(LauncherTheme.monoFont);
        filesTable.setRowHeight(22);
        LauncherTheme.styleTable(filesTable);
        filesTable.getTableHeader().setFont(LauncherTheme.uiFontBold);

        LauncherTheme.DarkTableCellRenderer rightRenderer =
                new LauncherTheme.DarkTableCellRenderer(JLabel.RIGHT);
        filesTable.getColumnModel().getColumn(1).setCellRenderer(rightRenderer);
        filesTable.getColumnModel().getColumn(2).setCellRenderer(rightRenderer);

        JScrollPane tableScroll = new JScrollPane(filesTable);
        tableScroll.setBorder(BorderFactory.createEmptyBorder());
        LauncherTheme.styleScroll(tableScroll);
        tablePanel.add(tableScroll, BorderLayout.CENTER);
        right.add(tablePanel, BorderLayout.CENTER);

        // Actions bottom
        JPanel actionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        actionsPanel.setOpaque(false);

        syncGameBtn.addActionListener(e -> performSyncSelected());
        restoreGameBtn.addActionListener(e -> performRestoreSelected());
        launchGameBtn.addActionListener(e -> launchSelectedGame());
        uploadSettingsBtn.addActionListener(e -> performUploadSettings());
        restoreSettingsBtn.addActionListener(e -> performRestoreSettings());
        deleteCustomGameBtn.addActionListener(e -> performDeleteSelected());
        openInBrowserBtn.addActionListener(e -> openSelectedGameInBrowser());

        for (JButton b : List.of(openInBrowserBtn, launchGameBtn, syncGameBtn, restoreGameBtn, uploadSettingsBtn, restoreSettingsBtn, deleteCustomGameBtn)) {
            Dimension pref = b.getPreferredSize();
            b.setPreferredSize(new Dimension(pref.width, SiteButton.PREFERRED_HEIGHT));
            b.setMaximumSize(new Dimension(Integer.MAX_VALUE, SiteButton.PREFERRED_HEIGHT));
            b.setMinimumSize(new Dimension(pref.width, SiteButton.PREFERRED_HEIGHT));
        }

        actionsPanel.add(openInBrowserBtn);
        actionsPanel.add(deleteCustomGameBtn);
        actionsPanel.add(restoreSettingsBtn);
        actionsPanel.add(uploadSettingsBtn);
        actionsPanel.add(restoreGameBtn);
        actionsPanel.add(syncGameBtn);
        actionsPanel.add(launchGameBtn);
        right.add(actionsPanel, BorderLayout.SOUTH);

        return right;
    }

    private JPanel buildBottomBar() {
        JPanel bar = new JPanel(new BorderLayout(10, 2));
        bar.setOpaque(false);
        bar.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 4));

        progressBar.setUI(new SiteProgressBarUI());
        progressBar.setStringPainted(false);
        progressBar.setVisible(true);
        progressBar.setIndeterminate(false);
        progressBar.setValue(0);
        progressBar.setOpaque(false);
        progressBar.setBorderPainted(false);
        progressBar.setPreferredSize(new Dimension(200, 5));

        progressPercentLabel.setFont(LauncherTheme.uiFontBold);
        progressPercentLabel.setForeground(LauncherTheme.MUTED);
        progressPercentLabel.setText(" ");
        progressPercentLabel.setPreferredSize(new Dimension(36, progressPercentLabel.getFont().getSize() + 4));
        progressPercentLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        progressLabel.setFont(LauncherTheme.uiFont);
        progressLabel.setForeground(LauncherTheme.MUTED);
        progressLabel.setText("Готово");
        progressLabel.setHorizontalAlignment(SwingConstants.LEFT);

        JPanel labels = new JPanel(new BorderLayout(10, 0));
        labels.setOpaque(false);
        labels.add(progressLabel, BorderLayout.CENTER);
        labels.add(progressPercentLabel, BorderLayout.EAST);

        // Same layout as top quota bar: labels west, long bar stretching in center.
        bar.add(labels, BorderLayout.WEST);
        bar.add(progressBar, BorderLayout.CENTER);

        return bar;
    }

    private void refreshList() {
        registry.reload();
        invalidateSearchAndStatusCache();
        filterList();
    }

    private void invalidateSearchAndStatusCache() {
        cachedFilterQuery = null;
        cachedFilterResult = List.of();
        statusCache.clear();
        cachedSyncedCount = -1;
        cachedStatusTotal = -1;
    }

    private void scheduleFilterList() {
        if (searchDebounceTimer != null) {
            searchDebounceTimer.stop();
        }
        searchDebounceTimer = new Timer(180, e -> filterList());
        searchDebounceTimer.setRepeats(false);
        searchDebounceTimer.start();
    }

    public void scanSupportedGames() {
        setBusy(true, "Сканирование игр…");
        progressBar.setIndeterminate(true);
        new SwingWorker<String, String>() {
            @Override
            protected String doInBackground() throws Exception {
                registry.scanSupportedGames();
                DesktopGamesScanner.ScanResult r = DesktopGamesScanner.scanAllKnownRoots(registry, msg -> publish(msg));
                registry.dedupeAndPrune();
                int total = registry.getAllGames().size();
                return "Каталог: " + total + ", обновлено: " + r.added;
            }

            @Override
            protected void process(List<String> chunks) {
                if (!chunks.isEmpty()) {
                    applyPublishedProgress(chunks.get(chunks.size() - 1));
                }
            }

            @Override
            protected void done() {
                progressBar.setIndeterminate(false);
                try {
                    String msg = get();
                    syncService.refreshWatches();
                    refreshList();
                    setBusy(false, msg);
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    setBusy(false, "Ошибка сканирования: " + cause.getMessage());
                    JOptionPane.showMessageDialog(SaveManagerDialog.this,
                            "Не удалось сканировать игры:\n" + cause.getMessage(),
                            "Ошибка", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void filterList() {
        String filter = searchField.getText().trim().toLowerCase(Locale.ROOT);
        GameSaveDefinition selected = gameList.getSelectedValue();

        List<GameSaveDefinition> matched;
        if (filter.equals(cachedFilterQuery) && cachedFilterResult != null) {
            matched = cachedFilterResult;
        } else {
            List<GameSaveDefinition> all = registry.getAllGames();
            matched = new ArrayList<>(all.size());
            for (GameSaveDefinition def : all) {
                if (filter.isEmpty()
                        || def.getName().toLowerCase(Locale.ROOT).contains(filter)
                        || def.getKey().toLowerCase(Locale.ROOT).contains(filter)) {
                    matched.add(def);
                }
            }
            cachedFilterQuery = filter;
            cachedFilterResult = List.copyOf(matched);
        }

        listModel.clear();
        for (GameSaveDefinition def : matched) {
            listModel.addElement(def);
        }

        if (selected != null) {
            for (int i = 0; i < listModel.size(); i++) {
                if (listModel.get(i).getKey().equals(selected.getKey())) {
                    gameList.setSelectedIndex(i);
                    updateGamesCountFooter();
                    return;
                }
            }
        }
        if (!listModel.isEmpty() && gameList.getSelectedIndex() < 0
                && parent != null && parent.isShowing()) {
            gameList.setSelectedIndex(0);
        }
        updateGamesCountFooter();
    }

    private void updateGamesCountFooter() {
        int total = listModel.size();
        if (cachedSyncedCount >= 0 && cachedStatusTotal == total && statusCache.size() >= total) {
            int synced = 0;
            for (int i = 0; i < listModel.size(); i++) {
                StatusKind k = statusCache.get(listModel.get(i).getKey());
                if (k == StatusKind.CLOUD) {
                    synced++;
                }
            }
            gamesCountFooter.setText("Всего: " + total + " · Синхронизировано: " + synced);
            return;
        }
        int synced = 0;
        for (int i = 0; i < listModel.size(); i++) {
            GameSaveDefinition def = listModel.get(i);
            StatusKind kind = statusCache.computeIfAbsent(def.getKey(), k -> resolveStatus(def));
            if (kind == StatusKind.CLOUD) {
                synced++;
            }
        }
        cachedSyncedCount = synced;
        cachedStatusTotal = total;
        gamesCountFooter.setText("Всего: " + total + " · Синхронизировано: " + synced);
    }

    private void onGameSelected(GameSaveDefinition def) {
        filesTableLoadSeq++;
        filesTableModel.setRowCount(0);
        if (def == null) {
            gameTitleLabel.setText("Выберите игру");
            gameIconLabel.setIcon(null);
            gameTypeBadge.setText("");
            pathField.setText("");
            installPathField.setText("");
            settingsPathField.setText("");
            syncStatusLabel.setText("Статус: не выбрано");
            syncDateLabel.setText("");
            shaLabel.setText("");
            filesTableModel.setRowCount(0);
            syncGameBtn.setEnabled(false);
            syncGameBtn.setToolTipText(null);
            restoreGameBtn.setEnabled(false);
            launchGameBtn.setEnabled(false);
            openInBrowserBtn.setEnabled(false);
            uploadSettingsBtn.setEnabled(false);
            restoreSettingsBtn.setEnabled(false);
            deleteCustomGameBtn.setVisible(false);
            resetPathBtn.setVisible(false);
            return;
        }

        gameTitleLabel.setText(def.getName());
        ImageIcon icon = GameIconExtractor.getIcon(def, registry.getShortcutsDir(), 48);
        gameIconLabel.setIcon(icon);
        deleteCustomGameBtn.setVisible(true);

        if (def.isCustom()) {
            gameTypeBadge.setText("Пользовательская игра");
            resetPathBtn.setVisible(false);
        } else {
            gameTypeBadge.setText("Стандартная игра");
            resetPathBtn.setVisible(def.getCustomSavePath() != null && !def.getCustomSavePath().isBlank());
        }

        Path resolved = def.getResolvedPath();
        boolean unsafe = resolved != null && DesktopGamesScanner.isUnsafeSyncRoot(resolved);
        installPathField.setText(def.getInstallPath() != null ? def.getInstallPath() : "—");
        settingsPathField.setText(def.getSettingsPath() != null ? def.getSettingsPath() : "—");
        if (unsafe) {
            pathField.setText(resolved + " (не папка сейва — укажите сохранения)");
            pathField.setForeground(LauncherTheme.MUTED);
            applyListStatus(syncStatusLabel, StatusKind.ERROR, "Путь похож на установку — синк отключён");
            syncDateLabel.setText("Укажите папку сохранений через «Обзор…»");
            shaLabel.setText("SHA-256: —");
            syncGameBtn.setEnabled(false);
            syncGameBtn.setToolTipText("Укажите корректную папку сейва");
        } else {
            pathField.setText(resolved != null ? resolved.toString() : "(Папка сохранения не найдена)");
            pathField.setForeground(resolved != null ? LauncherTheme.TEXT : LauncherTheme.MUTED);

            StatusKind kind = resolveStatus(def);
            applyListStatus(syncStatusLabel, kind, statusText(def, kind));
            CloudSaveSyncService.GameSyncState state = syncService.getState(def.getKey());
            if (state != null && state.lastSyncedAt != null) {
                syncDateLabel.setText("Синхронизировано: " + state.lastSyncedAt);
            } else {
                syncDateLabel.setText("Последняя синхронизация: никогда");
            }
            if (state != null && state.sha256 != null && !state.sha256.isBlank()) {
                shaLabel.setText("SHA-256: " + state.sha256);
            } else {
                shaLabel.setText("SHA-256: —");
            }
            applyQuotaGate(def);
        }

        fillFilesTableAsync(def, resolved, unsafe);

        boolean pathOk = resolved != null && Files.exists(resolved) && !unsafe;
        syncGameBtn.setEnabled(pathOk);
        restoreGameBtn.setEnabled(true);
        launchGameBtn.setEnabled(resolveLaunchPath(def) != null);
        openInBrowserBtn.setEnabled(syncService.hasRemoteSave(def.getKey()));
        Path settingsRoot = GameSettingsSyncService.resolveSettingsRoot(def);
        boolean settingsOk = settingsRoot != null && Files.exists(settingsRoot)
                && !DesktopGamesScanner.isUnsafeSyncRoot(settingsRoot);
        uploadSettingsBtn.setEnabled(settingsOk);
        restoreSettingsBtn.setEnabled(settingsOk);
    }

    private void fillFilesTableAsync(GameSaveDefinition def, Path resolved, boolean unsafe) {
        final int seq = filesTableLoadSeq;
        if (def == null || resolved == null || unsafe || !Files.exists(resolved)) {
            return;
        }
        new SwingWorker<List<Object[]>, Void>() {
            @Override
            protected List<Object[]> doInBackground() {
                List<File> files = SavePathDetector.listFilesInSave(resolved, def);
                List<Object[]> rows = new ArrayList<>();
                for (File f : files) {
                    String rel = resolved.isAbsolute() ? resolved.relativize(f.toPath()).toString() : f.getName();
                    String lower = rel.toLowerCase(Locale.ROOT);
                    if (lower.endsWith(".exe") || lower.endsWith(".dll")) {
                        continue;
                    }
                    String sizeStr = formatSize(f.length());
                    String dateStr = LocalDateTime.ofInstant(
                                    Instant.ofEpochMilli(f.lastModified()), ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    rows.add(new Object[]{rel, sizeStr, dateStr});
                }
                return rows;
            }

            @Override
            protected void done() {
                if (seq != filesTableLoadSeq) {
                    return;
                }
                filesTableModel.setRowCount(0);
                try {
                    for (Object[] row : get()) {
                        filesTableModel.addRow(row);
                    }
                } catch (Exception ignored) {
                }
            }
        }.execute();
    }

    private void browseCustomPath() {
        GameSaveDefinition def = gameList.getSelectedValue();
        if (def == null) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Выберите папку с сохранениями для: " + def.getName());
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        if (def.getResolvedPath() != null && Files.exists(def.getResolvedPath())) {
            chooser.setCurrentDirectory(def.getResolvedPath().toFile());
        }
        int res = LauncherTheme.showOpenDialog(this, chooser);
        if (res == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            if (selected != null) {
                String newPath = selected.getAbsolutePath();
                if (def.isCustom()) {
                    registry.addOrUpdateCustomGame(def.getKey(), def.getName(), newPath, def.getExeOrIconPath(), def.getShortcutName());
                } else {
                    registry.updatePathOverride(def.getKey(), newPath);
                }
                syncService.refreshWatches();
                refreshList();
            }
        }
    }

    private void resetToDefaultPath() {
        GameSaveDefinition def = gameList.getSelectedValue();
        if (def == null || def.isCustom()) {
            return;
        }
        registry.updatePathOverride(def.getKey(), null);
        refreshList();
    }

    private void openInExplorer() {
        GameSaveDefinition def = gameList.getSelectedValue();
        if (def == null || def.getResolvedPath() == null || !Files.exists(def.getResolvedPath())) {
            JOptionPane.showMessageDialog(this, "Папка сохранения не найдена на диске.", "Внимание", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            Desktop.getDesktop().open(def.getResolvedPath().toFile());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Не удалось открыть папку: " + e.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openAddCustomGameDialog() {
        JDialog dlg = new JDialog(parent, "Добавить свою игру", true);
        dlg.setLayout(new BorderLayout(10, 10));
        dlg.setMinimumSize(new Dimension(520, 280));
        dlg.setLocationRelativeTo(this);

        JPanel panel = new HudFramePanel();
        panel.setLayout(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.fill = GridBagConstraints.HORIZONTAL;

        JTextField nameField = new JTextField(24);
        LauncherTheme.styleField(nameField);

        JTextField savePathField = new JTextField(24);
        LauncherTheme.styleField(savePathField);

        JTextField exePathField = new JTextField(24);
        LauncherTheme.styleField(exePathField);

        c.gridx = 0; c.gridy = 0; c.weightx = 0;
        panel.add(new JLabel("Название игры:"), c);
        c.gridx = 1; c.weightx = 1;
        panel.add(nameField, c);

        c.gridx = 0; c.gridy = 1; c.weightx = 0;
        panel.add(new JLabel("Папка сохранения:"), c);
        c.gridx = 1; c.weightx = 1;
        JPanel spRow = new JPanel(new BorderLayout(4, 0));
        spRow.setOpaque(false);
        spRow.add(savePathField, BorderLayout.CENTER);
        JButton spBrowse = new SiteButton("…", false);
        spBrowse.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
            if (LauncherTheme.showOpenDialog(dlg, fc) == JFileChooser.APPROVE_OPTION) {
                savePathField.setText(fc.getSelectedFile().getAbsolutePath());
            }
        });
        spRow.add(spBrowse, BorderLayout.EAST);
        panel.add(spRow, c);

        c.gridx = 0; c.gridy = 2; c.weightx = 0;
        panel.add(new JLabel("EXE или ярлык (иконка):"), c);
        c.gridx = 1; c.weightx = 1;
        JPanel exeRow = new JPanel(new BorderLayout(4, 0));
        exeRow.setOpaque(false);
        exeRow.add(exePathField, BorderLayout.CENTER);
        JButton exeBrowse = new SiteButton("…", false);
        exeBrowse.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
            if (LauncherTheme.showOpenDialog(dlg, fc) == JFileChooser.APPROVE_OPTION) {
                exePathField.setText(fc.getSelectedFile().getAbsolutePath());
            }
        });
        exeRow.add(exeBrowse, BorderLayout.EAST);
        panel.add(exeRow, c);

        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        btnBar.setOpaque(false);
        JButton okBtn = new SiteButton("Добавить", true);
        JButton cancelBtn = new SiteButton("Отмена", false);

        okBtn.addActionListener(e -> {
            String name = nameField.getText().trim();
            String saveP = savePathField.getText().trim();
            String exeP = exePathField.getText().trim();
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(dlg, "Укажите название игры", "Внимание", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (saveP.isEmpty()) {
                JOptionPane.showMessageDialog(dlg, "Укажите путь к сохранению", "Внимание", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String key = "custom_" + name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "_") + "_" + System.currentTimeMillis() % 10000;
            registry.addOrUpdateCustomGame(key, name, saveP, exeP, null);
            dlg.dispose();
            refreshList();
            syncService.refreshWatches();
        });
        cancelBtn.addActionListener(e -> dlg.dispose());

        btnBar.add(cancelBtn);
        btnBar.add(okBtn);

        dlg.add(panel, BorderLayout.CENTER);
        dlg.add(btnBar, BorderLayout.SOUTH);
        dlg.pack();
        dlg.setVisible(true);
    }

    private void performDeleteSelected() {
        GameSaveDefinition def = gameList.getSelectedValue();
        if (def == null) {
            return;
        }
        int res = JOptionPane.showConfirmDialog(this,
                "Удалить \"" + def.getName() + "\" из списка лаунчера?",
                "Подтверждение", JOptionPane.YES_NO_OPTION);
        if (res == JOptionPane.YES_OPTION) {
            registry.removeGameFromList(def.getKey());
            refreshList();
            syncService.refreshWatches();
        }
    }

    private void performSyncSelected() {
        GameSaveDefinition def = gameList.getSelectedValue();
        if (def == null) {
            return;
        }
        setBusy(true, "Синхронизация " + def.getName() + "...");
        new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                syncService.syncGameNow(def, this::publish);
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                if (!chunks.isEmpty()) {
                    applyPublishedProgress(chunks.get(chunks.size() - 1));
                }
            }

            @Override
            protected void done() {
                setBusy(false, "Синхронизация завершена");
                try {
                    get();
                    onGameSelected(def);
                    refreshCloudStatusAsync();
                    updateGamesCountFooter();
                } catch (Exception e) {
                    setBusy(false, "Ошибка: " + e.getMessage());
                }
            }
        }.execute();
    }

    private void performUploadSettings() {
        GameSaveDefinition def = gameList.getSelectedValue();
        if (def == null) {
            return;
        }
        setBusy(true, "Сохранение настроек " + def.getName() + "…");
        new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                settingsSyncService.uploadSettings(def, this::publish);
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                if (!chunks.isEmpty()) {
                    applyPublishedProgress(chunks.get(chunks.size() - 1));
                }
            }

            @Override
            protected void done() {
                setBusy(false, "Настройки сохранены");
                try {
                    get();
                    refreshCloudStatusAsync();
                } catch (Exception e) {
                    Throwable c = e.getCause() != null ? e.getCause() : e;
                    setBusy(false, "Ошибка: " + c.getMessage());
                }
            }
        }.execute();
    }

    private void performRestoreSettings() {
        GameSaveDefinition def = gameList.getSelectedValue();
        if (def == null) {
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this,
                "Восстановить настройки для \"" + def.getName() + "\" из облака?",
                "Настройки", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        setBusy(true, "Восстановление настроек…");
        new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                settingsSyncService.restoreSettings(def, this::publish);
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                if (!chunks.isEmpty()) {
                    applyPublishedProgress(chunks.get(chunks.size() - 1));
                }
            }

            @Override
            protected void done() {
                setBusy(false, "Настройки восстановлены");
                try {
                    get();
                    onGameSelected(def);
                } catch (Exception e) {
                    Throwable c = e.getCause() != null ? e.getCause() : e;
                    setBusy(false, "Ошибка: " + c.getMessage());
                }
            }
        }.execute();
    }

    private void performRestoreSelected() {
        GameSaveDefinition def = gameList.getSelectedValue();
        if (def == null) {
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this,
                "Восстановить сохранение для \"" + def.getName() + "\" из облака?\nСуществующие локальные файлы будут сохранены в резервную копию .bak.",
                "Восстановление из облака", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        setBusy(true, "Восстановление " + def.getName() + "...");
        new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                syncService.downloadAndRestore(def, this::publish);
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                if (!chunks.isEmpty()) {
                    applyPublishedProgress(chunks.get(chunks.size() - 1));
                }
            }

            @Override
            protected void done() {
                setBusy(false, "Восстановление завершено");
                try {
                    get();
                    registry.reload();
                    onGameSelected(def);
                    refreshCloudStatusAsync();
                } catch (Exception e) {
                    setBusy(false, "Ошибка: " + e.getMessage());
                }
            }
        }.execute();
    }

    private void performSyncAll() {
        setBusy(true, "Синхронизация всех игр...");
        new SwingWorker<Integer, String>() {
            @Override
            protected Integer doInBackground() throws Exception {
                return syncService.syncAllNow(this::publish);
            }

            @Override
            protected void process(List<String> chunks) {
                if (!chunks.isEmpty()) {
                    applyPublishedProgress(chunks.get(chunks.size() - 1));
                }
            }

            @Override
            protected void done() {
                try {
                    int count = get();
                    onGameSelected(gameList.getSelectedValue());
                    refreshCloudStatusAsync();
                    setBusy(false, "Синхронизировано игр: " + count);
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    setBusy(false, "Ошибка: " + cause.getMessage());
                    onGameSelected(gameList.getSelectedValue());
                    refreshCloudStatusAsync();
                }
            }
        }.execute();
    }

    private void performDownloadAll() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Сохранить все сейвы");
        chooser.setSelectedFile(new File("platforma151-saves.zip"));
        if (LauncherTheme.showSaveDialog(this, chooser) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File chosen = chooser.getSelectedFile();
        if (chosen == null) {
            return;
        }
        String name = chosen.getName();
        if (!name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            chosen = new File(chosen.getParentFile(), name + ".zip");
        }
        final Path dest = chosen.toPath();
        setBusy(true, "Скачивание всех сейвов…");
        new SwingWorker<Path, String>() {
            @Override
            protected Path doInBackground() throws Exception {
                return syncService.downloadAll(dest, this::publish);
            }

            @Override
            protected void process(List<String> chunks) {
                if (!chunks.isEmpty()) {
                    applyPublishedProgress(chunks.get(chunks.size() - 1));
                }
            }

            @Override
            protected void done() {
                try {
                    Path saved = get();
                    setBusy(false, "Архив сохранён: " + saved.getFileName());
                } catch (Exception e) {
                    setBusy(false, "Ошибка: " + e.getMessage());
                }
            }
        }.execute();
    }

    private void openWebsiteSaves() {
        try {
            String url = siteBaseUrl + "/saves";
            Desktop.getDesktop().browse(URI.create(url));
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Не удалось открыть браузер: " + e.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openSelectedGameInBrowser() {
        GameSaveDefinition def = gameList.getSelectedValue();
        if (def == null || !syncService.hasRemoteSave(def.getKey())) {
            return;
        }
        try {
            String encoded = java.net.URLEncoder.encode(def.getKey(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("+", "%20");
            String url = siteBaseUrl + "/saves/" + encoded;
            Desktop.getDesktop().browse(URI.create(url));
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Не удалось открыть браузер: " + e.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Import a folder of .lnk/.url shortcuts (from settings) and rescan. */
    public void importShortcutsFolder(Path folder) {
        if (folder == null || !Files.isDirectory(folder)) {
            setBusy(false, "Папка ярлыков не найдена");
            return;
        }
        setBusy(true, "Импорт ярлыков…");
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                registry.setShortcutsDir(folder);
                DesktopGamesScanner.ScanResult r = DesktopGamesScanner.scanAndRegister(registry, folder);
                registry.dedupeAndPrune();
                int total = registry.getAllGames().size();
                return "Каталог: " + total + ", обновлено: " + r.added;
            }

            @Override
            protected void done() {
                try {
                    String msg = get();
                    syncService.refreshWatches();
                    refreshList();
                    setBusy(false, msg);
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    setBusy(false, "Ошибка импорта: " + cause.getMessage());
                }
            }
        }.execute();
    }

    private Path resolveLaunchPath(GameSaveDefinition def) {
        if (def == null) {
            return null;
        }
        String shortcutName = def.getShortcutName();
        List<Path> roots = new ArrayList<>();
        if (registry.getShortcutsDir() != null) {
            roots.add(registry.getShortcutsDir());
        }
        Path desktopGames = DesktopGamesScanner.defaultGamesDir();
        if (!roots.contains(desktopGames)) {
            roots.add(desktopGames);
        }
        if (shortcutName != null && !shortcutName.isBlank()) {
            for (Path root : roots) {
                if (root == null || !Files.isDirectory(root)) {
                    continue;
                }
                Path lnk = root.resolve(shortcutName);
                if (Files.isRegularFile(lnk)) {
                    return lnk;
                }
            }
        }
        String exe = def.getExeOrIconPath();
        if (exe != null && !exe.isBlank()) {
            try {
                Path p = Path.of(exe);
                if (Files.isRegularFile(p)) {
                    String lower = p.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (lower.endsWith(".exe") || lower.endsWith(".lnk") || lower.endsWith(".url")) {
                        return p;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        if (shortcutName != null && !shortcutName.isBlank()) {
            for (Path root : roots) {
                if (root == null || !Files.isDirectory(root)) {
                    continue;
                }
                Path candidate = root.resolve(shortcutName);
                if (Files.isRegularFile(candidate)) {
                    var resolved = DesktopGamesScanner.resolveShortcut(candidate);
                    if (resolved != null && resolved.exePath != null) {
                        Path exePath = Path.of(resolved.exePath);
                        if (Files.isRegularFile(exePath)) {
                            return candidate;
                        }
                    }
                    return candidate;
                }
            }
        }
        return null;
    }

    private void launchSelectedGame() {
        GameSaveDefinition def = gameList.getSelectedValue();
        if (def == null) {
            return;
        }
        Path launch = resolveLaunchPath(def);
        if (launch == null) {
            setBusy(false, "Ярлык или EXE не найден");
            return;
        }
        try {
            new ProcessBuilder("cmd", "/c", "start", "", launch.toAbsolutePath().toString()).start();
            setBusy(false, "Запуск: " + def.getName());
        } catch (Exception e) {
            setBusy(false, "Не удалось запустить: " + e.getMessage());
        }
    }

    private void setBusy(boolean busy, String text) {
        if (busy) {
            progressBar.setIndeterminate(true);
            progressBar.setValue(0);
            progressPercentLabel.setText("…");
            progressPercentLabel.setForeground(LauncherTheme.ACCENT);
        } else {
            progressBar.setIndeterminate(false);
            progressBar.setValue(0);
            progressPercentLabel.setText(" ");
            progressPercentLabel.setForeground(LauncherTheme.MUTED);
        }
        progressLabel.setText(text != null && !text.isBlank() ? text : " ");
        if (busy) {
            syncGameBtn.setEnabled(false);
            restoreGameBtn.setEnabled(false);
            launchGameBtn.setEnabled(false);
            uploadSettingsBtn.setEnabled(false);
            restoreSettingsBtn.setEnabled(false);
        } else {
            onGameSelected(gameList.getSelectedValue());
            invalidateSearchAndStatusCache();
            updateGamesCountFooter();
            gameList.repaint();
        }
    }

    /** Determinate progress 0–100 with status text (EDT). */
    private void setProgress(int percent, String text) {
        int p = Math.max(0, Math.min(100, percent));
        progressBar.setIndeterminate(false);
        progressBar.setValue(p);
        progressPercentLabel.setText(p + "%");
        progressPercentLabel.setForeground(LauncherTheme.ACCENT);
        if (text != null && !text.isBlank()) {
            progressLabel.setText(text);
        }
        syncGameBtn.setEnabled(false);
        restoreGameBtn.setEnabled(false);
        launchGameBtn.setEnabled(false);
        uploadSettingsBtn.setEnabled(false);
        restoreSettingsBtn.setEnabled(false);
    }

    /** Apply progress from worker publish: optional "pct\\tmessage", else stage heuristic. */
    private void applyPublishedProgress(String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        int tab = raw.indexOf('\t');
        if (tab > 0) {
            try {
                int pct = Integer.parseInt(raw.substring(0, tab).trim());
                setProgress(pct, raw.substring(tab + 1));
                return;
            } catch (NumberFormatException ignored) {
            }
        }
        int stage = guessStagePercent(raw);
        if (stage >= 0) {
            setProgress(stage, raw);
        } else {
            progressLabel.setText(raw);
        }
    }

    private static int guessStagePercent(String msg) {
        String m = msg.toLowerCase(Locale.ROOT);
        if (m.contains("архивац") || m.contains("упаков")) {
            return 25;
        }
        if (m.contains("отправка") || m.contains("загрузк") || m.contains("upload")) {
            return 65;
        }
        if (m.contains("скачив") || m.contains("download")) {
            return 40;
        }
        if (m.contains("распаков") || m.contains("восстанов")) {
            return 80;
        }
        if (m.contains("успешно") || m.contains("готов") || m.contains("сохран")) {
            return 100;
        }
        return -1;
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1048576) return (Math.round(bytes / 1024.0 * 10.0) / 10.0) + " KB";
        return (Math.round(bytes / 1048576.0 * 10.0) / 10.0) + " MB";
    }

    private enum StatusKind { CLOUD, MISSING, PENDING, ERROR }

    private StatusKind resolveStatus(GameSaveDefinition def) {
        CloudSaveSyncService.GameSyncState state = syncService.getState(def.getKey());
        if (state != null && state.lastError != null && !state.lastError.isBlank()) {
            return StatusKind.ERROR;
        }
        Path resolved = def.getResolvedPath();
        boolean localOk = resolved != null && Files.exists(resolved) && !DesktopGamesScanner.isUnsafeSyncRoot(resolved);
        if (!localOk) {
            return StatusKind.MISSING;
        }
        if (syncService.isPending(def.getKey())) {
            return StatusKind.PENDING;
        }
        if (state != null && state.sha256 != null && !state.sha256.isBlank()) {
            return StatusKind.CLOUD;
        }
        return StatusKind.PENDING;
    }

    private String statusText(GameSaveDefinition def, StatusKind kind) {
        CloudSaveSyncService.GameSyncState state = syncService.getState(def.getKey());
        return switch (kind) {
            case CLOUD -> "В облаке (" + formatSize(state != null ? state.lastSize : 0) + ")";
            case MISSING -> "Файлы не найдены";
            case PENDING -> "Ожидает синхронизацию";
            case ERROR -> "Ошибка" + (state != null && state.lastError != null ? ": " + state.lastError : "");
        };
    }

    private static Color statusColor(StatusKind kind) {
        return switch (kind) {
            case CLOUD -> new Color(0x60, 0xd0, 0x70);
            case MISSING -> LauncherTheme.MUTED;
            case PENDING -> new Color(0xf0, 0xc0, 0x50);
            case ERROR -> new Color(0xe0, 0x70, 0x70);
        };
    }

    private static void applyListStatus(JLabel label, StatusKind kind, String text) {
        label.setText(text);
        label.setForeground(statusColor(kind));
    }

    private void applyQuotaGate(GameSaveDefinition def) {
        Path resolved = def.getResolvedPath();
        boolean canSync = resolved != null && Files.exists(resolved) && !DesktopGamesScanner.isUnsafeSyncRoot(resolved);
        if (!canSync) {
            syncGameBtn.setEnabled(false);
            syncGameBtn.setToolTipText("Папка сейва не найдена");
            return;
        }
        syncGameBtn.setEnabled(true);
        syncGameBtn.setToolTipText(null);
    }

    private void browseSettingsPaths() {
        GameSaveDefinition def = gameList.getSelectedValue();
        if (def == null) {
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Выбор файлов конфигурации");
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setMultiSelectionEnabled(true);
        if (LauncherTheme.showOpenDialog(this, fc) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File[] files = fc.getSelectedFiles();
        if (files == null || files.length == 0) {
            File one = fc.getSelectedFile();
            if (one != null) {
                files = new File[]{one};
            }
        }
        if (files == null || files.length == 0) {
            return;
        }
        Path first = files[0].toPath();
        Path settingsRoot = files.length == 1 ? first : first.getParent();
        String settings = settingsRoot != null ? settingsRoot.toString() : first.toString();
        registry.addOrUpdateCustomGame(
                def.getKey(),
                def.getName(),
                def.getCustomSavePath() != null ? def.getCustomSavePath()
                        : (def.getResolvedPath() != null ? def.getResolvedPath().toString() : null),
                def.getExeOrIconPath(),
                def.getShortcutName(),
                settings,
                def.getInstallPath());
        refreshList();
        onGameSelected(registry.getGame(def.getKey()));
    }

    private final class GameListCellRenderer extends JPanel implements ListCellRenderer<GameSaveDefinition> {
        private final JLabel iconLbl = new JLabel();
        private final JLabel nameLbl = new JLabel();
        private final JLabel statusLbl = new JLabel();

        GameListCellRenderer() {
            setLayout(new BorderLayout(8, 2));
            setOpaque(true);
            setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

            iconLbl.setPreferredSize(new Dimension(32, 32));
            add(iconLbl, BorderLayout.WEST);

            JPanel textPanel = new JPanel();
            textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
            textPanel.setOpaque(false);
            textPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

            nameLbl.setOpaque(false);
            nameLbl.setFont(LauncherTheme.uiFontBold);
            nameLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
            statusLbl.setOpaque(false);
            statusLbl.setFont(LauncherTheme.uiFont);
            statusLbl.setHorizontalAlignment(SwingConstants.LEFT);
            statusLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

            textPanel.add(nameLbl);
            textPanel.add(statusLbl);
            add(textPanel, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends GameSaveDefinition> list, GameSaveDefinition def, int index, boolean isSelected, boolean cellHasFocus) {
            if (def == null) {
                return this;
            }

            nameLbl.setText(def.getName());
            iconLbl.setIcon(GameIconExtractor.getIcon(def, registry.getShortcutsDir(), 32));

            StatusKind kind = statusCache.get(def.getKey());
            if (kind == null) {
                kind = resolveStatus(def);
                statusCache.put(def.getKey(), kind);
            }
            statusLbl.setText(statusText(def, kind));
            statusLbl.setForeground(statusColor(kind));

            if (isSelected) {
                setBackground(LauncherTheme.BG2);
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 3, 0, 0, LauncherTheme.ACCENT),
                        BorderFactory.createEmptyBorder(6, 5, 6, 8)));
                nameLbl.setForeground(LauncherTheme.ACCENT);
            } else {
                setBackground(LauncherTheme.SURFACE2);
                setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
                nameLbl.setForeground(LauncherTheme.TEXT);
            }

            return this;
        }
    }
}
