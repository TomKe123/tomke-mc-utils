package com.tomkestookit;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
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

/**
 * 全服“特定方块连锁”白名单。普通方块不再默认连锁；只有这里配置过的
 * 方块才会按各自“单次挖掘数量限制”参与同类连锁。
 */
public final class VeinMineBlockConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(TomkesTookitMod.MOD_ID);
    private static final Gson GSON = new Gson();
    public static final int MAX_LIMIT = 32;
    public static final int DEFAULT_LIMIT = 32;

    private static Map<String, Integer> limits = new HashMap<>();
    private static Path dataFile;

    private VeinMineBlockConfig() {}

    public static void load(MinecraftServer server) {
        dataFile = server.getSavePath(WorldSavePath.ROOT).resolve("data/tomkestookit_veinmine_blocks.json");
        limits = defaultLimits();
        if (!Files.exists(dataFile)) {
            save();
            return;
        }
        try (Reader r = Files.newBufferedReader(dataFile)) {
            Type type = new TypeToken<Map<String, Integer>>() {}.getType();
            Map<String, Integer> loaded = GSON.fromJson(r, type);
            if (loaded != null) {
                limits = sanitize(loaded);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load veinmine block config", e);
        } catch (RuntimeException e) {
            LOGGER.warn("Veinmine block config is corrupted ({}), using defaults.", e.getMessage());
        }
        save();
    }

    private static Map<String, Integer> defaultLimits() {
        Map<String, Integer> defaults = new HashMap<>();
        defaults.put(idOf(Blocks.MAGMA_BLOCK), DEFAULT_LIMIT);
        defaults.put(idOf(Blocks.ANDESITE), DEFAULT_LIMIT);
        return defaults;
    }

    private static Map<String, Integer> sanitize(Map<String, Integer> raw) {
        Map<String, Integer> out = new HashMap<>();
        for (Map.Entry<String, Integer> e : raw.entrySet()) {
            Identifier id = Identifier.tryParse(e.getKey());
            Integer limit = e.getValue();
            if (id == null || limit == null) continue;
            if (!Registries.BLOCK.containsId(id)) continue;
            out.put(id.toString(), clamp(limit));
        }
        return out;
    }

    public static boolean isConfigured(Block block) {
        return limits.containsKey(idOf(block));
    }

    public static int getLimit(Block block) {
        return limits.getOrDefault(idOf(block), 0);
    }

    public static void set(Block block, int limit) {
        limits.put(idOf(block), clamp(limit));
        save();
    }

    public static void resetDefaults() {
        limits = defaultLimits();
        save();
    }

    public static boolean remove(Block block) {
        boolean removed = limits.remove(idOf(block)) != null;
        if (removed) save();
        return removed;
    }

    public static Map<String, Integer> all() {
        return new java.util.TreeMap<>(limits);
    }

    public static int clamp(int limit) {
        return Math.max(1, Math.min(MAX_LIMIT, limit));
    }

    public static String idOf(Block block) {
        Identifier id = Registries.BLOCK.getId(block);
        return id == null ? "minecraft:air" : id.toString();
    }

    private static void save() {
        if (dataFile == null) return;
        try {
            Files.createDirectories(dataFile.getParent());
            try (Writer w = Files.newBufferedWriter(dataFile)) {
                GSON.toJson(new java.util.TreeMap<>(limits), w);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save veinmine block config", e);
        }
    }
}
