package com.tomkestookit.menu;

import com.tomkestookit.CarpetBotUtil;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Carpet 假人详细控制菜单 9x6：
 *
 *   row 0:  [信息 0] . . . [标题 4] . . . [返回 8]
 *   row 1:  持续动作（切换）：攻击 / 使用 / 跳跃 / 潜行 / 前进 / 后退
 *   row 2:  瞬时动作（单次）：攻击一次 / 使用一次 / 跳一下 / 丢弃主手 / 全部丢弃
 *   row 3:  姿态 / 视角：站立 / 睡觉 / 骑乘 / 面朝我 / 面朝上 / 面朝下
 *   row 4:  修（kill）/ 转生（重登）
 *   row 5:  [返回详情]
 *
 * 所有 Carpet 命令通过 CarpetBotUtil.runAsServer 以 permission level 2 执行；
 * 玩家自身不需要 OP。
 */
public final class BotControlMenu {

    private BotControlMenu() {}

    public static void open(ServerPlayerEntity viewer, UUID targetUuid) {
        ServerPlayerEntity target = viewer.getServer().getPlayerManager().getPlayer(targetUuid);
        if (target == null || !CarpetBotUtil.isFakePlayer(target)) {
            viewer.sendMessage(Text.literal("§c[控制] 目标不是假人或已下线。"), true);
            PlayerDetailMenu.open(viewer, targetUuid);
            return;
        }
        String name = target.getGameProfile().getName();

        Inventory inv = MenuScreenHandler.buildInventory(6);
        Map<Integer, Consumer<ServerPlayerEntity>> actions = new HashMap<>();

        // 顶部：信息 + 标题 + 返回
        inv.setStack(0, MenuButtons.skull(target.getGameProfile(),
            "§c§l[假人] " + name,
            List.of("§7位置 §f" + fmt(target.getX(), target.getY(), target.getZ()),
                "§7生命 §c" + (int) Math.ceil(target.getHealth()),
                "§7选中槽位 §f" + (target.getInventory().selectedSlot + 1))));
        inv.setStack(4, MenuButtons.button(Items.COMMAND_BLOCK, "§b§l§n详细控制",
            List.of("§7所有按钮以 OP 身份运行 Carpet 命令",
                "§8玩家自身不需要 OP")));
        inv.setStack(8, MenuButtons.backButton());
        actions.put(8, p -> PlayerDetailMenu.open(p, targetUuid));

        // Row 1（槽 9..17）：持续动作切换
        setActionBtn(inv, actions, targetUuid, 9, Items.IRON_SWORD, "§c持续攻击", "§7持续挥砍主手武器",
            p -> com.tomkestookit.CarpetBotAction.continuous(targetOrNull(p, targetUuid), com.tomkestookit.CarpetBotAction.Action.ATTACK));
        setBtn(inv, actions, 10, Items.STONE_PICKAXE, "§e持续使用", "§7持续按住主手右键",
            "player " + name + " use continuous");
        setBtn(inv, actions, 11, Items.RABBIT_FOOT, "§a持续跳跃", "§7持续跳跃",
            "player " + name + " jump continuous");
        setBtn(inv, actions, 12, Items.LEATHER_BOOTS, "§9潜行", "§7切换潜行状态",
            "player " + name + " sneak");
        setBtn(inv, actions, 13, Items.NETHERITE_BOOTS, "§9持续冲刺", "§7持续冲刺",
            "player " + name + " sprint continuous");
        setBtn(inv, actions, 14, Items.LEAD, "§d持续掉落", "§7持续按 sneak+dropitem",
            "player " + name + " drop continuous");

        // Row 2（槽 18..26）：瞬时动作
        setActionBtn(inv, actions, targetUuid, 18, Items.WOODEN_SWORD, "§7攻击一次", "§7挥主手一次",
            p -> com.tomkestookit.CarpetBotAction.once(targetOrNull(p, targetUuid), com.tomkestookit.CarpetBotAction.Action.ATTACK));
        setBtn(inv, actions, 19, Items.WOODEN_PICKAXE, "§7使用一次", "§7右键一次主手",
            "player " + name + " use once");
        setBtn(inv, actions, 20, Items.FEATHER, "§7跳一下", "§7跳一次",
            "player " + name + " jump once");
        setBtn(inv, actions, 21, Items.OAK_PLANKS, "§7丢主手", "§7假人丢弃当前主手 stack",
            "player " + name + " drop");
        setBtn(inv, actions, 22, Items.TNT, "§4§l丢弃全部背包", "§7假人清空背包（掉落物）",
            "player " + name + " dropAll");

        // Row 3（槽 27..35）：姿态 / 移动
        setBtn(inv, actions, 27, Items.WHITE_BED, "§a让 TA 睡觉", "§7走向最近的床并睡下",
            "player " + name + " sleep");
        setBtn(inv, actions, 28, Items.WATER_BUCKET, "§a让 TA 醒来", "§7从床上站起来",
            "player " + name + " wake");
        setBtn(inv, actions, 29, Items.COMPASS, "§b面向我", "§7假人视角对准发起玩家",
            "player " + name + " look at " + viewer.getGameProfile().getName());
        setBtn(inv, actions, 30, Items.SUNFLOWER, "§b面朝北", "§7视角朝北 (yaw=180)",
            "player " + name + " turn 180 0");
        setBtn(inv, actions, 31, Items.ARROW, "§b面朝上", "§7视角朝上 (pitch=-90)",
            "player " + name + " turn 0 -90");
        setBtn(inv, actions, 32, Items.SPECTRAL_ARROW, "§b面朝下", "§7视角朝下 (pitch=90)",
            "player " + name + " turn 0 90");
        setBtn(inv, actions, 33, Items.MINECART, "§e停止全部动作", "§7让假人停下所有持续动作",
            "player " + name + " stop");

        // Row 4（槽 36..44）：状态开关 / 移除
        // 无击退开关
        {
            boolean nk = com.tomkestookit.BotAnchorTracker.noKnockback(targetUuid);
            inv.setStack(36, MenuButtons.button(nk ? Items.NETHERITE_CHESTPLATE : Items.LEATHER_CHESTPLATE,
                "§l" + (nk ? "§a无击退：开" : "§7无击退：关"),
                List.of("§7开启后假人不再被击退",
                    "§8伤害、爆炸、拉扯都不会推动 TA",
                    "",
                    "§8点击切换")));
            actions.put(36, p -> {
                boolean now = !com.tomkestookit.BotAnchorTracker.noKnockback(targetUuid);
                com.tomkestookit.BotAnchorTracker.setNoKnockback(targetUuid, now);
                p.sendMessage(Text.literal("§7[控制] " + name + " 无击退：" + (now ? "§a开" : "§c关")), true);
                open(p, targetUuid);
            });
        }

        // 锁定位置开关
        {
            boolean anchored = com.tomkestookit.BotAnchorTracker.isAnchored(targetUuid);
            String label = anchored ? "§a锁定位置：开" : "§7锁定位置：关";
            String lore1;
            if (anchored) {
                var a = com.tomkestookit.BotAnchorTracker.anchor(targetUuid);
                lore1 = String.format("§7锁定点 §f%.1f, %.1f, %.1f", a.x(), a.y(), a.z());
            } else {
                lore1 = "§7当前 §c未锁定";
            }
            inv.setStack(37, MenuButtons.button(anchored ? Items.ENDER_EYE : Items.ENDER_PEARL,
                "§l" + label,
                List.of(lore1,
                    "§7点击：以假人§f当前位置§7锁定",
                    "§7再次点击：解除锁定",
                    "",
                    "§8锁定期间只锁坐标，假人仍可自由转向 / 攻击")));
            actions.put(37, p -> {
                if (com.tomkestookit.BotAnchorTracker.isAnchored(targetUuid)) {
                    com.tomkestookit.BotAnchorTracker.clearAnchor(targetUuid);
                    p.sendMessage(Text.literal("§7[控制] " + name + " 已解除位置锁定"), true);
                } else {
                    ServerPlayerEntity t = p.getServer().getPlayerManager().getPlayer(targetUuid);
                    if (t == null) {
                        p.sendMessage(Text.literal("§c[控制] 目标已下线"), true);
                        return;
                    }
                    com.tomkestookit.BotAnchorTracker.setAnchor(targetUuid,
                        new com.tomkestookit.BotAnchorTracker.Anchor(
                            t.getWorld().getRegistryKey().getValue().toString(),
                            t.getX(), t.getY(), t.getZ(),
                            t.getYaw(), t.getPitch()));
                    p.sendMessage(Text.literal("§7[控制] " + name + String.format(
                        " 已锁定在 §f%.1f, %.1f, %.1f",
                        t.getX(), t.getY(), t.getZ())), true);
                }
                open(p, targetUuid);
            });
        }

        // 索敌模式
        {
            var mode = com.tomkestookit.BotAnchorTracker.targetMode(targetUuid);
            net.minecraft.item.Item icon = switch (mode) {
                case ARMOR_STAND -> Items.ARMOR_STAND;
                case PASSIVE -> Items.WHEAT;
                case HOSTILE -> Items.ROTTEN_FLESH;
                default -> Items.SPYGLASS;
            };
            inv.setStack(38, MenuButtons.button(icon,
                "§l§6索敌：§f" + mode.zh,
                List.of("§7自动寻找最近目标并面向 TA",
                    "§8不会自动攻击；攻击方式由 once / continuous / interval 控制",
                    "§8不会索敌玩家，也不会伤害真实玩家",
                    "",
                    "§7循环：关闭 → 盔甲架 → 友好生物 → 敌对生物")));
            actions.put(38, p -> {
                var next = com.tomkestookit.BotAnchorTracker.cycleTargetMode(targetUuid);
                p.sendMessage(Text.literal("§7[控制] " + name + " 索敌模式：§f" + next.zh), true);
                open(p, targetUuid);
            });
        }

        // 间隔攻击：打开告示牌输入 tick 间隔
        {
            int interval = com.tomkestookit.BotAnchorTracker.attackInterval(targetUuid);
            inv.setStack(39, MenuButtons.button(interval > 0 ? Items.CLOCK : Items.TARGET,
                "§l§e间隔攻击：§f" + (interval > 0 ? interval + " tick" : "关"),
                List.of("§7按固定 tick 间隔执行一次 attack once",
                    "§7输入 0 可关闭",
                    "§8索敌只负责转向，攻击节奏由这里控制")));
            actions.put(39, p -> com.tomkestookit.SignEditPrompt.open(p,
                    new String[]{"攻击间隔tick", interval > 0 ? String.valueOf(interval) : "20", "0=关闭", ""},
                    lines -> p.getServer().execute(() -> {
                        String s = lines.length > 1 ? lines[1] : (lines.length > 0 ? lines[0] : "");
                        int ticks;
                        try {
                            ticks = Integer.parseInt(s.strip());
                        } catch (Exception e) {
                            p.sendMessage(Text.literal("§c[控制] tick 数字无效"), true);
                            return;
                        }
                        ticks = Math.max(0, Math.min(72000, ticks));
                        ServerPlayerEntity t = p.getServer().getPlayerManager().getPlayer(targetUuid);
                        boolean ok;
                        if (ticks == 0) {
                            com.tomkestookit.BotAnchorTracker.setAttackInterval(targetUuid, 0);
                            ok = com.tomkestookit.CarpetBotAction.stop(t);
                        } else {
                            com.tomkestookit.BotAnchorTracker.setAttackInterval(targetUuid, ticks);
                            ok = com.tomkestookit.CarpetBotAction.interval(t, com.tomkestookit.CarpetBotAction.Action.ATTACK, ticks);
                        }
                        if (!ok) {
                            p.sendMessage(Text.literal("§c[控制] Carpet interval 动作执行失败"), true);
                            return;
                        }
                        p.sendMessage(Text.literal("§7[控制] " + name + " 间隔攻击：§f" + (ticks == 0 ? "关" : ticks + " tick")), true);
                        open(p, targetUuid);
                    })
                ));
        }

        setBtn(inv, actions, 40, Items.BARRIER, "§4§l§n关闭假人",
            "§c让假人下线（保留在原地作为实体？取决于 Carpet 版本）",
            "player " + name + " kill");

        MenuButtons.fillBackground(inv);

        BotMenuTracker.watch(viewer.getUuid(), targetUuid);
        viewer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, playerInv, ignored) -> {
                MenuScreenHandler h = new MenuScreenHandler(syncId, playerInv, inv, actions);
                h.setOnClose(sp -> BotMenuTracker.unwatch(sp.getUuid()));
                return h;
            },
            Text.literal("§c§l§n控制假人 - " + name)
        ));
    }

    private static void setActionBtn(Inventory inv, Map<Integer, Consumer<ServerPlayerEntity>> actions,
                                     UUID targetUuid, int slot, net.minecraft.item.Item icon,
                                     String name, String description,
                                     java.util.function.Function<ServerPlayerEntity, Boolean> action) {
        inv.setStack(slot, MenuButtons.button(icon, "§l" + name,
            List.of(description, "", "§8直接调用 Carpet action pack")));
        actions.put(slot, p -> {
            boolean ok = false;
            try { ok = action.apply(p); } catch (Throwable ignored) {}
            if (!ok) p.sendMessage(Text.literal("§c[控制] 动作执行失败：" + name), true);
        });
    }

    private static ServerPlayerEntity targetOrNull(ServerPlayerEntity viewer, UUID targetUuid) {
        return viewer.getServer().getPlayerManager().getPlayer(targetUuid);
    }

    private static void setBtn(Inventory inv, Map<Integer, Consumer<ServerPlayerEntity>> actions,
                               int slot, net.minecraft.item.Item icon,
                               String name, String description, String command) {
        inv.setStack(slot, MenuButtons.button(icon, "§l" + name,
            List.of(description,
                "",
                "§8执行 §7/" + command)));
        actions.put(slot, p -> {
            boolean ok = CarpetBotUtil.runAsServer(p, command);
            if (!ok) {
                p.sendMessage(Text.literal("§c[控制] 命令执行失败：/" + command)
                    .formatted(Formatting.RED), true);
            }
        });
    }

    private static String fmt(double x, double y, double z) {
        return String.format("%.0f, %.0f, %.0f", x, y, z);
    }
}
