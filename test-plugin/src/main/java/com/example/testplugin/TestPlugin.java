package com.example.testplugin;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Particle;
import org.bukkit.boss.BossBar;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;

public class TestPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getLogger().info("TestPlugin enabled!");
        World world = getServer().getWorlds().get(0);
        if (world != null) {
            for (Player player : world.getPlayers()) {
                getLogger().info("Found player: " + player.getName());

                // Test RayTrace
                world.rayTraceBlocks(player.getLocation(), new Vector(0, 1, 0), 5.0, FluidCollisionMode.NEVER);

                // Test Particle
                world.spawnParticle(Particle.FLAME, player.getLocation(), 10);
                player.spawnParticle(Particle.HEART, player.getLocation(), 5);
            }

            // Test BossBar
            BossBar bar = getServer().createBossBar("Test Bar", BarColor.RED, BarStyle.SOLID);
            if (!world.getPlayers().isEmpty()) {
                bar.addPlayer(world.getPlayers().iterator().next());
            }
            bar.removeAll();
        }
    }
}
