package io.nightbeam.donutrtp;

import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public final class RtpSearchTask {
    private final ServerWorld world;
    private final RtpConfig config;
    private final BlockPos origin;
    private final ThreadLocalRandom random = ThreadLocalRandom.current();
    private int attempts;
    private BlockPos result;
    private boolean finished;

    public RtpSearchTask(ServerWorld world, BlockPos origin, RtpConfig config) {
        this.world = world;
        this.config = config;
        this.origin = origin;
    }

    public boolean tick() {
        if (finished) {
            return true;
        }

        int budget = Math.max(1, config.attemptsPerTick());
        for (int i = 0; i < budget; i++) {
            if (attempts >= config.maxAttempts()) {
                finished = true;
                return true;
            }

            attempts++;
            Optional<BlockPos> candidate = nextCandidate();
            if (candidate.isPresent()) {
                result = candidate.get();
                finished = true;
                return true;
            }
        }

        return false;
    }

    public boolean isFinished() {
        return finished;
    }

    public Optional<BlockPos> result() {
        return Optional.ofNullable(result);
    }

    private Optional<BlockPos> nextCandidate() {
        int x = origin.getX() + random.nextInt(-config.radius(), config.radius() + 1);
        int z = origin.getZ() + random.nextInt(-config.radius(), config.radius() + 1);
        int surfaceY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        int baseY = Math.max(config.minY(), surfaceY + 1);

        for (int offset = 0; offset <= 8; offset++) {
            BlockPos feet = new BlockPos(x, baseY + offset, z);
            if (isSafe(feet)) {
                return Optional.of(feet);
            }
        }

        return Optional.empty();
    }

    private boolean isSafe(BlockPos feet) {
        BlockPos head = feet.up();
        BlockPos below = feet.down();

        BlockState feetState = world.getBlockState(feet);
        BlockState headState = world.getBlockState(head);
        BlockState belowState = world.getBlockState(below);

        if (!feetState.isAir() || !headState.isAir()) {
            return false;
        }

        if (!belowState.isSolidBlock(world, below)) {
            return false;
        }

        String belowName = belowState.getBlock().toString().toLowerCase();
        if (belowName.contains("lava") || belowName.contains("fire") || belowName.contains("magma") || belowName.contains("cactus") || belowName.contains("campfire")) {
            return false;
        }

        return true;
    }
}
