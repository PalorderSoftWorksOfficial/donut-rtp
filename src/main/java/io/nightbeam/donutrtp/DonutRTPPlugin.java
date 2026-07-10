package io.nightbeam.donutrtp;

import io.nightbeam.donutrtp.command.RtpCommand;
import io.nightbeam.donutrtp.config.ConfigManager;
import io.nightbeam.donutrtp.gui.GuiManager;
import io.nightbeam.donutrtp.integration.worldguard.WorldGuardHook;
import io.nightbeam.donutrtp.listener.PlayerMoveCancelListener;
import io.nightbeam.donutrtp.listener.RtpZoneListener;
import io.nightbeam.donutrtp.rtp.RtpManager;
import io.nightbeam.donutrtp.rtp.RtpZoneManager;
import io.nightbeam.donutrtp.util.FoliaCompat;
import io.nightbeam.donutrtp.util.HeadDatabaseService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class DonutRTPPlugin extends JavaPlugin {

    private FoliaCompat foliaCompat;
    private ConfigManager configManager;
    private HeadDatabaseService headDatabaseService;
    private GuiManager guiManager;
    private RtpManager rtpManager;
    private RtpZoneManager rtpZoneManager;
    private WorldGuardHook worldGuardHook;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("messages.yml", false);

        this.foliaCompat = new FoliaCompat(this);
        this.configManager = new ConfigManager(this);
        this.configManager.reload();
        this.headDatabaseService = new HeadDatabaseService(this);
        this.worldGuardHook = WorldGuardHook.create(this);

        this.rtpManager = new RtpManager(this, foliaCompat, configManager);
        this.rtpZoneManager = new RtpZoneManager(foliaCompat, configManager, rtpManager, worldGuardHook);
        this.guiManager = new GuiManager(this, configManager, rtpManager, headDatabaseService);

        getServer().getPluginManager().registerEvents(guiManager, this);
        getServer().getPluginManager().registerEvents(new PlayerMoveCancelListener(rtpManager), this);
        getServer().getPluginManager().registerEvents(new RtpZoneListener(rtpZoneManager), this);

        RtpCommand command = new RtpCommand(configManager, guiManager, rtpZoneManager);
        PluginCommand rtp = getCommand("rtp");
        if (rtp != null) {
            rtp.setExecutor(command);
            rtp.setTabCompleter(command);
        } else {
            getLogger().severe("Command 'rtp' is missing in plugin.yml");
        }

        getLogger().info("DonutRTP enabled. Folia mode: " + foliaCompat.isFolia()
                + ", WorldGuard: " + worldGuardHook.isAvailable());
    }

    @Override
    public void onDisable() {
        if (rtpZoneManager != null) {
            rtpZoneManager.shutdown();
        }
        if (rtpManager != null) {
            rtpManager.shutdown();
        }
        if (foliaCompat != null) {
            foliaCompat.shutdown();
        }
    }
}
