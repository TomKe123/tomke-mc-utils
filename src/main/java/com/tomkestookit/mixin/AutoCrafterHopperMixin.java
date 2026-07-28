package com.tomkestookit.mixin;

import com.tomkestookit.AutoCrafterHandler;
import com.tomkestookit.AutoCrafterInventory;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让漏斗把自动合成器工作台当作 SidedInventory 处理。
 *
 * 工作台本身没有 BlockEntity，所以 vanilla 的 {@code HopperBlockEntity#getInventoryAt}
 * 会走到"按 pos 查 blockEntity"这一步返回 null，漏斗就完全无法与合成器交互。
 * 这里在 HEAD 提前介入：如果该位置是我们的自动合成器，直接返回共享 inv。
 */
@Mixin(HopperBlockEntity.class)
public abstract class AutoCrafterHopperMixin {

    @Inject(
        method = "getInventoryAt(Lnet/minecraft/world/World;DDD)Lnet/minecraft/inventory/Inventory;",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void tomkestookit$autoCrafterInventoryAt(World world, double x, double y, double z,
                                                            CallbackInfoReturnable<Inventory> cir) {
        if (!com.tomkestookit.TomkesTookitConfig.enabled("item.auto_crafter_ops")) return;
        if (!(world instanceof ServerWorld sw)) return;
        BlockPos pos = new BlockPos(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z));
        AutoCrafterInventory inv = AutoCrafterHandler.sharedInventoryAt(sw, pos);
        if (inv != null) cir.setReturnValue(inv);
    }
}
