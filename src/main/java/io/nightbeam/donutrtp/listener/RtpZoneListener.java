package io.nightbeam.donutrtp.listener;

import io.nightbeam.donutrtp.rtp.RtpZoneManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class RtpZoneListener implements Listener {

    private final RtpZoneManager rtpZoneManager;

    public RtpZoneListener(RtpZoneManager rtpZoneManager) {
        this.rtpZoneManager = rtpZoneManager;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        // Ignore yaw/pitch-only movement
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        rtpZoneManager.onPlayerMove(event.getPlayer(), event.getFrom(), event.getTo());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        rtpZoneManager.onPlayerQuit(event.getPlayer());
    }
}
