package io.nightbeam.donutrtp.config;

import io.nightbeam.donutrtp.rtp.WorldType;

public record WorldGuardZoneSettings(
        boolean enabled,
        boolean worldGuardEnabled,
        String region,
        String worldName,
        ZoneTriggerMode triggerMode,
        boolean requirePlayerMovement,
        int cooldownSeconds,
        String cooldownMessage,
        boolean bypassEnabled,
        String bypassPermission,
        boolean countdownEnabled,
        int countdownSeconds,
        boolean cancelOnMove,
        WorldType destinationWorldType,
        String messageEnteringZone,
        String messageSearching,
        String messageTeleported,
        String messageNoSafeLocation,
        String messageCountdownCancelled
) {

    public boolean hasRegionConfigured() {
        return region != null && !region.isBlank()
                && worldName != null && !worldName.isBlank();
    }

    public boolean isWorldGuardFeatureActive() {
        return enabled && worldGuardEnabled && hasRegionConfigured();
    }
}
