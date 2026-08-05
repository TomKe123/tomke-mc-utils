package com.tomkestookit.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.HoglinEntity;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.entity.mob.ZoglinEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 修复收纳实体用刷怪蛋放出后，成年实体概率变成幼体的BUG。
 *
 * 原版问题：SpawnEggItem 调用 entity.initialize(..., EntityTag) 后，
 * 部分子类在 initialize 里会概率性覆盖 Age/IsBaby。
 *
 * 解决方案：hook spawnFromItemStack RETURN（spawn egg 的唯一调用点），
 * 此时实体已完全初始化，根据 EntityTag 最后修正年龄。
 */
@Mixin(EntityType.class)
public abstract class EntitySpawnAgeRestoreMixin {

    @Inject(method = "spawnFromItemStack(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/entity/SpawnReason;ZZ)Lnet/minecraft/entity/Entity;",
        at = @At("RETURN"))
    private void tomkestookit$restoreCapturedAge(
        net.minecraft.server.world.ServerWorld world,
        ItemStack stack,
        net.minecraft.entity.player.PlayerEntity player,
        BlockPos pos,
        SpawnReason reason,
        boolean alignPosition,
        boolean invertY,
        CallbackInfoReturnable<Entity> cir) {
        try {
            if (reason != SpawnReason.SPAWN_EGG) return;
            if (!com.tomkestookit.TomkesTookitConfig.enabled("item.entity_capture")) return;
            Entity entity = cir.getReturnValue();
            if (entity == null) return;
            NbtCompound nbt = stack.getNbt();
            if (nbt == null) return;
            NbtCompound entityTag = nbt.getCompound("EntityTag");
            if (entityTag == null || entityTag.isEmpty()) return;

            // 恢复 PassiveEntity 年龄
            if (entity instanceof PassiveEntity passive && entityTag.contains("Age")) {
                int savedAge = entityTag.getInt("Age");
                passive.setBreedingAge(savedAge);
                // 婴儿村民二次确保
                if (passive instanceof VillagerEntity villager) {
                    villager.setBreedingAge(savedAge);
                }
            }

            // 恢复 IsBaby
            if (entityTag.contains("IsBaby")) {
                boolean isBaby = entityTag.getBoolean("IsBaby");
                if (entity instanceof ZombieEntity z) z.setBaby(isBaby);
                else if (entity instanceof PiglinEntity p) p.setBaby(isBaby);
                else if (entity instanceof HoglinEntity h) h.setBaby(isBaby);
                else if (entity instanceof ZoglinEntity zg) zg.setBaby(isBaby);
            }
        } catch (Throwable ignored) {}
    }
}
