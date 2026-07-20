package com.starterkit.landmark;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.starterkit.StarterKitMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.util.*;

/**
 * 服务器地标持久化。存 JSON 文件（world/data/starterkit_landmarks.json）。
 *
 *   - 唯一键 = 名字（大小写敏感）。
 *   - 保序：用 LinkedHashMap，菜单里按添加顺序显示。
 *   - 单进程：所有读写在服务器主线程/命令线程，不加锁。
 */
public final class LandmarkStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(StarterKitMod.MOD_ID);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Map<String, Landmark> landmarks = new LinkedHashMap<>();
    private static Path dataFile;

    private LandmarkStore() {}

    public static void load(MinecraftServer server) {
        dataFile = server.getSavePath(WorldSavePath.ROOT).resolve("data/starterkit_landmarks.json");
        if (!Files.exists(dataFile)) return;
        try (Reader r = Files.newBufferedReader(dataFile)) {
            Type type = new TypeToken<LinkedHashMap<String, Landmark>>() {}.getType();
            LinkedHashMap<String, Landmark> loaded = GSON.fromJson(r, type);
            if (loaded != null) landmarks = loaded;
        } catch (IOException e) {
            LOGGER.error("Failed to load landmarks", e);
        }
    }

    public static Collection<Landmark> all() {
        return Collections.unmodifiableCollection(landmarks.values());
    }

    public static Landmark get(String name) {
        return landmarks.get(name);
    }

    public static boolean exists(String name) {
        return landmarks.containsKey(name);
    }

    /** 返回 true 表示新增，false 表示已存在（不覆盖）。*/
    public static boolean add(Landmark lm) {
        if (landmarks.containsKey(lm.name)) return false;
        landmarks.put(lm.name, lm);
        save();
        return true;
    }

    /** 返回 true 表示删除成功。*/
    public static boolean remove(String name) {
        if (landmarks.remove(name) == null) return false;
        save();
        return true;
    }

    private static void save() {
        if (dataFile == null) return;
        try {
            Files.createDirectories(dataFile.getParent());
            try (Writer w = Files.newBufferedWriter(dataFile)) {
                GSON.toJson(landmarks, w);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save landmarks", e);
        }
    }
}
