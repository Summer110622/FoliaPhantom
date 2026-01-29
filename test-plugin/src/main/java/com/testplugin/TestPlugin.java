package com.testplugin;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class TestPlugin extends JavaPlugin implements CommandExecutor {
    @Override
    public void onEnable() {
        // This is the call we want to transform
        Bukkit.getPluginManager().callEvent(new PlayerJoinEvent(null, "test"));
        this.getCommand("testoffline").setExecutor(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("testoffline")) {
            // This call should be transformed to getOfflinePlayerAsync
            sender.sendMessage("Getting offline player data for Notch...");
            Bukkit.getOfflinePlayer("Notch");
            sender.sendMessage("Got offline player data for Notch.");
            return true;
        }
        return false;
    }
}
