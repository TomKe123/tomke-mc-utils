package com.tomkestookit.mixin;

import com.tomkestookit.AutoCrafterHandler;
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

/** 3×3：中心工作台，四角铁锭，四边红石 → 自动合成器。*/
@Mixin(CraftingScreenHandler.class)
public class AutoCrafterCraftingMixin {
    @Inject(
        method = "updateResult(Lnet/minecraft/screen/ScreenHandler;Lnet/minecraft/world/World;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/inventory/RecipeInputInventory;Lnet/minecraft/inventory/CraftingResultInventory;)V",
        at = @At("TAIL")
    )
    private static void tomkestookit$autoCrafterResult(ScreenHandler handler, World world,
                                                       PlayerEntity player,
                                                       RecipeInputInventory input,
                                                       CraftingResultInventory result,
                                                       CallbackInfo ci) {
        if (world.isClient) return;
        if (!(player instanceof ServerPlayerEntity sp)) return;
        if (!result.getStack(0).isEmpty()) return;
        if (!com.tomkestookit.TomkesTookitConfig.enabled("recipe.auto_crafter")) return;
        if (input.getWidth() != 3 || input.getHeight() != 3) return;
        if (input.getStack(4).getItem() != Items.CRAFTING_TABLE) return;
        int[] corners = {0, 2, 6, 8};
        int[] edges = {1, 3, 5, 7};
        for (int i : corners) if (input.getStack(i).getItem() != Items.IRON_INGOT) return;
        for (int i : edges) if (input.getStack(i).getItem() != Items.REDSTONE) return;

        ItemStack out = AutoCrafterHandler.buildItem();
        com.tomkestookit.FakeCraftMarker.mark(out);
        result.setStack(0, out);
        sp.networkHandler.sendPacket(new ScreenHandlerSlotUpdateS2CPacket(
            handler.syncId, handler.nextRevision(), 0, out));
    }
}
