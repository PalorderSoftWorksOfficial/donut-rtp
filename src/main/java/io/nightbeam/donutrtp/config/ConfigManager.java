package io.nightbeam.donutrtp.config;

import io.nightbeam.donutrtp.rtp.WorldType;
import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ConfigManager {

    private static final Sound DEFAULT_TELEPORT_SOUND = Sound.ENTITY_ENDERMAN_TELEPORT;
    private static final Sound DEFAULT_ACTIONBAR_COOLDOWN_SOUND = Sound.BLOCK_NOTE_BLOCK_HAT;

    private final JavaPlugin plugin;
    private FileConfiguration messagesConfig;
    private volatile Settings cachedSettings;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
        ensureMissingDefaults();
        plugin.reloadConfig();

        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        ensureMissingMessageDefaults(messagesFile);
        this.messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

        FileConfiguration config = plugin.getConfig();
        int warmup = Math.max(0, config.getInt("warmup-seconds", 5));
        int cooldown = Math.max(0, config.getInt("cooldown-seconds", 300));
        int maxAttempts = Math.max(1, config.getInt("max-attempts", 60));
        boolean instantTeleport = config.getBoolean("instant-teleport", false);
        boolean fillEmpty = config.getBoolean("gui.fill-empty-slots", true);

        Map<WorldType, GuiItemSettings> guiItems = new EnumMap<>(WorldType.class);
        guiItems.put(WorldType.OVERWORLD, readGuiItemSettings(config, "gui.items.overworld", 11, Material.GRASS_BLOCK,
                "&aOverworld RTP",
                List.of("&7Click to randomly teleport", "&7in the Overworld")));
        guiItems.put(WorldType.NETHER, readGuiItemSettings(config, "gui.items.nether", 13, Material.NETHERRACK,
                "&cNether RTP",
                List.of("&7Click to randomly teleport", "&7in the Nether")));
        guiItems.put(WorldType.END, readGuiItemSettings(config, "gui.items.end", 15, Material.END_STONE,
                "&dEnd RTP",
                List.of("&7Click to randomly teleport", "&7in The End")));

        TeleportSoundSettings teleportSound = readTeleportSoundSettings(config);
        ActionBarCooldownSoundSettings actionBarCooldownSound = readActionBarCooldownSoundSettings(config);

        Map<WorldType, WorldSettings> worlds = new EnumMap<>(WorldType.class);
        worlds.put(WorldType.OVERWORLD, readWorldSettings(config, "worlds.overworld", "world", 5000, 60));
        worlds.put(WorldType.NETHER, readWorldSettings(config, "worlds.nether", "world_nether", 4000, 40));
        worlds.put(WorldType.END, readWorldSettings(config, "worlds.end", "world_the_end", 3000, 50));

        WorldGuardZoneSettings worldGuardZone = readWorldGuardZoneSettings(config);
        boolean rtpZonesEnabled = config.getBoolean("rtp-zones.enabled", false);
        List<RtpZoneSettings> rtpZones = readRtpZones(config);

        this.cachedSettings = new Settings(
                warmup,
                cooldown,
                maxAttempts,
                instantTeleport,
                fillEmpty,
                guiItems,
                teleportSound,
                actionBarCooldownSound,
                worlds,
                worldGuardZone,
                rtpZonesEnabled,
                rtpZones
        );
    }

    public Settings settings() {
        return Objects.requireNonNull(cachedSettings, "Settings not loaded yet");
    }

    public String message(String key) {
        return prefix() + plainMessage(key);
    }

    public String plainMessage(String key) {
        return color(messagesConfig.getString(key, ""));
    }

    public String formatPlaceholders(String input, Map<String, String> placeholders) {
        if (input == null) {
            return "";
        }
        String result = input;
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue() == null ? "" : entry.getValue();
                result = result.replace("{" + key + "}", value);
                result = result.replace("%" + key + "%", value);
            }
        }
        return color(result);
    }

    public String messageWithPlaceholders(String key, Map<String, String> placeholders) {
        return prefix() + formatPlaceholders(messagesConfig.getString(key, ""), placeholders);
    }

    public String zoneMessage(String configured, String fallbackKey, Map<String, String> placeholders) {
        String raw = (configured != null && !configured.isBlank())
                ? configured
                : messagesConfig.getString(fallbackKey, "");
        return prefix() + formatPlaceholders(raw, placeholders);
    }

    private String prefix() {
        return color(messagesConfig.getString("prefix", "&6&lRTP &8» "));
    }

    public List<String> messageList(String key) {
        return messagesConfig.getStringList(key).stream().map(this::color).toList();
    }

    public String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }

    public void setWorldGuardZone(String worldName, String regionId) {
        FileConfiguration config = plugin.getConfig();
        config.set("rtp-zone.enabled", true);
        config.set("rtp-zone.worldguard.enabled", true);
        config.set("rtp-zone.worldguard.world", worldName);
        config.set("rtp-zone.worldguard.region", regionId);
        plugin.saveConfig();
        reload();
    }

    public void removeWorldGuardZone() {
        FileConfiguration config = plugin.getConfig();
        config.set("rtp-zone.worldguard.region", "");
        plugin.saveConfig();
        reload();
    }

    private void ensureMissingMessageDefaults(File messagesFile) {
        if (!messagesFile.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(messagesFile);
        boolean changed = false;
        Map<String, String> defaults = Map.ofEntries(
                Map.entry("zone-countdown-actionbar", "&7Teleporting in &e%seconds% &7seconds..."),
                Map.entry("zone-set", "&aRTP zone set to region &e{region} &ain world &e{world}&a."),
                Map.entry("zone-removed", "&aRTP zone configuration cleared."),
                Map.entry("zone-region-missing", "&cWorldGuard region &e{region} &cwas not found in world &e{world}&c."),
                Map.entry("zone-worldguard-missing", "&cWorldGuard is not installed or not enabled."),
                Map.entry("zone-not-configured", "&cNo RTP zone region is configured. Use /donutrtp zone set <region>."),
                Map.entry("zone-info-header", "&6RTP Zone Info:")
        );
        for (Map.Entry<String, String> entry : defaults.entrySet()) {
            if (!yaml.isSet(entry.getKey())) {
                yaml.set(entry.getKey(), entry.getValue());
                changed = true;
            }
        }
        if (changed) {
            try {
                yaml.save(messagesFile);
            } catch (Exception ex) {
                plugin.getLogger().warning("Could not update messages.yml with new defaults: " + ex.getMessage());
            }
        }
    }

    private void ensureMissingDefaults() {
        FileConfiguration config = plugin.getConfig();
        boolean changed = false;

        if (!config.isSet("instant-teleport")) {
            config.set("instant-teleport", false);
            changed = true;
        }
        if (!config.isSet("actionbar-cooldown.enabled")) {
            config.set("actionbar-cooldown.enabled", false);
            changed = true;
        }
        if (!config.isSet("actionbar-cooldown.sound")) {
            config.set("actionbar-cooldown.sound", "BLOCK_NOTE_BLOCK_HAT");
            changed = true;
        }
        if (!config.isSet("actionbar-cooldown.volume")) {
            config.set("actionbar-cooldown.volume", 1.0);
            changed = true;
        }
        if (!config.isSet("actionbar-cooldown.pitch")) {
            config.set("actionbar-cooldown.pitch", 1.0);
            changed = true;
        }
        if (!config.isSet("rtp-zones.enabled")) {
            config.set("rtp-zones.enabled", false);
            changed = true;
        }

        // rtp-zone defaults (migration-safe)
        if (!config.isSet("rtp-zone.enabled")) {
            config.set("rtp-zone.enabled", true);
            changed = true;
        }
        if (!config.isSet("rtp-zone.destination-world-type")) {
            config.set("rtp-zone.destination-world-type", "OVERWORLD");
            changed = true;
        }
        if (!config.isSet("rtp-zone.worldguard.enabled")) {
            config.set("rtp-zone.worldguard.enabled", true);
            changed = true;
        }
        if (!config.isSet("rtp-zone.worldguard.region")) {
            config.set("rtp-zone.worldguard.region", "");
            changed = true;
        }
        if (!config.isSet("rtp-zone.worldguard.world")) {
            config.set("rtp-zone.worldguard.world", "world");
            changed = true;
        }
        if (!config.isSet("rtp-zone.trigger.mode")) {
            config.set("rtp-zone.trigger.mode", "ENTER");
            changed = true;
        }
        if (!config.isSet("rtp-zone.trigger.require-player-movement")) {
            config.set("rtp-zone.trigger.require-player-movement", true);
            changed = true;
        }
        if (!config.isSet("rtp-zone.cooldown.seconds")) {
            config.set("rtp-zone.cooldown.seconds", 300);
            changed = true;
        }
        if (!config.isSet("rtp-zone.cooldown.message")) {
            config.set("rtp-zone.cooldown.message",
                    "&cYou must wait &e{time} &cbefore using the RTP zone again.");
            changed = true;
        }
        if (!config.isSet("rtp-zone.cooldown.bypass-enabled")) {
            config.set("rtp-zone.cooldown.bypass-enabled", true);
            changed = true;
        }
        if (!config.isSet("rtp-zone.cooldown.bypass-permission")) {
            config.set("rtp-zone.cooldown.bypass-permission", "donutrtp.cooldown.bypass");
            changed = true;
        }
        if (!config.isSet("rtp-zone.countdown.enabled")) {
            config.set("rtp-zone.countdown.enabled", true);
            changed = true;
        }
        if (!config.isSet("rtp-zone.countdown.seconds")) {
            config.set("rtp-zone.countdown.seconds", 5);
            changed = true;
        }
        if (!config.isSet("rtp-zone.countdown.cancel-on-move")) {
            config.set("rtp-zone.countdown.cancel-on-move", false);
            changed = true;
        }
        if (!config.isSet("rtp-zone.messages.entering-zone")) {
            config.set("rtp-zone.messages.entering-zone", "&eRandom teleport starting...");
            changed = true;
        }
        if (!config.isSet("rtp-zone.messages.searching")) {
            config.set("rtp-zone.messages.searching", "&eSearching for a safe location...");
            changed = true;
        }
        if (!config.isSet("rtp-zone.messages.teleported")) {
            config.set("rtp-zone.messages.teleported", "&aYou were randomly teleported!");
            changed = true;
        }
        if (!config.isSet("rtp-zone.messages.no-safe-location")) {
            config.set("rtp-zone.messages.no-safe-location", "&cNo safe location could be found.");
            changed = true;
        }
        if (!config.isSet("rtp-zone.messages.countdown-cancelled")) {
            config.set("rtp-zone.messages.countdown-cancelled", "&cRTP zone countdown cancelled.");
            changed = true;
        }

        if (changed) {
            plugin.saveConfig();
        }
    }

    private WorldGuardZoneSettings readWorldGuardZoneSettings(FileConfiguration config) {
        boolean enabled = config.getBoolean("rtp-zone.enabled", true);
        boolean wgEnabled = config.getBoolean("rtp-zone.worldguard.enabled", true);
        String region = config.getString("rtp-zone.worldguard.region", "");
        String world = config.getString("rtp-zone.worldguard.world", "world");

        String modeRaw = config.getString("rtp-zone.trigger.mode", "ENTER");
        ZoneTriggerMode mode = ZoneTriggerMode.fromString(modeRaw, ZoneTriggerMode.ENTER);
        if (modeRaw != null && !modeRaw.isBlank()) {
            try {
                ZoneTriggerMode.valueOf(modeRaw.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Invalid rtp-zone.trigger.mode '" + modeRaw + "', using ENTER");
            }
        }

        boolean requireMovement = config.getBoolean("rtp-zone.trigger.require-player-movement", true);
        int cooldownSeconds = Math.max(0, config.getInt("rtp-zone.cooldown.seconds", 300));
        String cooldownMessage = config.getString(
                "rtp-zone.cooldown.message",
                "&cYou must wait &e{time} &cbefore using the RTP zone again."
        );
        boolean bypassEnabled = config.getBoolean("rtp-zone.cooldown.bypass-enabled", true);
        String bypassPermission = config.getString(
                "rtp-zone.cooldown.bypass-permission",
                "donutrtp.cooldown.bypass"
        );

        boolean countdownEnabled = config.getBoolean("rtp-zone.countdown.enabled", true);
        int countdownSeconds = Math.max(0, config.getInt("rtp-zone.countdown.seconds", 5));
        boolean cancelOnMove = config.getBoolean("rtp-zone.countdown.cancel-on-move", false);

        WorldType destination = WorldType.OVERWORLD;
        String destRaw = config.getString("rtp-zone.destination-world-type", "OVERWORLD");
        try {
            destination = WorldType.valueOf(destRaw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning(
                    "Invalid rtp-zone.destination-world-type '" + destRaw + "', using OVERWORLD"
            );
        }

        return new WorldGuardZoneSettings(
                enabled,
                wgEnabled,
                region == null ? "" : region.trim(),
                world == null ? "world" : world.trim(),
                mode,
                requireMovement,
                cooldownSeconds,
                cooldownMessage,
                bypassEnabled,
                bypassPermission == null ? "donutrtp.cooldown.bypass" : bypassPermission.trim(),
                countdownEnabled,
                countdownSeconds,
                cancelOnMove,
                destination,
                config.getString("rtp-zone.messages.entering-zone", "&eRandom teleport starting..."),
                config.getString("rtp-zone.messages.searching", "&eSearching for a safe location..."),
                config.getString("rtp-zone.messages.teleported", "&aYou were randomly teleported!"),
                config.getString("rtp-zone.messages.no-safe-location", "&cNo safe location could be found."),
                config.getString("rtp-zone.messages.countdown-cancelled", "&cRTP zone countdown cancelled.")
        );
    }

    private GuiItemSettings readGuiItemSettings(
            FileConfiguration config,
            String path,
            int defaultSlot,
            Material defaultMaterial,
            String defaultName,
            List<String> defaultLore
    ) {
        int slot = config.getInt(path + ".slot", defaultSlot);
        if (slot < 0 || slot > 26) {
            plugin.getLogger().warning("Invalid GUI slot " + slot + " at " + path + ", using default " + defaultSlot);
            slot = defaultSlot;
        }

        String name = color(config.getString(path + ".name", defaultName));
        List<String> lore = config.getStringList(path + ".lore");
        if (lore.isEmpty()) {
            lore = defaultLore;
        }
        lore = lore.stream().map(this::color).toList();

        String rawMaterial = config.getString(path + ".material");
        String headDatabaseId = parseHeadDatabaseId(rawMaterial, path + ".material");
        Material material = headDatabaseId != null
                ? defaultMaterial
                : parseMaterial(rawMaterial, defaultMaterial, path + ".material");
        HeadSettings head = readHeadSettings(config.getConfigurationSection(path + ".head"));

        return new GuiItemSettings(slot, name, lore, material, head, headDatabaseId);
    }

    private String parseHeadDatabaseId(String raw, String path) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (!trimmed.regionMatches(true, 0, "hdb-", 0, 4) || trimmed.length() <= 4) {
            return null;
        }
        String id = trimmed.substring(4).trim();
        if (id.isEmpty()) {
            plugin.getLogger().warning("Invalid HeadDatabase material at " + path + ", missing ID after hdb- prefix");
            return null;
        }
        return id;
    }

    private HeadSettings readHeadSettings(ConfigurationSection section) {
        if (section == null) {
            return new HeadSettings(null, null, null);
        }
        return new HeadSettings(
                section.getString("texture"),
                section.getString("uuid"),
                section.getString("player")
        );
    }

    private Material parseMaterial(String raw, Material fallback, String path) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        Material material = Material.matchMaterial(raw.trim());
        if (material == null || material.isAir() || !material.isItem()) {
            plugin.getLogger().warning("Invalid material '" + raw + "' at " + path + ", using default " + fallback);
            return fallback;
        }
        return material;
    }

    private TeleportSoundSettings readTeleportSoundSettings(FileConfiguration config) {
        boolean enabled = config.getBoolean("teleport-sound.enabled", true);
        String rawSound = config.getString("teleport-sound.sound", "ENTITY_ENDERMAN_TELEPORT");
        Sound sound = resolveSound(rawSound, DEFAULT_TELEPORT_SOUND, "teleport");
        float volume = clamp(config.getDouble("teleport-sound.volume", 1.0), 0.0, 2.0);
        float pitch = clamp(config.getDouble("teleport-sound.pitch", 1.0), 0.0, 2.0);
        return new TeleportSoundSettings(enabled, sound, volume, pitch);
    }

    private ActionBarCooldownSoundSettings readActionBarCooldownSoundSettings(FileConfiguration config) {
        boolean enabled = config.getBoolean("actionbar-cooldown.enabled", false);
        String rawSound = config.getString("actionbar-cooldown.sound", "BLOCK_NOTE_BLOCK_HAT");
        Sound sound = resolveSound(rawSound, DEFAULT_ACTIONBAR_COOLDOWN_SOUND, "actionbar-cooldown");
        float volume = clamp(config.getDouble("actionbar-cooldown.volume", 1.0), 0.0, 2.0);
        float pitch = clamp(config.getDouble("actionbar-cooldown.pitch", 1.0), 0.0, 2.0);
        return new ActionBarCooldownSoundSettings(enabled, sound, volume, pitch);
    }

    private Sound resolveSound(String raw, Sound fallback, String label) {
        if (raw == null || raw.isBlank()) {
            plugin.getLogger().warning(label + " sound is empty, using default " + fallback);
            return fallback;
        }

        String trimmed = raw.trim();
        try {
            return Sound.valueOf(trimmed.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            // Try registry lookup below.
        }

        NamespacedKey key = NamespacedKey.fromString(trimmed.contains(":") ? trimmed : "minecraft:" + trimmed);
        if (key != null) {
            Sound registrySound = Registry.SOUNDS.get(key);
            if (registrySound != null) {
                return registrySound;
            }
        }

        plugin.getLogger().warning("Invalid " + label + " sound '" + raw + "', using default " + fallback);
        return fallback;
    }

    private float clamp(double value, double min, double max) {
        return (float) Math.max(min, Math.min(max, value));
    }

    private WorldSettings readWorldSettings(
            FileConfiguration config,
            String path,
            String defaultWorld,
            int defaultRadius,
            int defaultMinY
    ) {
        String worldName = config.getString(path + ".world-name", defaultWorld);
        int radius = Math.max(1, config.getInt(path + ".radius", defaultRadius));
        int minY = config.getInt(path + ".min-y", defaultMinY);
        return new WorldSettings(worldName, radius, minY);
    }

    private List<RtpZoneSettings> readRtpZones(FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("rtp-zones.zones");
        if (section == null) {
            return List.of();
        }

        List<RtpZoneSettings> zones = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            String path = "rtp-zones.zones." + id;
            boolean enabled = config.getBoolean(path + ".enabled", true);
            String world = config.getString(path + ".world");
            if (world == null || world.isBlank()) {
                plugin.getLogger().warning("RTP zone '" + id + "' is missing world, skipping");
                continue;
            }

            String worldTypeRaw = config.getString(path + ".world-type", "OVERWORLD");
            WorldType worldType;
            try {
                worldType = WorldType.valueOf(worldTypeRaw.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning(
                        "Invalid world-type '" + worldTypeRaw + "' for RTP zone '" + id + "', skipping"
                );
                continue;
            }

            int countdown = Math.max(1, config.getInt(path + ".countdown-seconds", 10));
            double halfSizeX = Math.max(0.5, config.getDouble(path + ".half-size-x", 1.0));
            double halfSizeY = Math.max(0.5, config.getDouble(path + ".half-size-y", 1.0));
            double halfSizeZ = Math.max(0.5, config.getDouble(path + ".half-size-z", 1.0));

            zones.add(new RtpZoneSettings(
                    id,
                    enabled,
                    world.trim(),
                    config.getDouble(path + ".x"),
                    config.getDouble(path + ".y"),
                    config.getDouble(path + ".z"),
                    halfSizeX,
                    halfSizeY,
                    halfSizeZ,
                    countdown,
                    worldType,
                    config.getString(path + ".permission")
            ));
        }
        return List.copyOf(zones);
    }
}
