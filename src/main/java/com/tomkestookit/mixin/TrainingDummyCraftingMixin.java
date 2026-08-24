package com.tomkestookit.mixin;

import com.tomkestookit.trainingdummy.TrainingDummyHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 训练假人合成配方：3×3 中心盔甲架，周围 8 个干草块。
 *
 * 拦截 updateResult TAIL，当 vanilla 未识别配方（result 槽为空）时覆盖成训练假人。
 * 手动 sendPacket 一次 SlotUpdate，让客户端看到结果。
 */
@Mixin(CraftingScreenHandler.class)
public class TrainingDummyCraftingMixin {

    @Inject(
        method = "updateResult(Lnet/minecraft/screen/ScreenHandler;Lnet/minecraft/world/World;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/inventory/RecipeInputInventory;Lnet/minecraft/inventory/CraftingResultInventory;)V",
        at = @At("TAIL")
    )
    private static void tomkestookit$trainingDummyResult(ScreenHandler handler, World world,
                                                        PlayerEntity player,
                                                        RecipeInputInventory input,
                                                        CraftingResultInventory result,
                                                        CallbackInfo ci) {
        if (world.isClient) return;
        if (!(player instanceof ServerPlayerEntity sp)) return;
        if (!result.getStack(0).isEmpty()) return;
        if (!com.tomkestookit.TomkesTookitConfig.enabled("recipe.training_dummy")) return;

        if (!TrainingDummyHandler.matches3x3(input)) return;

        ItemStack out = TrainingDummyHandler.buildResult();
        com.tomkestookit.FakeCraftMarker.mark(out);
        result.setStack(0, out);
        sp.networkHandler.sendPacket(new ScreenHandlerSlotUpdateS2CPacket(
            handler.syncId, handler.nextRevision(), 0, out));
    }
}
