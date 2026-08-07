package com.tomkestookit.materials;

/**
 * CustomModelData 常量表：每个自定义物品有唯一整数 ID。
 * 资源包通过 predicate.custom_model_data 来匹配并替换贴图。
 *
 * 编号规则：
 *   100xxx = 重铸石
 *   200xxx = 饰品
 *   300xxx = 原创武器
 *   400xxx = 怪物素材
 */
public final class CustomModelDataRegistry {

    private CustomModelDataRegistry() {}

    // ============================
    //  重铸石 (100001 ~ 100099)
    // ============================
    public static final int REFORGE_FURY_SHARD          = 100001;
    public static final int REFORGE_BERSERKER_CORE      = 100002;
    public static final int REFORGE_SWIFT_FANG          = 100003;
    public static final int REFORGE_IRON_BARK           = 100004;
    public static final int REFORGE_DIAMOND_SHELL       = 100005;
    public static final int REFORGE_FEATHERWEIGHT_GEM   = 100006;
    public static final int REFORGE_TITAN_PLATE         = 100007;
    public static final int REFORGE_LUCKY_CLOVER        = 100008;
    public static final int REFORGE_MENDING_MOSS        = 100009;
    public static final int REFORGE_KNOCKBACK_CRYSTAL   = 100010;
    public static final int REFORGE_SKYFORGE_RUNE       = 100011;
    public static final int REFORGE_SOUL_MEND           = 100012;
    public static final int REFORGE_ANTIGRAV_BOOTS      = 100013;
    public static final int REFORGE_INFERNO_CRYSTAL     = 100014;
    public static final int REFORGE_WARDWOOD_BARK       = 100015;
    public static final int REFORGE_BEDROCK_BULWARK     = 100016;
    public static final int REFORGE_DRAGON_SCALE_PLATE  = 100017;
    public static final int REFORGE_TITAN_HEART         = 100018;
    public static final int REFORGE_COSMIC_ESSENCE      = 100019;
    public static final int REFORGE_ABYSSAL_MIGHT       = 100020;
    public static final int REFORGE_SOUL_EDGE           = 100021;
    public static final int REFORGE_BLOOD_MOON_FURY     = 100022;
    public static final int REFORGE_VOID_PIERCE         = 100023;
    public static final int REFORGE_STARDUST_WEIGHT     = 100024;
    public static final int REFORGE_SWEEP_WIDENER       = 100025;
    public static final int REFORGE_SWEEP_GRAND         = 100026;
    public static final int REFORGE_TARGET_RUSH         = 100027;

    // ============================
    //  饰品 (200001 ~ 200099)
    // ============================
    public static final int TRINKET_CAFFEINE_PATCH      = 200001;
    public static final int TRINKET_LODESTAR_PIN        = 200002;
    public static final int TRINKET_NIGHTWING_CHARM     = 200003;
    public static final int TRINKET_TURTLE_WARD         = 200004;
    public static final int TRINKET_CATS_EYE            = 200005;
    public static final int TRINKET_EMBER_SIGIL         = 200006;
    public static final int TRINKET_IRON_ARM            = 200007;
    public static final int TRINKET_LARDER_GLASS        = 200008;
    public static final int TRINKET_ENDER_ANCHOR        = 200009;
    public static final int TRINKET_SOUL_LANTERN_CHARM  = 200010;
    public static final int TRINKET_AQUA_LUNG           = 200011;
    public static final int TRINKET_GREENTHUMB_RING     = 200012;
    public static final int TRINKET_VOID_PEARL          = 200013;
    public static final int TRINKET_PROSPECTOR_LENS     = 200014;
    public static final int TRINKET_TOTEM_SPLINTER      = 200015;
    public static final int TRINKET_BLOOD_FURY          = 200016;
    public static final int TRINKET_AURORA_PENDANT      = 200017;
    public static final int TRINKET_INSIGHT_PRISM       = 200018;
    public static final int TRINKET_HASTE_CHARM_1       = 200019;
    public static final int TRINKET_HASTE_CHARM_2       = 200020;
    public static final int TRINKET_HASTE_CHARM_3       = 200021;
    public static final int TRINKET_MAGMA_STRIDER       = 200022;
    public static final int TRINKET_ADRENALINE_INJECTOR = 200023;
    public static final int TRINKET_TIME_DILATION_CLOCK = 200024;
    public static final int TRINKET_WARLORD_CROWN       = 200025;
    public static final int TRINKET_TIDE_CALLER_SHELL   = 200026;
    public static final int TRINKET_ABYSSAL_HEART       = 200027;
    public static final int TRINKET_PHOENIX_FEATHER     = 200028;
    public static final int TRINKET_LAST_STAND_BULWARK  = 200029;
    public static final int TRINKET_GRAVITY_WELL        = 200030;
    public static final int TRINKET_ASTRAL_HUB          = 200031;
    public static final int TRINKET_TOTEM_HEART         = 200032;
    public static final int TRINKET_XP_SCROLL           = 200033;
    public static final int TRINKET_XP_TOME             = 200034;
    public static final int TRINKET_XP_CROWN            = 200035;
    public static final int TRINKET_DRAGON_HEART        = 200036;
    public static final int TRINKET_ABYSSAL_NECKLACE    = 200037;
    public static final int TRINKET_SOUL_CRYSTAL_PENDANT = 200038;
    public static final int TRINKET_STARDUST_COMPASS    = 200039;
    public static final int TRINKET_BLOOD_MOON_AMULET   = 200040;
    public static final int TRINKET_VOID_HEART_TRINKET  = 200041;

    // ============================
    //  原创武器 (300001 ~ 300099)
    // ============================
    public static final int WEAPON_BLOOD_MOON_SCYTHE    = 300001;
    public static final int WEAPON_SOUL_REAPER          = 300002;
    public static final int WEAPON_ABYSSAL_TRIDENT      = 300003;
    public static final int WEAPON_VOID_BLADE           = 300004;

    // ============================
    //  怪物素材 (400001 ~ 400099)
    // ============================
    public static final int MAT_CORRUPT_SINEW           = 400001;
    public static final int MAT_ABYSSAL_SHARD           = 400002;
    public static final int MAT_SOUL_CRYSTAL            = 400003;
    public static final int MAT_STARDUST                = 400004;
    public static final int MAT_BLOOD_MOON_ESSENCE      = 400005;
    public static final int MAT_VOID_CORE               = 400006;

    // ============================
    //  Boss 令牌 (500001 ~ 500099)
    // ============================
    public static final int BOSS_INFERNAL_WARDEN    = 500001;
    public static final int BOSS_ABYSSAL_GUARDIAN   = 500002;
    public static final int BOSS_FROST_LICH         = 500003;
    public static final int BOSS_VOID_DRAGON_CHILD  = 500004;

    // ---- 查表方法 ----

    /** 根据重铸石 id 获取 CustomModelData 值。*/
    public static int forReforge(String id) {
        return switch (id) {
            case "fury_shard"          -> REFORGE_FURY_SHARD;
            case "berserker_core"      -> REFORGE_BERSERKER_CORE;
            case "swift_fang"          -> REFORGE_SWIFT_FANG;
            case "iron_bark"           -> REFORGE_IRON_BARK;
            case "diamond_shell"       -> REFORGE_DIAMOND_SHELL;
            case "featherweight_gem"   -> REFORGE_FEATHERWEIGHT_GEM;
            case "titan_plate"         -> REFORGE_TITAN_PLATE;
            case "lucky_clover"        -> REFORGE_LUCKY_CLOVER;
            case "mending_moss"        -> REFORGE_MENDING_MOSS;
            case "knockback_crystal"   -> REFORGE_KNOCKBACK_CRYSTAL;
            case "skyforge_rune"       -> REFORGE_SKYFORGE_RUNE;
            case "soul_mend"           -> REFORGE_SOUL_MEND;
            case "antigrav_boots"      -> REFORGE_ANTIGRAV_BOOTS;
            case "inferno_crystal"     -> REFORGE_INFERNO_CRYSTAL;
            case "wardwood_bark"       -> REFORGE_WARDWOOD_BARK;
            case "bedrock_bulwark"     -> REFORGE_BEDROCK_BULWARK;
            case "dragon_scale_plate"  -> REFORGE_DRAGON_SCALE_PLATE;
            case "titan_heart"         -> REFORGE_TITAN_HEART;
            case "cosmic_essence"      -> REFORGE_COSMIC_ESSENCE;
            case "abyssal_might"       -> REFORGE_ABYSSAL_MIGHT;
            case "soul_edge"           -> REFORGE_SOUL_EDGE;
            case "blood_moon_fury"     -> REFORGE_BLOOD_MOON_FURY;
            case "void_pierce"         -> REFORGE_VOID_PIERCE;
            case "stardust_weight"     -> REFORGE_STARDUST_WEIGHT;
            case "sweep_widener"       -> REFORGE_SWEEP_WIDENER;
            case "sweep_grand"         -> REFORGE_SWEEP_GRAND;
            case "target_rush"         -> REFORGE_TARGET_RUSH;
            default -> 0;
        };
    }

    /** 根据饰品 id 获取 CustomModelData 值。*/
    public static int forTrinket(String id) {
        return switch (id) {
            case "caffeine_patch"       -> TRINKET_CAFFEINE_PATCH;
            case "lodestar_pin"         -> TRINKET_LODESTAR_PIN;
            case "nightwing_charm"      -> TRINKET_NIGHTWING_CHARM;
            case "turtle_ward"          -> TRINKET_TURTLE_WARD;
            case "cats_eye"             -> TRINKET_CATS_EYE;
            case "ember_sigil"          -> TRINKET_EMBER_SIGIL;
            case "iron_arm"             -> TRINKET_IRON_ARM;
            case "larder_glass"         -> TRINKET_LARDER_GLASS;
            case "ender_anchor"         -> TRINKET_ENDER_ANCHOR;
            case "soul_lantern_charm"   -> TRINKET_SOUL_LANTERN_CHARM;
            case "aqua_lung"            -> TRINKET_AQUA_LUNG;
            case "greenthumb_ring"      -> TRINKET_GREENTHUMB_RING;
            case "void_pearl"           -> TRINKET_VOID_PEARL;
            case "prospector_lens"      -> TRINKET_PROSPECTOR_LENS;
            case "totem_splinter"       -> TRINKET_TOTEM_SPLINTER;
            case "blood_fury"           -> TRINKET_BLOOD_FURY;
            case "aurora_pendant"       -> TRINKET_AURORA_PENDANT;
            case "insight_prism"        -> TRINKET_INSIGHT_PRISM;
            case "haste_charm_1"        -> TRINKET_HASTE_CHARM_1;
            case "haste_charm_2"        -> TRINKET_HASTE_CHARM_2;
            case "haste_charm_3"        -> TRINKET_HASTE_CHARM_3;
            case "magma_strider"        -> TRINKET_MAGMA_STRIDER;
            case "adrenaline_injector"  -> TRINKET_ADRENALINE_INJECTOR;
            case "time_dilation_clock"  -> TRINKET_TIME_DILATION_CLOCK;
            case "warlord_crown"        -> TRINKET_WARLORD_CROWN;
            case "tide_caller_shell"    -> TRINKET_TIDE_CALLER_SHELL;
            case "abyssal_heart"        -> TRINKET_ABYSSAL_HEART;
            case "phoenix_feather"      -> TRINKET_PHOENIX_FEATHER;
            case "last_stand_bulwark"   -> TRINKET_LAST_STAND_BULWARK;
            case "gravity_well"         -> TRINKET_GRAVITY_WELL;
            case "astral_hub"           -> TRINKET_ASTRAL_HUB;
            case "totem_heart"          -> TRINKET_TOTEM_HEART;
            case "xp_scroll"            -> TRINKET_XP_SCROLL;
            case "xp_tome"              -> TRINKET_XP_TOME;
            case "xp_crown"             -> TRINKET_XP_CROWN;
            case "dragon_heart"         -> TRINKET_DRAGON_HEART;
            case "abyssal_necklace"     -> TRINKET_ABYSSAL_NECKLACE;
            case "soul_crystal_pendant" -> TRINKET_SOUL_CRYSTAL_PENDANT;
            case "stardust_compass"     -> TRINKET_STARDUST_COMPASS;
            case "blood_moon_amulet"    -> TRINKET_BLOOD_MOON_AMULET;
            case "void_heart_trinket"   -> TRINKET_VOID_HEART_TRINKET;
            default -> 0;
        };
    }

    /** 根据武器 id 获取 CustomModelData 值。*/
    public static int forWeapon(String id) {
        return switch (id) {
            case "blood_moon_scythe"   -> WEAPON_BLOOD_MOON_SCYTHE;
            case "soul_reaper"         -> WEAPON_SOUL_REAPER;
            case "abyssal_trident"     -> WEAPON_ABYSSAL_TRIDENT;
            case "void_blade"          -> WEAPON_VOID_BLADE;
            default -> 0;
        };
    }

    /** 根据素材 id 获取 CustomModelData 值。*/
    public static int forMaterial(String id) {
        return switch (id) {
            case "corrupt_sinew"       -> MAT_CORRUPT_SINEW;
            case "abyssal_shard"       -> MAT_ABYSSAL_SHARD;
            case "soul_crystal"        -> MAT_SOUL_CRYSTAL;
            case "stardust"            -> MAT_STARDUST;
            case "blood_moon_essence"  -> MAT_BLOOD_MOON_ESSENCE;
            case "void_core"           -> MAT_VOID_CORE;
            default -> 0;
        };
    }

    /** 根据 Boss 令牌 id 获取 CustomModelData 值。*/
    public static int forBoss(String id) {
        return switch (id) {
            case "infernal_warden"   -> BOSS_INFERNAL_WARDEN;
            case "abyssal_guardian"  -> BOSS_ABYSSAL_GUARDIAN;
            case "frost_lich"        -> BOSS_FROST_LICH;
            case "void_dragon_child" -> BOSS_VOID_DRAGON_CHILD;
            default -> 0;
        };
    }
}
