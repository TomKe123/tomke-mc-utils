package com.tomkestookit;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.EnderChestInventory;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
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
 * 工作台：{@link CraftingScreenHandler} + {@link ScreenHandlerContext#EMPTY}，
 *   vanilla 里 CraftingTableBlock 也是这条路径。EMPTY context 意味着"关掉时物品不掉地上"——
 *   会用 shift-click 或 Q 键掉在玩家周围，符合玩家体感。
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
        Text title = Text.literal("§6§l§n工作台");
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, playerInv, ignored) -> factory(syncId, playerInv),
            title
        ));
        return true;
    }

    private static ScreenHandler factory(int syncId, PlayerInventory playerInv) {
        // ScreenHandlerContext.EMPTY：关闭时输入槽的物品会自动回到玩家背包 / 溢出掉地上，
        // 无绑定方块位置——符合"随身"语义。
        return new CraftingScreenHandler(syncId, playerInv, ScreenHandlerContext.EMPTY);
    }
}
