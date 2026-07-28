package com.tomkestookit.mixin;

import net.minecraft.entity.EntityData;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 修复收纳实体用刷怪蛋放出后，成年村民/动物概率变成幼体的问题。
 *
 * vanilla 的 spawn egg 生成会调用 PassiveEntity.initialize，某些实体在这里
 * 会随机设置 Age（小概率 baby）。虽然 SpawnEggItem 会应用 EntityTag，
 * 但实际顺序/实体实现可能导致 Age 被 initialize 的逻辑覆盖。
 *
 * 这里在 initialize TAIL 做第二层保险：如果 spawn egg 的 EntityTag 明确带 Age，
 * 就在初始化结束后强制把 breeding age 设回去。
 *
 * 这样：
 *   - 成年村民收纳 → 放出仍成年（Age=0）
 *   - 小村民收纳 → 放出仍小村民（Age<0）
 */
@Mixin(PassiveEntity.class)
public abstract class PassiveEntityAgeRestoreMixin {

    @Inject(method = "initialize", at = @At("TAIL"))
    private void tomkestookit$restoreCapturedAge(
        ServerWorldAccess world,
        LocalDifficulty difficulty,
        SpawnReason spawnReason,
        @Nullable EntityData entityData,
        @Nullable NbtCompound entityNbt,
        CallbackInfoReturnable<EntityData> cir
    ) {
        if (spawnReason != SpawnReason.SPAWN_EGG) return;
        if (!com.tomkestookit.TomkesTookitConfig.enabled("item.entity_capture")) return;
        if (entityNbt == null || !entityNbt.contains("Age")) return;
        ((PassiveEntity) (Object) this).setBreedingAge(entityNbt.getInt("Age"));
    }
}
