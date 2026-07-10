package io.nightbeam.donutrtp.integration.worldguard;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Direct WorldGuard 7 API access. Only load this class when WorldGuard is present
 * to avoid {@link NoClassDefFoundError} with softdepend.
 */
final class WorldGuardAccess {

    boolean regionExists(String worldName, String regionId) {
        ProtectedRegion region = getRegion(worldName, regionId);
        return region != null;
    }

    boolean contains(Location location, String worldName, String regionId) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        if (worldName == null || regionId == null || regionId.isBlank()) {
            return false;
        }
        if (!location.getWorld().getName().equals(worldName)) {
            return false;
        }

        ProtectedRegion region = getRegion(worldName, regionId);
        if (region == null) {
            return false;
        }

        BlockVector3 vector = BlockVector3.at(
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()
        );
        return region.contains(vector);
    }

    Set<String> listRegionIds(String worldName) {
        RegionManager manager = getRegionManager(worldName);
        if (manager == null) {
            return Collections.emptySet();
        }
        return manager.getRegions().keySet().stream()
                .map(String::toLowerCase)
                .collect(Collectors.toUnmodifiableSet());
    }

    private ProtectedRegion getRegion(String worldName, String regionId) {
        if (worldName == null || regionId == null || regionId.isBlank()) {
            return null;
        }
        RegionManager manager = getRegionManager(worldName);
        if (manager == null) {
            return null;
        }
        return manager.getRegion(regionId);
    }

    private RegionManager getRegionManager(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        return container.get(BukkitAdapter.adapt(world));
    }
}
