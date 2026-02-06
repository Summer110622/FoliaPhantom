package com.example.testplugin;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.attribute.Attribute;
import org.bukkit.Bukkit;

public class TestPlugin extends JavaPlugin {
  @Override
  public void onEnable() {
    getLogger().info("TestPlugin enabled!");

    // Run asynchronously to trigger the need for thread-safety
    Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
      World world = getServer().getWorlds().get(0);
      if (world != null && !world.getPlayers().isEmpty()) {
        Player player = world.getPlayers().iterator().next();

        // Potion Effects
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1));
        boolean hasSpeed = player.hasPotionEffect(PotionEffectType.SPEED);
        PotionEffect speed = player.getPotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.SPEED);

        // Attributes
        if (player.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
          double maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
          getLogger().info("Max Health: " + maxHealth);
        }
      }
    });
  }
}
