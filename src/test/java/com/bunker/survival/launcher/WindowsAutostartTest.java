package com.bunker.survival.launcher;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowsAutostartTest {

    @Test
    void quotesWindowsPathsAndKeepsTrayArgs() {
        WindowsAutostart.LaunchSpec spec = new WindowsAutostart.LaunchSpec(
                "C:\\Users\\name\\AppData\\Local\\Programs\\Platforma151\\Platforma151.exe",
                "--tray");
        assertEquals(
                "\"C:\\Users\\name\\AppData\\Local\\Programs\\Platforma151\\Platforma151.exe\" --tray",
                spec.commandLine());
    }

    @Test
    void psSingleQuotedEscapesApostrophe() {
        assertEquals("'O''Brien'", WindowsAutostart.psSingleQuoted("O'Brien"));
    }

    @Test
    void runKeyRoundTripOnWindows() throws Exception {
        Assumptions.assumeTrue(LauncherPaths.isWindows(), "Registry autostart is Windows-only");
        String name = "Platforma151AutostartUnitTest";
        try {
            WindowsAutostart.registerRunValue(name, "\"C:\\Windows\\System32\\notepad.exe\" --tray");
            WindowsAutostart.enableStartupApproved(
                    "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\StartupApproved\\Run",
                    name);
            assertTrue(WindowsAutostart.isRunValueSet(name));
        } finally {
            WindowsAutostart.removeRunValue(name);
        }
        assertFalse(WindowsAutostart.isRunValueSet(name));
    }
}
