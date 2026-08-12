package com.tomkestookit.mixin;

import com.tomkestookit.entitycap.SpawnReasonHolder;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 在 MobEntity.initialize HEAD 把 SpawnReason 写入到 Entity 上的 duck field，
 * 之后 ServerWorld.spawnEntity 可读回来判定是否被动生成。
 *
 * initialize 是每个 mob 从"创建 → 加入世界"路径必经的地方，
 * vanilla 的所有 spawn 都会走这里（NATURAL / SPAWNER / STRUCTURE / SPAWN_EGG / BREEDING / …）。
 */
@Mixin(MobEntity.class)
public abstract class MobEntityInitializeSpawnReasonMixin {

    @Inject(method = "initialize", at = @At("HEAD"))
    private void tomkestookit$captureSpawnReason(
            ServerWorldAccess world,
            LocalDifficulty difficulty,
            SpawnReason spawnReason,
            @Nullable EntityData entityData,
            @Nullable NbtCompound entityNbt,
            CallbackInfoReturnable<EntityData> cir) {
        ((SpawnReasonHolder) (Object) this).tomkestookit$setSpawnReason(spawnReason);
    }
}
