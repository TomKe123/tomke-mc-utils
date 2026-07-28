package com.tomkestookit.logistics;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * 物流终端上方的显示盔甲架：不可见、无重力、marker、customNameVisible。
 * 通过 NBT 一次性配置（避免访问 private setter）。
 */
public final class LogisticsMarker {

    private LogisticsMarker() {}

    /** scoreboard tag，标识物流终端 marker 盔甲架，用于识别和清理孤儿。*/
    public static final String TAG = "tomkestookit_logistics_marker";

    /** 在方块顶面正中生成一个盔甲架并返回其 UUID。*/
    public static UUID create(ServerWorld world, BlockPos barrelPos, Text text) {
        ArmorStandEntity stand = EntityType.ARMOR_STAND.create(world);
        if (stand == null) return null;
        NbtCompound nbt = new NbtCompound();
        // 位置
        NbtList pos = new NbtList();
        pos.add(NbtDouble.of(barrelPos.getX() + 0.5));
        pos.add(NbtDouble.of(barrelPos.getY() + 1.1));
        pos.add(NbtDouble.of(barrelPos.getZ() + 0.5));
        nbt.put("Pos", pos);
        nbt.putBoolean("Invisible", true);
        nbt.putBoolean("NoGravity", true);
        nbt.putBoolean("Marker", true);
        nbt.putBoolean("Small", true);
        nbt.putBoolean("ShowArms", false);
        nbt.putBoolean("Invulnerable", true);
        nbt.putBoolean("CustomNameVisible", true);
        nbt.putString("CustomName", Text.Serializer.toJson(text));
        // scoreboard tags：让我们能在 chunk 加载时找到孤儿 marker
        NbtList tags = new NbtList();
        tags.add(net.minecraft.nbt.NbtString.of(TAG));
        nbt.put("Tags", tags);
        stand.readNbt(nbt);
        // 确认 tag 落到 CommandTags（vanilla readNbt 后 Tags 会走进 scoreboardTags）
        stand.addCommandTag(TAG);
        stand.refreshPositionAndAngles(
            barrelPos.getX() + 0.5, barrelPos.getY() + 1.1, barrelPos.getZ() + 0.5,
            0.0f, 0.0f);
        world.spawnEntity(stand);
        return stand.getUuid();
    }

    /** 更新盔甲架的 CustomName。找不到则返回 false（可能需要重建）。*/
    public static boolean updateText(MinecraftServer server, String dim, UUID uuid, Text text) {
        if (uuid == null) return false;
        ServerWorld world = resolveWorld(server, dim);
        if (world == null) return false;
        Entity e = world.getEntity(uuid);
        if (!(e instanceof ArmorStandEntity)) return false;
        e.setCustomName(text);
        e.setCustomNameVisible(true);
        return true;
    }

    public static void remove(MinecraftServer server, String dim, UUID uuid) {
        if (uuid == null) return;
        ServerWorld world = resolveWorld(server, dim);
        if (world == null) return;
        Entity e = world.getEntity(uuid);
        if (e != null) {
            e.discard();
        }
    }

    /**
     * 位置兜底扫描：清除 barrelPos 上方 (y+1.1) 附近**带我们 tag** 的盔甲架。
     * 用于跨维度或 UUID 不匹配时的清理，只影响物流终端 marker，不误伤别的实体。
     */
    public static void sweepAt(MinecraftServer server, String dim, BlockPos barrelPos) {
        com.tomkestookit.ArmorStandMarkerUtil.sweepAt(
            resolveWorld(server, dim), barrelPos, 1.1, LogisticsMarker::isOurs);
    }

    /** 判定一个 armor stand 是不是我们的 marker（tag 或 CustomName 前缀）。*/
    public static boolean isOurs(ArmorStandEntity stand) {
        if (stand.getCommandTags().contains(TAG)) return true;
        if (!stand.hasCustomName()) return false;
        Text name = stand.getCustomName();
        if (name == null) return false;
        String s = name.getString();
        // 兼容老版本没打 tag 的 marker，靠名字前缀识别
        return s.contains("物流终端 #")
            || s.contains("[未配对]")
            || s.contains("[对端未加载]")
            || s.contains("[对端丢失]");
    }

    /**
     * 全世界扫描孤儿 marker。委托给 ArmorStandMarkerUtil.sweepOrphans。
     */
    public static int sweepOrphans(MinecraftServer server, java.util.Set<UUID> aliveUuids) {
        return com.tomkestookit.ArmorStandMarkerUtil.sweepOrphans(server, LogisticsMarker::isOurs, aliveUuids);
    }

    public static ServerWorld resolveWorld(MinecraftServer server, String dim) {
        if (dim == null) return null;
        Identifier id = Identifier.tryParse(dim);
        if (id == null) return null;
        RegistryKey<World> key = RegistryKey.of(RegistryKeys.WORLD, id);
        return server.getWorld(key);
    }
}
