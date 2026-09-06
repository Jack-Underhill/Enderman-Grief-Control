package noendermangrief;

import noendermangrief.listener.EndermanBlockListener;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Main plugin entry point.
 *
 * This class is created and managed by the Paper/Spigot server.
 * It must match the "main" value in plugin.yml:
 *   noendermangrief.NoEndermanGriefPlugin
 */
public class NoEndermanGriefPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        // Ensure default config.yml is saved to the plugin data folder
        // (plugins/NoEndermanGrief/config.yml) if it does not exist.
        saveDefaultConfig();

        getLogger().info("NoEndermanGrief is enabling...");

        // Register our event listener so we can intercept enderman block changes.
        getServer().getPluginManager().registerEvents(
                new EndermanBlockListener(this),
                this
        );

        getLogger().info("NoEndermanGrief has been enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("NoEndermanGrief has been disabled.");
    }

    /**
     * Checks whether the plugin is enabled for a specific world.
     *
     * We first look for an explicit entry under "worlds.<worldName>".
     * If there is none, we fall back to the "default-enabled" flag.
     */
    public boolean isWorldEnabled(String worldName) {
        boolean defaultEnabled = getConfig().getBoolean("default-enabled", true);

        ConfigurationSection worldsSection = getConfig().getConfigurationSection("worlds");
        if (worldsSection != null && worldsSection.contains(worldName)) {
            return worldsSection.getBoolean(worldName);
        }

        return defaultEnabled;
    }

    /**
     * Whether per-event logging is enabled.
     */
    public boolean isLoggingEnabled() {
        return getConfig().getBoolean("logging.enabled", false);
    }

    /**
     * Logs that an enderman's block pickup or placement was denied. Bukkit's logger already
     * prefixes console output with "[NoEndermanGrief]" and its own timestamp, so the message
     * itself stays short.
     */
    public void logEndermanBlockCancel(Block block, String action) {
        String coords = block.getX() + ", " + block.getY() + ", " + block.getZ();
        getLogger().info("Denied " + action + " at (" + coords + ").");
    }

    private static final List<String> SUBCOMMANDS = List.of("reload", "status", "toggle", "set");
    private static final List<String> SET_KEYS = List.of("default", "logging");
    private static final List<String> BOOLEANS = List.of("true", "false");

    /**
     * Command handler for: /enderman <reload|status|toggle|set>
     * Lets admins reload config.yml, inspect current settings, and change them
     * in-game, all without restarting the server.
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("enderman")) {
            return false; // Not our command.
        }

        if (!sender.hasPermission("noendermangrief.admin")) {
            sender.sendMessage("You do not have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("Usage: /enderman <reload|status|toggle|set>");
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> handleReload(sender);
            case "status" -> handleStatus(sender, args);
            case "toggle" -> handleToggle(sender, args);
            case "set" -> handleSet(sender, args);
            default -> sender.sendMessage("Unknown subcommand. Usage: /enderman <reload|status|toggle|set>");
        }
        return true;
    }

    private void handleReload(CommandSender sender) {
        reloadConfig();
        sender.sendMessage("NoEndermanGrief configuration reloaded.");
        getLogger().info("Configuration reloaded by " + sender.getName());
    }

    private void handleStatus(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            String world = args[1];
            sender.sendMessage("World '" + world + "': " + (isWorldEnabled(world) ? "enabled" : "disabled"));
            return;
        }

        boolean defaultEnabled = getConfig().getBoolean("default-enabled", true);
        sender.sendMessage("Default: " + (defaultEnabled ? "enabled" : "disabled")
                + ", logging: " + (isLoggingEnabled() ? "enabled" : "disabled"));

        ConfigurationSection worldsSection = getConfig().getConfigurationSection("worlds");
        if (worldsSection != null) {
            for (String world : worldsSection.getKeys(false)) {
                sender.sendMessage("  " + world + ": " + (worldsSection.getBoolean(world) ? "enabled" : "disabled"));
            }
        }
    }

    private void handleToggle(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /enderman toggle <world> [true|false]");
            return;
        }

        String world = args[1];
        boolean newValue = args.length >= 3 ? Boolean.parseBoolean(args[2]) : !isWorldEnabled(world);
        getConfig().set("worlds." + world, newValue);
        saveConfig();
        sender.sendMessage("World '" + world + "' is now " + (newValue ? "enabled" : "disabled") + ".");
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("Usage: /enderman set <default|logging> <true|false>");
            return;
        }

        boolean value = Boolean.parseBoolean(args[2]);
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "default" -> {
                getConfig().set("default-enabled", value);
                saveConfig();
                sender.sendMessage("Default is now " + (value ? "enabled" : "disabled") + ".");
            }
            case "logging" -> {
                getConfig().set("logging.enabled", value);
                saveConfig();
                sender.sendMessage("Logging is now " + (value ? "enabled" : "disabled") + ".");
            }
            default -> sender.sendMessage("Usage: /enderman set <default|logging> <true|false>");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("enderman") || !sender.hasPermission("noendermangrief.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return startingWith(SUBCOMMANDS, args[0]);
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && (subcommand.equals("toggle") || subcommand.equals("status"))) {
            List<String> worlds = getServer().getWorlds().stream().map(World::getName).collect(Collectors.toList());
            return startingWith(worlds, args[1]);
        }
        if (args.length == 2 && subcommand.equals("set")) {
            return startingWith(SET_KEYS, args[1]);
        }
        if (args.length == 3 && (subcommand.equals("toggle") || subcommand.equals("set"))) {
            return startingWith(BOOLEANS, args[2]);
        }

        return Collections.emptyList();
    }

    private List<String> startingWith(List<String> options, String prefix) {
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lowerPrefix))
                .collect(Collectors.toList());
    }
}
