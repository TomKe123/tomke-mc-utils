package com.tomkestookit;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 玩家最近一次死亡地点。持久化到 world/data/tomkestookit_deaths.json。
 * 供 /back 命令与死亡聊天提示使用。
 */
public final class DeathLocationTracker {

    private static final Logger LOGGER = LoggerFactory.getLogger(TomkesTookitMod.MOD_ID);
    private static final Gson GSON = new Gson();
    private static Map<String, DeathLocation> locations = new HashMap<>();
    private static Path dataFile;

    /** 死亡地点数据（dim + xyz + 面朝角度）。用 double 保留精度，落地不至于卡进方块。*/
    public static final class DeathLocation {
        public String dimension;
        public double x, y, z;
        public float yaw, pitch;

        public DeathLocation() {}

        public DeathLocation(String dimension, double x, double y, double z, float yaw, float pitch) {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    private DeathLocationTracker() {}

    public static void load(MinecraftServer server) {
        dataFile = server.getSavePath(WorldSavePath.ROOT).resolve("data/tomkestookit_deaths.json");
        if (Files.exists(dataFile)) {
            try (Reader r = Files.newBufferedReader(dataFile)) {
                Type type = new TypeToken<Map<String, DeathLocation>>() {}.getType();
                Map<String, DeathLocation> loaded = GSON.fromJson(r, type);
                if (loaded != null) locations = new HashMap<>(loaded);
            } catch (IOException e) {
                LOGGER.error("Failed to load death location data", e);
            }
        }
    }

    public static void record(UUID uuid, DeathLocation loc) {
        locations.put(uuid.toString(), loc);
        save();
    }

    public static DeathLocation get(UUID uuid) {
        return locations.get(uuid.toString());
    }

    public static boolean has(UUID uuid) {
        return locations.containsKey(uuid.toString());
    }

    private static void save() {
        if (dataFile == null) return;
        try {
            Files.createDirectories(dataFile.getParent());
            try (Writer w = Files.newBufferedWriter(dataFile)) {
                GSON.toJson(locations, w);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save death location data", e);
        }
    }
}
