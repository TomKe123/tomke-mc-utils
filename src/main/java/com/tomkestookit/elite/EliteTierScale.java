package com.tomkestookit.elite;

/**
 * 精英怪物层级数值表。
 * 每一层定义生命/伤害/护甲/速度/追踪距离的倍率，以及生成概率和光环参数。
 */
public final class EliteTierScale {

    public record TierData(double healthMult, double damageMult, double armorMult,
                           double speedMult, double followRangeMult,
                           double spawnChance, double debuffChance, int debuffDurationTicks) {}

    // spawnChance 现在真的作为"每只 mob 首次评估时是否精英化"的概率使用（EliteMobHandler 消费）
    // armorMult 现在直接作为附加护甲值（每点约 4% 减伤），不再乘 4
    private static final TierData[] TIERS = {
        null, // index 0 unused
        new TierData(1.5, 1.2, 2.0, 1.05, 1.5, 0.35, 0.10, 60),   // T1  ≈ 1.62× 有效血量
        new TierData(2.0, 1.4, 3.0, 1.08, 2.0, 0.25, 0.15, 80),   // T2  ≈ 2.36×
        new TierData(2.8, 1.7, 5.0, 1.10, 3.0, 0.15, 0.25, 100),  // T3  ≈ 3.85×
        new TierData(3.8, 2.0, 7.0, 1.13, 4.0, 0.08, 0.35, 120),  // T4  ≈ 5.85×
        new TierData(5.0, 2.4, 10.0, 1.15, 5.0, 0.04, 0.45, 160), // T5  ≈ 9.2×
        new TierData(15.0, 4.0, 15.0, 1.0, 60.0, 1.0, 1.0, 200),  // Boss (index 6)
    };

    /** 威胁评分阈值：rating >= threshold[i] → tier = i+1 */
    public static final double[] TIER_THRESHOLDS = {0.10, 0.25, 0.40, 0.60, 0.80};

    private EliteTierScale() {}

    public static TierData get(int tier) {
        if (tier < 1 || tier >= TIERS.length) return TIERS[1];
        return TIERS[tier];
    }

    public static int getBossTier() {
        return 6;
    }
}
