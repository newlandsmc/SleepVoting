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
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class SleepVoting extends JavaPlugin implements Listener {
    private static SleepVoting instance;
    private Set<UUID> votes = new HashSet<>();
    private boolean isCurrentlyNight = false, skippingNight = false;
    private String worldName;
    private Component actionBar = null;

    public static SleepVoting getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        if (!getDataFolder().exists()) {
            getDataFolder().mkdir();
        }
        saveDefaultConfig();
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
            votes.clear();
            isCurrentlyNight = false;
        } else if (!isCurrentlyNight && night) {
            votes.clear();
            isCurrentlyNight = true;
        }

        if (night) {
            if (votes.size() >= getRequiredVotes(world)) {
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
        votes.clear();
        if (getConfig().getBoolean("smooth.enabled")) {
            long addTicks = getConfig().getLong("smooth.add-ticks", 10);
            long runTicks = getConfig().getLong("smooth.run-ticks", 2);
            skippingNight = true;
            new SmoothTimeSetRunnable(world, addTicks).runTaskTimer(this, 0, runTicks);
        } else {
            world.setTime(getNightEnd(world));
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


    /*
    @EventHandler
    public void onTimeSkip(TimeSkipEvent event) {
        update();
    }
     */

    public void sendActionBar(World world, String sleepingPlayer, Player sleeping, boolean sendToAll) {
        if (actionBar == null) return;
        Component component = actionBar
                .replaceText(TextReplacementConfig.builder()
                        .matchLiteral("%player%")
                        .replacement(sleepingPlayer).build())
                .replaceText(TextReplacementConfig.builder()
                        .matchLiteral("%votes%")
                        .replacement(votes.size() + "")
                        .build())
                .replaceText(TextReplacementConfig.builder()
                        .matchLiteral("%required%")
                        .replacement(getRequiredVotes(world) + "")
                        .build());
        if (sendToAll) {
            for (Player player : world.getPlayers()) {
                player.sendActionBar(component);
            }
        }
        if (sleeping != null) {
            getServer().getScheduler().runTaskLater(this, () -> {
                sleeping.sendActionBar(component);
            }, getConfig().getLong("ticks-before-sending-new-actionbar", 5));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSleep(PlayerBedEnterEvent event) {
        if (event.getBedEnterResult() != PlayerBedEnterEvent.BedEnterResult.OK) return;
        if (!votes.add(event.getPlayer().getUniqueId())) {
            sendActionBar(event.getBed().getWorld(), event.getPlayer().getName(), event.getPlayer(), true);
            event.getPlayer().setStatistic(Statistic.TIME_SINCE_REST, 0);
        } else {
            sendActionBar(event.getBed().getWorld(), event.getPlayer().getName(), event.getPlayer(), false);
        }
    }

    public boolean isNight(World world) {
        return world.getTime() >= getNightStart(world) && world.getTime() <= getNightEnd(world);
    }

    public long getNightStart(World world) {
        return world.isClearWeather() ? 12542 : 12010;
    }

    public long getNightEnd(World world) {
        return world.isClearWeather() ? 23459 : 23991;
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

    public void setSkippingNight(boolean skippingNight) {
        this.skippingNight = skippingNight;
    }
}
