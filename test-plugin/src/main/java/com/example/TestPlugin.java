package com.example;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.World;
import java.util.List;
import org.bukkit.entity.Player;

public class TestPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getLogger().info("TestPlugin enabled!");
        World world = getServer().getWorlds().get(0);
        if (world != null) {
            List<Player> players = world.getPlayers();
            getLogger().info("Found " + players.size() + " players.");
        }
    }
}
