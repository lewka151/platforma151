package com.bunker.survival.launcher;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.text.JTextComponent;
import javax.swing.plaf.basic.BasicCheckBoxUI;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.plaf.basic.BasicTextFieldUI;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GraphicsEnvironment;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.Set;

/** Site-token colors and Swing defaults for the launcher. */
public final class LauncherTheme {
    public static final Color BG = new Color(0x0c0e12);
    public static final Color BG2 = new Color(0x12151a);
    public static final Color PANEL = new Color(0x161a21);
    public static final Color PANEL_INNER = new Color(0x1c2129);
    public static final Color ACCENT = new Color(0xa8976a);
    public static final Color ACCENT2 = new Color(0x8f7f55);
    public static final Color TEXT = new Color(0xe6e4df);
    public static final Color MUTED = new Color(0x8a8780);
    public static final Color FIELD_BG = new Color(0x12151a);
    public static final Color BORDER = new Color(0x2a2e36);
    public static final Color BTN_BG = new Color(0x1c2129);
    public static final Color BTN_HOVER_BG = new Color(0x232830);
    public static final Color BTN_HOVER_BORDER = new Color(0x8f7f55);
    public static final Color BTN_PRIMARY_BG = new Color(0xa8976a);
    public static final Color BTN_PRIMARY_FG = new Color(0x14120e);
    public static final Color LOG_FG = new Color(0xc8c4bc);
    public static final Color SURFACE2 = new Color(0x1c2129);
    public static final Color CLOSE_HOVER = new Color(0xc45c4a);
    public static final Color SCROLL_TRACK = new Color(0x12151a);
    public static final Color SCROLL_THUMB = new Color(0x3a414c);
    public static final Color SCROLL_THUMB_HOVER = new Color(0x4a5568);

    private static final String CYRILLIC_PROBE = "Проверить связь Журнал URL сервера Бункер";
    private static Set<String> availableFontFamilies;
    private static boolean fontsInstalled;

    public static Font uiFont;
    public static Font uiFontBold;
    public static Font uiFontTitle;
    public static Font monoFont;

    private LauncherTheme() {
    }

    public static void installFontsAndDefaults() {
        if (!fontsInstalled || uiFont == null) {
            uiFont = pickFont(new String[]{
                    "Segoe UI", "Tahoma", "Arial", "Microsoft Sans Serif", Font.SANS_SERIF
            }, Font.PLAIN, 13f);
            uiFontBold = uiFont.deriveFont(Font.BOLD, 13f);
            uiFontTitle = uiFont.deriveFont(Font.BOLD, 20f);
            monoFont = pickFont(new String[]{
                    "Cascadia Mono", "Cascadia Code", "JetBrains Mono", "DejaVu Sans Mono",
                    "Segoe UI", "Tahoma", "Consolas", "Courier New", Font.MONOSPACED
            }, Font.PLAIN, 12f);
            fontsInstalled = true;
        }

        UIManager.put("Label.font", uiFont);
        UIManager.put("Button.font", uiFontBold);
        UIManager.put("TextField.font", uiFont);
        UIManager.put("TextArea.font", monoFont);
        UIManager.put("CheckBox.font", uiFont);
        UIManager.put("TitledBorder.font", uiFontBold);
        UIManager.put("OptionPane.messageFont", uiFont);
        UIManager.put("OptionPane.buttonFont", uiFont);
        UIManager.put("ScrollPane.font", uiFont);

        UIManager.put("Label.foreground", TEXT);
        UIManager.put("CheckBox.foreground", TEXT);
        UIManager.put("TextField.foreground", TEXT);
        UIManager.put("TextField.background", FIELD_BG);
        UIManager.put("TextField.caretForeground", TEXT);
        UIManager.put("TextArea.foreground", LOG_FG);
        UIManager.put("TextArea.background", SURFACE2);
        UIManager.put("TextArea.caretForeground", TEXT);
        UIManager.put("Button.foreground", TEXT);
        UIManager.put("Button.background", BTN_BG);
        UIManager.put("OptionPane.background", PANEL);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("Panel.background", BG);
        UIManager.put("control", PANEL);
        UIManager.put("controlHighlight", PANEL_INNER);
        UIManager.put("controlLtHighlight", PANEL_INNER);
        UIManager.put("controlShadow", BORDER);
        UIManager.put("controlDkShadow", BG);
        UIManager.put("text", TEXT);
        UIManager.put("textHighlight", new Color(0x4a5568));
        UIManager.put("textHighlightText", TEXT);
        UIManager.put("textInactiveText", MUTED);
        UIManager.put("info", PANEL);
        UIManager.put("infoText", TEXT);
        UIManager.put("nimbusBase", PANEL);
        UIManager.put("ScrollPane.background", SURFACE2);
        UIManager.put("ScrollPane.border", BorderFactory.createEmptyBorder());
        UIManager.put("Viewport.background", SURFACE2);
        UIManager.put("ScrollBar.background", SCROLL_TRACK);
        UIManager.put("ScrollBar.foreground", SCROLL_THUMB);
        UIManager.put("ScrollBar.thumb", SCROLL_THUMB);
        UIManager.put("ScrollBar.track", SCROLL_TRACK);
        UIManager.put("List.background", SURFACE2);
        UIManager.put("List.foreground", TEXT);
        UIManager.put("List.selectionBackground", PANEL_INNER);
        UIManager.put("List.selectionForeground", ACCENT);
        UIManager.put("Table.background", SURFACE2);
        UIManager.put("Table.foreground", TEXT);
        UIManager.put("Table.selectionBackground", PANEL_INNER);
        UIManager.put("Table.selectionForeground", TEXT);
        UIManager.put("Table.gridColor", BORDER);
        UIManager.put("TableHeader.background", BG2);
        UIManager.put("TableHeader.foreground", TEXT);
        UIManager.put("SplitPane.background", BG);
        UIManager.put("SplitPane.dividerFocusColor", BORDER);
        UIManager.put("SplitPaneDivider.draggingColor", ACCENT2);
        UIManager.put("ProgressBar.background", SURFACE2);
        UIManager.put("ProgressBar.foreground", ACCENT);
        UIManager.put("ProgressBar.selectionBackground", TEXT);
        UIManager.put("ProgressBar.selectionForeground", BG);
        UIManager.put("Spinner.background", FIELD_BG);
        UIManager.put("Spinner.foreground", TEXT);
        UIManager.put("CheckBox.background", PANEL);
        UIManager.put("CheckBox.foreground", TEXT);
        UIManager.put("CheckBox.interiorBackground", FIELD_BG);
        UIManager.put("FileChooser.background", PANEL);
        UIManager.put("FormattedTextField.background", FIELD_BG);
        UIManager.put("FormattedTextField.foreground", TEXT);
        UIManager.put("FormattedTextField.caretForeground", TEXT);

        UIManager.put("PopupMenu.background", PANEL);
        UIManager.put("PopupMenu.foreground", TEXT);
        UIManager.put("PopupMenu.border", BorderFactory.createLineBorder(BORDER));
        UIManager.put("MenuItem.background", PANEL);
        UIManager.put("MenuItem.foreground", TEXT);
        UIManager.put("MenuItem.selectionBackground", PANEL_INNER);
        UIManager.put("MenuItem.selectionForeground", ACCENT);
        UIManager.put("MenuItem.acceleratorForeground", MUTED);
        UIManager.put("MenuItem.acceleratorSelectionForeground", ACCENT);
        UIManager.put("MenuItem.font", uiFont.deriveFont(Font.PLAIN, 14.5f));
        UIManager.put("Menu.background", PANEL);
        UIManager.put("Menu.foreground", TEXT);
        UIManager.put("Menu.selectionBackground", PANEL_INNER);
        UIManager.put("Menu.selectionForeground", ACCENT);
        UIManager.put("Separator.foreground", BORDER);
        UIManager.put("Separator.background", BORDER);
    }

    /**
     * System JFileChooser keeps a light Windows chrome; force dark ink so
     * list/labels/buttons stay readable (avoids white-on-white after dark UIManager).
     */
    public static Object[] beginSystemFileChooserContrast() {
        Color ink = Color.BLACK;
        Color paper = Color.WHITE;
        Color btnFace = new Color(0xe9ecef);
        String[] keys = {
                "FileChooser.background",
                "FileChooser.listViewBackground",
                "FileChooser.foreground",
                "List.background",
                "List.foreground",
                "List.selectionBackground",
                "List.selectionForeground",
                "Label.foreground",
                "Button.background",
                "Button.foreground",
                "ComboBox.background",
                "ComboBox.foreground",
                "ComboBox.selectionBackground",
                "ComboBox.selectionForeground",
                "TextField.background",
                "TextField.foreground",
                "TextField.caretForeground",
                "Panel.background",
                "control",
                "text",
                "textHighlight",
                "textHighlightText",
                "OptionPane.messageForeground",
                "OptionPane.background",
                "Tree.background",
                "Tree.foreground",
                "Tree.textForeground",
                "Tree.textBackground",
                "Tree.selectionForeground",
                "Tree.selectionBackground",
                "Table.background",
                "Table.foreground",
                "Table.selectionBackground",
                "Table.selectionForeground",
                "TableHeader.background",
                "TableHeader.foreground",
                "ScrollPane.background",
                "Viewport.background",
                "PopupMenu.background",
                "PopupMenu.foreground",
                "PopupMenu.border",
                "Menu.background",
                "Menu.foreground",
                "Menu.selectionBackground",
                "Menu.selectionForeground",
                "MenuItem.background",
                "MenuItem.foreground",
                "MenuItem.selectionBackground",
                "MenuItem.selectionForeground",
                "MenuItem.acceleratorForeground",
                "MenuItem.acceleratorSelectionForeground",
                "Separator.foreground",
                "Separator.background",
        };
        Object[] snapshot = new Object[keys.length * 2];
        for (int i = 0; i < keys.length; i++) {
            snapshot[i * 2] = keys[i];
            snapshot[i * 2 + 1] = UIManager.get(keys[i]);
        }
        UIManager.put("FileChooser.background", paper);
        UIManager.put("FileChooser.listViewBackground", paper);
        UIManager.put("FileChooser.foreground", ink);
        UIManager.put("List.background", paper);
        UIManager.put("List.foreground", ink);
        UIManager.put("List.selectionBackground", new Color(0x0d6efd));
        UIManager.put("List.selectionForeground", Color.WHITE);
        UIManager.put("Label.foreground", ink);
        UIManager.put("Button.background", btnFace);
        UIManager.put("Button.foreground", ink);
        UIManager.put("ComboBox.background", paper);
        UIManager.put("ComboBox.foreground", ink);
        UIManager.put("ComboBox.selectionBackground", new Color(0x0d6efd));
        UIManager.put("ComboBox.selectionForeground", Color.WHITE);
        UIManager.put("TextField.background", paper);
        UIManager.put("TextField.foreground", ink);
        UIManager.put("TextField.caretForeground", ink);
        UIManager.put("Panel.background", paper);
        UIManager.put("control", paper);
        UIManager.put("text", ink);
        UIManager.put("textHighlight", new Color(0x0d6efd));
        UIManager.put("textHighlightText", Color.WHITE);
        UIManager.put("OptionPane.messageForeground", ink);
        UIManager.put("OptionPane.background", paper);
        UIManager.put("Tree.background", paper);
        UIManager.put("Tree.foreground", ink);
        UIManager.put("Tree.textForeground", ink);
        UIManager.put("Tree.textBackground", paper);
        UIManager.put("Tree.selectionForeground", Color.WHITE);
        UIManager.put("Tree.selectionBackground", new Color(0x0d6efd));
        UIManager.put("Table.background", paper);
        UIManager.put("Table.foreground", ink);
        UIManager.put("Table.selectionBackground", new Color(0x0d6efd));
        UIManager.put("Table.selectionForeground", Color.WHITE);
        UIManager.put("TableHeader.background", btnFace);
        UIManager.put("TableHeader.foreground", ink);
        UIManager.put("ScrollPane.background", paper);
        UIManager.put("Viewport.background", paper);
        UIManager.put("PopupMenu.background", paper);
        UIManager.put("PopupMenu.foreground", ink);
        UIManager.put("PopupMenu.border", BorderFactory.createLineBorder(new Color(0xced4da)));
        UIManager.put("Menu.background", paper);
        UIManager.put("Menu.foreground", ink);
        UIManager.put("Menu.selectionBackground", new Color(0x0d6efd));
        UIManager.put("Menu.selectionForeground", Color.WHITE);
        UIManager.put("MenuItem.background", paper);
        UIManager.put("MenuItem.foreground", ink);
        UIManager.put("MenuItem.selectionBackground", new Color(0x0d6efd));
        UIManager.put("MenuItem.selectionForeground", Color.WHITE);
        UIManager.put("MenuItem.acceleratorForeground", new Color(0x495057));
        UIManager.put("MenuItem.acceleratorSelectionForeground", Color.WHITE);
        UIManager.put("Separator.foreground", new Color(0xced4da));
        UIManager.put("Separator.background", paper);
        return snapshot;
    }

    public static void endSystemFileChooserContrast(Object[] snapshot) {
        if (snapshot == null) {
            return;
        }
        for (int i = 0; i + 1 < snapshot.length; i += 2) {
            String key = (String) snapshot[i];
            Object val = snapshot[i + 1];
            if (val == null) {
                UIManager.put(key, null);
            } else {
                UIManager.put(key, val);
            }
        }
    }

    public static JFileChooser darkDirectoryChooser(String title) {
        applyRussianFileChooserStrings();
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title != null ? title : "Выбор папки");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        return chooser;
    }

    public static void applyRussianFileChooserStrings() {
        UIManager.put("FileChooser.openDialogTitleText", "Открыть");
        UIManager.put("FileChooser.saveDialogTitleText", "Сохранить");
        UIManager.put("FileChooser.lookInLabelText", "Папка:");
        UIManager.put("FileChooser.saveInLabelText", "Папка:");
        UIManager.put("FileChooser.fileNameLabelText", "Имя файла:");
        UIManager.put("FileChooser.filesOfTypeLabelText", "Тип файлов:");
        UIManager.put("FileChooser.upFolderToolTipText", "На уровень выше");
        UIManager.put("FileChooser.homeFolderToolTipText", "Домой");
        UIManager.put("FileChooser.newFolderToolTipText", "Новая папка");
        UIManager.put("FileChooser.listViewButtonToolTipText", "Список");
        UIManager.put("FileChooser.detailsViewButtonToolTipText", "Таблица");
        UIManager.put("FileChooser.openButtonText", "Открыть");
        UIManager.put("FileChooser.saveButtonText", "Сохранить");
        UIManager.put("FileChooser.cancelButtonText", "Отмена");
        UIManager.put("FileChooser.updateButtonText", "Обновить");
        UIManager.put("FileChooser.helpButtonText", "Справка");
        UIManager.put("FileChooser.directoryOpenButtonText", "Выбрать");
        UIManager.put("FileChooser.acceptAllFileFilterText", "Все файлы");
        UIManager.put("FileChooser.newFolderActionLabelText", "Создать папку");
        UIManager.put("FileChooser.newFolderButtonText", "Создать папку");
        UIManager.put("FileChooser.renameFileButtonText", "Переименовать");
        UIManager.put("FileChooser.newFolderErrorText", "Не удалось создать папку");
        UIManager.put("FileChooser.newFolderParentDoesNotExistText", "Родительская папка не существует");
        UIManager.put("FileChooser.newFolderPermissionText", "Нет прав на создание папки");
        UIManager.put("FileChooser.fileDescriptionText", "Файл");
        UIManager.put("FileChooser.folderDescriptionText", "Папка");
        UIManager.put("OptionPane.yesButtonText", "Да");
        UIManager.put("OptionPane.noButtonText", "Нет");
        UIManager.put("OptionPane.okButtonText", "OK");
        UIManager.put("OptionPane.cancelButtonText", "Отмена");
    }

    public static void installRussianTextPopup(JTextComponent field) {
        JPopupMenu menu = new JPopupMenu();
        menu.add(textPopupItem(field, "Вырезать", KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.CTRL_DOWN_MASK), field::cut));
        menu.add(textPopupItem(field, "Копировать", KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK), field::copy));
        menu.add(textPopupItem(field, "Вставить", KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK), field::paste));
        menu.add(textPopupItem(field, "Удалить", null, () -> field.replaceSelection("")));
        menu.addSeparator();
        menu.add(textPopupItem(field, "Выделить всё", KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK), field::selectAll));
        field.setComponentPopupMenu(menu);
    }

    private static JMenuItem textPopupItem(JTextComponent field, String label, KeyStroke accel, Runnable action) {
        JMenuItem item = new JMenuItem(label);
        if (accel != null) {
            item.setAccelerator(accel);
        }
        item.addActionListener(e -> {
            field.requestFocusInWindow();
            action.run();
        });
        return item;
    }

    public static void localizeFileChooser(JFileChooser chooser) {
        if (chooser == null) {
            return;
        }
        localizeTextComponents(chooser);
    }

    private static void localizeTextComponents(Component root) {
        if (root instanceof JTextComponent tc) {
            installRussianTextPopup(tc);
        }
        if (root instanceof Container cont) {
            for (Component child : cont.getComponents()) {
                localizeTextComponents(child);
            }
        }
    }

    public static int showOpenDialog(Component parent, JFileChooser chooser) {
        applyRussianFileChooserStrings();
        Object[] snap = beginSystemFileChooserContrast();
        try {
            SwingUtilities.updateComponentTreeUI(chooser);
            localizeFileChooser(chooser);
            return chooser.showOpenDialog(parent);
        } finally {
            endSystemFileChooserContrast(snap);
        }
    }

    public static int showSaveDialog(Component parent, JFileChooser chooser) {
        applyRussianFileChooserStrings();
        Object[] snap = beginSystemFileChooserContrast();
        try {
            SwingUtilities.updateComponentTreeUI(chooser);
            localizeFileChooser(chooser);
            return chooser.showSaveDialog(parent);
        } finally {
            endSystemFileChooserContrast(snap);
        }
    }

    public static void styleCheck(JCheckBox box) {
        box.setUI(new BasicCheckBoxUI() {
            @Override
            public void paint(Graphics g, JComponent c) {
                // Avoid Nimbus/default fill behind the label text (white strip).
                AbstractButton b = (AbstractButton) c;
                b.setOpaque(false);
                super.paint(g, c);
            }
        });
        box.setForeground(TEXT);
        box.setFont(uiFont);
        box.setOpaque(false);
        box.setContentAreaFilled(false);
        box.setBorderPainted(false);
        box.setFocusPainted(false);
        box.setBackground(new Color(0, 0, 0, 0));
        box.setAlignmentY(Component.CENTER_ALIGNMENT);
    }

    public static void styleScroll(JScrollPane scroll) {
        styleScroll(scroll, SURFACE2);
    }

    public static void styleScroll(JScrollPane scroll, Color background) {
        Color bg = background != null ? background : SURFACE2;
        scroll.setOpaque(true);
        scroll.setBackground(bg);
        scroll.getViewport().setOpaque(true);
        scroll.getViewport().setBackground(bg);
        scroll.getVerticalScrollBar().setUI(new DarkScrollBarUI());
        scroll.getHorizontalScrollBar().setUI(new DarkScrollBarUI());
        scroll.getVerticalScrollBar().setOpaque(true);
        scroll.getVerticalScrollBar().setBackground(SCROLL_TRACK);
        scroll.getHorizontalScrollBar().setOpaque(true);
        scroll.getHorizontalScrollBar().setBackground(SCROLL_TRACK);
    }

    public static void styleList(JList<?> list) {
        list.setOpaque(true);
        list.setBackground(SURFACE2);
        list.setForeground(TEXT);
        list.setSelectionBackground(BG2);
        list.setSelectionForeground(ACCENT);
    }

    public static void styleTable(JTable table) {
        table.setOpaque(true);
        table.setBackground(SURFACE2);
        table.setForeground(TEXT);
        table.setSelectionBackground(PANEL_INNER);
        table.setSelectionForeground(TEXT);
        table.setGridColor(BORDER);
        table.setFillsViewportHeight(true);
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(true);
        table.setDefaultRenderer(Object.class, new DarkTableCellRenderer());
        JTableHeader header = table.getTableHeader();
        if (header != null) {
            header.setOpaque(true);
            header.setBackground(BG2);
            header.setForeground(TEXT);
            header.setDefaultRenderer(new DarkHeaderRenderer());
        }
    }

    public static void styleSplit(JSplitPane split) {
        split.setOpaque(true);
        split.setBackground(BG);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setDividerSize(5);
        split.setUI(new BasicSplitPaneUI() {
            @Override
            public BasicSplitPaneDivider createDefaultDivider() {
                return new BasicSplitPaneDivider(this) {
                    @Override
                    public void paint(Graphics g) {
                        g.setColor(BG);
                        g.fillRect(0, 0, getWidth(), getHeight());
                        g.setColor(BORDER);
                        if (getHeight() >= getWidth()) {
                            int m = getWidth() / 2;
                            g.drawLine(m, 0, m, getHeight());
                        } else {
                            int m = getHeight() / 2;
                            g.drawLine(0, m, getWidth(), m);
                        }
                    }
                };
            }
        });
    }

    public static final class DarkTableCellRenderer extends DefaultTableCellRenderer {
        private final int align;

        public DarkTableCellRenderer() {
            this(LEFT);
        }

        public DarkTableCellRenderer(int align) {
            this.align = align;
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setHorizontalAlignment(align);
            setOpaque(true);
            setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
            if (isSelected) {
                setBackground(PANEL_INNER);
                setForeground(TEXT);
            } else {
                setBackground(SURFACE2);
                setForeground(TEXT);
            }
            return this;
        }
    }

    public static final class DarkHeaderRenderer extends DefaultTableCellRenderer {
        public DarkHeaderRenderer() {
            setOpaque(true);
            setHorizontalAlignment(LEFT);
        }

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setOpaque(true);
            setBackground(BG2);
            setForeground(TEXT);
            if (uiFontBold != null) {
                setFont(uiFontBold);
            }
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 1, BORDER),
                    BorderFactory.createEmptyBorder(6, 8, 6, 8)));
            return this;
        }
    }

    public static void styleSpinner(JSpinner spinner) {
        spinner.setFont(uiFont);
        spinner.setOpaque(true);
        spinner.setBackground(FIELD_BG);
        spinner.setForeground(TEXT);
        spinner.setBorder(BorderFactory.createLineBorder(BORDER, 1));
        Component editor = spinner.getEditor();
        if (editor instanceof JSpinner.DefaultEditor de) {
            JTextField field = de.getTextField();
            field.setFont(uiFont);
            field.setForeground(TEXT);
            field.setBackground(FIELD_BG);
            field.setCaretColor(TEXT);
            field.setSelectionColor(new Color(0x4a5568));
            field.setSelectedTextColor(TEXT);
            field.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            de.setOpaque(true);
            de.setBackground(FIELD_BG);
        }
    }

    /**
     * Interval control with site-styled − / + on the left and right of the value
     * (no vertical spinner arrows).
     */
    public static JPanel horizontalStepper(SpinnerNumberModel model, Runnable onChange) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        row.setOpaque(false);

        JLabel valueLbl = new JLabel(String.valueOf(model.getNumber().intValue()));
        valueLbl.setFont(uiFontBold != null ? uiFontBold : uiFont);
        valueLbl.setForeground(TEXT);
        valueLbl.setOpaque(true);
        valueLbl.setBackground(FIELD_BG);
        valueLbl.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1),
                BorderFactory.createEmptyBorder(6, 14, 6, 14)));
        valueLbl.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        valueLbl.setPreferredSize(new Dimension(52, SiteButton.PREFERRED_HEIGHT));

        SiteButton minus = new SiteButton("−", false);
        SiteButton plus = new SiteButton("+", false);
        Dimension arrowSize = new Dimension(40, SiteButton.PREFERRED_HEIGHT);
        minus.setPreferredSize(arrowSize);
        plus.setPreferredSize(arrowSize);

        Runnable refresh = () -> valueLbl.setText(String.valueOf(model.getNumber().intValue()));
        minus.addActionListener(e -> {
            Object prev = model.getPreviousValue();
            if (prev != null) {
                model.setValue(prev);
                refresh.run();
                if (onChange != null) {
                    onChange.run();
                }
            }
        });
        plus.addActionListener(e -> {
            Object next = model.getNextValue();
            if (next != null) {
                model.setValue(next);
                refresh.run();
                if (onChange != null) {
                    onChange.run();
                }
            }
        });
        model.addChangeListener(e -> refresh.run());

        row.add(minus);
        row.add(valueLbl);
        row.add(plus);
        return row;
    }

    /** Invisible but draggable split divider (no visible strip; E-resize cursor on hover). */
    public static void styleSplitInvisibleResizable(JSplitPane split) {
        split.setOpaque(true);
        split.setBackground(PANEL);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setDividerSize(4);
        split.setUI(new BasicSplitPaneUI() {
            @Override
            public BasicSplitPaneDivider createDefaultDivider() {
                return new BasicSplitPaneDivider(this) {
                    {
                        addMouseListener(new MouseAdapter() {
                            @Override
                            public void mouseEntered(MouseEvent e) {
                                setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
                            }

                            @Override
                            public void mouseExited(MouseEvent e) {
                                setCursor(Cursor.getDefaultCursor());
                            }
                        });
                    }

                    @Override
                    public void paint(Graphics g) {
                        g.setColor(PANEL);
                        g.fillRect(0, 0, getWidth(), getHeight());
                    }
                };
            }
        });
    }

    /** Resizable split divider with hover highlight and E-resize cursor. */
    public static void styleSplitResizable(JSplitPane split) {
        split.setOpaque(true);
        split.setBackground(BG);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setDividerSize(6);
        split.setUI(new BasicSplitPaneUI() {
            @Override
            public BasicSplitPaneDivider createDefaultDivider() {
                return new BasicSplitPaneDivider(this) {
                    private boolean hovering;

                    {
                        addMouseListener(new MouseAdapter() {
                            @Override
                            public void mouseEntered(MouseEvent e) {
                                hovering = true;
                                setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
                                repaint();
                            }

                            @Override
                            public void mouseExited(MouseEvent e) {
                                hovering = false;
                                setCursor(Cursor.getDefaultCursor());
                                repaint();
                            }
                        });
                    }

                    @Override
                    public void paint(Graphics g) {
                        g.setColor(BG);
                        g.fillRect(0, 0, getWidth(), getHeight());
                        if (hovering) {
                            Color accent = ACCENT;
                            g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 70));
                            g.fillRect(0, 0, getWidth(), getHeight());
                        }
                        g.setColor(hovering ? ACCENT : BORDER);
                        if (getHeight() >= getWidth()) {
                            int m = getWidth() / 2;
                            g.drawLine(m, 0, m, getHeight());
                        } else {
                            int m = getHeight() / 2;
                            g.drawLine(0, m, getWidth(), m);
                        }
                    }
                };
            }
        });
    }

    /** Invisible split divider (no vertical strip between panes). */
    public static void styleSplitInvisible(JSplitPane split) {
        split.setOpaque(true);
        split.setBackground(BG);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setDividerSize(0);
        split.setUI(new BasicSplitPaneUI() {
            @Override
            public BasicSplitPaneDivider createDefaultDivider() {
                return new BasicSplitPaneDivider(this) {
                    @Override
                    public void paint(Graphics g) {
                        g.setColor(BG);
                        g.fillRect(0, 0, getWidth(), getHeight());
                    }
                };
            }
        });
    }

    public static void styleField(JTextField field) {
        field.setUI(new BasicTextFieldUI());
        field.setOpaque(true);
        field.setFont(uiFont);
        field.setForeground(TEXT);
        field.setBackground(FIELD_BG);
        field.setCaretColor(TEXT);
        field.setSelectionColor(new Color(0x4a5568));
        field.setSelectedTextColor(TEXT);
        field.setBorder(BorderFactory.createCompoundBorder(
                new MetalBorder(BORDER),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        installRussianTextPopup(field);
    }

    public static JButton styledButton(String text, boolean primary) {
        return new SiteButton(text, primary);
    }

    private static Font pickFont(String[] candidates, int style, float size) {
        if (availableFontFamilies == null) {
            availableFontFamilies = new HashSet<>();
            for (String family : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
                availableFontFamilies.add(family.toLowerCase());
            }
        }
        for (String name : candidates) {
            if (!Font.SANS_SERIF.equals(name)
                    && !Font.MONOSPACED.equals(name)
                    && !Font.DIALOG.equals(name)
                    && !availableFontFamilies.contains(name.toLowerCase())) {
                continue;
            }
            Font font = new Font(name, style, Math.round(size));
            if (font.canDisplayUpTo(CYRILLIC_PROBE) == -1) {
                return font.deriveFont(style, size);
            }
        }
        Font fallback = new Font(Font.DIALOG, style, Math.round(size));
        return fallback.deriveFont(style, size);
    }
}
