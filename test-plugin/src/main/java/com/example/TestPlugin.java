package com.example;

import org.bukkit.Bukkit;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class TestPlugin extends JavaPlugin implements Listener {
    @Override
    public void onEnable() {
        int count = Bukkit.getServer().getOnlinePlayers().size();
        getLogger().info("There are " + count + " players online.");
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        World world = event.getPlayer().getWorld();
        int entityCount = world.getEntities().size();
        getLogger().info("Player " + event.getPlayer().getName() + " joined a world with " + entityCount + " entities.");

        // Add more calls to test other transformers
        org.bukkit.block.Block block = world.getBlockAt(0, 0, 0);
        block.setBlockData(block.getBlockData()); // Test setBlockData(BlockData)
        world.getHighestBlockAt(0, 0);
        world.getNearbyEntities(event.getPlayer().getLocation(), 10, 10, 10);
    }
}
