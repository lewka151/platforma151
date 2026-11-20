package com.bunker.survival.launcher;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;

public final class TitleBar extends JPanel {
    private final JLabel versionLabel;

    public TitleBar(JFrame frame, String version, Runnable onMinimize, Runnable onClose) {
        setLayout(new BorderLayout(8, 0));
        setOpaque(true);
        setBackground(LauncherTheme.BG2);
        setPreferredSize(new Dimension(720, 42));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, LauncherTheme.BORDER),
                BorderFactory.createEmptyBorder(0, 12, 0, 4)));

        versionLabel = new JLabel("v" + version);
        versionLabel.setForeground(LauncherTheme.MUTED);
        versionLabel.setFont(LauncherTheme.uiFontBold.deriveFont(15f));
        versionLabel.setOpaque(false);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        left.setOpaque(false);
        left.add(versionLabel);
        add(left, BorderLayout.WEST);

        ChromeButton minBtn = new ChromeButton(ChromeButton.Kind.MINIMIZE);
        ChromeButton closeBtn = new ChromeButton(ChromeButton.Kind.CLOSE);
        minBtn.addActionListener(e -> onMinimize.run());
        closeBtn.addActionListener(e -> onClose.run());

        JPanel chrome = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 5));
        chrome.setOpaque(false);
        chrome.add(minBtn);
        chrome.add(closeBtn);
        add(chrome, BorderLayout.EAST);

        WindowDrag.install(frame, this, left, versionLabel);
    }

    /** @deprecated brand is no longer shown; use {@link #TitleBar(JFrame, String, Runnable, Runnable)} */
    @Deprecated
    public TitleBar(JFrame frame, String brandName, String version, Runnable onMinimize, Runnable onClose) {
        this(frame, version, onMinimize, onClose);
    }

    public void setVersion(String version) {
        versionLabel.setText("v" + version);
    }
}
