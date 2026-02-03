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

    public void testEntitySync(org.bukkit.entity.Player player) {
        player.addPassenger(player);
        player.removePassenger(player);
        player.eject();
        player.getNearbyEntities(10, 10, 10);
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, 100, 1));
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.SPEED);
    }

    public void otherMethod() {
        Bukkit.getOnlinePlayers();
        Bukkit.getWorlds();
    }
}
