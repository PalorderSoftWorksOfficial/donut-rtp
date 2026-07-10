package io.nightbeam.donutrtp.config;

import io.nightbeam.donutrtp.rtp.WorldType;
import java.util.List;
import java.util.Map;

public record Settings(
        int warmupSeconds,
        int cooldownSeconds,
        int maxAttempts,
        boolean instantTeleport,
        boolean fillEmptySlots,
        Map<WorldType, GuiItemSettings> guiItems,
        TeleportSoundSettings teleportSound,
        ActionBarCooldownSoundSettings actionBarCooldownSound,
        Map<WorldType, WorldSettings> worlds,
        WorldGuardZoneSettings worldGuardZone,
        boolean rtpZonesEnabled,
        List<RtpZoneSettings> rtpZones
) {
}
