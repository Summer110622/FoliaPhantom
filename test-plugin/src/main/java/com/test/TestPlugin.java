
package com.test;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.logging.Logger;

public class TestPlugin extends JavaPlugin {
    private static final Logger LOGGER = Logger.getLogger("TestPlugin");

    @Override
    public void onEnable() {
        // This is a classic thread-unsafe call that SchedulerClassTransformer should patch.
        // It will be transformed into a call to FoliaPatcher.runTask(this, () -> ...).
        Bukkit.getScheduler().runTask(this, () -> {
            LOGGER.info("This task is running on the main server thread.");
        });

        getLogger().info("TestPlugin enabled. Scheduler task has been submitted.");
    }

    @Override
    public void onDisable() {
        getLogger().info("TestPlugin disabled.");
    }
}
