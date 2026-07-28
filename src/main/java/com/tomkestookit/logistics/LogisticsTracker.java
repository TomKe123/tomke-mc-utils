package com.tomkestookit.logistics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.tomkestookit.TomkesTookitMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 物流终端 pairId → LogisticsPair 的持久化。
 * 存到 world/data/tomkestookit_logistics.json。
 */
public final class LogisticsTracker {

    private static final Logger LOGGER = LoggerFactory.getLogger(TomkesTookitMod.MOD_ID);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Map<String, LogisticsPair> pairs = new LinkedHashMap<>();
    private static Path dataFile;

    private LogisticsTracker() {}

    public static void load(MinecraftServer server) {
        dataFile = server.getSavePath(WorldSavePath.ROOT).resolve("data/tomkestookit_logistics.json");
        if (!Files.exists(dataFile)) return;
        try (Reader r = Files.newBufferedReader(dataFile)) {
            Type type = new TypeToken<LinkedHashMap<String, LogisticsPair>>() {}.getType();
            LinkedHashMap<String, LogisticsPair> loaded = GSON.fromJson(r, type);
            if (loaded != null) pairs = loaded;
        } catch (IOException e) {
            LOGGER.error("Failed to load logistics tracker", e);
        }
    }

    public static LogisticsPair get(String pairId) {
        return pairs.get(pairId);
    }

    public static Collection<LogisticsPair> all() {
        return pairs.values();
    }

    public static void put(LogisticsPair pair) {
        pairs.put(pair.pairId, pair);
        save();
    }

    public static void save() {
        if (dataFile == null) return;
        try {
            Files.createDirectories(dataFile.getParent());
            try (Writer w = Files.newBufferedWriter(dataFile)) {
                GSON.toJson(pairs, w);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save logistics tracker", e);
        }
    }

    public static void remove(String pairId) {
        if (pairs.remove(pairId) != null) save();
    }

    /** 按 dim + BlockPos 查找是否是某 pair 的 endpoint。*/
    public static LogisticsPair findByPos(String dim, BlockPos pos) {
        for (LogisticsPair p : pairs.values()) {
            if (p.a != null && p.a.dim != null && p.a.dim.equals(dim)
                && p.a.x == pos.getX() && p.a.y == pos.getY() && p.a.z == pos.getZ()) return p;
            if (p.b != null && p.b.dim != null && p.b.dim.equals(dim)
                && p.b.x == pos.getX() && p.b.y == pos.getY() && p.b.z == pos.getZ()) return p;
        }
        return null;
    }

    public static LogisticsPair.Endpoint endpointAt(LogisticsPair pair, String dim, BlockPos pos) {
        if (pair.a != null && pair.a.dim != null && pair.a.dim.equals(dim)
            && pair.a.x == pos.getX() && pair.a.y == pos.getY() && pair.a.z == pos.getZ()) return pair.a;
        if (pair.b != null && pair.b.dim != null && pair.b.dim.equals(dim)
            && pair.b.x == pos.getX() && pair.b.y == pos.getY() && pair.b.z == pos.getZ()) return pair.b;
        return null;
    }

    public static String newPairId(java.util.Random rng) {
        // 6 位十六进制随机数
        while (true) {
            int n = rng.nextInt(0xFFFFFF + 1);
            String id = String.format("%06X", n);
            if (!pairs.containsKey(id)) return id;
        }
    }
}
