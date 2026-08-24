package com.tomkestookit.mixin;

import com.tomkestookit.trainingdummy.TrainingDummyHandler;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 拦截玩家右键盔甲架：如果是训练假人，打开 DPS 测试菜单。
 */
@Mixin(ArmorStandEntity.class)
public abstract class TrainingDummyInteractMixin {

    @Inject(method = "interactAt", at = @At("HEAD"), cancellable = true)
    private void onInteractAt(PlayerEntity player, Vec3d hitPos, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if (player.getWorld().isClient) return;

        ArmorStandEntity self = (ArmorStandEntity) (Object) this;
        if (!TrainingDummyHandler.isTrainingDummyEntity(self)) return;

        ItemStack handStack = player.getStackInHand(hand);

        // 如果手持斧子，不拦截（让 ArmorStandDurableMixin 处理拆除逻辑）
        if (handStack.getItem() instanceof net.minecraft.item.AxeItem) return;

        // 空手或非斧子：打开 DPS 菜单
        TrainingDummyHandler.openDpsMenu(player, self);
        cir.setReturnValue(ActionResult.SUCCESS);
    }
}
