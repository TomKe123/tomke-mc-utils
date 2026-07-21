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

public class KitTracker {

    private static final Logger LOGGER = LoggerFactory.getLogger(TomkesTookitMod.MOD_ID);
    private static final Gson GSON = new Gson();
    private static Set<String> claimed = new HashSet<>();
    private static Path dataFile;

    public static void load(MinecraftServer server) {
        dataFile = server.getSavePath(WorldSavePath.ROOT).resolve("data/tomkestookit_claimed.json");
        if (Files.exists(dataFile)) {
            try (Reader r = Files.newBufferedReader(dataFile)) {
                Type type = new TypeToken<Set<String>>() {}.getType();
                Set<String> loaded = GSON.fromJson(r, type);
                if (loaded != null) claimed = new HashSet<>(loaded);
            } catch (IOException e) {
                LOGGER.error("Failed to load tomkestookit claim data", e);
            }
        }
    }

    public static boolean hasClaimed(UUID uuid) {
        return claimed.contains(uuid.toString());
    }

    public static void markClaimed(UUID uuid) {
        claimed.add(uuid.toString());
        save();
    }

    private static void save() {
        if (dataFile == null) return;
        try {
            Files.createDirectories(dataFile.getParent());
            try (Writer w = Files.newBufferedWriter(dataFile)) {
                GSON.toJson(claimed, w);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save tomkestookit claim data", e);
        }
    }
}
