package com.tomkestookit.mixin;

import com.tomkestookit.recipe.SaturationPotion;
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
 * 拦截 CraftingScreenHandler.updateResult 的尾巴，若输入是 3x3 全面包
 * 就把结果槽（slot 0）设为"永久饱和喷溅药水"。
 *
 * 之所以 mixin 到这一个静态方法：CraftingScreenHandler 和 PlayerScreenHandler
 * 都调用它更新结果槽，一次覆盖两处。
 *
 * 关键设计：
 *   - 只在服务端替换。客户端上 vanilla 走一次 updateResult 会把 result 置空
 *     （因为它没找到配方），服务端下一 tick 通过 SlotUpdate 把我们塞的结果
 *     同步下去；玩家看到的最终显示就是药水，不会因客户端不知道配方而红叉。
 *   - vanilla onTakeItem 里遍历输入槽 removeStack(i, 1) 是无条件的
 *     （只要不空就扣），因此我们不需要额外处理扣材料。
 */
@Mixin(CraftingScreenHandler.class)
public class CraftingResultMixin {

    @Inject(
        method = "updateResult(Lnet/minecraft/screen/ScreenHandler;Lnet/minecraft/world/World;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/inventory/RecipeInputInventory;Lnet/minecraft/inventory/CraftingResultInventory;)V",
        at = @At("TAIL")
    )
    private static void tomkestookit$overrideResult(ScreenHandler handler, World world,
                                                  PlayerEntity player,
                                                  RecipeInputInventory input,
                                                  CraftingResultInventory result,
                                                  CallbackInfo ci) {
        if (world.isClient) return;
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return;

        // 只有 vanilla 没找到配方（result 已被 setStack(EMPTY)）时才覆盖，
        // 避免踩到未来 datapack 可能加的匹配。
        if (!result.getStack(0).isEmpty()) return;

        ItemStack stack;
        if (com.tomkestookit.TomkesTookitConfig.enabled("recipe.greed_potion")
            && com.tomkestookit.recipe.GreedPotion.matches3x3(input)) {
            stack = com.tomkestookit.recipe.GreedPotion.buildResult();
        } else if (com.tomkestookit.TomkesTookitConfig.enabled("recipe.sugar_tnt")
            && com.tomkestookit.recipe.SugarTnt.matches3x3(input)) {
            stack = com.tomkestookit.recipe.SugarTnt.buildResult();
        } else if (com.tomkestookit.TomkesTookitConfig.enabled("recipe.saturation_potion")
            && SaturationPotion.matches3x3(input)) {
            stack = SaturationPotion.buildResult();
        } else {
            return;
        }
        com.tomkestookit.FakeCraftMarker.mark(stack);
        result.setStack(0, stack);

        // vanilla 在 setStack(EMPTY) 之后已经 sendPacket 了一次 EMPTY 结果，
        // 客户端此时看到的是空 result；我们必须再发一次同步包让客户端看到药水。
        serverPlayer.networkHandler.sendPacket(new ScreenHandlerSlotUpdateS2CPacket(
            handler.syncId, handler.nextRevision(), 0, stack));
    }
}
