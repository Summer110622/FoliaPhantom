package com.testplugin;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class TestPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        // This is the call we want to transform
        Bukkit.getPluginManager().callEvent(new PlayerJoinEvent(null, "test"));

        // New test case for getOfflinePlayer
        Bukkit.getOfflinePlayer("test-user");
        getServer().getOfflinePlayer(java.util.UUID.randomUUID());
    }
}
