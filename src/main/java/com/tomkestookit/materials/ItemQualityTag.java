package com.tomkestookit.materials;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.text.Text;

/**
 * 统一的特殊物品尾部标注：品质 + 获取途径。
 *
 * 所有本包特殊物品在 buildStack 结束前应追加两行：
 *   §8━━━━━━━━━━━
 *   §f品质 §e★★☆☆☆ §7稀有
 *   §f获取 §7合成 §8· 铁砧 §8· 掉落等
 *
 * 品质等级：
 *   COMMON     §f普通    ★☆☆☆☆
 *   UNCOMMON   §a优良    ★★☆☆☆
 *   RARE       §9稀有    ★★★☆☆
 *   EPIC       §5史诗    ★★★★☆
 *   LEGENDARY  §6§l传奇  ★★★★★
 */
public final class ItemQualityTag {

    public enum Quality {
        COMMON("§f普通", "§7★☆☆☆☆"),
        UNCOMMON("§a优良", "§a★★☆☆☆"),
        RARE("§9稀有", "§9★★★☆☆"),
        EPIC("§5史诗", "§5★★★★☆"),
        LEGENDARY("§6§l传奇", "§6★★★★★");

        public final String label;
        public final String stars;
        Quality(String label, String stars) {
            this.label = label;
            this.stars = stars;
        }
    }

    private ItemQualityTag() {}

    /**
     * 追加"品质 + 获取途径"两行到 ItemStack 的 lore 末尾。
     * 会先追加一条分割线 §8━━━━━━━━━━━ 与前面 lore 分开。
     *
     * @param stack 目标物品
     * @param q     品质枚举
     * @param source 获取途径描述（例如 "3×3 合成"、"精英怪掉落"、"Boss 掉落"、"铁砧强化"）
     */
    public static void append(ItemStack stack, Quality q, String source) {
        NbtCompound nbt = stack.getOrCreateNbt();
        NbtCompound display = nbt.getCompound("display");
        NbtList lore = display.contains("Lore") ? display.getList("Lore", 8) : new NbtList();

        addLine(lore, "§8━━━━━━━━━━━━━━");
        addLine(lore, "§f品质 §8· " + q.label + " " + q.stars);
        addLine(lore, "§f获取 §8· §7" + source);

        display.put("Lore", lore);
        nbt.put("display", display);
    }

    private static void addLine(NbtList lore, String text) {
        lore.add(NbtString.of(Text.Serializer.toJson(
            Text.literal(text).styled(s -> s.withItalic(false)))));
    }
}
