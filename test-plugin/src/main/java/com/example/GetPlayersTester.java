package com.example;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class GetPlayersTester {

    private final JavaPlugin plugin;

    public GetPlayersTester(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void printPlayerList(World world) {
        List<Player> players = world.getPlayers();
        plugin.getLogger().info("Players in world " + world.getName() + ": " + players.size());
    }
}
