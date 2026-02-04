package com.example.testplugin;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.bukkit.FluidCollisionMode;

public class TestPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getLogger().info("TestPlugin enabled!");
        World world = getServer().getWorlds().get(0);
        if (world != null) {
            // New Chunk API
            Chunk chunk = world.getChunkAt(0, 0);
            chunk.load(true);
            chunk.getEntities();

            // New RayTrace API
            Location loc = new Location(world, 0, 100, 0);
            Vector dir = new Vector(0, -1, 0);
            world.rayTraceBlocks(loc, dir, 10.0, FluidCollisionMode.NEVER);

            // New Entity API
            Player firstPlayer = null;
            for (Player player : world.getPlayers()) {
                firstPlayer = player;
                getLogger().info("Found player: " + player.getName());
            }

            if (firstPlayer != null) {
                firstPlayer.addScoreboardTag("folia-phantom-test");
                firstPlayer.getNearbyEntities(5, 5, 5);
                firstPlayer.eject();
            }
        }
    }
}
