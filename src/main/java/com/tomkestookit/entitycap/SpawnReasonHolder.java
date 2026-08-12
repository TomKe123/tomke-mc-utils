package com.tomkestookit.entitycap;

import net.minecraft.entity.SpawnReason;

/**
 * Duck interface 注入到 Entity，记录该实体的 SpawnReason。
 *
 * SpawnReason 在 vanilla 里不持久化到 Entity 上（只在 initialize/spawn 调用时作为参数传入），
 * 我们通过 mixin 在 MobEntity.initialize HEAD 把 reason 存到这个 field，
 * 之后在 ServerWorld.spawnEntity 里可读取来判定是不是被动生成。
 *
 * 服务器重启后该字段为 null（不持久化），已加载的实体一律视为"未知/非被动"，
 * 不会被 EntityCapHandler 计入检查。
 */
public interface SpawnReasonHolder {
    void tomkestookit$setSpawnReason(SpawnReason reason);
    SpawnReason tomkestookit$getSpawnReason();
}
