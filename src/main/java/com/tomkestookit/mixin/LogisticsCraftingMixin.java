package com.tomkestookit.mixin;

import com.tomkestookit.logistics.LogisticsHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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
 * 3×3：中心 LODESTONE，四周 IRON_BLOCK → 产出 2 个物流终端（同一 pairId）。
 * 与其他 crafting 结果 mixin 一样：只在 vanilla 未识别时覆盖。
 */
@Mixin(CraftingScreenHandler.class)
public class LogisticsCraftingMixin {

    @Inject(
        method = "updateResult(Lnet/minecraft/screen/ScreenHandler;Lnet/minecraft/world/World;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/inventory/RecipeInputInventory;Lnet/minecraft/inventory/CraftingResultInventory;)V",
        at = @At("TAIL")
    )
    private static void tomkestookit$logisticsResult(ScreenHandler handler, World world,
                                                    PlayerEntity player,
                                                    RecipeInputInventory input,
                                                    CraftingResultInventory result,
                                                    CallbackInfo ci) {
        if (world.isClient) return;
        if (!(player instanceof ServerPlayerEntity sp)) return;
        if (!result.getStack(0).isEmpty()) return;
        if (!com.tomkestookit.TomkesTookitConfig.enabled("recipe.logistics")) return;
        if (input.getWidth() != 3 || input.getHeight() != 3) return;

        // 中心 lodestone
        if (input.getStack(4).getItem() != Items.LODESTONE) return;
        // 四周 iron_block × 8
        int[] surround = {0, 1, 2, 3, 5, 6, 7, 8};
        for (int i : surround) {
            if (input.getStack(i).getItem() != Items.IRON_BLOCK) return;
        }

        ItemStack out = LogisticsHandler.buildResultPair();
        com.tomkestookit.FakeCraftMarker.mark(out);
        result.setStack(0, out);
        sp.networkHandler.sendPacket(new ScreenHandlerSlotUpdateS2CPacket(
            handler.syncId, handler.nextRevision(), 0, out));
    }
}
