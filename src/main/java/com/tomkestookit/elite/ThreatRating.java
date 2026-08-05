package com.tomkestookit.elite;

import com.tomkestookit.CarpetBotUtil;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.List;
import java.util.UUID;

/**
 * 玩家威胁评分（v3）：实时读取"当前最强武器 + 身上正面 BUFF"计算 tier。
 *
 * 不再采集伤害数据，不再持久化 —— 每次 getTier 都当场从 player 状态计算。
 * 这样：
 *   - 玩家换上更强武器立即升 tier
 *   - buff 一到期 tier 立即降回
 *   - 假人不产生 rating（判假人在调用端）
 *
 * 评分公式（rating ∈ [0, 1]）：
 *   weaponScore = min(1, effectiveAttack / 15.0)
 *       effectiveAttack = 玩家所有武器格中最强的 (attack_damage + max(sharpness, smite, arthropods) × 0.5 + 0.5)
 *   armorScore  = min(1, (armor + toughness) / 32.0)
 *   buffScore   = sum(effect加成)：
 *       STRENGTH:      +0.10 × (amp+1)
 *       RESISTANCE:    +0.10 × (amp+1)
 *       SPEED:         +0.05 × (amp+1)
 *       HASTE:         +0.05 × (amp+1)
 *       REGENERATION:  +0.05 × (amp+1)
 *       ABSORPTION:    +0.05 × (amp+1)
 *       HERO_OF_THE_VILLAGE: +0.05 × (amp+1)
 *   rating = 0.50 × weaponScore + 0.20 × armorScore + 0.30 × min(1, buffScore)
 *
 * Tier 映射用 {@link EliteTierScale#TIER_THRESHOLDS}。
 */
public final class ThreatRating {

    private ThreatRating() {}

    /** 服务器引用：仅 EliteCommand.info 使用（load/save 已废弃）。*/
    @SuppressWarnings("unused")
    private static MinecraftServer serverRef;

    // ==== 兼容旧 API：这些方法保留但不做事，防止外部编译失败 ====

    public static void register() {
        // 装备/BUFF 是即时读取的，无需注册 tick
    }

    public static void load(MinecraftServer server) {
        serverRef = server;
        // 不再持久化
    }

    public static void save() {
        // 不再持久化
    }

    public static void recordDamageDealt(UUID playerUuid, float amount) {}
    public static void recordDamageTaken(UUID playerUuid, float amount) {}
    public static void recordKill(UUID playerUuid, double killValue) {}
    public static void setRating(UUID playerUuid, double rating) {
        // 调试路径已由 /elite set 命令直接调 EliteMobHandler.applyElite 替代
    }

    // ==== 核心：实时评分 ====

    /** 通过 UUID 查找 player。找不到返回 0。*/
    public static double getRating(UUID playerUuid) {
        if (serverRef == null) return 0.0;
        for (ServerPlayerEntity p : serverRef.getPlayerManager().getPlayerList()) {
            if (p.getUuid().equals(playerUuid)) return getRating(p);
        }
        return 0.0;
    }

    /** 主入口：实时计算玩家当前威胁评分。*/
    public static double getRating(ServerPlayerEntity player) {
        if (CarpetBotUtil.isFakePlayer(player)) return 0.0;

        // 武器分：扫主手 + 副手 + 背包所有武器格，取最强
        double bestAttack = 0.0;
        // 主手先看（考虑属性叠加）
        double mainhandAttack = player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        bestAttack = Math.max(bestAttack, mainhandAttack + enchantBonus(player.getMainHandStack()));
        // 副手
        bestAttack = Math.max(bestAttack, itemBaseAttack(player.getOffHandStack())
            + enchantBonus(player.getOffHandStack()));
        // 背包（找可能更强的武器）
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (s.isEmpty()) continue;
            double v = itemBaseAttack(s) + enchantBonus(s);
            if (v > bestAttack) bestAttack = v;
        }
        double weaponScore = Math.min(1.0, bestAttack / 15.0);

        // 护甲分
        double armor = player.getAttributeValue(EntityAttributes.GENERIC_ARMOR);
        double toughness = player.getAttributeValue(EntityAttributes.GENERIC_ARMOR_TOUGHNESS);
        double armorScore = Math.min(1.0, (armor + toughness) / 32.0);

        // BUFF 分
        double buffScore = 0.0;
        for (StatusEffectInstance eff : player.getStatusEffects()) {
            var type = eff.getEffectType();
            int amp = eff.getAmplifier() + 1;
            if (type == StatusEffects.STRENGTH)              buffScore += 0.10 * amp;
            else if (type == StatusEffects.RESISTANCE)       buffScore += 0.10 * amp;
            else if (type == StatusEffects.SPEED)            buffScore += 0.05 * amp;
            else if (type == StatusEffects.HASTE)            buffScore += 0.05 * amp;
            else if (type == StatusEffects.REGENERATION)     buffScore += 0.05 * amp;
            else if (type == StatusEffects.ABSORPTION)       buffScore += 0.05 * amp;
            else if (type == StatusEffects.HERO_OF_THE_VILLAGE) buffScore += 0.05 * amp;
        }
        buffScore = Math.min(1.0, buffScore);

        double rating = 0.50 * weaponScore + 0.20 * armorScore + 0.30 * buffScore;
        return Math.max(0.0, Math.min(1.0, rating));
    }

    /** 从物品的属性 modifier 里拿基础攻击伤害。*/
    private static double itemBaseAttack(ItemStack stack) {
        if (stack.isEmpty()) return 0.0;
        double best = 0.0;
        var mods = stack.getItem().getAttributeModifiers(net.minecraft.entity.EquipmentSlot.MAINHAND);
        for (var entry : mods.entries()) {
            if (entry.getKey() == EntityAttributes.GENERIC_ATTACK_DAMAGE) {
                best += entry.getValue().getValue();
            }
        }
        return best;
    }

    /** 附魔加成：max(sharpness, smite, arthropods) × 0.5 + 0.5（0 级返回 0）。*/
    private static double enchantBonus(ItemStack stack) {
        if (stack.isEmpty()) return 0.0;
        int s = EnchantmentHelper.getLevel(Enchantments.SHARPNESS, stack);
        int sm = EnchantmentHelper.getLevel(Enchantments.SMITE, stack);
        int a = EnchantmentHelper.getLevel(Enchantments.BANE_OF_ARTHROPODS, stack);
        int best = Math.max(s, Math.max(sm, a));
        return best > 0 ? 0.5 + 0.5 * best : 0.0;
    }

    /** 按阈值表映射 tier。*/
    public static int getTier(UUID playerUuid) {
        double rating = getRating(playerUuid);
        return ratingToTier(rating);
    }

    public static int getTier(ServerPlayerEntity player) {
        return ratingToTier(getRating(player));
    }

    private static int ratingToTier(double rating) {
        int tier = 0;
        for (int i = 0; i < EliteTierScale.TIER_THRESHOLDS.length; i++) {
            if (rating >= EliteTierScale.TIER_THRESHOLDS[i]) tier = i + 1;
        }
        return tier;
    }

    /** 找 64 格内最高 tier 真人（假人跳过）。*/
    public static int getNearestTier(BlockPos pos, ServerWorld world) {
        Box box = new Box(pos).expand(64);
        List<ServerPlayerEntity> players = world.getEntitiesByClass(
            ServerPlayerEntity.class, box, p -> !CarpetBotUtil.isFakePlayer(p)
        );
        int maxTier = 0;
        for (ServerPlayerEntity p : players) {
            int t = getTier(p);
            if (t > maxTier) maxTier = t;
        }
        return maxTier;
    }
}
