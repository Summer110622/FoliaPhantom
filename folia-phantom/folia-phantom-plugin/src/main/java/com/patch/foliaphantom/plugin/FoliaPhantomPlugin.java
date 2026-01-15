/*
 * This file is part of Folia Phantom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 Marv
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.patch.foliaphantom.plugin;

import com.patch.foliaphantom.core.patcher.FoliaPatcher;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.concurrent.TimeUnit;

public class FoliaPhantomPlugin extends JavaPlugin {

    private PluginWatcher watcher;
    private int watcherTaskId = -1;

    @Override
    public void onEnable() {
        // Save default configuration
        saveDefaultConfig();

        // Print banner
        printBanner();

        // Initialize plugin watcher
        try {
            watcher = new PluginWatcher(this);
            getLogger().info("Plugin watcher initialized.");

            // Setup folders
            setupFolders();

            // Start the watcher task
            startWatcherTask();
        } catch (Exception e) {
            getLogger().severe("Failed to initialize plugin watcher: " + e.getMessage());
            e.printStackTrace();
        }

        // Register commands
        PatchCommand patchCommand = new PatchCommand(this);
        getCommand("foliapatch").setExecutor(patchCommand);
        getCommand("foliapatch").setTabCompleter(patchCommand);

        // Log configuration
        logConfiguration();

        getLogger().info("FoliaPhantom enabled successfully!");
    }

    @Override
    public void onDisable() {
        // Stop the watcher task
        if (watcherTaskId != -1) {
            Bukkit.getAsyncScheduler().cancelTasks(this);
            getLogger().info("Plugin watcher stopped.");
        }

        // Print statistics
        if (watcher != null) {
            var stats = watcher.getStatistics();
            getLogger().info("=== Patching Statistics ===");
            getLogger().info("Total Processed: " + stats.get("total"));
            getLogger().info("Successfully Patched: " + stats.get("patched"));
            getLogger().info("Skipped: " + stats.get("skipped"));
            getLogger().info("Failed: " + stats.get("failed"));
        }

        getLogger().info("FoliaPhantom disabled.");
    }

    private void printBanner() {
        getLogger().info("========================================");
        getLogger().info("   FoliaPhantom v" + getPluginMeta().getVersion());
        getLogger().info("   Automatic Plugin Patcher for Folia");
        getLogger().info("========================================");
    }

    private void setupFolders() {
        File serverRoot = getDataFolder().getParentFile().getParentFile();
        File watchFolder = new File(serverRoot,
                getConfig().getString("auto-patch.watch-folder", "plugins/folia-patch-queue"));
        File outputFolder = new File(serverRoot, getConfig().getString("auto-patch.output-folder", "plugins/patched"));

        if (!watchFolder.exists()) {
            if (watchFolder.mkdirs()) {
                getLogger().info("Created watch folder: " + watchFolder.getAbsolutePath());
            }
        }

        if (!outputFolder.exists()) {
            if (outputFolder.mkdirs()) {
                getLogger().info("Created output folder: " + outputFolder.getAbsolutePath());
            }
        }
    }

    private void startWatcherTask() {
        if (!getConfig().getBoolean("auto-patch.enabled", true)) {
            getLogger().warning("Auto-patching is disabled in config.yml");
            return;
        }

        long checkInterval = getConfig().getLong("auto-patch.check-interval", 5);

        // Schedule async repeating task using Folia's AsyncScheduler
        Bukkit.getAsyncScheduler().runAtFixedRate(
                this,
                task -> {
                    if (watcher != null) {
                        watcher.run();
                    }
                },
                checkInterval,
                checkInterval,
                TimeUnit.SECONDS);

        getLogger().info("Auto-patching enabled (checking every " + checkInterval + " seconds)");
    }

    private void logConfiguration() {
        File serverRoot = getDataFolder().getParentFile().getParentFile();
        File watchFolder = new File(serverRoot, getConfig().getString("auto-patch.watch-folder"));
        File outputFolder = new File(serverRoot, getConfig().getString("auto-patch.output-folder"));

        getLogger().info("Configuration:");
        getLogger().info("  Watch Folder: " + watchFolder.getAbsolutePath());
        getLogger().info("  Output Folder: " + outputFolder.getAbsolutePath());
        getLogger().info("  Auto-Patch: " + (getConfig().getBoolean("auto-patch.enabled") ? "Enabled" : "Disabled"));
        getLogger().info("  Skip Folia-Supported: " + getConfig().getBoolean("filters.skip-folia-supported"));

        if (!getConfig().getStringList("filters.blacklist").isEmpty()) {
            getLogger().info("  Blacklist: " + getConfig().getStringList("filters.blacklist"));
        }
        if (!getConfig().getStringList("filters.whitelist").isEmpty()) {
            getLogger().info("  Whitelist: " + getConfig().getStringList("filters.whitelist"));
        }
    }

    public PluginWatcher getWatcher() {
        return watcher;
    }
}
