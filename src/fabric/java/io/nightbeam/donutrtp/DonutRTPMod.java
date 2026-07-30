package io.nightbeam.donutrtp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

public final class DonutRTPMod implements ModInitializer {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("donut-rtp.json");
    private static volatile Config CONFIG = Config.load(CONFIG_PATH);

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register(this::registerCommands);
    }

    private void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("rtp")
                .executes(context -> teleportSelf(context.getSource()))
                .then(CommandManager.literal("reload")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(context -> reloadConfig(context.getSource()))));
    }

    private int reloadConfig(ServerCommandSource source) {
        CONFIG = Config.load(CONFIG_PATH);
        source.sendFeedback(() -> Text.literal("DonutRTP configuration reloaded."), false);
        return 1;
    }

    private int teleportSelf(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        ServerWorld world = player.getServerWorld();
        Config config = CONFIG;

        Optional<BlockPos> target = findSafeSpot(world, config.radius, config.minY, config.maxAttempts);
        if (target.isEmpty()) {
            source.sendError(Text.literal("No safe teleport location was found."));
            return 0;
        }

        BlockPos pos = target.get();
        double x = pos.getX() + 0.5D;
        double y = pos.getY();
        double z = pos.getZ() + 0.5D;

        player.networkHandler.requestTeleport(x, y, z, player.getYaw(), player.getPitch());
        player.refreshPositionAndAngles(x, y, z, player.getYaw(), player.getPitch());
        player.setVelocity(Vec3d.ZERO);
        source.sendFeedback(() -> Text.literal("Teleported."), false);
        return 1;
    }

    private Optional<BlockPos> findSafeSpot(ServerWorld world, int radius, int minY, int maxAttempts) {
        BlockPos spawn = world.getSpawnPos();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int x = spawn.getX() + random.nextInt(-radius, radius + 1);
            int z = spawn.getZ() + random.nextInt(-radius, radius + 1);
            int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            int y = Math.max(minY, topY + 1);

            BlockPos feet = new BlockPos(x, y, z);
            BlockPos head = feet.up();
            BlockPos below = feet.down();

            if (isSafe(world, feet, head, below)) {
                return Optional.of(feet);
            }
        }

        return Optional.empty();
    }

    private boolean isSafe(ServerWorld world, BlockPos feet, BlockPos head, BlockPos below) {
        BlockState feetState = world.getBlockState(feet);
        BlockState headState = world.getBlockState(head);
        BlockState belowState = world.getBlockState(below);
        return feetState.isAir() && headState.isAir() && belowState.isSolidBlock(world, below);
    }

    private static final class Config {
        private final int radius;
        private final int minY;
        private final int maxAttempts;

        private Config(int radius, int minY, int maxAttempts) {
            this.radius = radius;
            this.minY = minY;
            this.maxAttempts = maxAttempts;
        }

        private static Config load(Path path) {
            try {
                Files.createDirectories(path.getParent());
                if (Files.notExists(path)) {
                    Config defaults = new Config(5000, 0, 80);
                    Files.writeString(path, GSON.toJson(defaults.toJson()), StandardCharsets.UTF_8);
                    return defaults;
                }

                try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    int radius = getInt(json, "radius", 5000);
                    int minY = getInt(json, "minY", 0);
                    int maxAttempts = getInt(json, "maxAttempts", 80);
                    return new Config(Math.max(1, radius), minY, Math.max(1, maxAttempts));
                }
            } catch (IOException | IllegalStateException ex) {
                return new Config(5000, 0, 80);
            }
        }

        private JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("radius", radius);
            json.addProperty("minY", minY);
            json.addProperty("maxAttempts", maxAttempts);
            return json;
        }

        private static int getInt(JsonObject json, String key, int fallback) {
            return json.has(key) && json.get(key).isJsonPrimitive() && json.get(key).getAsJsonPrimitive().isNumber()
                    ? json.get(key).getAsInt()
                    : fallback;
        }
    }
}
