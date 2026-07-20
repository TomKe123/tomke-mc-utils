package com.starterkit.menu;

import com.mojang.authlib.GameProfile;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * 菜单按钮工厂。集中处理三件事：
 *   - 关闭 CustomName 的斜体（原版默认斜体，观感像"重命名"）
 *   - 生成物品栈 + display.Lore 结构（Text 序列化成 JSON 字符串）
 *   - 提供全局装饰件：玻璃板背景、翻页箭头、返回/关闭按钮
 *
 * 命名约定：文本可直接使用 §-color 前缀，本类不做转义。
 */
public final class MenuButtons {

    private MenuButtons() {}

    /** 灰色玻璃板占位，空名字（客户端看不到 tooltip）。*/
    public static ItemStack filler() {
        ItemStack stack = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        stack.setCustomName(Text.literal(" "));
        return stack;
    }

    /** 顶部/底部装饰玻璃板（亮色，跟 filler 区分）。*/
    public static ItemStack header() {
        ItemStack stack = new ItemStack(Items.LIGHT_BLUE_STAINED_GLASS_PANE);
        stack.setCustomName(Text.literal(" "));
        return stack;
    }

    /** 用玻璃板铺满整个 inventory，作为菜单背景。*/
    public static void fillBackground(Inventory inv) {
        ItemStack filler = filler();
        for (int i = 0; i < inv.size(); i++) {
            if (inv.getStack(i).isEmpty()) inv.setStack(i, filler.copy());
        }
    }

    public static ItemStack closeButton() {
        return button(Items.BARRIER, "§c§l✕ 关闭",
            List.of("§7关闭菜单"));
    }

    public static ItemStack backButton() {
        return button(Items.ARROW, "§e◀ 返回主菜单",
            List.of("§7点击回到主菜单"));
    }

    /**
     * 翻页按钮。启用状态用发光箭头（附魔光效），禁用状态用普通箭头 + 灰色文字。
     * @param next  true = 下一页，false = 上一页
     * @param page  当前页（0-indexed）
     * @param total 总页数
     * @param enabled 是否可点
     */
    public static ItemStack pageButton(boolean next, int page, int total, boolean enabled) {
        String arrow = next ? "▶" : "◀";
        String label = next ? "下一页" : "上一页";
        Formatting c = enabled ? Formatting.GREEN : Formatting.DARK_GRAY;
        List<String> lore = new ArrayList<>();
        lore.add("§7第 " + (page + 1) + " / " + Math.max(1, total) + " 页");
        if (!enabled) lore.add("§8已到边界");
        ItemStack stack = button(
            enabled ? Items.SPECTRAL_ARROW : Items.ARROW,
            (c.toString()) + (next ? label + " " + arrow : arrow + " " + label),
            lore
        );
        return stack;
    }

    /** 通用按钮：物品 + 无斜体名字 + lore。*/
    public static ItemStack button(Item item, String name, List<String> loreLines) {
        ItemStack stack = new ItemStack(item);
        applyName(stack, name);
        applyLore(stack, loreLines);
        return stack;
    }

    /** 玩家头颅按钮（会异步拉取皮肤）。*/
    public static ItemStack skull(GameProfile profile, String name, List<String> loreLines) {
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.put("SkullOwner", NbtHelper.writeGameProfile(new NbtCompound(), profile));
        applyName(stack, name);
        applyLore(stack, loreLines);
        return stack;
    }

    /** 让物品带附魔光效（无实际附魔）：塞一个空的 Enchantments 列表 + 隐藏 Flags。*/
    public static ItemStack withGlow(ItemStack stack) {
        NbtCompound nbt = stack.getOrCreateNbt();
        NbtList enchList = new NbtList();
        NbtCompound fakeEnch = new NbtCompound();
        // 用一个存在但对该物品无实际效果的附魔 id，避免部分客户端渲染问题
        fakeEnch.putString("id", Registries.ENCHANTMENT.getId(Enchantments.UNBREAKING).toString());
        fakeEnch.putInt("lvl", 1);
        enchList.add(fakeEnch);
        nbt.put("Enchantments", enchList);
        nbt.putInt("HideFlags", 1); // hide enchantments tooltip
        return stack;
    }

    // ------------------------------------------------------------------ helpers

    private static void applyName(ItemStack stack, String name) {
        stack.setCustomName(Text.literal(name).styled(s -> s.withItalic(false)));
    }

    private static void applyLore(ItemStack stack, List<String> loreLines) {
        if (loreLines == null || loreLines.isEmpty()) return;
        NbtCompound nbt = stack.getOrCreateNbt();
        NbtCompound display = nbt.getCompound("display");
        NbtList lore = new NbtList();
        for (String line : loreLines) {
            lore.add(NbtString.of(Text.Serializer.toJson(
                Text.literal(line).styled(s -> s.withItalic(false)))));
        }
        display.put("Lore", lore);
        nbt.put("display", display);
    }
}
