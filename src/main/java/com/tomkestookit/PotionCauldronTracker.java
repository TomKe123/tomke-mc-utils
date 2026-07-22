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

/**
 * 炼药锅药水状态持久化。
 *
 * 每个装了药水的炼药锅存一份"药水源物品栈的 NBT"（不含 count），
 * 用来后续给箭附加 Potion / CustomPotionEffects。
 *
 * key 格式："dim:x,y,z"，如 "minecraft:overworld:100,64,-200"。
 * value 是 base64 后的 NBT compound（含 Potion / CustomPotionEffects 字段）。
 *
 * 存到 world/data/tomkestookit_potion_cauldrons.json。
 */
public final class PotionCauldronTracker {

    private static final Logger LOGGER = LoggerFactory.getLogger(TomkesTookitMod.MOD_ID);
    private static final Gson GSON = new Gson();

    /** value 是 SNBT 字符串（如 {Potion:"minecraft:strength"}）。*/
    private static Map<String, String> cauldrons = new HashMap<>();
    private static Path dataFile;

    private PotionCauldronTracker() {}

    public static void load(MinecraftServer server) {
        dataFile = server.getSavePath(WorldSavePath.ROOT).resolve("data/tomkestookit_potion_cauldrons.json");
        if (Files.exists(dataFile)) {
            try (Reader r = Files.newBufferedReader(dataFile)) {
                Type type = new TypeToken<Map<String, String>>() {}.getType();
                Map<String, String> loaded = GSON.fromJson(r, type);
                if (loaded != null) cauldrons = new HashMap<>(loaded);
            } catch (IOException e) {
                LOGGER.error("Failed to load potion cauldron data", e);
            }
        }
    }

    public static String key(String dimension, BlockPos pos) {
        return dimension + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    /** 存药水信息（覆盖已存在的）。*/
    public static void set(String key, NbtCompound nbt) {
        cauldrons.put(key, nbt.toString());
        save();
    }

    /** 读药水信息，不存在或无法解析返回 null。*/
    public static NbtCompound get(String key) {
        String snbt = cauldrons.get(key);
        if (snbt == null) return null;
        try {
            return StringNbtReader.parse(snbt);
        } catch (CommandSyntaxException e) {
            LOGGER.error("Failed to parse cauldron SNBT: {}", snbt, e);
            return null;
        }
    }

    public static boolean has(String key) {
        return cauldrons.containsKey(key);
    }

    public static void remove(String key) {
        if (cauldrons.remove(key) != null) save();
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
