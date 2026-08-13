package com.tomkestookit.trinket;

import com.tomkestookit.TomkesTookitMod;
import com.tomkestookit.materials.CustomModelDataRegistry;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;

import java.util.*;
import java.util.function.Consumer;

/**
 * 饰品注册表：8 种饰品的定义、合成判定、物品生成。
 */
public final class TrinketRegistry {

    public static final String NBT_KEY = "TomkesTookitTrinket";
    /** NBT 布尔：为 true 时该饰品被玩家手动禁用，applier 不触发。*/
    public static final String NBT_DISABLED = "TomkesTookitTrinketDisabled";

    public record TrinketDef(
        String id, String name, Item icon, Item center, Item surround,
        List<String> lore, Consumer<ServerPlayerEntity> applier,
        String prereqTrinketId // null = 普通饰品，非 null = 需要该 id 的饰品作为中心
    ) {}

    private static final List<TrinketDef> ALL = new ArrayList<>();

    static {
        reg("caffeine_patch", "咖啡因贴片", Items.SUGAR, Items.SPIDER_EYE, Items.SUGAR,
            List.of("§8「让加班这件事变得可以持续。」", "§7常驻 速度 I + 急迫 I"),
            p -> { eff(p, StatusEffects.SPEED, 0); eff(p, StatusEffects.HASTE, 0); });

        reg("lodestar_pin", "磁石胸针", Items.IRON_NUGGET, Items.LODESTONE, Items.IRON_NUGGET,
            List.of("§8「别人捡漏，你捡命。」", "§75 格内掉落物自动吸附"),
            TrinketRegistry::applyLodestar);

        reg("nightwing_charm", "幽夜之翼", Items.PHANTOM_MEMBRANE, Items.SPIDER_EYE, Items.PHANTOM_MEMBRANE,
            List.of("§8「幻翼死后没进小卖部，去了眼镜店。」", "§7常驻 夜视 + 缓降"),
            p -> { effLong(p, StatusEffects.NIGHT_VISION, 0); eff(p, StatusEffects.SLOW_FALLING, 0); });

        reg("turtle_ward", "龟壳护身符", Items.SCUTE, Items.IRON_INGOT, Items.SCUTE,
            List.of("§8「快撑不住时才叫救援。」", "§7HP<6 时获 4s 抗性 II（60s 冷却）"),
            TrinketRegistry::applyTurtleWard);

        reg("cats_eye", "猫瞳挂饰", Items.RABBIT_HIDE, Items.RABBIT_FOOT, Items.RABBIT_HIDE,
            List.of("§8「第九条命还剩几条，猫自己都数不清。」", "§7摔落伤害 = 0"),
            p -> {}); // 摔落在 TrinketEffectHandler 的 ALLOW_DAMAGE 里处理

        reg("ember_sigil", "火焰印记", Items.MAGMA_CREAM, Items.BLAZE_POWDER, Items.MAGMA_CREAM,
            List.of("§8「下次你不小心洗澡洗到岩浆里，就懂了。」", "§7常驻 防火"),
            p -> eff(p, StatusEffects.FIRE_RESISTANCE, 0));

        reg("iron_arm", "铁血臂环", Items.IRON_INGOT, Items.WITHER_SKELETON_SKULL, Items.IRON_INGOT,
            List.of("§8「肌肉记忆不够，就要靠外挂。」", "§7常驻 力量 I"),
            p -> eff(p, StatusEffects.STRENGTH, 0));

        reg("larder_glass", "饱食沙漏", Items.COOKED_BEEF, Items.GOLDEN_APPLE, Items.COOKED_BEEF,
            List.of("§8「胃在旋转，饥饿在原地。」", "§7每 5 秒饱食度 +1"),
            TrinketRegistry::applyLarder);

        // ---- 第二批：生存实用 ----

        reg("ender_anchor", "末影锚", Items.ENDER_PEARL, Items.CRYING_OBSIDIAN, Items.ENDER_PEARL,
            List.of("§8「死后最值钱的不是装备，是你的脚印。」",
                "§7死亡时自动保留经验（不掉落）"),
            p -> {}); // 死亡保留经验在 TrinketEffectHandler 里 hook

        reg("soul_lantern_charm", "灵魂灯笼", Items.SOUL_SAND, Items.LANTERN, Items.SOUL_SAND,
            List.of("§8「灵魂是天然的驱虫剂。」",
                "§7怪物在 8 格内不会主动攻击你",
                "§8除非你先动手"),
            p -> {}); // 仇恨清除在 TrinketEffectHandler tick 里处理

        reg("aqua_lung", "深海肺囊", Items.PRISMARINE_SHARD, Items.PUFFERFISH, Items.PRISMARINE_SHARD,
            List.of("§8「河豚看了看你，决定分享一点天赋。」",
                "§7常驻 水下呼吸 III + 水下速掘"),
            p -> { eff(p, StatusEffects.WATER_BREATHING, 2); eff(p, StatusEffects.CONDUIT_POWER, 0); });

        reg("greenthumb_ring", "翠指之环", Items.BONE_MEAL, Items.DIAMOND, Items.BONE_MEAL,
            List.of("§8「拇指绿到发光的人才能戴。」",
                "§7周围 3 格作物随机加速生长",
                "§8每秒随机催熟一次"),
            TrinketRegistry::applyGreenthumb);

        reg("void_pearl", "虚空珍珠", Items.OBSIDIAN, Items.ENDER_EYE, Items.OBSIDIAN,
            List.of("§8「跌入虚空的人说，最后看到了珍珠的光。」",
                "§7掉入虚空时自动传送回上次站的地面",
                "§8不掉经验、不计死亡"),
            TrinketRegistry::applyVoidSave);

        reg("prospector_lens", "探矿镜", Items.RAW_COPPER, Items.SPYGLASS, Items.RAW_COPPER,
            List.of("§8「地质学家看世界的方式和你不同。」",
                "§7挖掘时 10% 概率额外掉一份矿物",
                "§8仅对矿石类方块生效"),
            p -> {}); // 挖矿加成在 TrinketEffectHandler 的 block break hook 中

        reg("totem_splinter", "图腾碎片", Items.EMERALD, Items.TOTEM_OF_UNDYING, Items.EMERALD,
            List.of("§8「把不死鸟的一根羽毛缝进口袋。」",
                "§7死亡时触发一次弱化图腾效果",
                "§8保留 1 HP + 5s 抗性 I + 10s 恢复 II",
                "§8每 20 分钟冷却一次"),
            p -> {}); // 死亡拦截在 TrinketEffectHandler hook 中

        // ---- 进阶饰品（需要前置饰品作为合成中心）----

        regAdvanced("blood_fury", "血怒手环", Items.SPIDER_EYE, "iron_arm",
            List.of("§8「疼痛是自由的另一种语言。」",
                "§7常驻 力量 II + 攻速 +0.1",
                "§c进阶 §8← 铁血臂环"),
            p -> { eff(p, StatusEffects.STRENGTH, 1); });

        regAdvanced("aurora_pendant", "极光挂坠", Items.GLOWSTONE_DUST, "nightwing_charm",
            List.of("§8「北极星把最后一滴光留给你。」",
                "§7常驻 夜视 + 生命恢复 II",
                "§c进阶 §8← 幽夜之翼"),
            p -> { effLong(p, StatusEffects.NIGHT_VISION, 0);
                   eff(p, StatusEffects.REGENERATION, 1); });

        regAdvanced("insight_prism", "洞察棱镜", Items.ENDER_EYE, "prospector_lens",
            List.of("§8「地质学家现在能看见石头里的贪婪。」",
                "§7挖矿 25% 额外掉落 + 常驻 幸运 II",
                "§c进阶 §8← 探矿镜"),
            p -> eff(p, StatusEffects.LUCK, 1));

        // ---- 挖掘专精系列（急迫 I→II→III 递进）----

        reg("haste_charm_1", "矿工手套", Items.LEATHER, Items.IRON_PICKAXE, Items.RAW_IRON,
            List.of("§8「师傅说，好矿工从一双手开始。」",
                "§7常驻 急迫 I",
                "§a入门 §8· 挖掘专精 I/III"),
            p -> eff(p, StatusEffects.HASTE, 0));

        regAdvanced("haste_charm_2", "矿工护腕", Items.DIAMOND, "haste_charm_1",
            List.of("§8「铁镐磨秃了，护腕还在。」",
                "§7常驻 急迫 II",
                "§b进阶 §8← 矿工手套 §8· 挖掘专精 II/III"),
            p -> eff(p, StatusEffects.HASTE, 1));

        regAdvanced("haste_charm_3", "挖掘专精", Items.NETHERITE_SCRAP, "haste_charm_2",
            List.of("§8「你不再挖矿——你是矿。」",
                "§7常驻 急迫 III",
                "§6大师 §8← 矿工护腕 §8· 挖掘专精 III/III"),
            p -> eff(p, StatusEffects.HASTE, 2));

        // ---- 特殊环境系列 ----

        reg("magma_strider", "熔岩行者", Items.MAGMA_CREAM, Items.NETHERITE_INGOT, Items.MAGMA_CREAM,
            List.of("§8「下界人管这叫'泡温泉'。」",
                "§7取消岩浆速度惩罚（正常走路速度）",
                "§7在岩浆中常驻 §c防火",
                "§e像炽足兽一样在岩浆里冲浪"),
            TrinketRegistry::applyMagmaStrider);

        // ====================================================================
        //  升级链系列（材料强度与效果成正比）
        // ====================================================================

        // ---- 速度/敏捷系：咖啡因贴片 → 肾上腺素注射 → 时间裂隙 ----

        regAdvanced("adrenaline_injector", "肾上腺素注射", Items.GLOWSTONE_DUST, "caffeine_patch",
            List.of("§8「咖啡因不够用了，直接上针管。」",
                "§7常驻 速度 II + 急迫 II + 跳跃 I",
                "§c进阶 §8← 咖啡因贴片 §8· 敏捷 II/III"),
            p -> { eff(p, StatusEffects.SPEED, 1); eff(p, StatusEffects.HASTE, 1);
                   eff(p, StatusEffects.JUMP_BOOST, 0); });

        regAdvanced("time_dilation_clock", "时间裂隙", Items.AMETHYST_SHARD, "adrenaline_injector",
            List.of("§8「你开始看到世界一帧一帧地走。」",
                "§7常驻 速度 III + 急迫 III + 跳跃 II",
                "§6大师 §8← 肾上腺素注射 §8· 敏捷 III/III"),
            p -> { eff(p, StatusEffects.SPEED, 2); eff(p, StatusEffects.HASTE, 2);
                   eff(p, StatusEffects.JUMP_BOOST, 1); });

        // ---- 战斗/力量系：铁血臂环 → 血怒手环 → 战争领主 ----

        reg("iron_arm", "铁血臂环", Items.IRON_INGOT, Items.WITHER_SKELETON_SKULL, Items.IRON_INGOT,
            List.of("§8「肌肉记忆不够，就要靠外挂。」", "§7常驻 力量 I"),
            p -> eff(p, StatusEffects.STRENGTH, 0));

        regAdvanced("blood_fury", "血怒手环", Items.SPIDER_EYE, "iron_arm",
            List.of("§8「疼痛是自由的另一种语言。」",
                "§7常驻 力量 II + 攻速 +0.1",
                "§c进阶 §8← 铁血臂环"),
            p -> { eff(p, StatusEffects.STRENGTH, 1); });

        regAdvanced("warlord_crown", "战争领主", Items.NETHERITE_INGOT, "blood_fury",
            List.of("§8「一个能打的都没有——现在有了。」",
                "§7常驻 力量 III + 抗性 I + 速度 I",
                "§6大师 §8← 血怒手环 §8· 战斗系巅峰"),
            p -> { eff(p, StatusEffects.STRENGTH, 2); eff(p, StatusEffects.RESISTANCE, 0);
                   eff(p, StatusEffects.SPEED, 0); });

        // ---- 海洋系：深海肺囊 → 唤潮者之壳 → 深渊之心 ----

        regAdvanced("tide_caller_shell", "唤潮者之壳", Items.HEART_OF_THE_SEA, "aqua_lung",
            List.of("§8「打雷下雨收衣服，在海里反而更安全。」",
                "§7常驻 水下呼吸 + 海豚恩惠 + 潮涌能量",
                "§c进阶 §8← 深海肺囊 §8· 海洋 II/III"),
            p -> { eff(p, StatusEffects.WATER_BREATHING, 2);
                   eff(p, StatusEffects.DOLPHINS_GRACE, 0); eff(p, StatusEffects.CONDUIT_POWER, 0); });

        regAdvanced("abyssal_heart", "深渊之心", Items.NETHERITE_SCRAP, "tide_caller_shell",
            List.of("§8「水压大到能把钻石压碎，但你完好无损。」",
                "§7常驻 水下呼吸 V + 潮涌 + 海豚恩惠",
                "§7水下获得 速度 II + 力量 I",
                "§6大师 §8← 唤潮者之壳 §8· 海洋 III/III"),
            p -> { eff(p, StatusEffects.WATER_BREATHING, 4); eff(p, StatusEffects.CONDUIT_POWER, 0);
                   eff(p, StatusEffects.DOLPHINS_GRACE, 0);
                   if (p.isSubmergedInWater()) {
                       eff(p, StatusEffects.SPEED, 1); eff(p, StatusEffects.STRENGTH, 0);
                   }});

        // ---- 防护/保命系：龟壳护身符 → 凤凰之羽 → 不朽壁垒 ----

        regAdvanced("phoenix_feather", "凤凰之羽", Items.BLAZE_ROD, "turtle_ward",
            List.of("§8「龟壳在火里待太久变成灰，灰里有凤凰。」",
                "§7HP<10 时获 6s 恢复 III + 吸收 II（30s 冷却）",
                "§c进阶 §8← 龟壳护身符"),
            p -> {}); // 特殊逻辑在 applyTurtleWard 里也检查此 id

        regAdvanced("last_stand_bulwark", "不朽壁垒", Items.TOTEM_OF_UNDYING, "phoenix_feather",
            List.of("§8「死在战场上是荣誉，但在最后堡垒里死不掉才是真理。」",
                "§7HP<14 时获 6s 抗性 III + 恢复 IV + 吸收 III（30s 冷却）",
                "§7满血后额外 3s 抗性 I",
                "§6大师 §8← 凤凰之羽 §8· 防护系巅峰"),
            p -> {});

        // ---- 磁力系：磁石胸针 → 引力井 → 星界枢纽 ----

        regAdvanced("gravity_well", "引力井", Items.IRON_BLOCK, "lodestar_pin",
            List.of("§8「掉落的不是你，是整个世界在往你靠。」",
                "§710 格内掉落物自动吸附 + 铁/金掉物自动烧炼",
                "§c进阶 §8← 磁石胸针 §8· 磁力 II/III"),
            TrinketRegistry::applyLodestar); // 复用但范围在 applyLodestar 里根据 id 决定

        regAdvanced("astral_hub", "星界枢纽", Items.NETHER_STAR, "gravity_well",
            List.of("§8「物品不再掉落——它们学会了传送。」",
                "§715 格掉落物自动吸附 + 自动烧炼 + 经验球吸附",
                "§6大师 §8← 引力井 §8· 磁力 III/III"),
            TrinketRegistry::applyLodestar);

        // ---- 保命/不死系：图腾碎片 → 图腾之心 ----

        regAdvanced("totem_heart", "图腾之心", Items.NETHERITE_SCRAP, "totem_splinter",
            List.of("§8「你开始相信，死亡只是另一个传送点。」",
                "§7死亡时保留 50% HP + 抗性 II 8s + 恢复 II 10s",
                "§8每 15 分钟冷却一次（原 20 分钟）",
                "§c进阶 §8← 图腾碎片"),
            p -> {});

        // ---- 经验倍率系：卷轴 → 典籍 → 冠冕 ----

        reg("xp_scroll", "经验卷轴", Items.PAPER, Items.EXPERIENCE_BOTTLE, Items.PAPER,
            List.of("§8「记事本升级成了记忆本。」",
                "§7获取经验 §a×5",
                "§a基础 §8· 经验 I/III"),
            p -> {});

        regAdvanced("xp_tome", "经验典籍", Items.BOOK, "xp_scroll",
            List.of("§8「翻一页就是一学期。」",
                "§7获取经验 §a×20",
                "§c进阶 §8← 经验卷轴 §8· 经验 II/III"),
            p -> {});

        regAdvanced("xp_crown", "经验冠冕", Items.NETHERITE_INGOT, "xp_tome",
            List.of("§8「戴上它的人，不再计较学费。」",
                "§7获取经验 §a×100",
                "§6大师 §8← 经验典籍 §8· 经验 III/III"),
            p -> {});

        // ---- 独立传说级（不需要前置，但材料昂贵）----

        reg("dragon_heart", "龙心护符", Items.DRAGON_HEAD, Items.DRAGON_EGG, Items.DRAGON_BREATH,
            List.of("§8「末地最黑暗的秘密，封印于此。」",
                "§7常驻 力量 I + 抗性 I + 恢复 I + 吸收 I",
                "§5传奇 §8· 不需要升级，本身即巅峰"),
            p -> { eff(p, StatusEffects.STRENGTH, 0); eff(p, StatusEffects.RESISTANCE, 0);
                   eff(p, StatusEffects.REGENERATION, 0); eff(p, StatusEffects.ABSORPTION, 0); });

        // ====================================================================
        //  怪物专属素材系列饰品（v2 新增）
        // ====================================================================

        reg("abyssal_necklace", "深渊项链", Items.PRISMARINE_CRYSTALS, Items.HEART_OF_THE_SEA, Items.PRISMARINE_SHARD,
            List.of("§8「把深海的压力变成你的铠甲。」",
                "§7常驻 抗性 I + 潮涌能量",
                "§7水下额外获得 力量 I",
                "§b稀有 §8· 深邃碎片合成"),
            p -> { eff(p, StatusEffects.RESISTANCE, 0); eff(p, StatusEffects.CONDUIT_POWER, 0);
                   if (p.isSubmergedInWater()) eff(p, StatusEffects.STRENGTH, 0); });

        reg("soul_crystal_pendant", "灵魂水晶坠", Items.SOUL_LANTERN, Items.AMETHYST_SHARD, Items.SOUL_SAND,
            List.of("§8「灵魂在此安息——也在此反击。」",
                "§7击杀怪物 +3 额外经验球",
                "§7常驻 夜视",
                "§a较稀有 §8· 灵魂结晶合成"),
            p -> { effLong(p, StatusEffects.NIGHT_VISION, 0); });

        reg("stardust_compass", "星尘罗盘", Items.COMPASS, Items.NETHER_STAR, Items.GLOWSTONE_DUST,
            List.of("§8「宇宙尘指着回家的路——顺便给你幸运。」",
                "§7常驻 幸运 II + 速度 I",
                "§7掉落物增加 10%（影响 loot table）",
                "§e稀有 §8· 星尘合成"),
            p -> { eff(p, StatusEffects.LUCK, 1); eff(p, StatusEffects.SPEED, 0); });

        reg("blood_moon_amulet", "血月护符", Items.REDSTONE, Items.ENCHANTED_GOLDEN_APPLE, Items.REDSTONE_BLOCK,
            List.of("§8「血月升起时，受伤的人反而更强。」",
                "§7HP < 50% 时获得 力量 II + 速度 II",
                "§7常驻 恢复 I",
                "§c稀有 §8· 血月精华合成"),
            p -> { eff(p, StatusEffects.REGENERATION, 0);
                   if (p.getHealth() < p.getMaxHealth() * 0.5f) {
                       eff(p, StatusEffects.STRENGTH, 1); eff(p, StatusEffects.SPEED, 1);
                   }});

        reg("void_heart_trinket", "虚空之心", Items.ENDER_EYE, Items.END_CRYSTAL, Items.OBSIDIAN,
            List.of("§8「虚空既是深渊，也是港湾。」",
                "§7受到致命伤害时传送到安全位置（120s CD）",
                "§7常驻 抗性 I + 吸收 II",
                "§5传奇 §8· 虚空之核合成"),
            p -> { eff(p, StatusEffects.RESISTANCE, 0); eff(p, StatusEffects.ABSORPTION, 1); });
    }

    public static List<TrinketDef> all() { return Collections.unmodifiableList(ALL); }

    public static TrinketDef byId(String id) {
        for (TrinketDef d : ALL) if (d.id.equals(id)) return d;
        return null;
    }

    /** 生成一个饰品 ItemStack（附魔光效 + 自定义名/lore）。*/
    public static ItemStack buildStack(TrinketDef def) {
        ItemStack stack = new ItemStack(def.icon);
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putString(NBT_KEY, def.id);
        // CustomModelData 用于资源包贴图识别
        int cmd = CustomModelDataRegistry.forTrinket(def.id);
        if (cmd != 0) nbt.putInt("CustomModelData", cmd);
        stack.setCustomName(Text.literal("§d§l" + def.name).styled(s -> s.withItalic(false)));

        // 附魔光效（假附魔 + 隐藏 tooltip），饰品颜色主题从 §d（粉）换成 §b（青）
        NbtList enchList = new NbtList();
        NbtCompound fakeEnch = new NbtCompound();
        fakeEnch.putString("id",
            net.minecraft.registry.Registries.ENCHANTMENT.getId(
                net.minecraft.enchantment.Enchantments.UNBREAKING).toString());
        fakeEnch.putInt("lvl", 1);
        enchList.add(fakeEnch);
        nbt.put("Enchantments", enchList);
        nbt.putInt("HideFlags", 1);

        NbtCompound display = nbt.getCompound("display");
        NbtList lore = new NbtList();
        for (String line : def.lore) {
            lore.add(NbtString.of(Text.Serializer.toJson(
                Text.literal(line).styled(s -> s.withItalic(false)))));
        }
        lore.add(NbtString.of(Text.Serializer.toJson(
            Text.literal("§5饰品 §8- 放入饰品袋生效").styled(s -> s.withItalic(false)))));
        display.put("Lore", lore);
        nbt.put("display", display);

        // 追加品质 + 获取途径
        com.tomkestookit.materials.ItemQualityTag.append(stack,
            trinketQuality(def),
            trinketSource(def));
        return stack;
    }

    /** 根据 trinket 类型推断品质：大师>进阶>基础。*/
    private static com.tomkestookit.materials.ItemQualityTag.Quality trinketQuality(TrinketDef def) {
        String id = def.id;
        // 大师系列（进阶链末端）→ 传奇
        if ("time_dilation_clock".equals(id) || "warlord_crown".equals(id)
            || "abyssal_heart".equals(id) || "last_stand_bulwark".equals(id)
            || "astral_hub".equals(id) || "haste_charm_3".equals(id)
            || "xp_crown".equals(id))
            return com.tomkestookit.materials.ItemQualityTag.Quality.LEGENDARY;
        // 进阶（有前置饰品）→ 史诗
        if (def.prereqTrinketId != null)
            return com.tomkestookit.materials.ItemQualityTag.Quality.EPIC;
        // 图腾碎片、虚空珍珠、末影锚等保命/特殊 → 稀有
        if ("totem_splinter".equals(id) || "void_pearl".equals(id)
            || "ender_anchor".equals(id) || "xp_scroll".equals(id)
            || "magma_strider".equals(id) || "aqua_lung".equals(id))
            return com.tomkestookit.materials.ItemQualityTag.Quality.RARE;
        // 基础饰品 → 优良
        return com.tomkestookit.materials.ItemQualityTag.Quality.UNCOMMON;
    }

    /** 获取途径描述。*/
    private static String trinketSource(TrinketDef def) {
        if (def.prereqTrinketId != null) {
            TrinketDef prereq = byId(def.prereqTrinketId);
            String prereqName = prereq != null ? prereq.name : def.prereqTrinketId;
            return "3×3 合成 §8· §7中心放 §f" + prereqName + " §7升级而得";
        }
        return "3×3 合成 §8· §7放入饰品袋生效";
    }

    /** 从 ItemStack 读取 trinket id，非饰品返回 null。*/
    public static String getId(ItemStack stack) {
        if (stack.isEmpty()) return null;
        NbtCompound nbt = stack.getNbt();
        if (nbt == null || !nbt.contains(NBT_KEY)) return null;
        return nbt.getString(NBT_KEY);
    }

    /** 该饰品是否被玩家禁用（TrinketBag 菜单里右键切换）。*/
    public static boolean isDisabled(ItemStack stack) {
        if (stack.isEmpty()) return false;
        NbtCompound nbt = stack.getNbt();
        return nbt != null && nbt.getBoolean(NBT_DISABLED);
    }

    /**
     * 切换饰品启用/禁用状态。同时刷新 lore 与显示名（前缀带 §7[已禁用]）。
     * @return 切换后是否为"禁用"状态
     */
    public static boolean toggleDisabled(ItemStack stack) {
        if (stack.isEmpty() || getId(stack) == null) return false;
        NbtCompound nbt = stack.getOrCreateNbt();
        boolean newState = !nbt.getBoolean(NBT_DISABLED);
        nbt.putBoolean(NBT_DISABLED, newState);
        refreshDisplay(stack);
        return newState;
    }

    /** 根据禁用状态重建显示名与 lore（配 buildStack 保持一致）。*/
    private static void refreshDisplay(ItemStack stack) {
        String id = getId(stack);
        TrinketDef def = byId(id);
        if (def == null) return;
        boolean disabled = isDisabled(stack);
        NbtCompound nbt = stack.getOrCreateNbt();

        String nameStr = disabled
            ? "§7§m" + def.name + "§r §8[已禁用]"
            : "§d§l" + def.name;
        stack.setCustomName(Text.literal(nameStr).styled(s -> s.withItalic(false)));

        NbtCompound display = nbt.getCompound("display");
        NbtList lore = new NbtList();
        for (String line : def.lore) {
            lore.add(NbtString.of(Text.Serializer.toJson(
                Text.literal(line).styled(s -> s.withItalic(false)))));
        }
        lore.add(NbtString.of(Text.Serializer.toJson(
            Text.literal("§5饰品 §8- 放入饰品袋生效").styled(s -> s.withItalic(false)))));
        lore.add(NbtString.of(Text.Serializer.toJson(
            Text.literal(disabled ? "§c§l✘ 已禁用 §8- 菜单内右键启用" : "§a§l✓ 已启用 §8- 菜单内右键禁用")
                .styled(s -> s.withItalic(false)))));
        display.put("Lore", lore);
        nbt.put("display", display);
    }

    /** 匹配 3x3 输入是否为某个饰品配方。命中返回 def，否则 null。*/
    public static TrinketDef match3x3(net.minecraft.inventory.RecipeInputInventory inv) {
        if (inv.getWidth() != 3 || inv.getHeight() != 3) return null;
        for (TrinketDef def : ALL) {
            if (matches(inv, def)) return def;
        }
        return null;
    }

    private static boolean matches(net.minecraft.inventory.RecipeInputInventory inv, TrinketDef def) {
        ItemStack center = inv.getStack(4);
        if (center.isEmpty()) return false;

        if (def.prereqTrinketId() != null) {
            // 进阶饰品：中心必须是指定 id 的已有饰品 ItemStack
            String centerId = getId(center);
            if (!def.prereqTrinketId().equals(centerId)) return false;
        } else {
            // 普通饰品：中心必须是指定的原版物品
            if (def.center() == null || center.getItem() != def.center()) return false;
        }

        int[] surround = {0, 1, 2, 3, 5, 6, 7, 8};
        for (int i : surround) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty() || s.getItem() != def.surround()) return false;
        }
        return true;
    }

    private static void reg(String id, String name, Item surround, Item center, Item surroundSame,
                            List<String> lore, Consumer<ServerPlayerEntity> applier) {
        ALL.add(new TrinketDef(id, name, surround, center, surround, lore, applier, null));
    }

    private static void regAdvanced(String id, String name, Item surround, String prereqId,
                                    List<String> lore, Consumer<ServerPlayerEntity> applier) {
        // 进阶饰品：center 用 surround 的 icon 占位（实际判定看 prereqId），icon 用 surround
        ALL.add(new TrinketDef(id, name, surround, null, surround, lore, applier, prereqId));
    }

    /** 常规饰品 buff：5 秒（100 tick）。刷新间隔 1 秒，足够避免中断。*/
    private static void eff(ServerPlayerEntity p, net.minecraft.entity.effect.StatusEffect effect, int amp) {
        p.addStatusEffect(new StatusEffectInstance(effect, 100, amp, true, false, false));
    }

    /** 夜视专用长时长版本：60 秒（1200 tick），彻底避免结束前的闪烁。*/
    private static void effLong(ServerPlayerEntity p, net.minecraft.entity.effect.StatusEffect effect, int amp) {
        p.addStatusEffect(new StatusEffectInstance(effect, 1200, amp, true, false, false));
    }

    // ---- special appliers ----

    private static final Map<UUID, Long> TURTLE_COOLDOWN = new HashMap<>();

    private static void applyTurtleWard(ServerPlayerEntity p) {
        // 检查是否有升级版的保命饰品
        boolean hasLastStand = hasTrinketInBag(p, "last_stand_bulwark");
        boolean hasPhoenix = hasTrinketInBag(p, "phoenix_feather") || hasLastStand;

        float threshold = hasPhoenix ? 10.0f : 6.0f;
        if (hasLastStand) threshold = 14.0f;
        if (p.getHealth() >= threshold) return;

        long now = System.currentTimeMillis();
        long cooldown = hasLastStand ? 30_000 : 60_000;
        Long last = TURTLE_COOLDOWN.get(p.getUuid());
        if (last != null && now - last < cooldown) return;
        TURTLE_COOLDOWN.put(p.getUuid(), now);

        // 保命效果触发 → 不死图腾特效
        TotemFxHelper.playTotem(p);

        if (hasLastStand) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 120, 2, true, true, true));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 120, 3, true, true, true));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 120, 2, true, true, true));
        } else if (hasPhoenix) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 120, 2, true, true, true));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 120, 1, true, true, true));
        } else {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 80, 1, true, true, true));
        }
    }

    /** 快速检查背包中是否存在某 trinket 且未被禁用。*/
    public static boolean hasTrinketInBag(ServerPlayerEntity p, String trinketId) {
        ItemStack[] bag = com.tomkestookit.trinket.TrinketStore.read(p.getUuid());
        int capacity = com.tomkestookit.trinket.TrinketStore.getCapacity(p.getUuid());
        for (int i = 0; i < capacity && i < bag.length; i++) {
            if (trinketId.equals(getId(bag[i])) && !isDisabled(bag[i])) return true;
        }
        return false;
    }

    private static void applyLodestar(ServerPlayerEntity p) {
        ServerWorld world = p.getServerWorld();
        // 根据玩家拥有的磁力系饰品级别决定范围
        double range = 5.0;
        boolean smeltOres = false;
        boolean absorbXp = false;
        ItemStack[] bag = com.tomkestookit.trinket.TrinketStore.read(p.getUuid());
        int capacity = com.tomkestookit.trinket.TrinketStore.getCapacity(p.getUuid());
        for (int i = 0; i < capacity && i < bag.length; i++) {
            String id = getId(bag[i]);
            if ("gravity_well".equals(id)) { range = 10.0; smeltOres = true; }
            if ("astral_hub".equals(id)) { range = 15.0; smeltOres = true; absorbXp = true; }
        }
        Box box = p.getBoundingBox().expand(range);
        for (ItemEntity item : world.getEntitiesByClass(ItemEntity.class, box, e -> true)) {
            double dx = p.getX() - item.getX();
            double dy = p.getY() - item.getY();
            double dz = p.getZ() - item.getZ();
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist < 0.5) continue;
            double speed = 0.15 / dist;
            item.addVelocity(dx * speed, dy * speed + 0.02, dz * speed);
        }
        // 经验球吸附（星界枢纽）
        if (absorbXp) {
            for (net.minecraft.entity.ExperienceOrbEntity orb :
                    world.getEntitiesByClass(net.minecraft.entity.ExperienceOrbEntity.class, box, e -> true)) {
                double dx = p.getX() - orb.getX();
                double dy = p.getY() - orb.getY();
                double dz = p.getZ() - orb.getZ();
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (dist < 0.5) continue;
                double speed = 0.2 / dist;
                orb.addVelocity(dx * speed, dy * speed + 0.03, dz * speed);
            }
        }
    }

    private static final Map<UUID, Integer> LARDER_TIMER = new HashMap<>();

    private static void applyLarder(ServerPlayerEntity p) {
        int count = LARDER_TIMER.merge(p.getUuid(), 1, Integer::sum);
        if (count < 5) return;
        LARDER_TIMER.put(p.getUuid(), 0);
        if (p.getHungerManager().getFoodLevel() < 20) {
            p.getHungerManager().add(1, 0.3f);
        }
    }

    private static void applyGreenthumb(ServerPlayerEntity p) {
        ServerWorld world = p.getServerWorld();
        net.minecraft.util.math.BlockPos base = p.getBlockPos();
        // 随机催熟周围 3 格内的一格作物
        int dx = p.getRandom().nextBetween(-3, 3);
        int dy = p.getRandom().nextBetween(-1, 2);
        int dz = p.getRandom().nextBetween(-3, 3);
        net.minecraft.util.math.BlockPos target = base.add(dx, dy, dz);
        net.minecraft.block.BlockState state = world.getBlockState(target);
        if (state.getBlock() instanceof net.minecraft.block.CropBlock crop) {
            if (!crop.isMature(state)) {
                world.setBlockState(target, crop.withAge(Math.min(crop.getMaxAge(), crop.getAge(state) + 1)));
            }
        }
    }

    private static final Map<UUID, net.minecraft.util.math.BlockPos> LAST_GROUND = new HashMap<>();

    /** 每秒记录玩家脚下位置（只在 onGround 时更新）。*/
    public static void trackGround(ServerPlayerEntity p) {
        if (p.isOnGround() && !p.isInLava() && p.getY() > p.getWorld().getBottomY()) {
            LAST_GROUND.put(p.getUuid(), p.getBlockPos().toImmutable());
        }
    }

    /** 虚空珍珠：Y < bottomY - 5 时传送回地面。*/
    private static void applyVoidSave(ServerPlayerEntity p) {
        double bottomY = p.getWorld().getBottomY();
        if (p.getY() > bottomY - 5) return;
        net.minecraft.util.math.BlockPos safe = LAST_GROUND.get(p.getUuid());
        if (safe == null) safe = p.getServerWorld().getSpawnPos();
        p.teleport(p.getServerWorld(), safe.getX() + 0.5, safe.getY() + 1.0, safe.getZ() + 0.5,
            p.getYaw(), p.getPitch());
        p.setVelocity(0, 0, 0);
        p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket(p));
        // 从虚空抢救回来 → 不死图腾特效
        TotemFxHelper.playTotem(p);
        p.sendMessage(Text.literal("§5[虚空珍珠] 把你拽了回来。").styled(s -> s.withItalic(false)),
            true);
    }

    // ---- totem splinter ----
    private static final Map<UUID, Long> TOTEM_COOLDOWN = new HashMap<>();

    /** 图腾碎片/图腾之心死亡拦截：外部调用。返回 true = 已触发救命。*/
    public static boolean tryTotemSplinter(ServerPlayerEntity p) {
        // 检查是否有图腾之心（升级版）
        boolean hasTotemHeart = hasTrinketInBag(p, "totem_heart");
        long cooldown = hasTotemHeart ? 15 * 60 * 1000L : 20 * 60 * 1000L;

        Long last = TOTEM_COOLDOWN.get(p.getUuid());
        long now = System.currentTimeMillis();
        if (last != null && now - last < cooldown) return false;
        TOTEM_COOLDOWN.put(p.getUuid(), now);

        // 抵挡致死伤害 → 不死图腾特效
        TotemFxHelper.playTotem(p);

        if (hasTotemHeart) {
            p.setHealth(p.getMaxHealth() * 0.5f);
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 160, 1, true, true, true)); // 8s
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 200, 1, true, true, true)); // 10s
            p.sendMessage(Text.literal("§5[图腾之心] 死亡？不，只是另一个开始。").styled(s -> s.withItalic(false)), false);
        } else {
            p.setHealth(1.0f);
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 100, 0, true, true, true)); // 5s
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 200, 1, true, true, true)); // 10s
            p.sendMessage(Text.literal("§5[图腾碎片] 图腾的余温将你从边缘拉回。").styled(s -> s.withItalic(false)), false);
        }
        return true;
    }

    // ---- magma strider ----

    private static void applyMagmaStrider(ServerPlayerEntity p) {
        if (!p.isInLava()) return;
        // 防火（避免燃烧掉血）
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 60, 0, true, false, true));

        // 取消岩浆速度惩罚：直接修正玩家水平速度
        // vanilla 岩浆里 velocity 会被 *0.5 衰减 + 0.02f 加速系数，实际时速极慢
        // 我们放大水平速度分量补偿，让玩家在岩浆里能正常走路
        net.minecraft.util.math.Vec3d vel = p.getVelocity();
        double hSq = vel.x * vel.x + vel.z * vel.z;
        // 只在有水平位移时补偿（玩家在按 WASD）
        if (hSq > 1e-4) {
            double h = Math.sqrt(hSq);
            double target = 0.20; // 目标水平速度（略高于正常走路 0.1，抵消每 tick 衰减）
            if (h < target) {
                double scale = target / h;
                p.setVelocity(vel.x * scale, vel.y, vel.z * scale);
                p.velocityModified = true;
            }
        }
    }
}
