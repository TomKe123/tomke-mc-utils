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
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 实体抓取开关状态。持久化到 world/data/tomkestookit_capture.json。
 *
 * 默认关闭（opt-in）：新玩家不小心 shift + 右键空手 生物不会误抓。
 */
public final class EntityCaptureTracker {

    private static final Logger LOGGER = LoggerFactory.getLogger(TomkesTookitMod.MOD_ID);
    private static final Gson GSON = new Gson();
    private static Set<String> enabled = new HashSet<>();
    private static Path dataFile;

    private EntityCaptureTracker() {}

    public static void load(MinecraftServer server) {
        dataFile = server.getSavePath(WorldSavePath.ROOT).resolve("data/tomkestookit_capture.json");
        if (Files.exists(dataFile)) {
            try (Reader r = Files.newBufferedReader(dataFile)) {
                Type type = new TypeToken<Set<String>>() {}.getType();
                Set<String> loaded = GSON.fromJson(r, type);
                if (loaded != null) enabled = new HashSet<>(loaded);
            } catch (IOException e) {
                LOGGER.error("Failed to load entity capture data", e);
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
            LOGGER.error("Failed to save entity capture data", e);
        }
    }
}
