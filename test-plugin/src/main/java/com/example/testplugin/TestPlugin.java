package com.example.testplugin;

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
    }
}
