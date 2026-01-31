package com.testplugin;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.Collection;
import java.util.List;

public class TestPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        // This is the call we want to transform
        Bukkit.getPluginManager().callEvent(new PlayerJoinEvent(null, "test"));

        // Testing getOnlinePlayers (redirect to _o)
        Collection<? extends Player> players = Bukkit.getOnlinePlayers();
        getLogger().info("Online players: " + players.size());

        // Testing getWorlds (redirect to _w)
        List<World> worlds = Bukkit.getWorlds();
        getLogger().info("Worlds: " + worlds.size());
    }
}
