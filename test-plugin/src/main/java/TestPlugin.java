/*
 * This file is part of Folia Phantom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 Marv
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class TestPlugin extends JavaPlugin {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("testremove")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                // Get a nearby entity to test with
                for (Entity entity : player.getNearbyEntities(5, 5, 5)) {
                    // This is a thread-unsafe call that should be patched
                    entity.remove();
                    player.sendMessage("Removed entity: " + entity.getType());
                    return true;
                }
                player.sendMessage("No nearby entities found to remove.");
            }
            return true;
        }

        if (command.getName().equalsIgnoreCase("testtimeout")) {
            sender.sendMessage("Testing timeout by calling a blocking API from an async thread...");
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                try {
                    // This call will be transformed and should time out if the patch is applied correctly
                    int playerCount = Bukkit.getServer().getOnlinePlayers().size();
                    sender.sendMessage("Success! Found " + playerCount + " players.");
                } catch (Exception e) {
                    getLogger().severe("Caught expected exception during timeout test: " + e.getClass().getName());
                    e.printStackTrace();
                }
            });
            return true;
        }

        return false;
    }
}
