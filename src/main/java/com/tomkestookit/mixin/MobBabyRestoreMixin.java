package com.tomkestookit.mixin;

import net.minecraft.entity.EntityData;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.HoglinEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 修复收纳僵尸类 / 猪灵 / Hoglin 用刷怪蛋放出后概率变小的问题。
 *
 * PassiveEntityAgeRestoreMixin 只覆盖 PassiveEntity（走 Age 字段）。而 Zombie 类和
 * Piglin / Hoglin 属于 HostileEntity 分支，小/大状态存的是 dataTracker BABY，
 * NBT 键叫 "IsBaby"，用 setBaby(bool) 设置。这些实体的 initialize 里会有 5% 概率
 * 强制变小；我们在 initialize TAIL 里如果 spawn egg EntityTag 明确带 IsBaby，
 * 就 setBaby 回去。
 */
@Mixin(MobEntity.class)
public abstract class MobBabyRestoreMixin {

    @Inject(method = "initialize", at = @At("TAIL"))
    private void tomkestookit$restoreCapturedBaby(
        ServerWorldAccess world,
        LocalDifficulty difficulty,
        SpawnReason spawnReason,
        @Nullable EntityData entityData,
        @Nullable NbtCompound entityNbt,
        CallbackInfoReturnable<EntityData> cir
    ) {
        if (spawnReason != SpawnReason.SPAWN_EGG) return;
        if (!com.tomkestookit.TomkesTookitConfig.enabled("item.entity_capture")) return;
        if (entityNbt == null || !entityNbt.contains("IsBaby")) return;
        boolean baby = entityNbt.getBoolean("IsBaby");
        MobEntity self = (MobEntity) (Object) this;
        if (self instanceof ZombieEntity z) z.setBaby(baby);
        else if (self instanceof PiglinEntity p) p.setBaby(baby);
        else if (self instanceof HoglinEntity h) h.setBaby(baby);
    }
}
