package com.example.test;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class TestPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        new BukkitRunnable() {
            @Override
            public void run() {
                getLogger().info("Attempting to get objective from async task...");
                try {
                    // This call should be transformed by Folia Phantom
                    org.bukkit.scoreboard.Objective objective = Bukkit.getScoreboardManager().getMainScoreboard().getObjective("test");
                    if (objective == null) {
                        getLogger().info("Objective 'test' not found, which is expected.");
                    } else {
                        getLogger().warning("Objective 'test' was found unexpectedly.");
                    }
                } catch (Exception e) {
                    getLogger().severe("An exception occurred while getting the objective: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }.runTaskLaterAsynchronously(this, 20L); // Run async after 1 second
    }
}
