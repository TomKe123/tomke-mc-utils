package com.tomkestookit;

import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * 盔甲架 marker 通用清理工具。
 *
 * 各系统（物流终端、自动合成器）自己判定"是不是我的 marker"，其余的
 * 遍历/discard/AABB 扫描逻辑共用一份，避免重复代码 + 每系统各自实现。
 */
public final class ArmorStandMarkerUtil {

    private ArmorStandMarkerUtil() {}

    /**
     * 全世界扫描孤儿 marker：遍历所有加载 chunk 的 armor stand，
     * 判定为我方 marker 但 uuid 不在 alive 集合里 → discard。
     */
    public static int sweepOrphans(MinecraftServer server,
                                   Predicate<ArmorStandEntity> isMine,
                                   Set<UUID> aliveUuids) {
        int count = 0;
        for (ServerWorld world : server.getWorlds()) {
            List<ArmorStandEntity> found = null;
            for (Entity e : world.iterateEntities()) {
                if (!(e instanceof ArmorStandEntity stand)) continue;
                if (!isMine.test(stand)) continue;
                if (aliveUuids.contains(stand.getUuid())) continue;
                if (found == null) found = new ArrayList<>();
                found.add(stand);
            }
            if (found == null) continue;
            for (ArmorStandEntity stand : found) {
                stand.discard();
                count++;
            }
        }
        return count;
    }

    /**
     * 位置兜底扫描：清除给定方块上方一个小 AABB 内的我方 marker。
     * 用于 UUID 丢失 / 跨维度残留时的清理。
     *
     * @param yOffset marker 相对方块顶面的 y 偏移（原来物流终端和自动合成器都是 1.1）
     */
    public static void sweepAt(ServerWorld world, BlockPos anchor, double yOffset,
                               Predicate<ArmorStandEntity> isMine) {
        if (world == null) return;
        if (!world.isChunkLoaded(anchor.getX() >> 4, anchor.getZ() >> 4)) return;
        double cx = anchor.getX() + 0.5;
        double cy = anchor.getY() + yOffset;
        double cz = anchor.getZ() + 0.5;
        Box box = new Box(cx - 0.5, cy - 0.5, cz - 0.5, cx + 0.5, cy + 0.5, cz + 0.5);
        for (ArmorStandEntity s : world.getEntitiesByClass(ArmorStandEntity.class, box, isMine)) {
            s.discard();
        }
    }
}
