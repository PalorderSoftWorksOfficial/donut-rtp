package io.nightbeam.donutrtp.rtp;

import io.nightbeam.donutrtp.config.ActionBarCooldownSoundSettings;
import io.nightbeam.donutrtp.config.ConfigManager;
import io.nightbeam.donutrtp.util.FoliaCompat;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

public final class ZoneCountdownTask {

    private final FoliaCompat foliaCompat;
    private final ConfigManager configManager;
    private final ActionBarCooldownSoundSettings countdownSound;
    private final Player player;
    private final int initialSeconds;
    private final Supplier<Boolean> stillValid;
    private final Runnable onTickComplete;
    private final Runnable onCancelled;
    private final Runnable onFinished;

    private final AtomicBoolean active = new AtomicBoolean(true);
    private final AtomicInteger secondsLeft;

    public ZoneCountdownTask(
            FoliaCompat foliaCompat,
            ConfigManager configManager,
            ActionBarCooldownSoundSettings countdownSound,
            Player player,
            int countdownSeconds,
            Supplier<Boolean> stillValid,
            Runnable onTickComplete,
            Runnable onCancelled,
            Runnable onFinished
    ) {
        this.foliaCompat = foliaCompat;
        this.configManager = configManager;
        this.countdownSound = countdownSound;
        this.player = player;
        this.initialSeconds = Math.max(0, countdownSeconds);
        this.stillValid = stillValid;
        this.onTickComplete = onTickComplete;
        this.onCancelled = onCancelled;
        this.onFinished = onFinished;
        this.secondsLeft = new AtomicInteger(this.initialSeconds);
    }

    public void start() {
        if (initialSeconds <= 0) {
            if (active.compareAndSet(true, false)) {
                onFinished.run();
                onTickComplete.run();
            }
            return;
        }
        tick();
    }

    public void cancel(boolean sendCallback) {
        if (!active.compareAndSet(true, false)) {
            return;
        }
        clearActionBar();
        onFinished.run();
        if (sendCallback) {
            onCancelled.run();
        }
    }

    public boolean isActive() {
        return active.get();
    }

    private void tick() {
        if (!active.get()) {
            return;
        }
        if (!player.isOnline()) {
            cancel(false);
            return;
        }
        if (stillValid != null && !Boolean.TRUE.equals(stillValid.get())) {
            cancel(true);
            return;
        }

        int left = secondsLeft.getAndDecrement();
        if (left <= 0) {
            if (active.compareAndSet(true, false)) {
                clearActionBar();
                onFinished.run();
                onTickComplete.run();
            }
            return;
        }

        sendCountdownActionBar(left);
        playCountdownSound();
        foliaCompat.runLaterForEntity(player, this::tick, 20L);
    }

    private void sendCountdownActionBar(int seconds) {
        String text = configManager.plainMessage("zone-countdown-actionbar");
        if (text == null || text.isBlank()) {
            text = configManager.plainMessage("countdown-actionbar");
        }
        text = text
                .replace("%seconds%", String.valueOf(seconds))
                .replace("{seconds}", String.valueOf(seconds));
        player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(text));
    }

    private void playCountdownSound() {
        if (!countdownSound.enabled()) {
            return;
        }
        player.playSound(
                player.getLocation(),
                countdownSound.sound(),
                countdownSound.volume(),
                countdownSound.pitch()
        );
    }

    private void clearActionBar() {
        if (player.isOnline()) {
            player.sendActionBar(Component.empty());
        }
    }
}
