package com.testplugin;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.UUID;

public class TestPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getLogger().info("Enabled");
        Bukkit.getOnlinePlayers();
        Bukkit.getWorlds();
        Bukkit.getPlayer("test");
        Bukkit.getPlayer(UUID.randomUUID());
        Bukkit.getWorld("world");
        Bukkit.getWorld(UUID.randomUUID());
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "test");
        Bukkit.getOfflinePlayer("test");
        Bukkit.getOfflinePlayer(UUID.randomUUID());
    }

    public void otherMethod() {
        Bukkit.getOnlinePlayers();
        Bukkit.getWorlds();
    }
}
