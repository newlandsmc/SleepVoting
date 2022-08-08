package com.semivanilla.sleepvoting;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.*;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.stream.Collectors;

public final class SleepVoting extends JavaPlugin implements Listener {
    private static final String VANILLA_ACTIONBAR = "sleep.players_sleeping", VANILLA_ACTIONBAR_SLEEP_NOT_POSSIBLE = "sleep.not_possible", SKIPPING_NIGHT_ACTIONBAR = "sleep.skipping_night";
    private static SleepVoting instance;
    private Set<UUID> votes = new HashSet<>();
    private boolean isCurrentlyNight = false, skippingNight = false;
    private String worldName;
    private Component actionBar = null;
    private ProtocolManager protocolManager;

    public static SleepVoting getInstance() {
        return instance;
    }

    @Override
    public void onLoad() {
        protocolManager = ProtocolLibrary.getProtocolManager();
    }

    @Override
    public void onEnable() {
        instance = this;
        if (!getDataFolder().exists()) {
            getDataFolder().mkdir();
        }
        saveDefaultConfig();
        World world = Bukkit.getWorld(getConfig().getString("world", "world"));
        if (world == null) throw new IllegalStateException("World not found");
        world.setGameRule(GameRule.PLAYERS_SLEEPING_PERCENTAGE, 101);
        worldName = world.getName(); // We want the exact name of the world
        if (getConfig().getBoolean("action-bar.enabled"))
            actionBar = MiniMessage.miniMessage().deserialize(getConfig().getString("action-bar.bar", "<white>%player% has slept. %votes%/%required% votes to skip night."));

        getServer().getScheduler().runTaskTimer(this, this::update, 20, 20);

        getServer().getPluginManager().registerEvents(this, this);

        boolean protocolLib = Bukkit.getPluginManager().isPluginEnabled("ProtocolLib");
        if (getConfig().getBoolean("suppress-vanilla-actionbar", protocolLib)) {
            if (!protocolLib) {
                getLogger().severe("ProtocolLib is not installed. Disabling vanilla action bar suppression.");
                return;
            }
            getLogger().info("Registering ProtocolLib packet listener to suppress vanilla action bar.");
            protocolManager.addPacketListener(new PacketAdapter(this, ListenerPriority.NORMAL, PacketType.Play.Server.SET_ACTION_BAR_TEXT) {
                @Override
                public void onPacketSending(PacketEvent e) {
                    getLogger().info("Packet sent: " + e.getPacketType());
                    /*
                    Packet sent: SET_ACTION_BAR_TEXT[class=ClientboundSetActionBarTextPacket, id=67] | PacketContainer[type=SET_ACTION_BAR_TEXT[class=ClientboundSetActionBarTextPacket, id=67], structureModifier=StructureModifier[fieldType=class java.lang.Object, data=[com.comphenix.protocol.reflect.accessors.DefaultFieldAccessor@5faafad0, com.comphenix.protocol.reflect.accessors.DefaultFieldAccessor@20070d61, com.comphenix.protocol.reflect.accessors.DefaultFieldAccessor@6b03ad39]]] | null
                    Packet sent: SET_ACTION_BAR_TEXT[class=ClientboundSetActionBarTextPacket, id=67] | PacketContainer[type=SET_ACTION_BAR_TEXT[class=ClientboundSetActionBarTextPacket, id=67], structureModifier=StructureModifier[fieldType=class java.lang.Object, data=[com.comphenix.protocol.reflect.accessors.DefaultFieldAccessor@5faafad0, com.comphenix.protocol.reflect.accessors.DefaultFieldAccessor@20070d61, com.comphenix.protocol.reflect.accessors.DefaultFieldAccessor@6b03ad39]]] | null
                     */
                    if (e.getPacketType() == PacketType.Play.Server.SET_ACTION_BAR_TEXT) {
                        PacketContainer packet = e.getPacket();
                        for (WrappedChatComponent value : packet.getChatComponents().getValues()) {
                            getLogger().info(" - Packet value: " + value);
                        }
                        for (WrappedChatComponent[] value : packet.getChatComponentArrays().getValues()) {
                            getLogger().info(" - Packet value []: " + (value == null ? "null" : Arrays.toString(value)));
                        }
                        for (InternalStructure value : packet.getStructures().getValues()) {
                            getLogger().info(" - Structures: " + value);
                            if (value == null) continue;
                            if (value.getHandle() instanceof Component) {
                                Component component = (Component) value.getHandle();
                                Bukkit.broadcast(Component.text("[Dev] - ").append(component));
                            }
                            for (WrappedChatComponent wrappedChatComponent : value.getChatComponents().getValues()) {
                                getLogger().info("   - ChatComponent: " + wrappedChatComponent);
                            }
                            for (WrappedChatComponent[] wrappedChatComponents : value.getChatComponentArrays().getValues()) {
                                getLogger().info("   - ChatComponent []: " + (wrappedChatComponents == null ? "null" : Arrays.toString(wrappedChatComponents)));
                            }
                        }
                        for (Optional<InternalStructure> value : packet.getOptionalStructures().getValues()) {
                            getLogger().info(" - Optional structures: " + value);
                            if (value == null) continue;
                            for (WrappedChatComponent wrappedChatComponent : value.get().getChatComponents().getValues()) {
                                getLogger().info("   - ChatComponent: " + wrappedChatComponent);
                            }
                            for (WrappedChatComponent[] wrappedChatComponents : value.get().getChatComponentArrays().getValues()) {
                                getLogger().info("   - ChatComponent []: " + (wrappedChatComponents == null ? "null" : Arrays.toString(wrappedChatComponents)));
                            }
                        }
                    }
                    /*
                    EnumWrappers.ChatType chatType = e.getPacket().getChatTypes().read(0);
                    if(chatType == EnumWrappers.ChatType.GAME_INFO) {
                        PacketContainer packet = e.getPacket();
                        getLogger().info("a");
                        if (!e.getPlayer().getWorld().getName().equals(worldName)) {
                            return; // Not the world we're interested in
                        }
                        getLogger().info("b");
                        List<WrappedChatComponent> components = packet.getChatComponents().getValues();
                        for (WrappedChatComponent component : components) {
                            if (component == null) continue;
                            if (component.getJson().contains("\"translate\":\"" + VANILLA_ACTIONBAR + "\"") || component.getJson().contains("\"translate\":\"" + VANILLA_ACTIONBAR_SLEEP_NOT_POSSIBLE + "\"") ||
                            component.getJson().contains("\"translate\":\"" + SKIPPING_NIGHT_ACTIONBAR + "\"")) { // We're cancelling these actionbars since there is no actual api to modify them.
                                getLogger().info("Cancelling vanilla action bar: " + component.getJson());
                                e.setCancelled(true);
                                return;
                            } else getLogger().info("Not cancelling vanilla action bar: " + component.getJson());
                        }
                    }
                     */
                }

                @Override
                public void onPacketReceiving(PacketEvent event) {
                }
            });
            getLogger().info("ProtocolLib packet listener registered.");
        }
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
        Component actionBar = MiniMessage.miniMessage().deserialize(getConfig().getString("action-bar.skipping", "<white>Skipping night..."));
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
        return world.isClearWeather() ? (12542 - 10) : (12010 - 10); // Allow a small 10 tick buffer so the vote counts
    }

    public long getNightEnd(World world) {
        return world.isClearWeather() ? (23459 - 10) : (23991 - 10);
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
