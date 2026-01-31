package com.example.testplugin;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class TestPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getLogger().info("TestPlugin enabled!");
        World world = getServer().getWorlds().get(0);
        if (world != null) {
            for (Player player : world.getPlayers()) {
                getLogger().info("Found player: " + player.getName());
            }
        }

        // Test OfflinePlayer transformation
        OfflinePlayer op1 = Bukkit.getOfflinePlayer("Marv");
        getLogger().info("OfflinePlayer lookup test 1: " + op1.getName());

        OfflinePlayer op2 = getServer().getOfflinePlayer(java.util.UUID.randomUUID());
        getLogger().info("OfflinePlayer lookup test 2: " + op2.getUniqueId());
    }
}
