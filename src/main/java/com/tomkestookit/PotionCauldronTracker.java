package com.tomkestookit;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 炼药锅药水状态持久化。
 *
 * key 格式："dim:x,y,z"（例如 "minecraft:overworld:100,64,-200"）
 * value.snbt：药水 NBT 的 SNBT 字符串（Potion / CustomPotionEffects / CustomPotionColor）
 * value.labelUuid：锅上方悬浮字实体的 UUID 字符串（可为 null，惰性创建）
 *
 * 存到 world/data/tomkestookit_potion_cauldrons.json。
 */
public final class PotionCauldronTracker {

    private static final Logger LOGGER = LoggerFactory.getLogger(TomkesTookitMod.MOD_ID);
    private static final Gson GSON = new Gson();

    public static final class Entry {
        public String snbt;
        public String labelUuid; // 可 null

        public Entry() {}

        public Entry(String snbt, String labelUuid) {
            this.snbt = snbt;
            this.labelUuid = labelUuid;
        }
    }

    private static Map<String, Entry> cauldrons = new HashMap<>();
    private static Path dataFile;

    private PotionCauldronTracker() {}

    public static void load(MinecraftServer server) {
        dataFile = server.getSavePath(WorldSavePath.ROOT).resolve("data/tomkestookit_potion_cauldrons.json");
        if (!Files.exists(dataFile)) return;
        try (Reader r = Files.newBufferedReader(dataFile)) {
            Type type = new TypeToken<Map<String, Entry>>() {}.getType();
            Map<String, Entry> loaded = GSON.fromJson(r, type);
            if (loaded != null) cauldrons = new HashMap<>(loaded);
        } catch (IOException e) {
            LOGGER.error("Failed to load potion cauldron data", e);
        } catch (RuntimeException e) {
            // JsonSyntaxException / IllegalStateException：旧格式或损坏 → 起干净的
            // 把旧文件挪成 .bak 保留证据，避免下次 save 直接覆写。
            LOGGER.warn("Potion cauldron data is in a legacy or corrupted format ({}), " +
                "starting fresh. Backup saved as .bak.", e.getMessage());
            try {
                Files.move(dataFile, dataFile.resolveSibling(dataFile.getFileName() + ".bak"),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException moveErr) {
                LOGGER.error("Failed to back up bad cauldron data file", moveErr);
            }
        }
    }

    public static String key(String dimension, BlockPos pos) {
        return dimension + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    /** 存药水 NBT。若已存在则保留旧的 labelUuid。*/
    public static void setPotion(String key, NbtCompound nbt) {
        Entry e = cauldrons.get(key);
        if (e == null) {
            e = new Entry(nbt.toString(), null);
            cauldrons.put(key, e);
        } else {
            e.snbt = nbt.toString();
        }
        save();
    }

    /** 关联标签实体 UUID。*/
    public static void setLabelUuid(String key, UUID uuid) {
        Entry e = cauldrons.get(key);
        if (e == null) return;
        e.labelUuid = uuid == null ? null : uuid.toString();
        save();
    }

    /** 读药水 NBT，不存在或无法解析返回 null。*/
    public static NbtCompound getPotion(String key) {
        Entry e = cauldrons.get(key);
        if (e == null || e.snbt == null) return null;
        try {
            return StringNbtReader.parse(e.snbt);
        } catch (CommandSyntaxException ex) {
            LOGGER.error("Failed to parse cauldron SNBT: {}", e.snbt, ex);
            return null;
        }
    }

    /** 读标签实体 UUID，没有返回 null。*/
    public static UUID getLabelUuid(String key) {
        Entry e = cauldrons.get(key);
        if (e == null || e.labelUuid == null) return null;
        try {
            return UUID.fromString(e.labelUuid);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static boolean has(String key) {
        return cauldrons.containsKey(key);
    }

    public static void remove(String key) {
        if (cauldrons.remove(key) != null) save();
    }

    /** 用于遍历（如启动时扫尾孤儿）。返回的是内部 map 的快照，读用。*/
    public static Map<String, Entry> allEntries() {
        return new HashMap<>(cauldrons);
    }

    private static void save() {
        if (dataFile == null) return;
        try {
            Files.createDirectories(dataFile.getParent());
            try (Writer w = Files.newBufferedWriter(dataFile)) {
                GSON.toJson(cauldrons, w);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save potion cauldron data", e);
        }
    }
}
