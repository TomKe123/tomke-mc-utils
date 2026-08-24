package com.tomkestookit.mixin;

import com.tomkestookit.trainingdummy.TrainingDummyHandler;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ArmorStandItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 训练假人放置：物品放置成盔甲架实体后，给实体打上 trainingDummy 标记。
 *
 * 注入 useOnBlock 的 RETURN 点（此时盔甲架已生成），根据物品 NBT 判断是否训练假人，
 * 若是则查找刚放置位置的盔甲架并标记。用 RETURN 而非 INVOKE 避免依赖 vanilla
 * 内部 spawn 调用点的具体映射名（之前注入 World.spawnEntity 在 1.20.1 实际是
 * ServerWorld.spawnEntityAndPassengers，导致 0 目标匹配）。
 */
@Mixin(ArmorStandItem.class)
public abstract class TrainingDummyPlaceMixin {

    @Inject(method = "useOnBlock", at = @At("RETURN"))
    private void tomkestookit$markTrainingDummy(ItemUsageContext context,
                                                CallbackInfoReturnable<ActionResult> cir) {
        try {
            if (context.getWorld().isClient) return;
            if (cir.getReturnValue() == null || !cir.getReturnValue().isAccepted()) return;

            ItemStack stack = context.getStack();
            if (!TrainingDummyHandler.isTrainingDummyItem(stack)) return;

            // 查找刚放置的盔甲架并打 scoreboard tag（持久化标记）
            BlockPos pos = context.getBlockPos().offset(context.getSide());
            Box box = new Box(pos).expand(2.0);
            context.getWorld().getEntitiesByClass(ArmorStandEntity.class, box, e -> true)
                .stream().findFirst().ifPresent(TrainingDummyHandler::markEntity);
        } catch (Throwable ignored) {}
    }
}
