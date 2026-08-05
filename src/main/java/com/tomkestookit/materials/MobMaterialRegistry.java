package com.tomkestookit.materials;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * 怪物专属素材注册表。每种素材是一个 vanilla Item + NBT 标记 + 自定义名字。
 * 用 NBT 键 {@code TomkesTookitMaterial} 区分同种基础物品。
 *
 * 用途：作为 Trinket / Reforge / 武器合成材料，且部分素材可入 vanilla loot table。
 */
public final class MobMaterialRegistry {

    public static final String NBT_KEY = "TomkesTookitMaterial";

    public record MaterialDef(
        String id,
        String name,
        Item icon,
        String rarity,        // 颜色代码 §7/§a/§e/§c/§5
        List<String> lore
    ) {}

    private static final List<MaterialDef> ALL = new ArrayList<>();

    static {
        reg("corrupt_sinew", "腐化肌腱", Items.ROTTEN_FLESH, "§7",
            List.of("§8「一坨不甘心变成腐肉的肌腱。」",
                "§7低阶材料 · 用于合成基础饰品/武器"));

        reg("abyssal_shard", "深邃碎片", Items.PRISMARINE_CRYSTALS, "§a",
            List.of("§8「深海压力下才能形成的结晶。」",
                "§7水系材料 · 用于合成深海系饰品/重铸"));

        reg("soul_crystal", "灵魂结晶", Items.SOUL_SAND, "§a",
            List.of("§8「凝固的亡者呼吸，隐隐作响。」",
                "§7灵魂系材料 · 用于合成灵魂系装备"));

        reg("stardust", "星尘", Items.GLOWSTONE_DUST, "§e",
            List.of("§8「陨石在夜空划过时抖落的粉末。」",
                "§7时空系材料 · 用于合成飞行/时空系装备"));

        reg("blood_moon_essence", "血月精华", Items.REDSTONE, "§c",
            List.of("§8「只在血月之夜从精英怪血液中凝结。」",
                "§c稀有 · 用于合成高级重铸/武器"));

        reg("void_core", "虚空之核", Items.END_CRYSTAL, "§5",
            List.of("§8「掉进虚空的东西，回来的只有它。」",
                "§5传奇 · 用于合成顶级装备"));
    }

    private static void reg(String id, String name, Item icon, String rarity, List<String> lore) {
        ALL.add(new MaterialDef(id, name, icon, rarity, lore));
    }

    public static List<MaterialDef> all() { return ALL; }

    public static MaterialDef byId(String id) {
        for (MaterialDef d : ALL) if (d.id().equals(id)) return d;
        return null;
    }

    /** 从 ItemStack 读取素材 id；非素材返回 null。*/
    public static String getId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        NbtCompound nbt = stack.getNbt();
        if (nbt == null || !nbt.contains(NBT_KEY)) return null;
        return nbt.getString(NBT_KEY);
    }

    /** 判断 stack 是否为指定 id 的素材。*/
    public static boolean is(ItemStack stack, String id) {
        String actual = getId(stack);
        return id.equals(actual);
    }

    /** 生成 count 个素材 ItemStack。*/
    public static ItemStack build(MaterialDef def, int count) {
        ItemStack stack = new ItemStack(def.icon(), count);
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putString(NBT_KEY, def.id());
        stack.setCustomName(Text.literal(def.rarity() + "§l" + def.name())
            .styled(s -> s.withItalic(false)));

        NbtCompound display = nbt.getCompound("display");
        NbtList lore = new NbtList();
        for (String line : def.lore()) {
            lore.add(NbtString.of(Text.Serializer.toJson(
                Text.literal(line).styled(s -> s.withItalic(false)))));
        }
        lore.add(NbtString.of(Text.Serializer.toJson(
            Text.literal("§8怪物专属素材").styled(s -> s.withItalic(false)))));
        display.put("Lore", lore);
        nbt.put("display", display);
        return stack;
    }
}
