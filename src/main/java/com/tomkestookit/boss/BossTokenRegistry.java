package com.tomkestookit.boss;

import com.tomkestookit.materials.CustomModelDataRegistry;
import com.tomkestookit.materials.MobMaterialRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.inventory.RecipeInputInventory;
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
 * Boss 定义注册表。每个 BossDef 描述一种可召唤 Boss 的属性、合成配方、外观。
 *
 * 合成材料改用怪物专属素材（NBT 匹配）：
 *   - craftingCenter/craftingSurround 是 MobMaterialRegistry 的素材 id
 *   - match3x3 检查 3×3 内每一格的 TomkesTookitMaterial NBT 是否匹配
 */
public final class BossTokenRegistry {

    public record BossDef(String bossId, String displayName, EntityType<?> mobType,
                          Item tokenIcon, String craftingCenter, String craftingSurround,
                          double healthMult, double damageMult, double followRange,
                          BossBar.Color barColor, BossBar.Style barStyle, int xpReward) {}

    public static final List<BossDef> ALL = new ArrayList<>();

    static {
        // 下界监守者：8 灵魂结晶 + 血月精华（灵魂系不死主题）
        ALL.add(new BossDef("infernal_warden", "下界监守者", EntityType.WITHER_SKELETON,
                Items.WITHER_SKELETON_SKULL, "blood_moon_essence", "soul_crystal",
                20, 5, 60, BossBar.Color.RED, BossBar.Style.NOTCHED_12, 1500));

        // 深渊之王：8 深邃碎片 + 血月精华（水系主题）
        ALL.add(new BossDef("abyssal_guardian", "深渊之王", EntityType.ELDER_GUARDIAN,
                Items.HEART_OF_THE_SEA, "blood_moon_essence", "abyssal_shard",
                25, 4, 50, BossBar.Color.BLUE, BossBar.Style.NOTCHED_10, 1200));

        // 永冻巫妖：8 星尘 + 血月精华（远古/时空主题）
        ALL.add(new BossDef("frost_lich", "永冻巫妖", EntityType.SKELETON,
                Items.BLUE_ICE, "blood_moon_essence", "stardust",
                18, 6, 40, BossBar.Color.WHITE, BossBar.Style.NOTCHED_6, 1000));

        // 虚空幼龙：8 血月精华 + 虚空之核（终极配方）
        ALL.add(new BossDef("void_dragon_child", "虚空幼龙", EntityType.WITHER,
                Items.DRAGON_EGG, "void_core", "blood_moon_essence",
                30, 7, 80, BossBar.Color.PURPLE, BossBar.Style.NOTCHED_20, 2000));
    }

    private BossTokenRegistry() {}
    public static BossDef byId(String bossId) {
        if (bossId == null) return null;
        for (BossDef def : ALL) {
            if (def.bossId().equals(bossId)) return def;
        }
        return null;
    }

    public static List<BossDef> all() {
        return java.util.Collections.unmodifiableList(ALL);
    }

    /** 3x3 判定：中心为 def.craftingCenter，其余 8 格全为 def.craftingSurround。命中返回对应 BossDef，未命中 null。*/
    public static BossDef match3x3(RecipeInputInventory inv) {
        if (inv.getWidth() != 3 || inv.getHeight() != 3) return null;
        for (BossDef def : ALL) {
            if (matchesDef(inv, def)) return def;
        }
        return null;
    }

    private static boolean matchesDef(RecipeInputInventory inv, BossDef def) {
        ItemStack center = inv.getStack(4);
        if (!MobMaterialRegistry.is(center, def.craftingCenter())) return false;
        int[] surround = {0, 1, 2, 3, 5, 6, 7, 8};
        for (int i : surround) {
            ItemStack s = inv.getStack(i);
            if (!MobMaterialRegistry.is(s, def.craftingSurround())) return false;
        }
        return true;
    }

    /** 构造召唤令牌 ItemStack：NBT TomkesTookitBossToken=bossId，自定义命名/lore/附魔光效。*/
    public static ItemStack buildTokenStack(BossDef def) {
        ItemStack stack = new ItemStack(def.tokenIcon());

        stack.setCustomName(Text.literal("§c§l" + def.displayName() + " 召唤令牌")
                .styled(s -> s.withItalic(false)));

        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putString("TomkesTookitBossToken", def.bossId());

        int cmd = CustomModelDataRegistry.forBoss(def.bossId());
        if (cmd != 0) nbt.putInt("CustomModelData", cmd);

        NbtCompound display = nbt.getCompound("display");
        NbtList lore = new NbtList();
        List<String> lines = List.of(
                "§7右键任意方块以召唤",
                "§7§o「" + def.displayName() + "」",
                "§8生命 ×" + fmt(def.healthMult()) + "  伤害 ×" + fmt(def.damageMult()),
                "§8经验奖励：" + def.xpReward(),
                "§c§l⚠ 冷却：24 小时",
                "§8同服上限 3 只",
                "§8距离出生点 200 格以上"
        );
        for (String line : lines) {
            lore.add(NbtString.of(Text.Serializer.toJson(
                    Text.literal(line).styled(s -> s.withItalic(false)))));
        }
        display.put("Lore", lore);
        nbt.put("display", display);

        // 附魔光效：空 Enchantments 列表 + HideFlags 隐藏 "Enchanted" tooltip
        NbtList ench = new NbtList();
        ench.add(new NbtCompound());
        nbt.put("Enchantments", ench);
        nbt.putInt("HideFlags", 1);

        // Boss 令牌统一为传奇品质
        com.tomkestookit.materials.ItemQualityTag.append(stack,
            com.tomkestookit.materials.ItemQualityTag.Quality.LEGENDARY,
            "3×3 合成 §8· §7需精英怪掉落的怪物素材");

        return stack;
    }

    private static String fmt(double v) {
        if (v == Math.floor(v)) return String.valueOf((int) v);
        return String.valueOf(v);
    }
}

