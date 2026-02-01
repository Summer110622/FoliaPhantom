package com.example.testplugin;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public class TestPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getLogger().info("TestPlugin enabled!");
        World world = Bukkit.getWorlds().get(0);
        if (world != null) {
            for (Player player : world.getPlayers()) {
                getLogger().info("Found player: " + player.getName());

                // Test Entity interactions
                player.getNearbyEntities(10, 10, 10);
                player.eject();
            }

            // Test Chunk operations
            Chunk chunk = world.getChunkAt(0, 0);
            chunk.getEntities();
            chunk.load(true);
            chunk.isLoaded();
        }

        // Test global read operations (HPAM)
        Bukkit.getOnlinePlayers();
        Bukkit.getWorlds();
    }
}
