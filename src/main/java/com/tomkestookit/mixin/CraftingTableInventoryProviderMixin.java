package com.tomkestookit.mixin;

import com.tomkestookit.AutoCrafterHandler;
import com.tomkestookit.AutoCrafterInventory;
import net.minecraft.block.BlockState;
import net.minecraft.block.CraftingTableBlock;
import net.minecraft.block.InventoryProvider;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 让所有 CraftingTableBlock 实例都实现 {@link InventoryProvider}，
 * 这样漏斗（含 lithium 优化过的漏斗、Create 之类的机械管道）会用官方
 * Inventory 查询接口找到自动合成器的共享库存。
 *
 * 对于普通工作台位置返回 null，vanilla getInventoryAt 会继续走 fallback 路径，
 * 相当于没影响。
 */
@Mixin(CraftingTableBlock.class)
public abstract class CraftingTableInventoryProviderMixin implements InventoryProvider {

    @Override
    public SidedInventory getInventory(BlockState state, WorldAccess world, BlockPos pos) {
        if (!com.tomkestookit.TomkesTookitConfig.enabled("item.auto_crafter_ops")) return null;
        if (!(world instanceof ServerWorld sw)) return null;
        AutoCrafterInventory inv = AutoCrafterHandler.sharedInventoryAt(sw, pos);
        return inv;
    }
}
