package com.tomkestookit;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.util.*;

/**
 * 玩家的 "树木连锁采集" 开关状态。持久化到 world/data/tomkestookit_veinmine.json。
 * 保存的是所有已开启该功能的玩家 UUID 集合。
 */
public final class VeinMineTracker {

    private static final Logger LOGGER = LoggerFactory.getLogger(TomkesTookitMod.MOD_ID);
    private static final Gson GSON = new Gson();
    private static Set<String> enabled = new HashSet<>();
    private static Path dataFile;

    private VeinMineTracker() {}

    public static void load(MinecraftServer server) {
        dataFile = server.getSavePath(WorldSavePath.ROOT).resolve("data/tomkestookit_veinmine.json");
        if (Files.exists(dataFile)) {
            try (Reader r = Files.newBufferedReader(dataFile)) {
                Type type = new TypeToken<Set<String>>() {}.getType();
                Set<String> loaded = GSON.fromJson(r, type);
                if (loaded != null) enabled = new HashSet<>(loaded);
            } catch (IOException e) {
                LOGGER.error("Failed to load veinmine data", e);
            }
        }
    }

    public static boolean isEnabled(UUID uuid) {
        return enabled.contains(uuid.toString());
    }

    /** 返回切换后的新状态。true = 开启。*/
    public static boolean toggle(UUID uuid) {
        String key = uuid.toString();
        boolean now;
        if (enabled.contains(key)) {
            enabled.remove(key);
            now = false;
        } else {
            enabled.add(key);
            now = true;
        }
        save();
        return now;
    }

    private static void save() {
        if (dataFile == null) return;
        try {
            Files.createDirectories(dataFile.getParent());
            try (Writer w = Files.newBufferedWriter(dataFile)) {
                GSON.toJson(enabled, w);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save veinmine data", e);
        }
    }
}
