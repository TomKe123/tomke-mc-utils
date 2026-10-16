package com.tomkestookit;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.tomkestookit.menu.MenuClock;
import com.tomkestookit.regionclearer.RegionClearerHandler;
import com.tomkestookit.regionfiller.RegionFillerHandler;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public class KitCommand {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                CommandManager.literal("tomkestookit")
                    .requires(src -> true)
                    .then(CommandManager.literal("give")
                        .executes(KitCommand::giveKit))
                    .then(CommandManager.literal("regionclearer")
                        .requires(src -> true)
                        .then(CommandManager.literal("confirm")
                            .executes(ctx -> {
                                try {
                                    return RegionClearerHandler.confirm(ctx.getSource().getPlayerOrThrow());
                                } catch (Exception e) { return 0; }
                            }))
                        .then(CommandManager.literal("cancel")
                            .executes(ctx -> {
                                try {
                                    return RegionClearerHandler.cancel(ctx.getSource().getPlayerOrThrow());
                                } catch (Exception e) { return 0; }
                            })))
                    .then(CommandManager.literal("regionfiller")
                        .requires(src -> true)
                        .then(CommandManager.literal("confirm")
                            .executes(ctx -> {
                                try {
                                    return RegionFillerHandler.confirm(ctx.getSource().getPlayerOrThrow());
                                } catch (Exception e) { return 0; }
                            }))
                        .then(CommandManager.literal("cancel")
                            .executes(ctx -> {
                                try {
                                    return RegionFillerHandler.cancel(ctx.getSource().getPlayerOrThrow());
                                } catch (Exception e) { return 0; }
                            })))
                    .then(CommandManager.literal("invaccept")
                        .then(CommandManager.argument("id", StringArgumentType.word())
                            .executes(ctx -> {
                                try {
                                    return PlayerInventoryAccessRequest.accept(ctx.getSource().getPlayerOrThrow(),
                                        StringArgumentType.getString(ctx, "id"));
                                } catch (Exception e) { return 0; }
                            })))
                    .then(CommandManager.literal("invdeny")
                        .then(CommandManager.argument("id", StringArgumentType.word())
                            .executes(ctx -> {
                                try {
                                    return PlayerInventoryAccessRequest.deny(ctx.getSource().getPlayerOrThrow(),
                                        StringArgumentType.getString(ctx, "id"));
                                } catch (Exception e) { return 0; }
                            })))
                    .then(CommandManager.literal("autocrafter")
                        .requires(src -> src.hasPermissionLevel(2))
                        .then(CommandManager.literal("give")
                            .executes(ctx -> {
                                try {
                                    ctx.getSource().getPlayerOrThrow().getInventory().insertStack(AutoCrafterHandler.buildItem());
                                    return 1;
                                } catch (Exception e) { return 0; }
                            })))
                    .then(CommandManager.literal("logistics")
                        .requires(src -> src.hasPermissionLevel(2))
                        .then(CommandManager.literal("cleanup")
                            .executes(ctx -> {
                                int n = com.tomkestookit.logistics.LogisticsHandler
                                    .sweepOrphans(ctx.getSource().getServer());
                                final int cnt = n;
                                ctx.getSource().sendFeedback(() -> Text.literal(
                                    "已清理 " + cnt + " 个孤儿物流终端盔甲架。")
                                    .formatted(Formatting.YELLOW), false);
                                return cnt;
                            })))
                    .then(CommandManager.literal("admin")
                        .requires(src -> src.hasPermissionLevel(2))
                        .executes(ctx -> {
                            try {
                                com.tomkestookit.menu.AdminPanelMenu.open(ctx.getSource().getPlayerOrThrow());
                                return 1;
                            } catch (Exception e) { return 0; }
                        }))
                    // 精英怪 / Boss 命令已取消
                    // /tomkestookit cleanup vote <yes|no> <voteId>：任何玩家均可投票
                    // /tomkestookit cleanup force <all|dim>：仅 OP，跳过投票立即清理
                    .then(CommandManager.literal("cleanup")
                        .then(CommandManager.literal("vote")
                            .then(CommandManager.literal("yes")
                                .then(CommandManager.argument("voteId", StringArgumentType.word())
                                    .executes(ctx -> {
                                        try {
                                            return DroppedItemCleanup.voteCommand(
                                                ctx.getSource().getPlayerOrThrow(),
                                                StringArgumentType.getString(ctx, "voteId"), true);
                                        } catch (Exception e) { return 0; }
                                    })))
                            .then(CommandManager.literal("no")
                                .then(CommandManager.argument("voteId", StringArgumentType.word())
                                    .executes(ctx -> {
                                        try {
                                            return DroppedItemCleanup.voteCommand(
                                                ctx.getSource().getPlayerOrThrow(),
                                                StringArgumentType.getString(ctx, "voteId"), false);
                                        } catch (Exception e) { return 0; }
                                    }))))
                        .then(CommandManager.literal("force")
                            .requires(src -> src.hasPermissionLevel(2))
                            .executes(ctx -> DroppedItemCleanup.forceCleanup(ctx.getSource(), "all"))
                            .then(CommandManager.argument("target", StringArgumentType.string())
                                .executes(ctx -> DroppedItemCleanup.forceCleanup(
                                    ctx.getSource(), StringArgumentType.getString(ctx, "target"))))))
            ));
    }

    private static int giveKit(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity player;
        try {
            player = source.getPlayerOrThrow();
        } catch (Exception e) {
            return 0;
        }

        return tryClaim(player, false) ? 1 : 0;
    }

    /**
     * 命令 / 菜单共用的领取入口：两次领取间隔 24h（{@link KitTracker#COOLDOWN_MS}）。
     * @param actionBar 提示是否走动作栏（菜单内用 true，聊天命令用 false）
     * @return 是否真的发了物品
     */
    public static boolean tryClaim(ServerPlayerEntity player, boolean actionBar) {
        long left = KitTracker.remainingCooldown(player.getUuid());
        if (left > 0) {
            player.sendMessage(Text.literal("Kit 冷却中，" + KitTracker.formatRemaining(left) + "后可再次领取")
                .formatted(Formatting.RED), actionBar);
            return false;
        }

        giveKitItems(player);
        KitTracker.markClaimed(player.getUuid());
        player.sendMessage(Text.literal("已领取 Kit！24 小时后可再次领取").formatted(Formatting.GREEN), actionBar);
        return true;
    }

    /**
     * 只发放物品，不做已领取校验、不更新 tracker、不发消息。
     * 供菜单等其他入口在完成自己的校验后调用。
     *
     * 物品清单来自 {@link TomkesTookitConfig#kitItems()}；未定制时回落到 {@link #buildDefaultKit()}。
     */
    public static void giveKitItems(ServerPlayerEntity player) {
        for (ItemStack item : TomkesTookitConfig.kitItems()) {
            player.getInventory().insertStack(item);
        }
    }

    /**
     * 默认新手礼包内容。仅在 config 未定制过时生效；管理员通过面板改动后就走 config 里的清单。
     * 也是"重置默认"按钮的数据源。
     */
    public static List<ItemStack> buildDefaultKit() {
        // 反重力皮靴不再随礼包发放，改为 3×3 合成（见 AntigravBootsHandler.matches3x3）
        return List.of(
            new ItemStack(Items.LEATHER_BOOTS),
            new ItemStack(Items.LEATHER_HELMET),
            new ItemStack(Items.LEATHER_CHESTPLATE),
            new ItemStack(Items.LEATHER_LEGGINGS),
            new ItemStack(Items.STONE_PICKAXE),
            new ItemStack(Items.STONE_AXE),
            new ItemStack(Items.BREAD, 16),
            MenuClock.create()
        );
    }
}
