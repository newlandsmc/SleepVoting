package com.semivanilla.sleepvoting;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

import javax.swing.*;
import java.util.List;
import java.util.stream.Collectors;

public final class SleepVoting extends JavaPlugin implements Listener {
    private long nightStart, nightEnd;
    private int votes = 0;
    private boolean isCurrentlyNight = false, skippingNight = false;
    private String worldName;

    private Component actionBar = null;

    private static SleepVoting instance;

    @Override
    public void onEnable() {
        instance = this;
        if (!getDataFolder().exists()) {
            getDataFolder().mkdir();
        }
        saveDefaultConfig();

        nightStart = getConfig().getLong("night-start", 13000);
        nightEnd = getConfig().getLong("night-end", 23000);
        worldName = getConfig().getString("world", "world");
        if (getConfig().getBoolean("action-bar.enabled"))
            actionBar = MiniMessage.miniMessage().deserialize(getConfig().getString("action-bar.bar", "<white>%player% has slept. %votes%/%required% votes to skip night."));

        getServer().getScheduler().runTaskTimer(this, this::update, 20, 20);

        getServer().getPluginManager().registerEvents(this, this);
    }

    public void update() {
        if (skippingNight) return;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;
        boolean night = isNight(world);
        if (isCurrentlyNight && !night) {
            votes = 0;
            isCurrentlyNight = false;
        } else if (!isCurrentlyNight && night) {
            votes = 0;
            isCurrentlyNight = true;
        }

        if (night) {
            if (votes >= getRequiredVotes(world)) {
                skipNight(world);
            }
        }
    }

    public void skipNight(World world) {
        Component actionBar;
        if (getConfig().getBoolean("action-bar.skipping-night.use-vanilla-bar")) {
            actionBar = Component.translatable("sleep.skipping_night");
        } else {
            actionBar = MiniMessage.miniMessage().deserialize(getConfig().getString("action-bar.skipping-night.bar", "<white>Skipping night..."));
        }
        for (Player player : world.getPlayers()) {
            player.sendActionBar(actionBar);
        }
        votes = 0;
        if (getConfig().getBoolean("smooth.enabled")) {
            long addTicks = getConfig().getLong("smooth.add-ticks", 10);
            long runTicks = getConfig().getLong("smooth.run-ticks", 2);
            skippingNight = true;
            new SmoothTimeSetRunnable(world, addTicks).runTaskTimer(this, 0, runTicks);
        } else {
            world.setTime(nightEnd);
            resetWeather(world);
        }
    }

    public void resetWeather(World world) {
        if (getConfig().getBoolean("reset-weather")) {
            world.setStorm(false);
            world.setThundering(false);
        }
    }

    public int getRequiredVotes(World world) {
        //Use percentage from config
        return (int) (getCountedPlayers(world).size() * getConfig().getDouble("votes-required", 0.5));
    }

    public List<Player> getCountedPlayers(World world) {
        return world.getPlayers().stream().filter(this::shouldCount).collect(Collectors.toList());
    }

    public void sendActionBar(World world, String sleepingPlayer) {
        if (actionBar == null) return;
        Component component = actionBar
                .replaceText(TextReplacementConfig.builder()
                        .matchLiteral("%player%")
                        .replacement(sleepingPlayer).build())
                .replaceText(TextReplacementConfig.builder()
                        .matchLiteral("%votes%")
                        .replacement(votes + "")
                        .build())
                .replaceText(TextReplacementConfig.builder()
                        .matchLiteral("%required%")
                        .replacement(getRequiredVotes(world) + "")
                        .build());
        for (Player player : world.getPlayers()) {
            player.sendActionBar(component);
        }
    }

    /*
    @EventHandler
    public void onTimeSkip(TimeSkipEvent event) {
        update();
    }
     */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSleep(PlayerBedEnterEvent event) {
        if (event.getBedEnterResult() != PlayerBedEnterEvent.BedEnterResult.OK) return;

        votes++;
        sendActionBar(event.getBed().getWorld(), event.getPlayer().getName());
        event.getPlayer().setStatistic(Statistic.TIME_SINCE_REST, 0);
    }

    public boolean isNight(World world) {
        return world.getTime() >= nightStart && world.getTime() <= nightEnd;
    }

    private boolean isVanished(Player player) {
        for (MetadataValue meta : player.getMetadata("vanished")) {
            if (meta.asBoolean()) return true;
        }
        return false;
    }

    private boolean shouldCount(Player player) {
        return !isVanished(player) && !player.isSleepingIgnored();
    }


    public long getNightEnd() {
        return nightEnd;
    }

    public long getNightStart() {
        return nightStart;
    }

    public static SleepVoting getInstance() {
        return instance;
    }

    public void setSkippingNight(boolean skippingNight) {
        this.skippingNight = skippingNight;
    }
}
