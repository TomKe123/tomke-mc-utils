package com.tomkestookit;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.EnderChestInventory;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * 随身末影箱 / 随身工作台入口。
 *
 * 末影箱：调用 {@link ServerPlayerEntity#getEnderChestInventory()} 拿到玩家私有 27 格库存，
 *   用 {@link GenericContainerScreenHandler#createGeneric9x3} 打开标准 UI；
 *   vanilla EnderChestInventory.onOpen / onClose 会正常触发（无绑定的 activeBlockEntity 也 OK）。
 *
 * 工作台：{@link CraftingScreenHandler} + {@link ScreenHandlerContext#create}(world, pos)。
 *   非 EMPTY context 是必须的：
 *     - CraftingScreenHandler.onContentChanged 通过 context.run 触发 updateResult
 *       计算合成结果；EMPTY 会让输出槽永远空
 *     - close() 里 dropInventory 依赖 context.run 把 3×3 输入槽内容塞回背包/掉地，
 *       EMPTY 会导致物品被吞
 *   同时用匿名子类覆盖 canUse 恒返回 true：vanilla 的 CraftingScreenHandler.canUse
 *   会调 ScreenHandler.canUse(context, player, CRAFTING_TABLE)，检查 pos 处方块是否
 *   是工作台且距离 &le; 8。用玩家脚下方块位置显然过不了，如果不覆盖，服务器下一
 *   tick 就会关掉 UI —— 就是"随身工作台打不开"那个 bug 的成因。
 *   pos 用玩家当前 blockPos —— 只被 context.run(dropInventory) 用作物品掉落坐标，
 *   刚好落在玩家脚下（塞不下背包时也不会掉丢）。
 *
 * 两个入口都返 boolean（true=打开了；false=被 config 关掉或玩家不合法），
 * 供菜单按钮和命令 executes 共用。
 */
public final class PortableWorkstations {

    private PortableWorkstations() {}

    /** 打开随身末影箱。玩家必须是 ServerPlayer；被开关关掉时发红色提示并返回 false。*/
    public static boolean openEnderChest(ServerPlayerEntity player) {
        if (player == null) return false;
        if (!TomkesTookitConfig.enabled("item.portable_ender_chest")) {
            player.sendMessage(Text.literal("§c[随身末影箱] 该功能已被管理员禁用").formatted(Formatting.RED), true);
            return false;
        }
        EnderChestInventory ec = player.getEnderChestInventory();
        Text title = Text.literal("§5§l§n" + player.getGameProfile().getName() + " 的末影箱");
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, playerInv, ignored) -> GenericContainerScreenHandler.createGeneric9x3(syncId, playerInv, ec),
            title
        ));
        ec.onOpen(player);
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.BLOCK_ENDER_CHEST_OPEN, SoundCategory.BLOCKS, 0.5f, 0.9f);
        return true;
    }

    /** 打开随身工作台。返回 false = 被 config 关掉。*/
    public static boolean openCraftingTable(ServerPlayerEntity player) {
        if (player == null) return false;
        if (!TomkesTookitConfig.enabled("item.portable_workbench")) {
            player.sendMessage(Text.literal("§c[随身工作台] 该功能已被管理员禁用").formatted(Formatting.RED), true);
            return false;
        }
        ScreenHandlerContext context = ScreenHandlerContext.create(player.getWorld(), player.getBlockPos());
        Text title = Text.literal("§6§l§n工作台");
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, playerInv, ignored) -> new CraftingScreenHandler(syncId, playerInv, context) {
                // vanilla canUse 会检查 context 处方块是否是工作台，随身场景过不了；
                // 玩家能打开这个 UI 已经是靠命令/菜单授权过的，就让 canUse 一直放行。
                @Override public boolean canUse(PlayerEntity p) { return true; }
            },
            title
        ));
        return true;
    }
}
