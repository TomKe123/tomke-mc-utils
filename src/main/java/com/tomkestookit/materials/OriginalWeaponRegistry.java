package com.tomkestookit.materials;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 原创武器注册表：基于 vanilla Item 底模 + NBT 标记 + AttributeModifiers。
 *
 * 允许原版附魔叠加（用户选择），特殊效果通过 mixin 触发（读 NBT 键 TomkesTookitWeapon）。
 */
public final class OriginalWeaponRegistry {

    public static final String NBT_KEY = "TomkesTookitWeapon";

    // 每个武器一个固定 UUID 用于 attribute modifier（避免冲突）
    private static final UUID MOD_UUID_BASE = UUID.fromString("c0d7a5d0-0000-4000-8000-000000000000");

    public record WeaponDef(
        String id,
        String name,
        Item baseItem,           // 底模 Item（决定图标 + 基础耐久 + 附魔支持）
        double attackDamage,     // 覆盖 vanilla 攻击伤害（含 +1 基础）
        double attackSpeed,      // 覆盖 vanilla 攻速（负数用绝对值，通常 -2.4 = 快，-3.0 = 铁剑）
        String rarity,           // 颜色代码
        List<String> lore,
        // 合成
        Item centerItem,
        Item surroundItem
    ) {}

    private static final List<WeaponDef> ALL = new ArrayList<>();

    static {
        reg("blood_moon_scythe", "血月镰刀", Items.IRON_HOE,
            8.0, -2.5, "§c",
            List.of("§8「月光染红时，收割不止麦子。」",
                "§7攻击 §c8 §8· 攻速 §c1.5",
                "§7命中回血 15% 造成的伤害（不吸魔法/环境）",
                "§c稀有 · 血月精华锻造"),
            Items.REDSTONE, Items.IRON_HOE);

        reg("soul_reaper", "灵魂收割者", Items.NETHERITE_SWORD,
            10.0, -2.4, "§5",
            List.of("§8「灵魂收进刀里，你还想去哪？」",
                "§7攻击 §510 §8· 攻速 §51.6",
                "§7击杀怪物 +50% 经验（vanilla + Kill 掉的）",
                "§5史诗 · 灵魂结晶淬炼"),
            Items.SOUL_SAND, Items.NETHERITE_SWORD);

        reg("abyssal_trident", "深渊三叉戟", Items.TRIDENT,
            12.0, -2.9, "§b",
            List.of("§8「深海之力借你三秒。」",
                "§7攻击 §b12 §8· 攻速 §b1.1",
                "§7水下攻击 +50%",
                "§7攻击附带穿透（无视 30% 护甲）",
                "§b稀有 · 深邃碎片强化"),
            Items.PRISMARINE_CRYSTALS, Items.TRIDENT);

        reg("void_blade", "虚空之刃", Items.NETHERITE_SWORD,
            15.0, -2.2, "§5",
            List.of("§8「一刀两个宇宙。」",
                "§7攻击 §515 §8· 攻速 §51.8",
                "§7攻击后自动前进 §d3 §7格（穿透方块）",
                "§5传奇 · 虚空之核 + 星尘凝聚"),
            Items.END_CRYSTAL, Items.NETHERITE_SWORD);
    }

    private static void reg(String id, String name, Item baseItem,
                            double atk, double spd, String rarity,
                            List<String> lore, Item center, Item surround) {
        ALL.add(new WeaponDef(id, name, baseItem, atk, spd, rarity, lore, center, surround));
    }

    public static List<WeaponDef> all() { return ALL; }

    public static WeaponDef byId(String id) {
        for (WeaponDef d : ALL) if (d.id().equals(id)) return d;
        return null;
    }

    public static String getId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        NbtCompound nbt = stack.getNbt();
        if (nbt == null || !nbt.contains(NBT_KEY)) return null;
        return nbt.getString(NBT_KEY);
    }

    /** 生成一把原创武器 ItemStack。*/
    public static ItemStack buildStack(WeaponDef def) {
        ItemStack stack = new ItemStack(def.baseItem());
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putString(NBT_KEY, def.id());

        // CustomModelData 用于资源包贴图识别
        int cmd = CustomModelDataRegistry.forWeapon(def.id());
        if (cmd != 0) nbt.putInt("CustomModelData", cmd);

        // 自定义名称
        stack.setCustomName(Text.literal(def.rarity() + "§l" + def.name())
            .styled(s -> s.withItalic(false)));

        // 附魔光效 (不影响功能，只是显示光效)
        NbtList enchList = new NbtList();
        NbtCompound fakeEnch = new NbtCompound();
        fakeEnch.putString("id", "minecraft:unbreaking");
        fakeEnch.putInt("lvl", 1);
        enchList.add(fakeEnch);
        nbt.put("Enchantments", enchList);

        // 覆盖 AttributeModifiers：覆盖 vanilla 攻击力与攻速
        NbtList attrs = new NbtList();
        NbtCompound atkMod = new NbtCompound();
        atkMod.putString("AttributeName", "generic.attack_damage");
        atkMod.putString("Name", "tomkestookit:weapon_" + def.id() + "_atk");
        atkMod.putDouble("Amount", def.attackDamage() - 1.0); // vanilla 基础 +1
        atkMod.putInt("Operation", 0);
        atkMod.putString("Slot", "mainhand");
        atkMod.put("UUID", NbtHelper.fromUuid(uuidFor(def.id(), 0)));
        attrs.add(atkMod);

        NbtCompound spdMod = new NbtCompound();
        spdMod.putString("AttributeName", "generic.attack_speed");
        spdMod.putString("Name", "tomkestookit:weapon_" + def.id() + "_spd");
        spdMod.putDouble("Amount", def.attackSpeed() - 4.0); // vanilla 默认 4，负数下调
        spdMod.putInt("Operation", 0);
        spdMod.putString("Slot", "mainhand");
        spdMod.put("UUID", NbtHelper.fromUuid(uuidFor(def.id(), 1)));
        attrs.add(spdMod);

        nbt.put("AttributeModifiers", attrs);

        // Lore
        NbtCompound display = nbt.getCompound("display");
        NbtList lore = new NbtList();
        for (String line : def.lore()) {
            lore.add(NbtString.of(Text.Serializer.toJson(
                Text.literal(line).styled(s -> s.withItalic(false)))));
        }
        lore.add(NbtString.of(Text.Serializer.toJson(
            Text.literal("§8原创武器 §7· 可上原版附魔").styled(s -> s.withItalic(false)))));
        display.put("Lore", lore);
        nbt.put("display", display);

        // 品质：血月镰刀=稀有；灵魂收割者=史诗；深渊三叉戟=稀有；虚空之刃=传奇
        ItemQualityTag.Quality q = switch (def.id()) {
            case "void_blade" -> ItemQualityTag.Quality.LEGENDARY;
            case "soul_reaper" -> ItemQualityTag.Quality.EPIC;
            default -> ItemQualityTag.Quality.RARE;
        };
        ItemQualityTag.append(stack, q, "3×3 合成 §8· §7中心需怪物专属素材");

        return stack;
    }

    private static UUID uuidFor(String id, int slot) {
        long msb = MOD_UUID_BASE.getMostSignificantBits() + id.hashCode();
        long lsb = MOD_UUID_BASE.getLeastSignificantBits() + slot;
        return new UUID(msb, lsb);
    }

    /** 3x3 匹配：中心 + 8 相同的外围。*/
    public static WeaponDef match3x3(net.minecraft.inventory.RecipeInputInventory inv) {
        if (inv.getWidth() != 3 || inv.getHeight() != 3) return null;
        for (WeaponDef def : ALL) {
            if (matches(inv, def)) return def;
        }
        return null;
    }

    private static boolean matches(net.minecraft.inventory.RecipeInputInventory inv, WeaponDef def) {
        ItemStack center = inv.getStack(4);
        // 中心必须是对应的 MobMaterial NBT 物品（不能用普通原版物品代替）
        String matId = materialIdForCenter(def);
        if (matId != null) {
            if (!com.tomkestookit.materials.MobMaterialRegistry.is(center, matId)) return false;
        } else {
            if (center.getItem() != def.centerItem()) return false;
        }
        int[] surround = {0, 1, 2, 3, 5, 6, 7, 8};
        for (int i : surround) {
            if (inv.getStack(i).getItem() != def.surroundItem()) return false;
        }
        return true;
    }

    /** 根据 centerItem 映射到 MobMaterial id（如果有对应关系）。*/
    private static String materialIdForCenter(WeaponDef def) {
        Item c = def.centerItem();
        if (c == Items.REDSTONE) return "blood_moon_essence";
        if (c == Items.SOUL_SAND) return "soul_crystal";
        if (c == Items.PRISMARINE_CRYSTALS) return "abyssal_shard";
        if (c == Items.END_CRYSTAL) return "void_core";
        if (c == Items.GLOWSTONE_DUST) return "stardust";
        return null;
    }
}
