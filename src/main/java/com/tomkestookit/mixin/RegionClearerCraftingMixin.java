package com.tomkestookit.mixin;

import com.tomkestookit.regionclearer.RegionClearer;
import com.tomkestookit.regionclearer.RegionClearerRecipe;
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
 * 3×3：中心 iron_pickaxe，四周同种材质 → 区域清除斧。
 *
 * 同 CraftingResultMixin 的做法：拦截 updateResult TAIL，仅当 vanilla 未识别配方
 * （result 槽已空）时覆盖成我们的结果，避免踩到未来 datapack 里可能加的匹配。
 * 手动 sendPacket 一次 SlotUpdate，让客户端看到最终结果。
 */
@Mixin(CraftingScreenHandler.class)
public class RegionClearerCraftingMixin {

    @Inject(
        method = "updateResult(Lnet/minecraft/screen/ScreenHandler;Lnet/minecraft/world/World;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/inventory/RecipeInputInventory;Lnet/minecraft/inventory/CraftingResultInventory;)V",
        at = @At("TAIL")
    )
    private static void tomkestookit$regionClearerResult(ScreenHandler handler, World world,
                                                        PlayerEntity player,
                                                        RecipeInputInventory input,
                                                        CraftingResultInventory result,
                                                        CallbackInfo ci) {
        if (world.isClient) return;
        if (!(player instanceof ServerPlayerEntity sp)) return;
        if (!result.getStack(0).isEmpty()) return;
        if (!com.tomkestookit.TomkesTookitConfig.enabled("recipe.region_clearer")) return;

        RegionClearer.ClearerTier tier = RegionClearerRecipe.match3x3(input);
        if (tier == null) return;

        ItemStack out = RegionClearerRecipe.buildResult(tier);
        com.tomkestookit.FakeCraftMarker.mark(out);
        result.setStack(0, out);
        sp.networkHandler.sendPacket(new ScreenHandlerSlotUpdateS2CPacket(
            handler.syncId, handler.nextRevision(), 0, out));
    }
}
