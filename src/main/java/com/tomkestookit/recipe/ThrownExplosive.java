package com.tomkestookit.recipe;

import com.tomkestookit.infinitebucket.InfiniteBucket;
import com.tomkestookit.regionfiller.RegionFiller;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * 投掷炸弹：手雷 & 蹦蹦炸弹。
 *
 *   - 手雷      = 1 vanilla TNT + 1 水桶（无序合成）
 *   - 蹦蹦炸弹  = 1 大伊万 + 1 水桶（无序合成）
 *
 *   水桶消耗规则：
 *     - vanilla WATER_BUCKET → 消耗（走 vanilla remainder：会留下空桶，由 FakeCraftTakeMixin 处理）
 *     - 无限水桶 / 附魔水桶  → 不消耗
 *
 *   使用方式：手持右键抛出，飞行一段时间后爆炸。爆炸只造成伤害，不破坏方块。
 *   蹦蹦炸弹会在地面弹跳。
 */
public final class ThrownExplosive {

    /** NBT 键：0=手雷, 1=蹦蹦炸弹。*/
    public static final String NBT_KEY = "TomkesTookitThrownExplosive";

    private ThrownExplosive() {}

    public enum Type {
        GRENADE(0, "手雷", Formatting.RED,
            2.0f, 30, 1.4,
            List.of(
                "§8「拉环、五秒、扔出去。」",
                "§7右键手持 §f抛出",
                "§7爆炸 §c仅伤害 §7不破坏方块",
                "§8引信约 §f1.5 §8秒 §8· 半径 §f~2"
            )),
        BOUNCY_BOMB(1, "蹦蹦炸弹", Formatting.LIGHT_PURPLE,
            5.0f, 60, 1.2,
            List.of(
                "§8「弹一弹、再弹一弹，砰。」",
                "§7右键手持 §f抛出",
                "§7爆炸 §c仅伤害 §7不破坏方块",
                "§7落地会 §d弹跳",
                "§8引信约 §f3 §8秒 §8· 半径 §f~5"
            ));

        public final int ordinal;
        public final String zh;
        public final Formatting color;
        public final float power;
        public final int fuseTicks;
        public final double throwSpeed;
        public final List<String> lore;

        Type(int ordinal, String zh, Formatting color,
             float power, int fuseTicks, double throwSpeed, List<String> lore) {
            this.ordinal = ordinal;
            this.zh = zh;
            this.color = color;
            this.power = power;
            this.fuseTicks = fuseTicks;
            this.throwSpeed = throwSpeed;
            this.lore = lore;
        }

        public static Type byOrdinal(int i) {
            for (Type t : values()) if (t.ordinal == i) return t;
            return null;
        }
    }

    // --------------------------------------------------- 识别

    public static boolean isThrownExplosive(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() != Items.TNT) return false;
        NbtCompound nbt = stack.getNbt();
        return nbt != null && nbt.contains(NBT_KEY);
    }

    public static Type getType(ItemStack stack) {
        if (!isThrownExplosive(stack)) return null;
        return Type.byOrdinal(stack.getNbt().getInt(NBT_KEY));
    }

    // --------------------------------------------------- 3x3 无序匹配

    /**
     * 3x3 无序匹配：恰好 2 个非空格，一个是 TNT/大伊万，一个是水桶。
     * 返回：匹配到的 Type，未匹配返回 null。
     */
    public static Type match3x3(RecipeInputInventory inv) {
        if (inv.getWidth() != 3 || inv.getHeight() != 3) return null;

        ItemStack explosive = ItemStack.EMPTY;
        ItemStack bucket = ItemStack.EMPTY;
        int nonEmpty = 0;

        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty()) continue;
            nonEmpty++;
            if (nonEmpty > 2) return null;

            Item it = s.getItem();
            if (it == Items.TNT) {
                if (!explosive.isEmpty()) return null;
                explosive = s;
            } else if (it == Items.WATER_BUCKET) {
                if (!bucket.isEmpty()) return null;
                bucket = s;
            } else {
                return null;
            }
        }

        if (nonEmpty != 2 || explosive.isEmpty() || bucket.isEmpty()) return null;

        // 已经是投掷炸弹的 TNT 不能再套一层
        if (isThrownExplosive(explosive)) return null;

        // 大伊万 → 蹦蹦炸弹；普通 TNT → 手雷
        return SugarTnt.isSugarTnt(explosive) ? Type.BOUNCY_BOMB : Type.GRENADE;
    }

    // --------------------------------------------------- 构造结果

    public static ItemStack buildResult(Type type) {
        ItemStack stack = new ItemStack(Items.TNT);
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putInt(NBT_KEY, type.ordinal);

        // 假附魔光效
        NbtList enchList = new NbtList();
        NbtCompound fakeEnch = new NbtCompound();
        fakeEnch.putString("id", "minecraft:unbreaking");
        fakeEnch.putInt("lvl", 1);
        enchList.add(fakeEnch);
        nbt.put("Enchantments", enchList);
        nbt.putInt("HideFlags", 1);

        stack.setCustomName(Text.literal(type.zh)
            .styled(s -> s.withItalic(false).withBold(true).withColor(type.color)));

        NbtCompound display = nbt.getCompound("display");
        NbtList lore = new NbtList();
        for (String line : type.lore) {
            lore.add(NbtString.of(Text.Serializer.toJson(
                Text.literal(line).styled(s -> s.withItalic(false)))));
        }
        display.put("Lore", lore);
        nbt.put("display", display);

        com.tomkestookit.materials.ItemQualityTag.append(stack,
            type == Type.BOUNCY_BOMB
                ? com.tomkestookit.materials.ItemQualityTag.Quality.RARE
                : com.tomkestookit.materials.ItemQualityTag.Quality.UNCOMMON,
            "3×3 合成 §8· §7"
                + (type == Type.BOUNCY_BOMB ? "大伊万" : "TNT")
                + " + 水桶");
        return stack;
    }

    // --------------------------------------------------- 水桶保留判定

    /** 是否是"要保留"的水桶（附魔桶 / 无限桶）。这些不消耗输入。*/
    public static boolean isPreservedBucket(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() != Items.WATER_BUCKET) return false;
        if (InfiniteBucket.isInfinite(stack)) return true;
        if (RegionFiller.isTool(stack)) return true;
        return false;
    }
}
