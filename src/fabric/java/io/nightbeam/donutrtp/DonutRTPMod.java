package io.nightbeam.donutrtp;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.nio.file.Path;

public final class DonutRTPMod implements ModInitializer {
    public static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("donut-rtp.json");
    public static volatile RtpConfig CONFIG = RtpConfig.load(CONFIG_PATH);
    public static final RtpService SERVICE = new RtpService();

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register(this::registerCommands);
        ServerTickEvents.END_SERVER_TICK.register(SERVICE::tick);
    }

    public static void saveConfig() {
        CONFIG.save(CONFIG_PATH);
    }

    private void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("rtp")
                .executes(context -> openMainGui(context.getSource()))
                .then(CommandManager.literal("gui")
                        .executes(context -> openMainGui(context.getSource())))
                .then(CommandManager.literal("admin")
                        .requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
                        .executes(context -> openAdminGui(context.getSource())))
                .then(CommandManager.literal("reload")
                        .requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
                        .executes(context -> reloadConfig(context.getSource())))
                .then(CommandManager.literal("cancel")
                        .executes(context -> cancelTeleport(context.getSource()))));
    }

    private int startTeleport(ServerCommandSource source) {
        ServerPlayerEntity player = requirePlayer(source);
        if (player == null) {
            return 0;
        }

        SERVICE.startTeleport(player);
        return 1;
    }

    private int openMainGui(ServerCommandSource source) {
        ServerPlayerEntity player = requirePlayer(source);
        if (player == null) {
            return 0;
        }

        RtpGuiScreenHandler.openMain(player);
        return 1;
    }

    private int openAdminGui(ServerCommandSource source) {
        ServerPlayerEntity player = requirePlayer(source);
        if (player == null) {
            return 0;
        }

        RtpGuiScreenHandler.openAdmin(player);
        return 1;
    }

    private int reloadConfig(ServerCommandSource source) {
        CONFIG = RtpConfig.load(CONFIG_PATH);
        source.sendFeedback(() -> Text.literal("DonutRTP configuration reloaded."), false);
        return 1;
    }

    private int cancelTeleport(ServerCommandSource source) {
        ServerPlayerEntity player = requirePlayer(source);
        if (player == null) {
            return 0;
        }

        SERVICE.cancelTeleport(player, "Pending RTP cancelled.");
        return 1;
    }

    private ServerPlayerEntity requirePlayer(ServerCommandSource source) {
        if (source.getEntity() instanceof ServerPlayerEntity player) {
            return player;
        }

        source.sendError(Text.literal("Only players can use this command."));
        return null;
    }
}
