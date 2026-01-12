package com.example;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class TestPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        int count = Bukkit.getServer().getOnlinePlayers().size();
        getLogger().info("There are " + count + " players online.");
    }
}
