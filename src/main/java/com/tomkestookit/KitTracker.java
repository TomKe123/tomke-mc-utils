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
 * 新手礼包领取记录：uuid → 上次领取时间（epoch ms），两次领取间隔至少 {@link #COOLDOWN_MS}。
 *
 * 存储：world/data/tomkestookit_kit_claims.json。
 * 旧版 tomkestookit_claimed.json 只记"领过没有"，不带时间，视为冷却已过，不迁移。
 */
public class KitTracker {

    private static final Logger LOGGER = LoggerFactory.getLogger(TomkesTookitMod.MOD_ID);
    private static final Gson GSON = new Gson();
    public static final long COOLDOWN_MS = 24L * 60 * 60 * 1000;
    private static Map<String, Long> lastClaim = new HashMap<>();
    private static Path dataFile;

    public static void load(MinecraftServer server) {
        dataFile = server.getSavePath(WorldSavePath.ROOT).resolve("data/tomkestookit_kit_claims.json");
        lastClaim = new HashMap<>();
        if (Files.exists(dataFile)) {
            try (Reader r = Files.newBufferedReader(dataFile)) {
                Type type = new TypeToken<Map<String, Long>>() {}.getType();
                Map<String, Long> loaded = GSON.fromJson(r, type);
                if (loaded != null) lastClaim = new HashMap<>(loaded);
            } catch (Exception e) {
                LOGGER.error("Failed to load tomkestookit kit claim data", e);
            }
        }
    }

    /** 是否领取过（任意时间）。*/
    public static boolean hasClaimed(UUID uuid) {
        return lastClaim.containsKey(uuid.toString());
    }

    /** 距离下次可领取还剩多少毫秒；0 = 现在可以领。*/
    public static long remainingCooldown(UUID uuid) {
        Long last = lastClaim.get(uuid.toString());
        if (last == null) return 0;
        long left = last + COOLDOWN_MS - System.currentTimeMillis();
        return Math.max(0, left);
    }

    public static void markClaimed(UUID uuid) {
        lastClaim.put(uuid.toString(), System.currentTimeMillis());
        save();
    }

    /** 把剩余毫秒格式化成 "X 小时 Y 分"。*/
    public static String formatRemaining(long ms) {
        long totalMin = (ms + 59_999) / 60_000;
        long h = totalMin / 60, m = totalMin % 60;
        return h > 0 ? h + " 小时 " + m + " 分" : m + " 分";
    }

    private static void save() {
        if (dataFile == null) return;
        try {
            Files.createDirectories(dataFile.getParent());
            try (Writer w = Files.newBufferedWriter(dataFile)) {
                GSON.toJson(lastClaim, w);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save tomkestookit kit claim data", e);
        }
    }
}
