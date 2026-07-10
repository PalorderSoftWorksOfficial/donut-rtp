package io.nightbeam.donutrtp.rtp;

import io.nightbeam.donutrtp.config.ConfigManager;
import io.nightbeam.donutrtp.config.Settings;
import io.nightbeam.donutrtp.config.TeleportSoundSettings;
import io.nightbeam.donutrtp.config.WorldGuardZoneSettings;
import io.nightbeam.donutrtp.config.WorldSettings;
import io.nightbeam.donutrtp.util.FoliaCompat;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class RtpManager {

    private final JavaPlugin plugin;
    private final FoliaCompat foliaCompat;
    private final ConfigManager configManager;
    private final SafeLocationFinder locationFinder;

    private final Map<UUID, Long> cooldownUntilEpoch = new ConcurrentHashMap<>();
    private final Map<UUID, WarmupTask> warmups = new ConcurrentHashMap<>();

    public RtpManager(JavaPlugin plugin, FoliaCompat foliaCompat, ConfigManager configManager) {
        this.plugin = plugin;
        this.foliaCompat = foliaCompat;
        this.configManager = configManager;
        this.locationFinder = new SafeLocationFinder(foliaCompat);
    }

    public boolean isOnCooldown(Player player) {
        return remainingCooldownSeconds(player) > 0;
    }

    public long remainingCooldownSeconds(Player player) {
        long now = Instant.now().getEpochSecond();
        long cooldownUntil = cooldownUntilEpoch.getOrDefault(player.getUniqueId(), 0L);
        return Math.max(0L, cooldownUntil - now);
    }

    public boolean hasCooldownBypass(Player player) {
        WorldGuardZoneSettings zone = configManager.settings().worldGuardZone();
        if (!zone.bypassEnabled()) {
            return false;
        }
        String permission = zone.bypassPermission();
        return permission != null && !permission.isBlank() && player.hasPermission(permission);
    }

    public void applyCooldown(Player player, int seconds) {
        if (seconds <= 0 || hasCooldownBypass(player)) {
            return;
        }
        long expiresAt = Instant.now().getEpochSecond() + seconds;
        cooldownUntilEpoch.put(player.getUniqueId(), expiresAt);
    }

    public void startTeleport(Player player, WorldType type) {
        Settings settings = configManager.settings();

        if (!hasCooldownBypass(player) && isOnCooldown(player)) {
            long wait = remainingCooldownSeconds(player);
            player.sendMessage(configManager.message("cooldown").replace("%time%", String.valueOf(wait)));
            return;
        }

        WorldSettings worldSettings = settings.worlds().get(type);
        World world = Bukkit.getWorld(worldSettings.worldName());
        if (world == null) {
            player.sendMessage(configManager.message("world-not-found"));
            return;
        }

        if (settings.instantTeleport()) {
            doTeleport(player, world, worldSettings, settings, settings.cooldownSeconds(), null, null, null);
            return;
        }

        WarmupTask previous = warmups.remove(player.getUniqueId());
        if (previous != null) {
            previous.cancel(false);
        }

        WarmupTask warmup = new WarmupTask(
                foliaCompat,
                configManager,
                settings.actionBarCooldownSound(),
                player,
                settings.warmupSeconds(),
                () -> doTeleport(player, world, worldSettings, settings, settings.cooldownSeconds(), null, null, null),
                () -> player.sendMessage(configManager.message("cancelled-move"))
        );
        warmups.put(player.getUniqueId(), warmup);
        warmup.start();
    }

    public void teleportRandom(Player player, WorldType type) {
        teleportRandom(player, type, configManager.settings().cooldownSeconds(), null, null, null);
    }

    /**
     * Random teleport with custom cooldown duration and optional pre-colored messages.
     */
    public void teleportRandom(
            Player player,
            WorldType type,
            int cooldownSeconds,
            String searchingMessage,
            String teleportedMessage,
            String noSafeLocationMessage
    ) {
        Settings settings = configManager.settings();

        if (!hasCooldownBypass(player) && isOnCooldown(player)) {
            long wait = remainingCooldownSeconds(player);
            player.sendMessage(configManager.message("cooldown").replace("%time%", String.valueOf(wait)));
            return;
        }

        WorldSettings worldSettings = settings.worlds().get(type);
        World world = Bukkit.getWorld(worldSettings.worldName());
        if (world == null) {
            player.sendMessage(configManager.message("world-not-found"));
            return;
        }

        doTeleport(
                player,
                world,
                worldSettings,
                settings,
                cooldownSeconds,
                searchingMessage,
                teleportedMessage,
                noSafeLocationMessage
        );
    }

    public void cancelWarmupIfMoving(Player player) {
        WarmupTask warmup = warmups.remove(player.getUniqueId());
        if (warmup != null && warmup.isActive()) {
            warmup.cancel(true);
        }
    }

    public void shutdown() {
        warmups.values().forEach(task -> task.cancel(false));
        warmups.clear();
        cooldownUntilEpoch.clear();
    }

    private void doTeleport(
            Player player,
            World world,
            WorldSettings worldSettings,
            Settings settings,
            int cooldownSeconds,
            String searchingMessage,
            String teleportedMessage,
            String noSafeLocationMessage
    ) {
        if (!player.isOnline()) {
            warmups.remove(player.getUniqueId());
            return;
        }

        if (searchingMessage != null && !searchingMessage.isBlank()) {
            player.sendMessage(searchingMessage);
        }

        final String successMsg = teleportedMessage;
        final String failMsg = noSafeLocationMessage;

        foliaCompat.runAsync(() -> locationFinder.findSafeLocation(world, worldSettings, settings.maxAttempts())
                .thenAccept(location -> foliaCompat.runForEntity(player, () -> {
                    warmups.remove(player.getUniqueId());

                    if (!player.isOnline()) {
                        return;
                    }

                    if (location == null) {
                        if (failMsg != null && !failMsg.isBlank()) {
                            player.sendMessage(failMsg);
                        } else {
                            player.sendMessage(configManager.message("no-safe-location"));
                        }
                        return;
                    }

                    teleport(player, location);
                    playTeleportSound(player, settings);
                    applyCooldown(player, cooldownSeconds);
                    if (successMsg != null && !successMsg.isBlank()) {
                        player.sendMessage(successMsg);
                    } else {
                        player.sendMessage(configManager.message("teleported"));
                    }
                }))
                .exceptionally(throwable -> {
                    foliaCompat.runForEntity(player, () -> {
                        warmups.remove(player.getUniqueId());
                        if (player.isOnline()) {
                            if (failMsg != null && !failMsg.isBlank()) {
                                player.sendMessage(failMsg);
                            } else {
                                player.sendMessage(configManager.message("no-safe-location"));
                            }
                        }
                    });
                    plugin.getLogger().warning("Failed to find RTP location: " + throwable.getMessage());
                    return null;
                }));
    }

    private void teleport(Player player, Location location) {
        foliaCompat.teleport(player, location);
    }

    private void playTeleportSound(Player player, Settings settings) {
        TeleportSoundSettings soundSettings = settings.teleportSound();
        if (!soundSettings.enabled()) {
            return;
        }
        player.playSound(
                player.getLocation(),
                soundSettings.sound(),
                soundSettings.volume(),
                soundSettings.pitch()
        );
    }
}
