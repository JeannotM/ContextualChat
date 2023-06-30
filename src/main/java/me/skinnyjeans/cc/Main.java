package me.skinnyjeans.cc;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Main extends JavaPlugin implements Listener {

    private final static String[] arguments = { "author", "reload", "reset" };
    private Metrics METRICS;

    private boolean contextualFormatHasPlayer = false, formatHasPlayer = false;
    private String contextualFormat = null, format = null;

    private int maxCounter = 5, seconds = 120, counter = 0;
    private UUID lastPlayer = null;
    private long lastTime = 0L;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginCommand("contextualchat").setExecutor(this);
        Bukkit.getPluginCommand("contextualchat").setTabCompleter(this);
        METRICS = new Metrics(this, 18940);

        loadConfig();
    }

    public void loadConfig() {
        try {
            File configFile = new File(this.getDataFolder(), "config.yml");
            if (!configFile.exists())
                saveResource("config.yml", false);

            YamlConfiguration config = new YamlConfiguration();
            config.load(configFile);

            counter = 0;
            lastTime = 0;
            lastPlayer = null;

            seconds = config.getInt("seconds", 120);
            format = config.getString("format", null);
            maxCounter = config.getInt("max-contextual-count", 5);
            contextualFormat = config.getString("contextual-format", null);

            formatHasPlayer = format != null && format.contains("{player}");
            contextualFormatHasPlayer = contextualFormat != null && contextualFormat.contains("{player}");

            METRICS.addCustomChart(new Metrics.SimplePie("seconds_until_reset", () -> String.valueOf(seconds) ));
            METRICS.addCustomChart(new Metrics.SimplePie("max_message_counter", () -> String.valueOf(maxCounter)));
            METRICS.addCustomChart(new Metrics.SimplePie("format_has_player", () -> (format != null) + "_" + (formatHasPlayer)));
            METRICS.addCustomChart(new Metrics.SimplePie("context_has_player", () -> (contextualFormat != null) + "_" + (contextualFormatHasPlayer)));
        } catch (IOException | InvalidConfigurationException e) { e.printStackTrace(); }
    }

    @Override
    public void onDisable() { METRICS.shutdown(); }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if(args.length == 1)
            if(args[0].equalsIgnoreCase("author")) {
                sender.sendMessage("ContextualChat was made by SkinnyJeans");
            } else if(args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                loadConfig();
                sender.sendMessage("ContextualChat has been reloaded!");
            } else if(args[0].equalsIgnoreCase("reset")) {
                counter = 0;
                lastTime = 0;
                lastPlayer = null;
                sender.sendMessage("Counter has been reset!");
            }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        List<String> list = new ArrayList<>();
        if(args.length == 1) {
            String arg = args[0].toLowerCase();
            for(String argument : arguments)
                if(argument.contains(arg))
                    list.add(argument);
        }

        return list;
    }

    @EventHandler (priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncPlayerChatEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        long currentTime = System.currentTimeMillis() / 1000;

        // Event crashes if you try to take out the format of the player
        if(contextualFormat != null && lastPlayer == uuid && (currentTime - lastTime) < seconds && counter++ < maxCounter) {
            if(contextualFormatHasPlayer) {
                e.setFormat(formatText(e.getPlayer(), contextualFormat));
            } else formatMessage(e, contextualFormat);
        } else if(format != null) {
            counter = 0;
            if(formatHasPlayer) {
                e.setFormat(formatText(e.getPlayer(), format));
            } else formatMessage(e, format);
        }

        lastTime = currentTime;
        lastPlayer = uuid;
    }

    private String formatText(Player player, String text) {
        text = getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")
                ? PlaceholderAPI.setPlaceholders(player, text) : text;

        text = text.replace("%", "{").replace("{player}", "%1$s").replace("{message}", "%2$s");

        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private void formatMessage(AsyncPlayerChatEvent e, String currentFormat) {
        Bukkit.getLogger().info(e.getPlayer().getName() + " -> " + e.getMessage());
        String message = formatText(e.getPlayer(), currentFormat).replace("%2$s", e.getMessage());
        e.getRecipients().forEach(recipient -> recipient.sendMessage(e.getPlayer().getUniqueId(), message));
        e.setCancelled(true);
    }
}
