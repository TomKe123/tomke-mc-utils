package com.tomkestookit.mixin;

import com.tomkestookit.regionfiller.RegionFiller;
import com.tomkestookit.regionfiller.RegionFillerRecipe;
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
 * 3×3 全水桶 → 附魔水桶；全岩浆桶 → 附魔岩浆桶。
 * 只在 vanilla 未识别为其他配方（result 已空）时覆盖。
 */
@Mixin(CraftingScreenHandler.class)
public class RegionFillerCraftingMixin {

    @Inject(
        method = "updateResult(Lnet/minecraft/screen/ScreenHandler;Lnet/minecraft/world/World;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/inventory/RecipeInputInventory;Lnet/minecraft/inventory/CraftingResultInventory;)V",
        at = @At("TAIL")
    )
    private static void tomkestookit$regionFillerResult(ScreenHandler handler, World world,
                                                       PlayerEntity player,
                                                       RecipeInputInventory input,
                                                       CraftingResultInventory result,
                                                       CallbackInfo ci) {
        if (world.isClient) return;
        if (!(player instanceof ServerPlayerEntity sp)) return;
        if (!result.getStack(0).isEmpty()) return;
        if (!com.tomkestookit.TomkesTookitConfig.enabled("recipe.region_filler")) return;

        RegionFiller.FillerType type = RegionFillerRecipe.match3x3(input);
        if (type == null) return;

        ItemStack out = RegionFillerRecipe.buildResult(type);
        com.tomkestookit.FakeCraftMarker.mark(out);
        result.setStack(0, out);
        sp.networkHandler.sendPacket(new ScreenHandlerSlotUpdateS2CPacket(
            handler.syncId, handler.nextRevision(), 0, out));
    }
}
