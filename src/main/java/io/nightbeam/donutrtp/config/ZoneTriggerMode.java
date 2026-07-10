package io.nightbeam.donutrtp.config;

public enum ZoneTriggerMode {
    ENTER,
    INTERACT,
    BOTH;

    public boolean allowsEnter() {
        return this == ENTER || this == BOTH;
    }

    public static ZoneTriggerMode fromString(String raw, ZoneTriggerMode fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return ZoneTriggerMode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
