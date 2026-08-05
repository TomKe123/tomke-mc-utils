package com.tomkestookit.infinitebucket;

import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

/**
 * 无限水桶 / 无限岩浆桶：可无限倒液体而不消耗。
 *
 * 合成：3铁块+炼药锅+水桶/岩浆桶 摆成十字形（中心炼药锅，上下左右铁块+桶）。
 * 标记：NBT `TomkesTookitInfiniteBucket` + 附魔光效 + lore。
 * 使用：mixin 拦截 BucketItem.use 返回后将空桶换回满桶。
 */
public final class InfiniteBucket {

    public static final String NBT_KEY = "TomkesTookitInfiniteBucket";

    private InfiniteBucket() {}

    public enum BucketType {
        WATER(Items.WATER_BUCKET, "水"),
        LAVA(Items.LAVA_BUCKET, "岩浆");

        public final Item item;
        public final String zh;

        BucketType(Item item, String zh) {
            this.item = item;
            this.zh = zh;
        }
    }

    /** 生成无限桶物品（附魔光效 + lore）。*/
    public static ItemStack build(BucketType type) {
        ItemStack stack = new ItemStack(type.item);
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putInt(NBT_KEY, type.ordinal());

        // 附魔光效（假附魔）
        NbtList enchList = new NbtList();
        NbtCompound fakeEnch = new NbtCompound();
        fakeEnch.putString("id", Registries.ENCHANTMENT.getId(Enchantments.UNBREAKING).toString());
        fakeEnch.putInt("lvl", 1);
        enchList.add(fakeEnch);
        nbt.put("Enchantments", enchList);
        nbt.putInt("HideFlags", 1); // 隐藏附魔 tooltip

        // 自定义名称
        stack.setCustomName(Text.literal("§b§l无限" + type.zh + "桶").styled(s -> s.withItalic(false)));

        // lore
        NbtCompound display = nbt.getCompound("display");
        NbtList lore = new NbtList();
        lore.add(NbtString.of(Text.Serializer.toJson(
            Text.literal("§8「无限倒" + type.zh + "而不消耗」").styled(s -> s.withItalic(false)))));
        lore.add(NbtString.of(Text.Serializer.toJson(
            Text.literal("§7使用后自动补满").styled(s -> s.withItalic(false)))));
        display.put("Lore", lore);
        nbt.put("display", display);

        return stack;
    }

    /** 检查是否为无限桶。*/
    public static boolean isInfinite(ItemStack stack) {
        if (stack.isEmpty()) return false;
        NbtCompound nbt = stack.getNbt();
        if (nbt == null) return false;
        return nbt.contains(NBT_KEY);
    }

    /** 获取无限桶类型，非无限桶返回 null。*/
    public static BucketType getType(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        if (nbt == null || !nbt.contains(NBT_KEY)) return null;
        int ord = nbt.getInt(NBT_KEY);
        BucketType[] all = BucketType.values();
        if (ord < 0 || ord >= all.length) return null;
        return all[ord];
    }
}
