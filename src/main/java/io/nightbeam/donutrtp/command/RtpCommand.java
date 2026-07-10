package io.nightbeam.donutrtp.command;

import io.nightbeam.donutrtp.config.ConfigManager;
import io.nightbeam.donutrtp.config.WorldGuardZoneSettings;
import io.nightbeam.donutrtp.gui.GuiManager;
import io.nightbeam.donutrtp.integration.worldguard.WorldGuardHook;
import io.nightbeam.donutrtp.rtp.RtpZoneManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class RtpCommand implements CommandExecutor, TabCompleter {

    private final ConfigManager configManager;
    private final GuiManager guiManager;
    private final RtpZoneManager rtpZoneManager;

    public RtpCommand(ConfigManager configManager, GuiManager guiManager, RtpZoneManager rtpZoneManager) {
        this.configManager = configManager;
        this.guiManager = guiManager;
        this.rtpZoneManager = rtpZoneManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            return handleReload(sender);
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("zone")) {
            return handleZone(sender, args);
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (!player.hasPermission("donutrtp.use")) {
            player.sendMessage(configManager.message("no-permission"));
            return true;
        }

        guiManager.openMenu(player);
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("donutrtp.admin")) {
            sender.sendMessage(configManager.message("no-permission"));
            return true;
        }
        configManager.reload();
        rtpZoneManager.reload();
        sender.sendMessage(configManager.message("reloaded"));
        return true;
    }

    private boolean handleZone(CommandSender sender, String[] args) {
        if (!sender.hasPermission("donutrtp.zone.admin") && !sender.hasPermission("donutrtp.admin")) {
            sender.sendMessage(configManager.message("no-permission"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(configManager.color(
                    "&cUsage: /donutrtp zone <set|remove|info|reload> ..."
            ));
            return true;
        }

        String sub = args[1].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "set" -> handleZoneSet(sender, args);
            case "remove" -> handleZoneRemove(sender);
            case "info" -> handleZoneInfo(sender);
            case "reload" -> handleZoneReload(sender);
            default -> {
                sender.sendMessage(configManager.color(
                        "&cUnknown zone subcommand. Use set, remove, info, or reload."
                ));
                yield true;
            }
        };
    }

    private boolean handleZoneSet(CommandSender sender, String[] args) {
        WorldGuardHook hook = rtpZoneManager.worldGuardHook();
        if (!hook.isAvailable()) {
            sender.sendMessage(configManager.message("zone-worldguard-missing"));
            return true;
        }

        String worldName;
        String regionId;

        if (args.length == 3) {
            // /donutrtp zone set <region>
            if (!(sender instanceof Player player)) {
                sender.sendMessage(configManager.color(
                        "&cConsole must use: /donutrtp zone set <world> <region>"
                ));
                return true;
            }
            worldName = player.getWorld().getName();
            regionId = args[2];
        } else if (args.length >= 4) {
            // /donutrtp zone set <world> <region>
            worldName = args[2];
            regionId = args[3];
        } else {
            sender.sendMessage(configManager.color(
                    "&cUsage: /donutrtp zone set <region> OR /donutrtp zone set <world> <region>"
            ));
            return true;
        }

        if (Bukkit.getWorld(worldName) == null) {
            sender.sendMessage(configManager.message("world-not-found"));
            return true;
        }

        if (!hook.regionExists(worldName, regionId)) {
            sender.sendMessage(configManager.messageWithPlaceholders(
                    "zone-region-missing",
                    Map.of("region", regionId, "world", worldName)
            ));
            return true;
        }

        configManager.setWorldGuardZone(worldName, regionId);
        rtpZoneManager.reload();
        sender.sendMessage(configManager.messageWithPlaceholders(
                "zone-set",
                Map.of("region", regionId, "world", worldName)
        ));
        return true;
    }

    private boolean handleZoneRemove(CommandSender sender) {
        configManager.removeWorldGuardZone();
        rtpZoneManager.reload();
        sender.sendMessage(configManager.message("zone-removed"));
        return true;
    }

    private boolean handleZoneInfo(CommandSender sender) {
        WorldGuardZoneSettings zone = configManager.settings().worldGuardZone();
        WorldGuardHook hook = rtpZoneManager.worldGuardHook();

        sender.sendMessage(configManager.message("zone-info-header"));
        sender.sendMessage(configManager.color("&7Enabled: &f" + zone.enabled()));
        sender.sendMessage(configManager.color("&7WorldGuard available: &f" + hook.isAvailable()));
        sender.sendMessage(configManager.color("&7WorldGuard enabled in config: &f" + zone.worldGuardEnabled()));
        sender.sendMessage(configManager.color("&7Configured world: &f"
                + (zone.worldName() == null || zone.worldName().isBlank() ? "(none)" : zone.worldName())));
        sender.sendMessage(configManager.color("&7Configured region: &f"
                + (zone.region() == null || zone.region().isBlank() ? "(none)" : zone.region())));
        sender.sendMessage(configManager.color("&7Active WG path: &f" + rtpZoneManager.isUsingWorldGuardPath()));
        sender.sendMessage(configManager.color("&7Trigger mode: &f" + zone.triggerMode()));
        sender.sendMessage(configManager.color("&7Cooldown: &f" + zone.cooldownSeconds() + "s"));
        sender.sendMessage(configManager.color("&7Countdown: &f"
                + (zone.countdownEnabled() ? zone.countdownSeconds() + "s" : "disabled")));
        sender.sendMessage(configManager.color("&7Destination: &f" + zone.destinationWorldType()));
        return true;
    }

    private boolean handleZoneReload(CommandSender sender) {
        configManager.reload();
        rtpZoneManager.reload();
        sender.sendMessage(configManager.message("reloaded"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            List<String> options = new ArrayList<>();
            if ("reload".startsWith(input) && sender.hasPermission("donutrtp.admin")) {
                options.add("reload");
            }
            if ("zone".startsWith(input)
                    && (sender.hasPermission("donutrtp.zone.admin") || sender.hasPermission("donutrtp.admin"))) {
                options.add("zone");
            }
            return options;
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("zone")) {
            if (!(sender.hasPermission("donutrtp.zone.admin") || sender.hasPermission("donutrtp.admin"))) {
                return Collections.emptyList();
            }
            if (args.length == 2) {
                String input = args[1].toLowerCase(Locale.ROOT);
                List<String> subs = List.of("set", "remove", "info", "reload");
                return subs.stream().filter(s -> s.startsWith(input)).collect(Collectors.toList());
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("set")) {
                String input = args[2].toLowerCase(Locale.ROOT);
                // Suggest worlds and region ids (region ids from player's world if player)
                List<String> options = new ArrayList<>();
                for (World world : Bukkit.getWorlds()) {
                    if (world.getName().toLowerCase(Locale.ROOT).startsWith(input)) {
                        options.add(world.getName());
                    }
                }
                if (sender instanceof Player player) {
                    for (String regionId : rtpZoneManager.worldGuardHook().listRegionIds(player.getWorld().getName())) {
                        if (regionId.startsWith(input) && !options.contains(regionId)) {
                            options.add(regionId);
                        }
                    }
                }
                return options;
            }
            if (args.length == 4 && args[1].equalsIgnoreCase("set")) {
                String worldName = args[2];
                String input = args[3].toLowerCase(Locale.ROOT);
                return rtpZoneManager.worldGuardHook().listRegionIds(worldName).stream()
                        .filter(id -> id.startsWith(input))
                        .sorted()
                        .collect(Collectors.toList());
            }
        }

        return Collections.emptyList();
    }
}
