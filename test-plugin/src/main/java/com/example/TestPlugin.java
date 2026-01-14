package com.example;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class TestPlugin extends JavaPlugin {
    private GetPlayersTester tester;

    @Override
    public void onEnable() {
        tester = new GetPlayersTester(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            tester.printPlayerList(player.getWorld());
        }
        return false;
    }
}
