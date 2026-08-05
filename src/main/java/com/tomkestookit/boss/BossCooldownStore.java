package com.tomkestookit.boss;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.tomkestookit.TomkesTookitMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

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
 * Boss 令牌冷却持久化。每个玩家每种 Boss 独立 24 小时冷却。
 * 存储：world/data/tomkestookit_boss_cd.json
 */
public final class BossCooldownStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long COOLDOWN_MS = 86400000L; // 24 hours

    /** playerUuid string -> bossId -> last summon millis. */
    private static Map<String, Map<String, Long>> DATA = new HashMap<>();
    private static Path dataFile;

    private BossCooldownStore() {}

    public static void load(MinecraftServer server) {
        dataFile = server.getSavePath(WorldSavePath.ROOT)
                .resolve("data/tomkestookit_boss_cd.json");
        if (!Files.exists(dataFile)) return;
        try (Reader r = Files.newBufferedReader(dataFile)) {
            Type type = new TypeToken<HashMap<String, Map<String, Long>>>() {}.getType();
            HashMap<String, Map<String, Long>> loaded = GSON.fromJson(r, type);
            if (loaded != null) DATA = loaded;
        } catch (IOException e) {
            TomkesTookitMod.LOGGER.error("Failed to load boss cooldowns", e);
        }
    }
    private static void save() {
        if (dataFile == null) return;
        try {
            Files.createDirectories(dataFile.getParent());
            try (Writer w = Files.newBufferedWriter(dataFile)) {
                GSON.toJson(DATA, w);
            }
        } catch (IOException e) {
            TomkesTookitMod.LOGGER.error("Failed to save boss cooldowns", e);
        }
    }

    /** true 表示玩家可以召唤该 Boss（首次或冷却已过）。*/
    public static boolean canSummon(UUID player, String bossId) {
        Map<String, Long> per = DATA.get(player.toString());
        if (per == null) return true;
        Long last = per.get(bossId);
        if (last == null) return true;
        return System.currentTimeMillis() - last >= COOLDOWN_MS;
    }

    /** 记录一次召唤时间戳并落盘。*/
    public static void recordSummon(UUID player, String bossId) {
        Map<String, Long> per = DATA.computeIfAbsent(player.toString(), k -> new HashMap<>());
        per.put(bossId, System.currentTimeMillis());
        save();
    }

    /** 剩余冷却毫秒数。已到期或无记录返回 0。*/
    public static long getRemainingCooldown(UUID player, String bossId) {
        Map<String, Long> per = DATA.get(player.toString());
        if (per == null) return 0L;
        Long last = per.get(bossId);
        if (last == null) return 0L;
        long elapsed = System.currentTimeMillis() - last;
        long remaining = COOLDOWN_MS - elapsed;
        return Math.max(0L, remaining);
    }
}

