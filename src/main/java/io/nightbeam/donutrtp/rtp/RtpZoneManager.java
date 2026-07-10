package io.nightbeam.donutrtp.rtp;

import io.nightbeam.donutrtp.config.ConfigManager;
import io.nightbeam.donutrtp.config.RtpZoneSettings;
import io.nightbeam.donutrtp.config.Settings;
import io.nightbeam.donutrtp.config.WorldGuardZoneSettings;
import io.nightbeam.donutrtp.integration.worldguard.WorldGuardHook;
import io.nightbeam.donutrtp.util.FoliaCompat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class RtpZoneManager {

    private final FoliaCompat foliaCompat;
    private final ConfigManager configManager;
    private final RtpManager rtpManager;
    private final WorldGuardHook worldGuardHook;

    private final Map<UUID, ZoneCountdownTask> activeCountdowns = new HashMap<>();
    private final Set<UUID> playersInsideWorldGuardZone = new HashSet<>();
    private final Set<UUID> playersInsideLegacyZone = new HashSet<>();
    private Map<String, List<RtpZoneSettings>> legacyZonesByWorld = Map.of();
    private boolean useWorldGuardPath;

    public RtpZoneManager(
            FoliaCompat foliaCompat,
            ConfigManager configManager,
            RtpManager rtpManager,
            WorldGuardHook worldGuardHook
    ) {
        this.foliaCompat = foliaCompat;
        this.configManager = configManager;
        this.rtpManager = rtpManager;
        this.worldGuardHook = worldGuardHook;
        reload();
    }

    public void reload() {
        Settings settings = configManager.settings();
        WorldGuardZoneSettings wg = settings.worldGuardZone();

        worldGuardHook.resetWarnings();

        boolean wgConfigured = wg.isWorldGuardFeatureActive();
        useWorldGuardPath = wgConfigured && worldGuardHook.isAvailable();

        if (wgConfigured && !worldGuardHook.isAvailable()) {
            worldGuardHook.warnOnceIfRegionMissing(wg.worldName(), wg.region());
        } else if (useWorldGuardPath) {
            worldGuardHook.warnOnceIfRegionMissing(wg.worldName(), wg.region());
        }

        Map<String, List<RtpZoneSettings>> map = new HashMap<>();
        if (!useWorldGuardPath && settings.rtpZonesEnabled()) {
            for (RtpZoneSettings zone : settings.rtpZones()) {
                if (!zone.enabled()) {
                    continue;
                }
                map.computeIfAbsent(zone.worldName(), ignored -> new ArrayList<>()).add(zone);
            }
        }
        legacyZonesByWorld = Map.copyOf(map);

        // Cancel active countdowns on reload to avoid stale config
        shutdownCountdownsOnly();
        playersInsideWorldGuardZone.clear();
        playersInsideLegacyZone.clear();
    }

    public WorldGuardHook worldGuardHook() {
        return worldGuardHook;
    }

    public boolean isUsingWorldGuardPath() {
        return useWorldGuardPath;
    }

    public void onPlayerMove(Player player, Location from, Location to) {
        Settings settings = configManager.settings();

        ZoneCountdownTask active = activeCountdowns.get(player.getUniqueId());
        if (active != null) {
            handleActiveCountdownMove(player, from, to, active, settings);
            return;
        }

        if (useWorldGuardPath) {
            handleWorldGuardMove(player, from, to, settings.worldGuardZone());
            return;
        }

        handleLegacyCuboidMove(player, from, to, settings);
    }

    public void onPlayerQuit(Player player) {
        UUID id = player.getUniqueId();
        playersInsideWorldGuardZone.remove(id);
        playersInsideLegacyZone.remove(id);
        cancelCountdown(player, false);
    }

    public void shutdown() {
        shutdownCountdownsOnly();
        playersInsideWorldGuardZone.clear();
        playersInsideLegacyZone.clear();
    }

    private void shutdownCountdownsOnly() {
        activeCountdowns.values().forEach(task -> task.cancel(false));
        activeCountdowns.clear();
    }

    private void handleActiveCountdownMove(
            Player player,
            Location from,
            Location to,
            ZoneCountdownTask active,
            Settings settings
    ) {
        if (useWorldGuardPath) {
            WorldGuardZoneSettings wg = settings.worldGuardZone();
            boolean stillInside = isInsideWorldGuardZone(to, wg);
            if (!stillInside) {
                playersInsideWorldGuardZone.remove(player.getUniqueId());
                cancelCountdown(player, true);
                return;
            }
            if (wg.cancelOnMove() && blockChanged(from, to)) {
                cancelCountdown(player, true);
            }
            return;
        }

        // Legacy cuboid: leave zone cancels
        RtpZoneSettings zone = findLegacyZoneAt(to, settings);
        if (zone == null) {
            playersInsideLegacyZone.remove(player.getUniqueId());
            cancelCountdown(player, true);
        }
    }

    private void handleWorldGuardMove(Player player, Location from, Location to, WorldGuardZoneSettings wg) {
        if (!wg.enabled() || !wg.worldGuardEnabled()) {
            return;
        }
        if (!wg.triggerMode().allowsEnter()) {
            return;
        }
        if (wg.requirePlayerMovement() && !blockChanged(from, to)) {
            return;
        }

        UUID id = player.getUniqueId();
        boolean wasInside = playersInsideWorldGuardZone.contains(id);
        boolean nowInside = isInsideWorldGuardZone(to, wg);

        if (nowInside) {
            playersInsideWorldGuardZone.add(id);
        } else {
            playersInsideWorldGuardZone.remove(id);
        }

        if (!wasInside && nowInside) {
            tryStartWorldGuardZone(player, wg);
        }
    }

    private void handleLegacyCuboidMove(Player player, Location from, Location to, Settings settings) {
        if (!settings.rtpZonesEnabled()) {
            return;
        }

        UUID id = player.getUniqueId();
        RtpZoneSettings fromZone = findLegacyZoneAt(from, settings);
        RtpZoneSettings toZone = findLegacyZoneAt(to, settings);

        if (toZone != null) {
            playersInsideLegacyZone.add(id);
        } else {
            playersInsideLegacyZone.remove(id);
        }

        if (fromZone == null && toZone != null) {
            tryStartLegacyCountdown(player, toZone);
        }
    }

    private boolean isInsideWorldGuardZone(Location location, WorldGuardZoneSettings wg) {
        if (location == null || !wg.hasRegionConfigured()) {
            return false;
        }
        boolean inside = worldGuardHook.contains(location, wg.worldName(), wg.region());
        if (!inside) {
            // Re-check region existence once if player is in the right world
            if (location.getWorld() != null && location.getWorld().getName().equals(wg.worldName())) {
                worldGuardHook.warnOnceIfRegionMissing(wg.worldName(), wg.region());
            }
        }
        return inside;
    }

    private void tryStartWorldGuardZone(Player player, WorldGuardZoneSettings wg) {
        if (activeCountdowns.containsKey(player.getUniqueId())) {
            return;
        }
        if (!player.hasPermission("donutrtp.zone.use")) {
            return;
        }

        if (!rtpManager.hasCooldownBypass(player) && rtpManager.isOnCooldown(player)) {
            long wait = rtpManager.remainingCooldownSeconds(player);
            String msg = configManager.zoneMessage(
                    wg.cooldownMessage(),
                    "cooldown",
                    Map.of("time", String.valueOf(wait))
            );
            player.sendMessage(msg);
            return;
        }

        player.sendMessage(configManager.zoneMessage(
                wg.messageEnteringZone(),
                "zone-countdown-subtitle",
                Map.of()
        ));

        Runnable teleport = () -> {
            String searching = configManager.zoneMessage(wg.messageSearching(), "countdown", Map.of());
            String teleported = configManager.zoneMessage(wg.messageTeleported(), "teleported", Map.of());
            String noSafe = configManager.zoneMessage(wg.messageNoSafeLocation(), "no-safe-location", Map.of());
            rtpManager.teleportRandom(
                    player,
                    wg.destinationWorldType(),
                    wg.cooldownSeconds(),
                    searching,
                    teleported,
                    noSafe
            );
        };

        if (!wg.countdownEnabled() || wg.countdownSeconds() <= 0) {
            teleport.run();
            return;
        }

        UUID playerId = player.getUniqueId();
        ZoneCountdownTask task = new ZoneCountdownTask(
                foliaCompat,
                configManager,
                configManager.settings().actionBarCooldownSound(),
                player,
                wg.countdownSeconds(),
                () -> isInsideWorldGuardZone(player.getLocation(), configManager.settings().worldGuardZone()),
                teleport,
                () -> player.sendMessage(configManager.zoneMessage(
                        wg.messageCountdownCancelled(),
                        "zone-countdown-cancelled",
                        Map.of()
                )),
                () -> activeCountdowns.remove(playerId)
        );
        activeCountdowns.put(playerId, task);
        task.start();
    }

    private void tryStartLegacyCountdown(Player player, RtpZoneSettings zone) {
        if (activeCountdowns.containsKey(player.getUniqueId())) {
            return;
        }
        if (!player.hasPermission("donutrtp.zone.use")) {
            return;
        }
        if (zone.hasPermission() && !player.hasPermission(zone.permission())) {
            return;
        }

        if (!rtpManager.hasCooldownBypass(player) && rtpManager.isOnCooldown(player)) {
            long wait = rtpManager.remainingCooldownSeconds(player);
            player.sendMessage(configManager.message("cooldown").replace("%time%", String.valueOf(wait)));
            return;
        }

        UUID playerId = player.getUniqueId();
        ZoneCountdownTask task = new ZoneCountdownTask(
                foliaCompat,
                configManager,
                configManager.settings().actionBarCooldownSound(),
                player,
                zone.countdownSeconds(),
                () -> zone.contains(player.getLocation()),
                () -> rtpManager.teleportRandom(player, zone.worldType()),
                () -> player.sendMessage(configManager.message("zone-countdown-cancelled")),
                () -> activeCountdowns.remove(playerId)
        );
        activeCountdowns.put(playerId, task);
        task.start();
    }

    private void cancelCountdown(Player player, boolean sendCallback) {
        ZoneCountdownTask task = activeCountdowns.get(player.getUniqueId());
        if (task != null && task.isActive()) {
            task.cancel(sendCallback);
        }
    }

    private RtpZoneSettings findLegacyZoneAt(Location location, Settings settings) {
        if (location == null || location.getWorld() == null || !settings.rtpZonesEnabled()) {
            return null;
        }
        List<RtpZoneSettings> zones = legacyZonesByWorld.get(location.getWorld().getName());
        if (zones == null) {
            return null;
        }
        for (RtpZoneSettings zone : zones) {
            if (zone.contains(location)) {
                return zone;
            }
        }
        return null;
    }

    private static boolean blockChanged(Location from, Location to) {
        if (from == null || to == null) {
            return true;
        }
        return from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ();
    }
}
