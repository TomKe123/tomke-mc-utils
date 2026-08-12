package com.tomkestookit.reforge;

import com.tomkestookit.AntigravBootsHandler;
import com.tomkestookit.materials.CustomModelDataRegistry;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.text.Text;

import java.util.*;
import java.util.function.Predicate;

/**
 * 重铸石注册表：每种石头定义 id / 中文名 / 属性 / 适用装备 / 铁砧费用 / 文案。
 *
 * 兼容性设计：
 *   - 不覆盖现有 AttributeModifiers，而是追加到已有列表末尾
 *   - 每块石头有唯一 UUID，同一石头不会叠加（检测已有 UUID）
 *   - 适用范围用 Predicate<ItemStack> 判定，兼容任何 mod 的装备
 *   - 不限制装备必须是 vanilla Item
 */
public final class ReforgeRegistry {

    public static final String NBT_KEY = "TomkesTookitReforgeStone";
    public static final String APPLIED_TAG = "TomkesTookitReforges";

    public record ReforgeDef(
        String id, String name, Item icon,
        String attribute, double amount, int operation, String slot,
        UUID uuid, int anvilCost,
        Predicate<ItemStack> canApply,
        List<String> lore,
        Item recipeCenter, Item recipeSurround
    ) {}

    private static final List<ReforgeDef> ALL = new ArrayList<>();

    static {
        // ---- 武器系 ----
        // 中心 blaze_powder（图标），围 8 blaze_rod → "怒火"
        reg("fury_shard", "§c怒火碎片", Items.BLAZE_POWDER,
            "generic.attack_damage", 2.0, 0, "mainhand",
            "b1a00001-0000-0000-0000-000000000001", 8,
            ReforgeRegistry::isWeapon,
            List.of("§8§o把烈焰人的怒气磨成粉，塞进刀刃。", "§7+2 攻击伤害",
                "§7适用于：§f剑 / 斧 / 三叉戟"),
            Items.BLAZE_POWDER, Items.BLAZE_ROD);

        // 中心 nether_star（图标），围 8 wither_skeleton_skull → 稀有材料反映伤害强度
        reg("berserker_core", "狂战之核", Items.NETHER_STAR,
            "generic.attack_damage", 5.0, 0, "mainhand",
            "b1a00001-0000-0000-0000-000000000002", 30,
            ReforgeRegistry::isWeapon,
            List.of("§8「这玩意从末影龙心脏里挖出来的。可能是。」", "§7+5 攻击伤害", "§c稀有",
                "§7适用于：§f剑 / 斧 / 三叉戟"),
            Items.NETHER_STAR, Items.WITHER_SKELETON_SKULL);

        // 中心 phantom_membrane（图标），围 8 phantom_membrane → "牙齿=幻翼身体片段"
        reg("swift_fang", "迅捷之牙", Items.PHANTOM_MEMBRANE,
            "generic.attack_speed", 0.3, 0, "mainhand",
            "b1a00001-0000-0000-0000-000000000003", 12,
            ReforgeRegistry::isWeapon,
            List.of("§8「幻翼的牙齿比翅膀还值钱——因为它咬得快。」", "§7+0.3 攻击速度",
                "§7适用于：§f剑 / 斧 / 三叉戟"),
            Items.PHANTOM_MEMBRANE, Items.PHANTOM_MEMBRANE);

        // ---- 防具系 ----
        // 中心 iron_block（图标），围 8 木头 → "铁+树"
        reg("iron_bark", "铁树皮", Items.IRON_BLOCK,
            "generic.armor", 2.0, 0, "any_armor",
            "b1a00001-0000-0000-0000-000000000004", 10,
            ReforgeRegistry::isArmor,
            List.of("§8「树要是有这皮，伐木工就得换行了。」", "§7+2 护甲",
                "§7适用于：§f头盔 / 胸甲 / 护腿 / 靴子"),
            Items.IRON_BLOCK, Items.OAK_LOG);

        // 中心 diamond_block（图标），围 8 turtle_helmet 材料（scute）→ "壳"
        reg("diamond_shell", "钻石壳", Items.DIAMOND_BLOCK,
            "generic.armor_toughness", 3.0, 0, "any_armor",
            "b1a00001-0000-0000-0000-000000000005", 20,
            ReforgeRegistry::isArmor,
            List.of("§8「坚硬到你会怀疑矿工的手是铁做的。」", "§7+3 盔甲韧性", "§e较稀有",
                "§7适用于：§f头盔 / 胸甲 / 护腿 / 靴子"),
            Items.DIAMOND_BLOCK, Items.SCUTE);

        // 中心 feather（图标），围 8 feather → "轻盈"
        reg("featherweight_gem", "轻羽宝石", Items.FEATHER,
            "generic.movement_speed", 0.01, 0, "any_armor",
            "b1a00001-0000-0000-0000-000000000006", 8,
            ReforgeRegistry::isArmor,
            List.of("§8「穿上这个跑得快，脱下它还是快——因为更轻了。」", "§7+0.01 移速",
                "§7适用于：§f头盔 / 胸甲 / 护腿 / 靴子"),
            Items.FEATHER, Items.FEATHER);

        // 中心 netherite_ingot（图标），围 8 ancient_debris → "泰坦=下界最硬"
        // 叠加修复：per-slot UUID 派生使得同一 def 可叠在多件护甲上
        reg("titan_plate", "泰坦铭板", Items.NETHERITE_INGOT,
            "generic.max_health", 4.0, 0, "any_armor",
            "b1a00001-0000-0000-0000-000000000007", 25,
            ReforgeRegistry::isArmor,
            List.of("§8「古代残骸压制的命牌——写着'多活一秒'。」", "§7+4 最大生命", "§c稀有",
                "§7适用于：§f头盔 / 胸甲 / 护腿 / 靴子"),
            Items.NETHERITE_INGOT, Items.ANCIENT_DEBRIS);

        // ---- 通用系 ----
        // 中心 emerald（图标），围 8 emerald → "四叶草=幸运宝石"
        reg("lucky_clover", "幸运三叶草", Items.EMERALD,
            "generic.luck", 2.0, 0, "mainhand",
            "b1a00001-0000-0000-0000-000000000008", 15,
            s -> isWeapon(s) || isTool(s),
            List.of("§8「四叶草太稀有了，三叶也能将就。」", "§7+2 幸运", "§a影响掉落物表",
                "§7适用于：§f主手武器 / 工具"),
            Items.EMERALD, Items.EMERALD_BLOCK);

        // 中心 moss_block（图标），围 8 vine → "苔藓+藤"
        reg("mending_moss", "修复苔藓", Items.MOSS_BLOCK,
            "generic.armor", 1.0, 0, "any_armor",
            "b1a00001-0000-0000-0000-000000000009", 6,
            ReforgeRegistry::isArmor,
            List.of("§8「苔藓不会修铁，但它修了你的信心。」", "§7+1 护甲", "§7入门级重铸石",
                "§7适用于：§f头盔 / 胸甲 / 护腿 / 靴子"),
            Items.MOSS_BLOCK, Items.VINE);

        // 中心 slime_ball（图标），围 8 slime_block → "击退=史莱姆弹性"
        reg("knockback_crystal", "击退水晶", Items.SLIME_BALL,
            "generic.knockback_resistance", 0.2, 0, "any_armor",
            "b1a00001-0000-0000-0000-00000000000a", 12,
            ReforgeRegistry::isArmor,
            List.of("§8「史莱姆弹性的逆运用——让你不被弹走。」", "§7+20% 击退抗性",
                "§7适用于：§f头盔 / 胸甲 / 护腿 / 靴子"),
            Items.SLIME_BALL, Items.SLIME_BLOCK);

        // 中心 end_crystal（图标），围 8 chorus_fruit → "天锻=末地"
        reg("skyforge_rune", "天锻符文", Items.END_CRYSTAL,
            "generic.attack_speed", 0.1, 0, "mainhand",
            "b1a00001-0000-0000-0000-00000000000b", 15,
            ReforgeRegistry::isTool,
            List.of("§8「在半空中挖矿的人，叫做建筑师。在地上挖的，叫矿工。」",
                "§7取消悬空挖掘速度惩罚",
                "§7+0.1 攻击速度",
                "§e搭方块时再也不用踩实地面了",
                "§7适用于：§f镐 / 斧 / 锹 / 锄"),
            Items.END_CRYSTAL, Items.CHORUS_FRUIT);

        // 中心 experience_bottle（图标），围 8 lapis_block → "经验=青金石=附魔"
        // 特殊重铸石：不加属性（amount=0），tick 逻辑在 AutoMenderHandler 里处理
        reg("soul_mend", "经验汲取", Items.EXPERIENCE_BOTTLE,
            "generic.luck", 0.0, 0, "any",
            "b1a00001-0000-0000-0000-00000000000c", 25,
            s -> true, // 任何装备都可以
            List.of("§8「经验是最好的修理工。」",
                "§7装备损坏时自动消耗经验点修复",
                "§7每 2 秒消耗 1 经验点修 1 耐久",
                "§8不扣等级，只扣经验条绿色部分",
                "§5史诗",
                "§7适用于：§f任意可装备物品"),
            Items.EXPERIENCE_BOTTLE, Items.LAPIS_BLOCK);

        // 中心 phantom_membrane（图标），围 8 feather → "反重力=飞"
        // 特殊重铸石：铁砧打在靴子上获得反重力效果
        reg("antigrav_boots", "反重力核心", Items.PHANTOM_MEMBRANE,
            "generic.movement_speed", 2.0, 2, "feet",
            "b1a00001-0000-0000-0000-00000000000d", 10,
            AntigravBootsHandler::isBoots,
            List.of("§8「把幻翼的灵魂绑在鞋底，大地就够不着你了。」",
                "§7铁砧打在任意靴子上获得反重力效果",
                "§7跳跃 II + 速度 ×3 + 摔落减免 75%",
                "§8不在脚下也在心中，这里没有地心引力",
                "§7适用于：§f靴子"),
            Items.PHANTOM_MEMBRANE, Items.FEATHER);

        // ====================================================================
        //  升级链系列（材料强度与效果成正比）
        //  可以通过铁砧在已有重铸石上"覆盖升级"（同插槽替换）
        // ====================================================================

        // ---- 伤害系升级：怒火碎片 → 炎爆晶体 ----
        reg("inferno_crystal", "炎爆晶体", Items.BLAZE_ROD,
            "generic.attack_damage", 4.0, 0, "mainhand",
            "b1a00001-0000-0000-0000-000000000010", 18,
            ReforgeRegistry::isWeapon,
            List.of("§8「烈焰人的怒气煮沸了刀刃。」",
                "§7+4 攻击伤害",
                "§e稀有 §8- 升级自怒火碎片",
                "§7合成后铁砧覆盖已有怒火碎片",
                "§7适用于：§f剑 / 斧 / 三叉戟"),
            Items.BLAZE_POWDER, Items.MAGMA_BLOCK);

        // ---- 防具系升级：铁树皮 → 魔源树皮 → 磐石板甲 ----
        reg("wardwood_bark", "魔源树皮", Items.WARPED_STEM,
            "generic.armor", 4.0, 0, "any_armor",
            "b1a00001-0000-0000-0000-000000000011", 15,
            ReforgeRegistry::isArmor,
            List.of("§8「树学会了如何保护自己。」",
                "§7+4 护甲",
                "§e稀有 §8- 升级自铁树皮",
                "§7合成后铁砧覆盖已有铁树皮",
                "§7适用于：§f头盔 / 胸甲 / 护腿 / 靴子"),
            Items.IRON_BLOCK, Items.ANCIENT_DEBRIS);

        reg("bedrock_bulwark", "磐石板甲", Items.NETHERITE_BLOCK,
            "generic.armor", 7.0, 0, "any_armor",
            "b1a00001-0000-0000-0000-000000000012", 30,
            ReforgeRegistry::isArmor,
            List.of("§8「这就叫硬核。」",
                "§7+7 护甲 + 常驻 抗性 I",
                "§5史诗 §8- 升级自魔源树皮",
                "§7合成后铁砧覆盖已有魔源树皮",
                "§7适用于：§f头盔 / 胸甲 / 护腿 / 靴子"),
            Items.WARPED_STEM, Items.NETHERITE_BLOCK);

        // ---- 生命系升级：轻羽宝石 → 龙鳞铭板 → 泰坦之心 ----
        reg("dragon_scale_plate", "龙鳞铭板", Items.IRON_INGOT,
            "generic.max_health", 8.0, 0, "any_armor",
            "b1a00001-0000-0000-0000-000000000013", 25,
            ReforgeRegistry::isArmor,
            List.of("§8「龙的心脏比它的鳞片跳得更响。」",
                "§7+8 最大生命",
                "§e稀有 §8- 升级自轻羽宝石",
                "§7合成后铁砧覆盖已有轻羽宝石",
                "§7适用于：§f头盔 / 胸甲 / 护腿 / 靴子"),
            Items.FEATHER, Items.DRAGON_HEAD);

        reg("titan_heart", "泰坦之心", Items.DRAGON_HEAD,
            "generic.max_health", 14.0, 0, "any_armor",
            "b1a00001-0000-0000-0000-000000000014", 40,
            ReforgeRegistry::isArmor,
            List.of("§8「巨人的心在你胸口跳动。」",
                "§7+14 最大生命 + 常驻 恢复 I",
                "§5史诗 §8- 升级自龙鳞铭板",
                "§7合成后铁砧覆盖已有龙鳞铭板",
                "§7适用于：§f头盔 / 胸甲 / 护腿 / 靴子"),
            Items.NETHERITE_INGOT, Items.DRAGON_HEAD);

        // ---- 独立传说重铸 ----
        reg("cosmic_essence", "宇宙精华", Items.NETHER_STAR,
            "generic.luck", 5.0, 0, "mainhand",
            "b1a00001-0000-0000-0000-000000000015", 50,
            s -> isWeapon(s) || isTool(s),
            List.of("§8「陨石的荧光，用下界之星稳定下来。」",
                "§7+5 幸运 + 25% 双倍掉落",
                "§5传奇 §8- 独立即巅峰，不需要升级",
                "§7适用于：§f主手武器 / 工具"),
            Items.NETHER_STAR, Items.AMETHYST_SHARD);

        // ====================================================================
        //  怪物专属素材系列（v2 新增）
        // ====================================================================

        // 深海之力 —— 三叉戟/武器专用
        reg("abyssal_might", "深海之力", Items.PRISMARINE_CRYSTALS,
            "generic.attack_damage", 5.0, 0, "mainhand",
            "b1a00001-0000-0000-0000-000000000020", 22,
            ReforgeRegistry::isWeapon,
            List.of("§8「深海压力把武器磨得更快。」",
                "§7+5 攻击伤害",
                "§b稀有 §8- 深邃碎片锻造",
                "§7适用于：§f剑 / 斧 / 三叉戟"),
            Items.PRISMARINE_CRYSTALS, Items.PRISMARINE_SHARD);

        // 灵魂锋刃 —— 剑/斧专用
        reg("soul_edge", "灵魂锋刃", Items.SOUL_SAND,
            "generic.attack_damage", 4.0, 0, "mainhand",
            "b1a00001-0000-0000-0000-000000000021", 20,
            ReforgeRegistry::isWeapon,
            List.of("§8「亡者呼吸凝在刃口。」",
                "§7+4 攻击伤害 · 击杀恢复 2 HP",
                "§a较稀有 §8- 灵魂结晶淬炼",
                "§7适用于：§f剑 / 斧 / 三叉戟"),
            Items.SOUL_SAND, Items.SOUL_LANTERN);

        // 血月怒火 —— 武器高级升级
        reg("blood_moon_fury", "血月怒火", Items.REDSTONE_BLOCK,
            "generic.attack_damage", 8.0, 0, "mainhand",
            "b1a00001-0000-0000-0000-000000000022", 35,
            ReforgeRegistry::isWeapon,
            List.of("§8「月圆之夜，愤怒喷涌而出。」",
                "§7+8 攻击伤害",
                "§c稀有 §8- 血月精华锻造",
                "§7适用于：§f剑 / 斧 / 三叉戟"),
            Items.REDSTONE_BLOCK, Items.REDSTONE);

        // 虚空穿透 —— 传说，无视护甲
        reg("void_pierce", "虚空穿透", Items.END_CRYSTAL,
            "generic.attack_damage", 12.0, 0, "mainhand",
            "b1a00001-0000-0000-0000-000000000023", 60,
            ReforgeRegistry::isWeapon,
            List.of("§8「虚空绕过一切阻挡，包括你的护甲。」",
                "§7+12 攻击伤害",
                "§7内在效果：命中无视 30% 目标护甲",
                "§5传奇 §8- 虚空之核凝聚",
                "§7适用于：§f剑 / 斧 / 三叉戟"),
            Items.END_CRYSTAL, Items.NETHERITE_INGOT);

        // 星尘之重 —— 防具专用，增加生命上限 + 击退抗性
        reg("stardust_weight", "星尘之重", Items.GLOWSTONE_DUST,
            "generic.max_health", 6.0, 0, "any_armor",
            "b1a00001-0000-0000-0000-000000000024", 28,
            ReforgeRegistry::isArmor,
            List.of("§8「陨石的重量让你更难被推动。」",
                "§7+6 最大生命",
                "§7附加效果：+15% 击退抗性",
                "§e稀有 §8- 星尘凝聚",
                "§7适用于：§f头盔 / 胸甲 / 护腿 / 靴子"),
            Items.GLOWSTONE_DUST, Items.NETHERITE_SCRAP);

        // ====================================================================
        //  横扫范围扩大系列（改横扫之刃的 3 格范围）
        //  在 SweepRangeMixin 里读 APPLIED_TAG 决定 tier
        //  amount=0 因为不加任何 vanilla attribute，只是标记位
        // ====================================================================

        // 横扫扩幅 —— 3×3
        reg("sweep_widener", "横扫扩幅", Items.IRON_SWORD,
            "generic.luck", 0.0, 0, "mainhand",
            "b1a00001-0000-0000-0000-000000000030", 20,
            ReforgeRegistry::isWeapon,
            List.of("§8「一剑挥出去，扫过一片。」",
                "§7横扫范围扩大到 §f3×3",
                "§7需要武器附带附魔 §f横扫之刃",
                "§e稀有 §8- 独立不成链",
                "§7适用于：§f剑 / 斧"),
            Items.IRON_SWORD, Items.IRON_BLOCK);

        // 横扫巨阵 —— 5×5
        reg("sweep_grand", "横扫巨阵", Items.NETHERITE_SWORD,
            "generic.luck", 0.0, 0, "mainhand",
            "b1a00001-0000-0000-0000-000000000031", 45,
            ReforgeRegistry::isWeapon,
            List.of("§8「与其挥剑，不如挥手。」",
                "§7横扫范围扩大到 §f5×5",
                "§7需要武器附带附魔 §f横扫之刃",
                "§5史诗 §8- 覆盖横扫扩幅",
                "§7适用于：§f剑 / 斧"),
            Items.NETHERITE_SWORD, Items.NETHERITE_BLOCK);

        // ====================================================================
        //  索敌突进重铸
        // ====================================================================

        // 索敌：自动突进到最近目标并造成一次攻击
        reg("target_rush", "掠食本能", Items.SPIDER_EYE,
            "generic.luck", 0.0, 0, "mainhand",
            "b1a00001-0000-0000-0000-000000000032", 30,
            ReforgeRegistry::isWeapon,
            List.of("§8「猎物呼吸的频率你早已记住。」",
                "§7挥刀时自动突进到 §f5 格内 §7最近的敌人",
                "§7并造成一次攻击",
                "§8不可穿墙 · 需要视线 · 无冷却",
                "§c稀有 §8- 近战专属",
                "§7适用于：§f剑 / 斧 / 三叉戟"),
            Items.SPIDER_EYE, Items.ENDER_PEARL);
    }

    public static List<ReforgeDef> all() { return Collections.unmodifiableList(ALL); }

    public static ReforgeDef byId(String id) {
        for (ReforgeDef d : ALL) if (d.id.equals(id)) return d;
        return null;
    }

    /** 生成重铸石 ItemStack。*/
    public static ItemStack buildStack(ReforgeDef def) {
        ItemStack stack = new ItemStack(def.icon);
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putString(NBT_KEY, def.id);
        // CustomModelData 用于资源包贴图识别
        int cmd = CustomModelDataRegistry.forReforge(def.id);
        if (cmd != 0) nbt.putInt("CustomModelData", cmd);
        stack.setCustomName(Text.literal("§6§l" + def.name + " §7[重铸石]").styled(s -> s.withItalic(false)));
        NbtCompound display = nbt.getCompound("display");
        NbtList lore = new NbtList();
        for (String line : def.lore) {
            lore.add(NbtString.of(Text.Serializer.toJson(
                Text.literal(line).styled(s -> s.withItalic(false)))));
        }
        lore.add(NbtString.of(Text.Serializer.toJson(
            Text.literal("§6重铸石 §8- 铁砧 + 装备使用").styled(s -> s.withItalic(false)))));
        lore.add(NbtString.of(Text.Serializer.toJson(
            Text.literal("§8铁砧费用 " + def.anvilCost + " 级").styled(s -> s.withItalic(false)))));
        display.put("Lore", lore);
        nbt.put("display", display);

        // 追加品质 + 获取途径
        com.tomkestookit.materials.ItemQualityTag.append(stack,
            reforgeQuality(def.id),
            reforgeSource(def.id));
        return stack;
    }

    /** 根据 reforge id 推断品质。*/
    private static com.tomkestookit.materials.ItemQualityTag.Quality reforgeQuality(String id) {
        // 传奇：宇宙精华、虚空穿透、横扫巨阵
        if ("cosmic_essence".equals(id) || "void_pierce".equals(id) || "sweep_grand".equals(id))
            return com.tomkestookit.materials.ItemQualityTag.Quality.LEGENDARY;
        // 史诗：泰坦之心、磐石板甲、经验汲取
        if ("titan_heart".equals(id) || "bedrock_bulwark".equals(id) || "soul_mend".equals(id))
            return com.tomkestookit.materials.ItemQualityTag.Quality.EPIC;
        // 稀有：狂战之核、泰坦铭板、炎爆晶体、龙鳞铭板、深海之力、血月怒火、魔源树皮、横扫扩幅
        if ("berserker_core".equals(id) || "titan_plate".equals(id)
            || "inferno_crystal".equals(id) || "dragon_scale_plate".equals(id)
            || "abyssal_might".equals(id) || "blood_moon_fury".equals(id)
            || "wardwood_bark".equals(id) || "stardust_weight".equals(id)
            || "sweep_widener".equals(id))
            return com.tomkestookit.materials.ItemQualityTag.Quality.RARE;
        // 优良：钻石壳、天锻符文、灵魂锋刃、反重力核心
        if ("diamond_shell".equals(id) || "skyforge_rune".equals(id)
            || "soul_edge".equals(id) || "antigrav_boots".equals(id))
            return com.tomkestookit.materials.ItemQualityTag.Quality.UNCOMMON;
        // 其他：普通
        return com.tomkestookit.materials.ItemQualityTag.Quality.COMMON;
    }

    /** 根据 reforge id 描述获取途径。*/
    private static String reforgeSource(String id) {
        // 传奇重铸石
        if ("cosmic_essence".equals(id) || "titan_heart".equals(id)
            || "bedrock_bulwark".equals(id) || "dragon_scale_plate".equals(id))
            return "3×3 合成 §8· §7需要稀有材料";
        return "3×3 合成 §8· §7铁砧敲装备生效";
    }

    /** 从 ItemStack 读取重铸石 id。非重铸石返回 null。*/
    public static String getId(ItemStack stack) {
        if (stack.isEmpty()) return null;
        NbtCompound nbt = stack.getNbt();
        if (nbt == null || !nbt.contains(NBT_KEY)) return null;
        return nbt.getString(NBT_KEY);
    }

    /**
     * 检查装备上是否已有某块重铸石。
     * 优先看 APPLIED_TAG（id 字符串），命中直接判定已应用；
     * 再回退到 UUID 检测（同时也检查 per-slot 派生 UUID）。
     */
    public static boolean alreadyApplied(ItemStack equipment, ReforgeDef def) {
        NbtCompound nbt = equipment.getNbt();
        if (nbt == null) return false;
        // 优先：applied id 列表
        if (nbt.contains(APPLIED_TAG)) {
            NbtList applied = nbt.getList(APPLIED_TAG, 8);
            for (int i = 0; i < applied.size(); i++) {
                if (def.id.equals(applied.getString(i))) return true;
            }
        }
        // 回退：UUID（涵盖旧数据 + per-slot 派生 UUID）
        if (!nbt.contains("AttributeModifiers")) return false;
        NbtList attrs = nbt.getList("AttributeModifiers", 10);
        String slotStr = resolveSlot(def, equipment);
        UUID slotUuid = perSlotUuid(def, slotStr);
        for (int i = 0; i < attrs.size(); i++) {
            NbtCompound m = attrs.getCompound(i);
            if (m.contains("UUID")) {
                try {
                    UUID existing = NbtHelper.toUuid(m.get("UUID"));
                    if (def.uuid.equals(existing) || slotUuid.equals(existing)) return true;
                } catch (Exception ignored) { /* 非标准 UUID 格式，跳过 */ }
            }
        }
        return false;
    }

    /**
     * 派生 per-slot UUID：对 any_armor 类重铸石，让不同 slot 得到不同 UUID，
     * 使得同一 def 可以叠加在多件护甲上（修复泰坦铭板等只能生效一次的 BUG）。
     * 对 mainhand / offhand / feet 等固定 slot 定义，返回原始 UUID 保持兼容。
     */
    private static UUID perSlotUuid(ReforgeDef def, String slotStr) {
        if (!"any_armor".equals(def.slot) && !"any".equals(def.slot)) {
            return def.uuid;
        }
        // 只对 4 件护甲 slot 派生；其他情况返回原 UUID
        return switch (slotStr) {
            case "head"  -> deriveUuid(def.uuid, 1);
            case "chest" -> deriveUuid(def.uuid, 2);
            case "legs"  -> deriveUuid(def.uuid, 3);
            case "feet"  -> deriveUuid(def.uuid, 4);
            default      -> def.uuid;
        };
    }

    /** 从 base UUID 派生一个稳定的变体（异或 low 64 位的一个盐）。*/
    private static UUID deriveUuid(UUID base, int salt) {
        return new UUID(base.getMostSignificantBits(), base.getLeastSignificantBits() ^ ((long) salt * 0x9E3779B97F4A7C15L));
    }

    /** 把重铸石属性追加到装备的 AttributeModifiers。*/
    public static ItemStack applyReforge(ItemStack equipment, ReforgeDef def) {
        ItemStack result = equipment.copy();
        NbtCompound nbt = result.getOrCreateNbt();

        // 如果装备还没有 NBT AttributeModifiers，先把 vanilla 默认属性复制进去
        if (!nbt.contains("AttributeModifiers")) {
            NbtList defaultAttrs = buildVanillaDefaults(equipment);
            nbt.put("AttributeModifiers", defaultAttrs);
        }

        NbtList attrs = nbt.getList("AttributeModifiers", 10);

        // 确定 slot 字符串
        String slotStr = resolveSlot(def, equipment);

        // 对 any_armor 类重铸石，把 UUID 按 slot 派生成不同值，
        // 避免同一 def 在多件装备上因 UUID 相同而被 Minecraft 去重（叠加修复）。
        UUID modifierUuid = perSlotUuid(def, slotStr);

        NbtCompound modifier = new NbtCompound();
        modifier.putString("AttributeName", def.attribute);
        modifier.putString("Name", "tomkestookit:reforge_" + def.id);
        modifier.putDouble("Amount", def.amount);
        modifier.putInt("Operation", def.operation);
        modifier.putString("Slot", slotStr);
        modifier.put("UUID", NbtHelper.fromUuid(modifierUuid));
        attrs.add(modifier);

        nbt.put("AttributeModifiers", attrs);
        // 记录已应用的重铸到一个标记列表（给 lore 展示用）
        NbtList applied = nbt.contains(APPLIED_TAG) ? nbt.getList(APPLIED_TAG, 8) : new NbtList();
        applied.add(NbtString.of(def.id));
        nbt.put(APPLIED_TAG, applied);

        // 追加 lore
        NbtCompound display = nbt.getCompound("display");
        NbtList lore = display.contains("Lore") ? display.getList("Lore", 8) : new NbtList();
        lore.add(NbtString.of(Text.Serializer.toJson(
            Text.literal("§6⚒ " + def.name).styled(s -> s.withItalic(false)))));
        display.put("Lore", lore);
        nbt.put("display", display);

        return result;
    }

    // ---- helpers ----

    private static void reg(String id, String name, Item icon,
                            String attribute, double amount, int operation, String slot,
                            String uuid, int anvilCost,
                            Predicate<ItemStack> canApply, List<String> lore,
                            Item recipeCenter, Item recipeSurround) {
        ALL.add(new ReforgeDef(id, name, icon, attribute, amount, operation, slot,
            UUID.fromString(uuid), anvilCost, canApply, lore,
            recipeCenter, recipeSurround));
    }

    /** 匹配 3x3 输入是否为某个重铸石配方。命中返回 def，否则 null。*/
    public static ReforgeDef match3x3(net.minecraft.inventory.RecipeInputInventory inv) {
        if (inv.getWidth() != 3 || inv.getHeight() != 3) return null;
        for (ReforgeDef def : ALL) {
            if (matches(inv, def)) return def;
        }
        return null;
    }

    private static boolean matches(net.minecraft.inventory.RecipeInputInventory inv, ReforgeDef def) {
        ItemStack center = inv.getStack(4);
        if (center.isEmpty()) return false;
        // 排除已经是重铸石的（避免用一个重铸石当中心去合另一个）
        if (getId(center) != null) return false;

        // 中心：vanilla Item 匹配
        if (center.getItem() != def.recipeCenter) return false;

        // 外围 8 格：vanilla Item 匹配
        int[] surround = {0, 1, 2, 3, 5, 6, 7, 8};
        for (int i : surround) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty() || s.getItem() != def.recipeSurround) return false;
        }
        return true;
    }

    public static boolean isWeapon(ItemStack stack) {
        return stack.getItem() instanceof SwordItem
            || stack.getItem() instanceof AxeItem
            || stack.getItem() instanceof TridentItem;
    }

    public static boolean isArmor(ItemStack stack) {
        return stack.getItem() instanceof ArmorItem;
    }

    public static boolean isTool(ItemStack stack) {
        return stack.getItem() instanceof ToolItem;
    }

    private static String resolveSlot(ReforgeDef def, ItemStack equipment) {
        if ("any_armor".equals(def.slot) || "any".equals(def.slot)) {
            if (equipment.getItem() instanceof ArmorItem armor) {
                return switch (armor.getSlotType()) {
                    case HEAD -> "head";
                    case CHEST -> "chest";
                    case LEGS -> "legs";
                    case FEET -> "feet";
                    default -> "chest";
                };
            }
            // "any" 允许非防具，走 mainhand
            if ("any".equals(def.slot)) return "mainhand";
            return "chest";
        }
        return def.slot;
    }

    /** 把装备 vanilla 默认属性序列化为 NbtList（防止 NBT 覆盖后丢默认护甲等）。*/
    private static NbtList buildVanillaDefaults(ItemStack stack) {
        NbtList list = new NbtList();
        EquipmentSlot slot;
        String slotStr;
        if (stack.getItem() instanceof ArmorItem armor) {
            slot = armor.getSlotType();
            slotStr = switch (slot) {
                case HEAD -> "head"; case CHEST -> "chest";
                case LEGS -> "legs"; case FEET -> "feet"; default -> "chest";
            };
        } else {
            // 武器 / 工具 / 三叉戟 / mod 添加的手持物：统一走主手
            // 覆盖所有非 ToolItem 但仍带默认属性的物品（如 TridentItem）
            slot = EquipmentSlot.MAINHAND;
            slotStr = "mainhand";
        }
        var map = stack.getItem().getAttributeModifiers(slot);
        for (var entry : map.entries()) {
            NbtCompound m = new NbtCompound();
            m.putString("AttributeName",
                net.minecraft.registry.Registries.ATTRIBUTE.getId(entry.getKey()).toString());
            m.putString("Name", entry.getValue().getName());
            m.putDouble("Amount", entry.getValue().getValue());
            m.putInt("Operation", entry.getValue().getOperation().getId());
            m.putString("Slot", slotStr);
            m.put("UUID", NbtHelper.fromUuid(entry.getValue().getId()));
            list.add(m);
        }
        return list;
    }
}
