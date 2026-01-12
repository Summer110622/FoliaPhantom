package com.example.scoreboardtest;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public class ScoreboardTest extends JavaPlugin {

    @Override
    public void onEnable() {
        // Get a player to test with
        Player player = Bukkit.getPlayer("TestPlayer");
        if (player == null) {
            getLogger().warning("Test player not found!");
        }

        // Get the main scoreboard
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();

        // Register a new objective
        Objective objective = scoreboard.registerNewObjective("test", "dummy");
        objective.setDisplayName("Test Objective");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.getScore("player1").setScore(10);

        // Register a new team
        Team team = scoreboard.registerNewTeam("testTeam");
        team.setPrefix("Test");
        team.addEntry("player1");

        // Unregister them
        objective.unregister();
        team.unregister();
    }
}
