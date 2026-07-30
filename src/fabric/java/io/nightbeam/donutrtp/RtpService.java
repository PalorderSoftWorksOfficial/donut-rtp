package io.nightbeam.donutrtp;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class RtpService {
    private final Map<UUID, PendingTeleport> pendingTeleports = new HashMap<>();
    private final Map<UUID, Long> cooldownEnds = new HashMap<>();
    private long tick;

    public void startTeleport(ServerPlayerEntity player) {
        RtpConfig config = DonutRTPMod.CONFIG;
        UUID uuid = player.getUuid();
        if (pendingTeleports.containsKey(uuid)) {
            player.sendMessage(Text.literal("A teleport is already pending."), true);
            return;
        }

        long remaining = cooldownRemaining(uuid);
        if (remaining > 0) {
            player.sendMessage(Text.literal("Teleport is on cooldown for " + formatTicks(remaining) + "."), true);
            return;
        }

        pendingTeleports.put(uuid, new PendingTeleport(player.getEntityPos(), config.warmupSeconds() * 20L));
        if (config.warmupSeconds() > 0) {
            player.sendMessage(Text.literal("Random teleport starts in " + config.warmupSeconds() + "s."), true);
        } else {
            player.sendMessage(Text.literal("Searching for a safe location..."), true);
        }
    }

    public void cancelTeleport(ServerPlayerEntity player, String reason) {
        PendingTeleport pending = pendingTeleports.remove(player.getUuid());
        if (pending != null) {
            player.sendMessage(Text.literal(reason), true);
        }
    }

    public void tick(MinecraftServer server) {
        tick++;

        Iterator<Map.Entry<UUID, PendingTeleport>> iterator = pendingTeleports.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingTeleport> entry = iterator.next();
            UUID uuid = entry.getKey();
            PendingTeleport pending = entry.getValue();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);

            if (player == null) {
                iterator.remove();
                continue;
            }

            if (pending.warmupTicksRemaining > 0) {
                if (DonutRTPMod.CONFIG.cancelOnMove() && player.getEntityPos().squaredDistanceTo(pending.startPosition) > 0.25D) {
                    player.sendMessage(Text.literal("Teleport cancelled because you moved."), true);
                    iterator.remove();
                    continue;
                }

                pending.warmupTicksRemaining--;
                if (pending.warmupTicksRemaining % 20L == 0L || pending.warmupTicksRemaining == 0L) {
                    long seconds = Math.max(0L, pending.warmupTicksRemaining / 20L);
                    player.sendMessage(Text.literal(seconds == 0L ? "Searching for a safe location..." : "Random teleport starts in " + seconds + "s."), true);
                }

                if (pending.warmupTicksRemaining > 0L) {
                    continue;
                }
            }

            if (pending.searchTask == null) {
                pending.searchTask = new RtpSearchTask(player.getEntityWorld(), player.getBlockPos(), DonutRTPMod.CONFIG);
                player.sendMessage(Text.literal("Searching for a safe location..."), true);
            }

            boolean done = pending.searchTask.tick();
            if (!done) {
                continue;
            }

            if (pending.searchTask.result().isEmpty()) {
                player.sendMessage(Text.literal("No safe teleport location was found."), true);
                iterator.remove();
                continue;
            }

            BlockPos pos = pending.searchTask.result().get();
            double x = pos.getX() + 0.5D;
            double y = pos.getY();
            double z = pos.getZ() + 0.5D;
            player.networkHandler.requestTeleport(x, y, z, player.getYaw(), player.getPitch());
            player.refreshPositionAndAngles(x, y, z, player.getYaw(), player.getPitch());
            player.setVelocity(Vec3d.ZERO);
            cooldownEnds.put(uuid, tick + (long) DonutRTPMod.CONFIG.cooldownSeconds() * 20L);
            player.sendMessage(Text.literal("Teleported."), true);
            iterator.remove();
        }
    }

    public long cooldownRemaining(UUID uuid) {
        Long endsAt = cooldownEnds.get(uuid);
        if (endsAt == null) {
            return 0L;
        }

        return Math.max(0L, endsAt - tick);
    }

    public void clearState(UUID uuid) {
        pendingTeleports.remove(uuid);
    }

    public String statusLine() {
        return DonutRTPMod.CONFIG.summary();
    }

    private String formatTicks(long ticks) {
        long seconds = (ticks + 19L) / 20L;
        return seconds + "s";
    }

    private static final class PendingTeleport {
        private final Vec3d startPosition;
        private long warmupTicksRemaining;
        private RtpSearchTask searchTask;

        private PendingTeleport(Vec3d startPosition, long warmupTicksRemaining) {
            this.startPosition = startPosition;
            this.warmupTicksRemaining = Math.max(0L, warmupTicksRemaining);
        }
    }
}
