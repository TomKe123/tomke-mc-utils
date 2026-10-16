package com.tomkestookit.menu;

import com.tomkestookit.CarpetBotUtil;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 玩家详情页 9x6 布局：
 *
 *   row 0:  [信息 0] . [传送 2] . [槽位 5] . [控制 6] . [返回 8]
 *   row 1:  [装备行  45] [46] [47] [48] . [副手 50] . . [返回]
 *   row 2-4 (9..44)：假人 / 玩家的 36 格背包（9 快捷 + 27 主）
 *     9..17  hotbar   (pi 0..8)
 *     18..44 main     (pi 9..35)
 *   row 5 (45..53)：装备行 + 副手
 *     45 头盔   46 胸甲   47 护腿   48 靴子
 *     49 空
 *     50 副手
 *     51..53 空
 *
 * 想再要细节控制（走路 / 眼看 / 攻击目标 / 修杀）→ 顶部槽 6 打开 BotControlMenu。
 */
public final class PlayerDetailMenu {

    private PlayerDetailMenu() {}

    public static void open(ServerPlayerEntity viewer, UUID targetUuid) {
        ServerPlayerEntity target = viewer.getServer().getPlayerManager().getPlayer(targetUuid);
        if (target == null) {
            viewer.sendMessage(Text.literal("目标玩家已下线。").formatted(Formatting.RED), true);
            PlayerListMenu.open(viewer);
            return;
        }

        if (!CarpetBotUtil.isFakePlayer(target)) {
            openPlayerRequestPage(viewer, target);
            return;
        }
        // 以下：target 一定是假人
        Inventory inv = new BotInventoryWrapper(target);

        Map<Integer, Consumer<ServerPlayerEntity>> actions = new HashMap<>();
        Map<Integer, Consumer<ServerPlayerEntity>> rightActions = new HashMap<>();
        String targetName = target.getGameProfile().getName();

        // 顶部
        inv.setStack(0, infoCard(target, true));

        inv.setStack(2, MenuButtons.button(Items.ENDER_PEARL, "§a§l传送到该玩家",
            List.of("§7点击传送")));
        actions.put(2, p -> {
            ServerPlayerEntity t = p.getServer().getPlayerManager().getPlayer(targetName);
            if (t == null) return;
            com.tomkestookit.PlayerTeleport.teleport(p,
                (net.minecraft.server.world.ServerWorld) t.getWorld(),
                t.getX(), t.getY(), t.getZ(), t.getYaw(), t.getPitch());
            com.tomkestookit.SpawnProtection.grant(p, "传送到 " + targetName);
            p.closeHandledScreen();
        });

        // 主手槽位选择器
        int selected = target.getInventory().selectedSlot;
        inv.setStack(5, hotbarSelectorButton(selected + 1));
        actions.put(5, p -> {
            ServerPlayerEntity t = p.getServer().getPlayerManager().getPlayer(targetName);
            if (t == null) return;
            setSelectedSlot(t, (t.getInventory().selectedSlot + 1) % 9);
            open(p, targetUuid);
        });
        rightActions.put(5, p -> {
            ServerPlayerEntity t = p.getServer().getPlayerManager().getPlayer(targetName);
            if (t == null) return;
            setSelectedSlot(t, (t.getInventory().selectedSlot + 8) % 9);
            open(p, targetUuid);
        });

        // 详细控制按钮
        inv.setStack(6, MenuButtons.button(Items.REDSTONE, "§c§l详细控制",
            List.of("§7点击打开假人动作 / 姿态 / 移除 面板")));
        actions.put(6, p -> BotControlMenu.open(p, targetUuid));


        inv.setStack(8, MenuButtons.backButton());
        actions.put(8, PlayerListMenu::open);

        // 空隙填灰色玻璃板：mapToInventorySlot(slot) 为 null 且既无 action 也无 rightAction
        // 的位置视觉占位；LockedSlot 保证客户端无法拖走。
        for (int i = 0; i < 54; i++) {
            if (mapToInventorySlot(i) != null) continue;
            if (actions.containsKey(i)) continue;
            if (rightActions.containsKey(i)) continue;
            if (!inv.getStack(i).isEmpty()) continue;
            inv.setStack(i, MenuButtons.filler());
        }

        // 打开容器（一定是假人；普通玩家在前面已 return 到 openPlayerRequestPage）
        BotMenuTracker.watch(viewer.getUuid(), targetUuid);
        viewer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, playerInv, ignored) -> new BotInventoryScreenHandler(syncId, playerInv, inv,
                actions, rightActions, targetName, targetUuid),
            Text.literal("§c§l§n[假人] " + targetName)
        ));
    }

    // ------------------------------------------------------------------ 普通玩家授权页

    private static void openPlayerRequestPage(ServerPlayerEntity viewer, ServerPlayerEntity target) {
        Inventory inv = MenuScreenHandler.buildInventory(6);
        Map<Integer, Consumer<ServerPlayerEntity>> actions = new HashMap<>();
        String targetName = target.getGameProfile().getName();
        java.util.UUID targetUuid = target.getUuid();

        inv.setStack(4, infoCard(target, false));
        inv.setStack(22, MenuButtons.button(Items.CHEST, "§e§l申请查看/编辑背包",
            List.of("§7普通玩家背包需要对方同意后才能打开",
                "§7同意后可读取并写入真实背包",
                "§c未授权时不会打开可交互快照，避免吞物品",
                "",
                "§a点击发送请求")));
        actions.put(22, p -> {
            ServerPlayerEntity t = p.getServer().getPlayerManager().getPlayer(targetUuid);
            if (t == null) {
                p.sendMessage(Text.literal("目标玩家已下线。").formatted(Formatting.RED), true);
                return;
            }
            com.tomkestookit.PlayerInventoryAccessRequest.request(p, t);
        });
        inv.setStack(31, MenuButtons.button(Items.ENDER_PEARL, "§a§l传送到该玩家",
            List.of("§7点击传送")));
        actions.put(31, p -> {
            ServerPlayerEntity t = p.getServer().getPlayerManager().getPlayer(targetUuid);
            if (t == null) return;
            com.tomkestookit.PlayerTeleport.teleport(p,
                (net.minecraft.server.world.ServerWorld) t.getWorld(),
                t.getX(), t.getY(), t.getZ(), t.getYaw(), t.getPitch());
            com.tomkestookit.SpawnProtection.grant(p, "传送到 " + t.getGameProfile().getName());
            p.closeHandledScreen();
        });
        inv.setStack(49, MenuButtons.backButton());
        actions.put(49, PlayerListMenu::open);
        MenuButtons.fillBackground(inv);
        viewer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, playerInv, ignored) -> new MenuScreenHandler(syncId, playerInv, inv, actions),
            Text.literal("§b§l§n[玩家] " + targetName)
        ));
    }

    public static void openApprovedInventory(ServerPlayerEntity viewer, UUID targetUuid) {
        ServerPlayerEntity target = viewer.getServer().getPlayerManager().getPlayer(targetUuid);
        if (target == null || CarpetBotUtil.isFakePlayer(target)) {
            viewer.sendMessage(Text.literal("目标玩家已下线或不是普通玩家。").formatted(Formatting.RED), true);
            return;
        }
        Inventory inv = new PlayerInventoryWrapper(target);
        Map<Integer, Consumer<ServerPlayerEntity>> actions = new HashMap<>();
        Map<Integer, Consumer<ServerPlayerEntity>> rightActions = new HashMap<>();
        String targetName = target.getGameProfile().getName();
        inv.setStack(0, infoCard(target, false));


        inv.setStack(8, MenuButtons.backButton());
        actions.put(8, PlayerListMenu::open);
        for (int i = 0; i < 54; i++) {
            if (mapToInventorySlot(i) != null) continue;
            if (actions.containsKey(i)) continue;
            if (!inv.getStack(i).isEmpty()) continue;
            inv.setStack(i, MenuButtons.filler());
        }
        viewer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, playerInv, ignored) -> new BotInventoryScreenHandler(syncId, playerInv, inv,
                actions, rightActions, targetName, targetUuid),
            Text.literal("§b§l§n[已授权] " + targetName)
        ));
    }

    // ------------------------------------------------------------------ 快照 / wrapper

    private static Inventory snapshotOf(ServerPlayerEntity target) {
        Inventory inv = new SimpleInventory(54);
        PlayerInventory pi = target.getInventory();
        for (int i = 0; i < 9; i++) inv.setStack(9 + i, pi.getStack(i).copy());
        for (int i = 9; i < 36; i++) inv.setStack(18 + (i - 9), pi.getStack(i).copy());
        inv.setStack(45, pi.getStack(39).copy());
        inv.setStack(46, pi.getStack(38).copy());
        inv.setStack(47, pi.getStack(37).copy());
        inv.setStack(48, pi.getStack(36).copy());
        inv.setStack(50, pi.getStack(40).copy());
        return inv;
    }

    private static final class PlayerInventoryWrapper implements Inventory {
        private final ServerPlayerEntity target;
        private final ItemStack[] decorations = new ItemStack[54];

        PlayerInventoryWrapper(ServerPlayerEntity target) { this.target = target; }

        @Override public int size() { return 54; }
        @Override public boolean isEmpty() { return false; }

        @Override public ItemStack getStack(int slot) {
            Integer piSlot = mapToInventorySlot(slot);
            if (piSlot != null) return target.getInventory().getStack(piSlot);
            return decorations[slot] == null ? ItemStack.EMPTY : decorations[slot];
        }
        @Override public ItemStack removeStack(int slot, int amount) {
            Integer piSlot = mapToInventorySlot(slot);
            if (piSlot != null) return target.getInventory().removeStack(piSlot, amount);
            return ItemStack.EMPTY;
        }
        @Override public ItemStack removeStack(int slot) {
            Integer piSlot = mapToInventorySlot(slot);
            if (piSlot != null) return target.getInventory().removeStack(piSlot);
            return ItemStack.EMPTY;
        }
        @Override public void setStack(int slot, ItemStack stack) {
            Integer piSlot = mapToInventorySlot(slot);
            if (piSlot != null) {
                target.getInventory().setStack(piSlot, stack);
                target.getInventory().markDirty();
            } else {
                decorations[slot] = stack;
            }
        }
        @Override public void markDirty() { target.getInventory().markDirty(); }
        @Override public boolean canPlayerUse(net.minecraft.entity.player.PlayerEntity player) {
            return target.isAlive() && target.networkHandler != null;
        }
        @Override public void clear() {}
    }

    private static final class BotInventoryWrapper implements Inventory {
        private final ServerPlayerEntity bot;
        private final ItemStack[] decorations = new ItemStack[54];

        BotInventoryWrapper(ServerPlayerEntity bot) { this.bot = bot; }

        @Override public int size() { return 54; }
        @Override public boolean isEmpty() { return false; }

        @Override
        public ItemStack getStack(int slot) {
            Integer piSlot = mapToInventorySlot(slot);
            if (piSlot != null) return bot.getInventory().getStack(piSlot);
            return decorations[slot] == null ? ItemStack.EMPTY : decorations[slot];
        }

        @Override
        public ItemStack removeStack(int slot, int amount) {
            Integer piSlot = mapToInventorySlot(slot);
            if (piSlot != null) return bot.getInventory().removeStack(piSlot, amount);
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeStack(int slot) {
            Integer piSlot = mapToInventorySlot(slot);
            if (piSlot != null) return bot.getInventory().removeStack(piSlot);
            ItemStack s = decorations[slot];
            decorations[slot] = null;
            return s == null ? ItemStack.EMPTY : s;
        }

        @Override
        public void setStack(int slot, ItemStack stack) {
            Integer piSlot = mapToInventorySlot(slot);
            if (piSlot != null) {
                bot.getInventory().setStack(piSlot, stack);
                bot.getInventory().markDirty();
            } else {
                decorations[slot] = stack;
            }
        }

        @Override public void markDirty() { bot.getInventory().markDirty(); }
        @Override public boolean canPlayerUse(net.minecraft.entity.player.PlayerEntity player) { return true; }
        @Override public void clear() { /* 不清 */ }
    }

    static Integer mapToInventorySlot(int menuSlot) {
        if (menuSlot >= 9 && menuSlot <= 17) return menuSlot - 9;
        if (menuSlot >= 18 && menuSlot <= 44) return menuSlot - 18 + 9;
        if (menuSlot == 45) return 39;
        if (menuSlot == 46) return 38;
        if (menuSlot == 47) return 37;
        if (menuSlot == 48) return 36;
        if (menuSlot == 50) return 40;
        return null;
    }

    // ------------------------------------------------------------------ 按钮

    private static ItemStack hotbarSelectorButton(int oneBased) {
        int clamped = Math.max(1, Math.min(9, oneBased));
        ItemStack stack = new ItemStack(Items.PAPER, clamped);
        stack.setCustomName(Text.literal("§e§l假人主手槽位 §f" + clamped + "§7/9")
            .styled(s -> s.withItalic(false)));
        net.minecraft.nbt.NbtCompound nbt = stack.getOrCreateNbt();
        net.minecraft.nbt.NbtCompound display = nbt.getCompound("display");
        net.minecraft.nbt.NbtList lore = new net.minecraft.nbt.NbtList();
        for (String line : List.of(
            "§a左键 §7→ 下一个槽位",
            "§b右键 §7→ 上一个槽位",
            "§8数量下标显示当前选中槽 (1..9)"
        )) {
            lore.add(net.minecraft.nbt.NbtString.of(Text.Serializer.toJson(
                Text.literal(line).styled(s -> s.withItalic(false)))));
        }
        display.put("Lore", lore);
        nbt.put("display", display);
        return stack;
    }

    static void setSelectedSlot(ServerPlayerEntity target, int slot) {
        if (slot < 0 || slot > 8) return;
        target.getInventory().selectedSlot = slot;
        target.getInventory().markDirty();
    }

    private static ItemStack infoCard(ServerPlayerEntity target, boolean bot) {
        int food = target.getHungerManager().getFoodLevel();
        float saturation = target.getHungerManager().getSaturationLevel();
        int health = (int) Math.ceil(target.getHealth());
        int maxHealth = (int) Math.ceil(target.getMaxHealth());
        int air = target.getAir();
        int xp = target.experienceLevel;
        String dim = MenuUi.dimShort(target.getWorld().getRegistryKey().getValue().toString());

        List<String> lore = new ArrayList<>();
        lore.add((bot ? "§c[假人] §f" : "§7玩家 §f") + target.getGameProfile().getName());
        lore.add("§7生命 §c" + health + "§7/§c" + maxHealth);
        lore.add(String.format("§7饱食 §6%d §7饱和 §f%.1f", food, saturation));
        lore.add("§7空气 §b" + air);
        lore.add("§7等级 §e" + xp);
        lore.add(String.format("§7位置 §f%.0f, %.0f, %.0f §7@ §f%s",
            target.getX(), target.getY(), target.getZ(), dim));
        return MenuButtons.skull(target.getGameProfile(),
            (bot ? "§c§l[假人] " : "§e§l") + target.getGameProfile().getName(), lore);
    }
}
