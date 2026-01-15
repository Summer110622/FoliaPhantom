package com.example.testplugin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class TestPlugin extends JavaPlugin implements CommandExecutor {

    @Override
    public void onEnable() {
        getLogger().info("TestPlugin enabled!");
        this.getCommand("testteleport").setExecutor(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            player.teleport(player.getLocation().add(0, 10, 0));
            player.sendMessage("Teleported!");
            return true;
        }
        return false;
    }
}
