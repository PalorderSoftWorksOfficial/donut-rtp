package io.nightbeam.donutrtp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.world.ServerWorld;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class RtpConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private int radius = 5000;
    private int minY = 0;
    private int maxAttempts = 320;
    private int attemptsPerTick = 12;
    private int warmupSeconds = 3;
    private int cooldownSeconds = 120;
    private boolean cancelOnMove = true;

    public int radius() {
        return radius;
    }

    public void setRadius(int radius) {
        this.radius = Math.max(1, radius);
    }

    public void adjustRadius(int delta) {
        setRadius(this.radius + delta);
    }

    public int minY() {
        return minY;
    }

    public void setMinY(int minY) {
        this.minY = minY;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    public void adjustMaxAttempts(int delta) {
        setMaxAttempts(this.maxAttempts + delta);
    }

    public int attemptsPerTick() {
        return attemptsPerTick;
    }

    public void setAttemptsPerTick(int attemptsPerTick) {
        this.attemptsPerTick = Math.max(1, attemptsPerTick);
    }

    public void adjustAttemptsPerTick(int delta) {
        setAttemptsPerTick(this.attemptsPerTick + delta);
    }

    public int warmupSeconds() {
        return warmupSeconds;
    }

    public void setWarmupSeconds(int warmupSeconds) {
        this.warmupSeconds = Math.max(0, warmupSeconds);
    }

    public void adjustWarmupSeconds(int delta) {
        setWarmupSeconds(this.warmupSeconds + delta);
    }

    public int cooldownSeconds() {
        return cooldownSeconds;
    }

    public void setCooldownSeconds(int cooldownSeconds) {
        this.cooldownSeconds = Math.max(0, cooldownSeconds);
    }

    public void adjustCooldownSeconds(int delta) {
        setCooldownSeconds(this.cooldownSeconds + delta);
    }

    public boolean cancelOnMove() {
        return cancelOnMove;
    }

    public void setCancelOnMove(boolean cancelOnMove) {
        this.cancelOnMove = cancelOnMove;
    }

    public static RtpConfig load(Path path) {
        try {
            Files.createDirectories(path.getParent());
            if (Files.notExists(path)) {
                RtpConfig defaults = new RtpConfig();
                Files.writeString(path, GSON.toJson(defaults.toJson()), StandardCharsets.UTF_8);
                return defaults;
            }

            try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                RtpConfig config = new RtpConfig();
                config.setRadius(readInt(json, "radius", config.radius));
                config.setMinY(readInt(json, "minY", config.minY));
                config.setMaxAttempts(readInt(json, "maxAttempts", config.maxAttempts));
                config.setAttemptsPerTick(readInt(json, "attemptsPerTick", config.attemptsPerTick));
                config.setWarmupSeconds(readInt(json, "warmupSeconds", config.warmupSeconds));
                config.setCooldownSeconds(readInt(json, "cooldownSeconds", config.cooldownSeconds));
                config.setCancelOnMove(readBoolean(json, "cancelOnMove", config.cancelOnMove));
                return config;
            }
        } catch (IOException | IllegalStateException ex) {
            return new RtpConfig();
        }
    }

    public void save(Path path) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(toJson()), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to save RTP config", ex);
        }
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("radius", radius);
        json.addProperty("minY", minY);
        json.addProperty("maxAttempts", maxAttempts);
        json.addProperty("attemptsPerTick", attemptsPerTick);
        json.addProperty("warmupSeconds", warmupSeconds);
        json.addProperty("cooldownSeconds", cooldownSeconds);
        json.addProperty("cancelOnMove", cancelOnMove);
        return json;
    }

    public String summary() {
        return "Radius: " + radius + ", Attempts: " + maxAttempts + ", Per Tick: " + attemptsPerTick + ", Warmup: " + warmupSeconds + "s, Cooldown: " + cooldownSeconds + "s";
    }

    public static Path defaultPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("donut-rtp.json");
    }

    public RtpSearchTask createSearchTask(ServerWorld world) {
        return new RtpSearchTask(world, this);
    }

    private JsonObject toJsonInternal() {
        return toJson();
    }

    private static int readInt(JsonObject json, String key, int fallback) {
        return json.has(key) && json.get(key).isJsonPrimitive() && json.get(key).getAsJsonPrimitive().isNumber()
                ? json.get(key).getAsInt()
                : fallback;
    }

    private static boolean readBoolean(JsonObject json, String key, boolean fallback) {
        return json.has(key) && json.get(key).isJsonPrimitive() && json.get(key).getAsJsonPrimitive().isBoolean()
                ? json.get(key).getAsBoolean()
                : fallback;
    }
}
