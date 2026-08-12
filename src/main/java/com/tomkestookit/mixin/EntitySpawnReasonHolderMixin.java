package com.tomkestookit.mixin;

import com.tomkestookit.entitycap.SpawnReasonHolder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SpawnReason;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * 给 Entity 挂一个 SpawnReason 字段（不持久化）。
 * 由 MobEntityInitializeSpawnReasonMixin 在 initialize HEAD 时写入。
 */
@Mixin(Entity.class)
public abstract class EntitySpawnReasonHolderMixin implements SpawnReasonHolder {

    @Unique
    private SpawnReason tomkestookit$spawnReason;

    @Override
    public void tomkestookit$setSpawnReason(SpawnReason reason) {
        this.tomkestookit$spawnReason = reason;
    }

    @Override
    public SpawnReason tomkestookit$getSpawnReason() {
        return this.tomkestookit$spawnReason;
    }
}
