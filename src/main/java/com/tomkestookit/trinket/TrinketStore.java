package com.tomkestookit.trinket;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.tomkestookit.TomkesTookitMod;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 每玩家 27 格饰品袋持久化。存 JSON，值为 SNBT 字符串数组。
 * 支持渐进解锁：capacity 从 3 起步，通过消耗物资升级到 27。
 *
 * 性能优化：内存缓存已反序列化的 ItemStack[]，write/reset 时失效。
 * 所有访问都在 server thread，无需同步。
 */
public final class TrinketStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final int MAX_SIZE = 108;
    public static final int PAGE_SIZE = 27;
    public static final int PAGE_COUNT = MAX_SIZE / PAGE_SIZE; // 4
    public static final int INITIAL_CAPACITY = 3;
    // 老存档：一律按老的默认容量（原 27 = 一页）恢复
    public static final int LEGACY_DEFAULT_CAPACITY = PAGE_SIZE;

    private static final class Data {
        Map<String, String[]> slots = new LinkedHashMap<>();
        Map<String, Integer> capacity = new LinkedHashMap<>();
    }

    private static Data data = new Data();
    private static Path dataFile;

    /** 反序列化缓存：避免每 tick 重复解析 SNBT。key = uuid string。*/
    private static final Map<String, ItemStack[]> cache = new HashMap<>();

    private TrinketStore() {}
    public static void load(MinecraftServer server) {
        dataFile = server.getSavePath(WorldSavePath.ROOT).resolve("data/tomkestookit_trinkets.json");
        data = new Data();
        cache.clear();
        if (!Files.exists(dataFile)) return;
        try (Reader r = Files.newBufferedReader(dataFile)) {
            Type type = new TypeToken<Data>() {}.getType();
            Data loaded = GSON.fromJson(r, type);
            if (loaded != null) {
                if (loaded.slots == null) loaded.slots = new LinkedHashMap<>();
                if (loaded.capacity == null) loaded.capacity = new LinkedHashMap<>();
                data = loaded;
            }
        } catch (Exception e) {
            tryLoadLegacy();
        }
    }

    private static void tryLoadLegacy() {
        try (Reader r = Files.newBufferedReader(dataFile)) {
            Type legacy = new TypeToken<HashMap<String, String[]>>() {}.getType();
            Map<String, String[]> old = GSON.fromJson(r, legacy);
            if (old != null && !old.isEmpty()) {
                data.slots = new LinkedHashMap<>(old);
                for (String uuid : old.keySet()) {
                    data.capacity.put(uuid, LEGACY_DEFAULT_CAPACITY);
                }
                save();
            }
        } catch (Exception e) {
            TomkesTookitMod.LOGGER.error("Failed to load trinket store (legacy fallback)", e);
        }
    }

    /**
     * 读取玩家饰品袋（缓存命中时零开销）。
     * 返回的数组是内部缓存的引用，调用方不应修改。
     * 需要可写副本时用 {@link #readCopy(UUID)}。
     */
    public static ItemStack[] read(UUID playerId) {
        String key = playerId.toString();
        ItemStack[] cached = cache.get(key);
        if (cached != null) return cached;
        // 缓存未命中：反序列化
        ItemStack[] out = deserialize(key);
        cache.put(key, out);
        return out;
    }

    /** 读取可写副本（UI 用，需要修改 slot 内容时）。*/
    public static ItemStack[] readCopy(UUID playerId) {
        ItemStack[] src = read(playerId);
        ItemStack[] copy = new ItemStack[MAX_SIZE];
        for (int i = 0; i < MAX_SIZE; i++) {
            copy[i] = src[i].isEmpty() ? ItemStack.EMPTY : src[i].copy();
        }
        return copy;
    }

    private static ItemStack[] deserialize(String key) {
        ItemStack[] out = new ItemStack[MAX_SIZE];
        String[] arr = data.slots.get(key);
        for (int i = 0; i < MAX_SIZE; i++) {
            if (arr != null && i < arr.length && arr[i] != null && !arr[i].isBlank()) {
                try {
                    NbtCompound nbt = StringNbtReader.parse(arr[i]);
                    out[i] = ItemStack.fromNbt(nbt);
                } catch (Exception e) { out[i] = ItemStack.EMPTY; }
            } else {
                out[i] = ItemStack.EMPTY;
            }
        }
        return out;
    }

    public static void write(UUID playerId, ItemStack[] stacks) {
        String key = playerId.toString();
        String[] arr = new String[MAX_SIZE];
        boolean any = false;
        for (int i = 0; i < MAX_SIZE; i++) {
            if (stacks != null && i < stacks.length && stacks[i] != null && !stacks[i].isEmpty()) {
                NbtCompound nbt = new NbtCompound();
                stacks[i].writeNbt(nbt);
                arr[i] = nbt.toString();
                any = true;
            }
        }
        if (any) data.slots.put(key, arr);
        else data.slots.remove(key);
        // 失效缓存
        cache.remove(key);
        save();
    }

    public static int getCapacity(UUID playerId) {
        Integer c = data.capacity.get(playerId.toString());
        if (c == null) return INITIAL_CAPACITY;
        return Math.max(INITIAL_CAPACITY, Math.min(MAX_SIZE, c));
    }

    public static void setCapacity(UUID playerId, int capacity) {
        int clamped = Math.max(INITIAL_CAPACITY, Math.min(MAX_SIZE, capacity));
        data.capacity.put(playerId.toString(), clamped);
        save();
    }

    public static int resetAllCapacity() {
        int count = data.capacity.size();
        data.capacity.clear();
        save();
        return count;
    }

    public static void resetCapacity(UUID playerId) {
        data.capacity.remove(playerId.toString());
        save();
    }

    private static void save() {
        if (dataFile == null) return;
        try {
            Files.createDirectories(dataFile.getParent());
            try (Writer w = Files.newBufferedWriter(dataFile)) {
                GSON.toJson(data, w);
            }
        } catch (IOException e) {
            TomkesTookitMod.LOGGER.error("Failed to save trinket store", e);
        }
    }
}
