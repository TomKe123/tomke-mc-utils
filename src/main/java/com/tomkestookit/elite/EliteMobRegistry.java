package com.tomkestookit.elite;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 精英怪物注册中心。维护当前被追踪的精英怪 UUID 集合。
 * 通过 NBT 标记 {@code TomkesTookitElite} 判断实体是否为精英。
 */
public final class EliteMobRegistry {

    public static final String NBT_TAG = "TomkesTookitElite";

    private static final Set<UUID> TRACKED = new HashSet<>();

    private EliteMobRegistry() {}

    /**
     * 检查指定实体是否携带精英 NBT 标记。
     */
    public static boolean isElite(net.minecraft.entity.mob.MobEntity mob) {
        if (mob == null) return false;
        net.minecraft.nbt.NbtCompound nbt = new net.minecraft.nbt.NbtCompound();
        mob.writeCustomDataToNbt(nbt);
        return nbt.contains(NBT_TAG, net.minecraft.nbt.NbtElement.COMPOUND_TYPE);
    }

    /** 读取实体 NBT 中记录的精英 tier；非精英返回 0。*/
    public static int getTier(net.minecraft.entity.mob.MobEntity mob) {
        if (mob == null) return 0;
        net.minecraft.nbt.NbtCompound nbt = new net.minecraft.nbt.NbtCompound();
        mob.writeCustomDataToNbt(nbt);
        if (!nbt.contains(NBT_TAG, net.minecraft.nbt.NbtElement.COMPOUND_TYPE)) return 0;
        net.minecraft.nbt.NbtCompound tag = nbt.getCompound(NBT_TAG);
        return tag.getInt("tier");
    }

    /**
     * 将精英怪 UUID 注册到追踪集合。
     */
    public static void register(UUID uuid, int tier) {
        if (uuid != null) {
            TRACKED.add(uuid);
        }
    }

    /**
     * 从追踪集合中移除指定 UUID。
     */
    public static void remove(UUID uuid) {
        TRACKED.remove(uuid);
    }

    /**
     * 返回当前追踪的精英怪 UUID 集合的不可变副本。
     */
    public static Set<UUID> getTrackedMobs() {
        return Collections.unmodifiableSet(new HashSet<>(TRACKED));
    }

    /**
     * 清空所有追踪记录（用于服务器关闭时重置）。
     */
    public static void clear() {
        TRACKED.clear();
    }
}
