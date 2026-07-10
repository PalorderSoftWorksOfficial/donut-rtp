package io.nightbeam.donutrtp.integration.worldguard;

import java.util.Collections;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

/**
 * Soft WorldGuard integration. Safe when WorldGuard is missing.
 */
public final class WorldGuardHook {

    private final boolean available;
    private final WorldGuardAccess access;
    private final Logger logger;
    private boolean warnedMissing;

    private WorldGuardHook(boolean available, WorldGuardAccess access, Logger logger) {
        this.available = available;
        this.access = access;
        this.logger = logger;
    }

    public static WorldGuardHook create(Plugin plugin) {
        Logger logger = plugin.getLogger();
        if (!Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
            logger.info("WorldGuard not found — WorldGuard RTP zone feature is disabled.");
            return new WorldGuardHook(false, null, logger);
        }
        try {
            WorldGuardAccess access = new WorldGuardAccess();
            logger.info("WorldGuard hooked — region RTP zones are available.");
            return new WorldGuardHook(true, access, logger);
        } catch (NoClassDefFoundError | Exception ex) {
            logger.warning("WorldGuard is installed but could not be hooked: " + ex.getMessage());
            return new WorldGuardHook(false, null, logger);
        }
    }

    public boolean isAvailable() {
        return available && access != null;
    }

    public boolean regionExists(String worldName, String regionId) {
        if (!isAvailable()) {
            return false;
        }
        return access.regionExists(worldName, regionId);
    }

    public boolean contains(Location location, String worldName, String regionId) {
        if (!isAvailable()) {
            return false;
        }
        return access.contains(location, worldName, regionId);
    }

    public Set<String> listRegionIds(String worldName) {
        if (!isAvailable()) {
            return Collections.emptySet();
        }
        return access.listRegionIds(worldName);
    }

    /**
     * Logs a single warning if a configured region is missing. Subsequent calls are silent.
     */
    public void warnOnceIfRegionMissing(String worldName, String regionId) {
        if (warnedMissing) {
            return;
        }
        if (!isAvailable()) {
            warnedMissing = true;
            logger.warning("RTP zone is configured but WorldGuard is unavailable. Region-based zones are disabled.");
            return;
        }
        if (worldName == null || worldName.isBlank() || regionId == null || regionId.isBlank()) {
            return;
        }
        if (Bukkit.getWorld(worldName) == null) {
            warnedMissing = true;
            logger.warning("RTP zone world '" + worldName + "' is not loaded. Region-based zones will not work until it is.");
            return;
        }
        if (!regionExists(worldName, regionId)) {
            warnedMissing = true;
            logger.warning("Configured RTP zone region '" + regionId + "' was not found in world '"
                    + worldName + "'. Region-based zones are inactive until fixed.");
        }
    }

    public void resetWarnings() {
        warnedMissing = false;
    }
}
