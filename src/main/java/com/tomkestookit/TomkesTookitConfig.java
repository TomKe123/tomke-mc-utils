package com.tomkestookit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 全服管理员配置：
 *   - features: Map&lt;feature key, enabled?&gt;  — 未设置的 key 默认 true（向后兼容新特性）
 *   - kitItems: 新手礼包物品清单，落盘为 SNBT 字符串数组
 *
 * 存储：world/data/tomkestookit_config.json，服务器 SERVER_STARTED 时 load。
 *
 * 关闭时的 tear-down 回调注册在 registerTearDown()：某个特性从"开"变"关"时触发一次；
 * 不做"从关变开"的 warmup（各特性 register 在启服时无条件调用，早退型开关自然会生效）。
 */
public final class TomkesTookitConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(TomkesTookitMod.MOD_ID);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** JSON 数据结构。字段名即落盘 key。*/
    private static final class Data {
        Map<String, Boolean> features = new LinkedHashMap<>();
        List<String> kitItems = new ArrayList<>();       // 每项一段 SNBT
        boolean kitItemsInitialized = false;             // false 时 kitItems 回退到默认
    }

    private static Data data = new Data();
    private static Path dataFile;
    private static MinecraftServer currentServer;
    private static Supplier<List<ItemStack>> defaultKitSupplier = List::of;
    private static final Map<String, java.util.function.Consumer<MinecraftServer>> TEAR_DOWN = new LinkedHashMap<>();

    private TomkesTookitConfig() {}

    // ------------------------------------------------------------------ lifecycle

    public static void load(MinecraftServer server) {
        currentServer = server;
        dataFile = server.getSavePath(WorldSavePath.ROOT).resolve("data/tomkestookit_config.json");
        if (!Files.exists(dataFile)) return;
        try (Reader r = Files.newBufferedReader(dataFile)) {
            Type type = new TypeToken<Data>() {}.getType();
            Data loaded = GSON.fromJson(r, type);
            if (loaded != null) {
                if (loaded.features == null) loaded.features = new LinkedHashMap<>();
                if (loaded.kitItems == null) loaded.kitItems = new ArrayList<>();
                data = loaded;
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load tomkestookit config", e);
        }
    }

    /** Kit 默认清单由 KitCommand 在启服前调这个方法注入。*/
    public static void registerDefaultKit(Supplier<List<ItemStack>> supplier) {
        defaultKitSupplier = supplier;
    }

    /** feature 从"开"翻到"关"时触发一次的清理回调。同 key 后注册的覆盖前一个。*/
    public static void registerTearDown(String key, java.util.function.Consumer<MinecraftServer> r) {
        TEAR_DOWN.put(key, r);
    }

    // ------------------------------------------------------------------ features

    /** 未知 key 默认 true（升级兼容）。*/
    public static boolean enabled(String key) {
        Boolean v = data.features.get(key);
        return v == null || v;
    }

    public static void setEnabled(String key, boolean v) {
        Boolean prev = data.features.get(key);
        boolean was = prev == null || prev;
        data.features.put(key, v);
        save();
        if (was && !v) {
            java.util.function.Consumer<MinecraftServer> r = TEAR_DOWN.get(key);
            if (r != null) {
                try { r.accept(currentServer); }
                catch (Throwable t) { LOGGER.error("tear-down for {} failed", key, t); }
            }
        }
    }

    // ------------------------------------------------------------------ kit items

    public static List<ItemStack> kitItems() {
        if (!data.kitItemsInitialized) return copy(defaultKitSupplier.get());
        List<ItemStack> out = new ArrayList<>(data.kitItems.size());
        for (String snbt : data.kitItems) {
            try {
                NbtCompound tag = StringNbtReader.parse(snbt);
                ItemStack s = ItemStack.fromNbt(tag);
                if (!s.isEmpty()) out.add(s);
            } catch (Exception ignored) {}
        }
        return out;
    }

    public static void setKitItems(List<ItemStack> items) {
        List<String> encoded = new ArrayList<>(items.size());
        for (ItemStack s : items) {
            if (s == null || s.isEmpty()) continue;
            NbtCompound tag = new NbtCompound();
            s.writeNbt(tag);
            encoded.add(tag.toString());
        }
        data.kitItems = encoded;
        data.kitItemsInitialized = true;
        save();
    }

    public static void resetKitItems() {
        data.kitItems = new ArrayList<>();
        data.kitItemsInitialized = false;
        save();
    }

    public static boolean isKitCustomized() {
        return data.kitItemsInitialized;
    }

    // ------------------------------------------------------------------ internals

    private static List<ItemStack> copy(List<ItemStack> src) {
        if (src == null) return Collections.emptyList();
        List<ItemStack> out = new ArrayList<>(src.size());
        for (ItemStack s : src) if (s != null && !s.isEmpty()) out.add(s.copy());
        return out;
    }

    private static void save() {
        if (dataFile == null) return;
        try {
            Files.createDirectories(dataFile.getParent());
            try (Writer w = Files.newBufferedWriter(dataFile)) {
                GSON.toJson(data, w);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save tomkestookit config", e);
        }
    }
}
