package com.tomkestookit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 自动合成器（特殊工作台）的方块位置、配方、库存与显示 marker 持久化。*/
public final class AutoCrafterTracker {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, Config> CRAFTERS = new HashMap<>();
    private static Path dataFile;

    private AutoCrafterTracker() {}

    public static final class Config {
        public String dim;
        public int x;
        public int y;
        public int z;
        public String[] recipe = new String[9];
        public String[] inventory = new String[10];
        /** always / powered / pulse；pulseMode 用于兼容旧存档。*/
        public String triggerMode = "powered";
        public boolean pulseMode;
        public boolean powered;
        public int flashTicks;
        public String markerUuid;
        public String status = "空闲";

        public Config() {}

        Config(ServerWorld world, BlockPos pos) {
            this.dim = world.getRegistryKey().getValue().toString();
            this.x = pos.getX();
            this.y = pos.getY();
            this.z = pos.getZ();
        }
    }

    public static void load(MinecraftServer server) {
        dataFile = server.getSavePath(WorldSavePath.ROOT).resolve("data/tomkestookit_auto_crafters.json");
        CRAFTERS.clear();
        if (!Files.exists(dataFile)) return;
        try (Reader r = Files.newBufferedReader(dataFile)) {
            Type type = new TypeToken<HashMap<String, Config>>() {}.getType();
            Map<String, Config> loaded = GSON.fromJson(r, type);
            if (loaded != null) CRAFTERS.putAll(loaded);
            for (Config c : CRAFTERS.values()) normalize(c);
        } catch (IOException e) {
            TomkesTookitMod.LOGGER.error("Failed to load auto crafters", e);
        }
    }

    private static volatile boolean dirty = false;

    /** 标脏；实际磁盘写入由 AutoCrafterHandler.tick 每 200 tick flush 一次。*/
    public static void save() { dirty = true; }

    /** 立即落盘。仅在关键节点（例如破坏、mod 卸载）显式调用。*/
    public static void flush() {
        if (!dirty) return;
        dirty = false;
        if (dataFile == null) return;
        try {
            Files.createDirectories(dataFile.getParent());
            try (Writer w = Files.newBufferedWriter(dataFile)) {
                GSON.toJson(CRAFTERS, w);
            }
        } catch (IOException e) {
            TomkesTookitMod.LOGGER.error("Failed to save auto crafters", e);
        }
    }

    public static boolean isDirty() { return dirty; }

    public static Config get(ServerWorld world, BlockPos pos) {
        return CRAFTERS.get(key(world, pos));
    }

    public static boolean isAutoCrafter(ServerWorld world, BlockPos pos) {
        return get(world, pos) != null;
    }

    public static Config ensure(ServerWorld world, BlockPos pos) {
        Config c = CRAFTERS.computeIfAbsent(key(world, pos), k -> new Config(world, pos));
        normalize(c);
        return c;
    }

    public static void remove(ServerWorld world, BlockPos pos) {
        if (CRAFTERS.remove(key(world, pos)) != null) save();
    }

    public static void removeKey(String key) {
        if (CRAFTERS.remove(key) != null) save();
    }

    public static Iterable<Map.Entry<String, Config>> entries() {
        return CRAFTERS.entrySet();
    }

    public static String key(ServerWorld world, BlockPos pos) {
        return world.getRegistryKey().getValue() + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    public static BlockPos pos(Config c) {
        return new BlockPos(c.x, c.y, c.z);
    }

    public static boolean pointsToCraftingTable(MinecraftServer server, Config c) {
        ServerWorld world = resolveWorld(server, c.dim);
        if (world == null || !world.isChunkLoaded(c.x >> 4, c.z >> 4)) return true;
        return world.getBlockState(pos(c)).isOf(Blocks.CRAFTING_TABLE);
    }

    public static ServerWorld resolveWorld(MinecraftServer server, String dim) {
        Identifier id = Identifier.tryParse(dim);
        if (id == null) return null;
        for (ServerWorld world : server.getWorlds()) {
            if (world.getRegistryKey().getValue().equals(id)) return world;
        }
        return null;
    }

    public static ItemStack stackFromId(String id) {
        if (id == null || id.isBlank()) return ItemStack.EMPTY;
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null || !Registries.ITEM.containsId(identifier)) return ItemStack.EMPTY;
        return new ItemStack(Registries.ITEM.get(identifier));
    }

    public static String idFromStack(ItemStack stack) {
        if (stack.isEmpty()) return null;
        Item item = stack.getItem();
        Identifier id = Registries.ITEM.getId(item);
        return id == null ? null : id.toString();
    }

    public static ItemStack inventoryStack(Config c, int slot) {
        normalize(c);
        if (slot < 0 || slot >= c.inventory.length || c.inventory[slot] == null || c.inventory[slot].isBlank()) return ItemStack.EMPTY;
        try {
            return ItemStack.fromNbt(net.minecraft.nbt.StringNbtReader.parse(c.inventory[slot]));
        } catch (Exception ignored) {
            return ItemStack.EMPTY;
        }
    }

    public static void setInventoryStack(Config c, int slot, ItemStack stack) {
        normalize(c);
        if (slot < 0 || slot >= c.inventory.length) return;
        if (stack.isEmpty()) {
            c.inventory[slot] = null;
        } else {
            NbtCompound nbt = new NbtCompound();
            stack.writeNbt(nbt);
            c.inventory[slot] = nbt.toString();
        }
    }

    public static UUID markerUuid(Config c) {
        if (c.markerUuid == null || c.markerUuid.isBlank()) return null;
        try { return UUID.fromString(c.markerUuid); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private static void normalize(Config c) {
        if (c.recipe == null || c.recipe.length != 9) c.recipe = new String[9];
        if (c.inventory == null) c.inventory = new String[10];
        if (c.inventory.length != 10) {
            String[] old = c.inventory;
            c.inventory = new String[10];
            System.arraycopy(old, 0, c.inventory, 0, Math.min(old.length, c.inventory.length));
        }
        if (c.triggerMode == null || c.triggerMode.isBlank()) c.triggerMode = c.pulseMode ? "pulse" : "powered";
        if (c.status == null || c.status.isBlank()) c.status = "空闲";
    }
}
