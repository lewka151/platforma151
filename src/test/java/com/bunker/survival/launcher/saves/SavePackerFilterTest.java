package com.bunker.survival.launcher.saves;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SavePackerFilterTest {

    @Test
    void globalBlacklistBlocksDebugLogsAndExe() {
        assertFalse(SavePacker.isGloballyExcluded("profiles/player.dat"));
        assertTrue(SavePacker.isGloballyExcluded("debug/trace.txt"));
        assertTrue(SavePacker.isGloballyExcluded("Logs/server.log"));
        assertTrue(SavePacker.isGloballyExcluded("cache/foo.bin"));
        assertTrue(SavePacker.isGloballyExcluded("temp/x.tmp"));
        assertTrue(SavePacker.isGloballyExcluded("crashdumps/a.dmp"));
        assertTrue(SavePacker.isGloballyExcluded("mod/__pycache__/x.pyc"));
        assertTrue(SavePacker.isGloballyExcluded("tools/cheat.exe"));
        assertTrue(SavePacker.isGloballyExcluded("native.dll"));
        assertTrue(SavePacker.isGloballyExcluded("game.log"));
        assertTrue(SavePacker.isGloballyExcluded("Thumbs.db"));
    }

    @Test
    void dayzLikeExcludesJunkButKeepsSaves() {
        GameSaveDefinition dayz = new GameSaveDefinition("dayz", "DayZ");
        dayz.setExcludeDirs(List.of("debug", "Logs", "logs"));
        dayz.setExcludeGlobs(List.of("**/*.log", "**/*.exe", "**/*.tmp", "**/*.dmp"));

        assertTrue(SavePacker.isAllowedSaveRelative("profiles/player.cfg", dayz));
        assertTrue(SavePacker.isAllowedSaveRelative("storage_1/players.db", dayz));
        assertFalse(SavePacker.isAllowedSaveRelative("debug/script.log", dayz));
        assertFalse(SavePacker.isAllowedSaveRelative("Logs/server_console.log", dayz));
        assertFalse(SavePacker.isAllowedSaveRelative("profiles/error.log", dayz));
        assertFalse(SavePacker.isAllowedSaveRelative("tools/injector.exe", dayz));
    }

    @Test
    void whitelistStillWorksAfterGlobalExclude() {
        GameSaveDefinition souls = new GameSaveDefinition("elden_ring", "Elden Ring");
        souls.setFileExtensions(List.of(".sl2"));

        assertTrue(SavePacker.isAllowedSaveRelative("ER0000.sl2", souls));
        assertFalse(SavePacker.isAllowedSaveRelative("readme.txt", souls));
        assertFalse(SavePacker.isAllowedSaveRelative("mod.dll", souls));
        assertFalse(SavePacker.isAllowedSaveRelative("cache/ER0000.sl2", souls));
    }

    @Test
    void perGameExcludeGlobs() {
        GameSaveDefinition def = new GameSaveDefinition("x", "X");
        def.setExcludeGlobs(List.of("**/Screenshots/**", "*.bak"));
        assertFalse(SavePacker.isAllowedSaveRelative("Screenshots/a.png", def));
        assertFalse(SavePacker.isAllowedSaveRelative("save.bak", def));
        assertTrue(SavePacker.isAllowedSaveRelative("save.dat", def));
    }
}
