package com.tomkestookit.landmark;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.tomkestookit.TomkesTookitMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.util.*;

/**
 * 服务器地标持久化。存 JSON 文件（world/data/tomkestookit_landmarks.json）。
 *
 *   - 唯一键 = 名字（大小写敏感）。
 *   - 保序：用 LinkedHashMap，菜单里按添加顺序显示。
 *   - 单进程：所有读写在服务器主线程/命令线程，不加锁。
 */
public final class LandmarkStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(TomkesTookitMod.MOD_ID);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Map<String, Landmark> landmarks = new LinkedHashMap<>();
    private static Path dataFile;

    private LandmarkStore() {}

    public static void load(MinecraftServer server) {
        dataFile = server.getSavePath(WorldSavePath.ROOT).resolve("data/tomkestookit_landmarks.json");
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

    /** 名字长度上限（字符数）。*/
    public static final int MAX_NAME_LEN = 24;

    /** 名字合法性：非空、长度 1..24。*/
    public static boolean isValidName(String name) {
        if (name == null) return false;
        String trimmed = name.strip();
        if (trimmed.isEmpty()) return false;
        return trimmed.length() <= MAX_NAME_LEN;
    }

    /** 返回 true 表示新增，false 表示已存在（不覆盖）。*/
    public static boolean add(Landmark lm) {
        if (landmarks.containsKey(lm.name)) return false;
        landmarks.put(lm.name, lm);
        save();
        return true;
    }

    /**
     * 重命名。旧名不存在或新名已存在或新名非法则失败。
     * 保序：用新 LinkedHashMap 重建，把当前记录以新 key 放回原位置。
     */
    public static boolean rename(String oldName, String newName) {
        if (!landmarks.containsKey(oldName)) return false;
        if (!isValidName(newName)) return false;
        if (oldName.equals(newName)) return false;
        if (landmarks.containsKey(newName)) return false;

        LinkedHashMap<String, Landmark> rebuilt = new LinkedHashMap<>();
        for (Map.Entry<String, Landmark> e : landmarks.entrySet()) {
            if (e.getKey().equals(oldName)) {
                Landmark lm = e.getValue();
                lm.name = newName;
                rebuilt.put(newName, lm);
            } else {
                rebuilt.put(e.getKey(), e.getValue());
            }
        }
        landmarks = rebuilt;
        save();
        return true;
    }

    /** 设置图标 registry id。地标不存在返回 false。*/
    public static boolean setIcon(String name, String iconId) {
        Landmark lm = landmarks.get(name);
        if (lm == null) return false;
        lm.iconId = iconId;
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
