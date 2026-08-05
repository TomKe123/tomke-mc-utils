package com.tomkestookit.elite;

/**
 * 精英怪物层级数值表。
 * 每一层定义生命/伤害/护甲/速度/追踪距离的倍率，以及生成概率和光环参数。
 */
public final class EliteTierScale {

    public record TierData(double healthMult, double damageMult, double armorMult,
                           double speedMult, double followRangeMult,
                           double spawnChance, double debuffChance, int debuffDurationTicks) {}

    private static final TierData[] TIERS = {
        null, // index 0 unused
        new TierData(2.0, 1.5, 1.2, 1.1, 1.5, 0.03, 0.15, 60),   // T1
        new TierData(3.5, 2.0, 1.5, 1.15, 2.0, 0.08, 0.25, 80),  // T2
        new TierData(5.0, 3.0, 2.0, 1.2, 3.0, 0.15, 0.35, 100),  // T3
        new TierData(8.0, 4.0, 3.0, 1.25, 4.0, 0.20, 0.50, 120), // T4
        new TierData(12.0, 5.0, 4.0, 1.3, 5.0, 0.25, 0.65, 160), // T5
        new TierData(20.0, 5.0, 5.0, 1.0, 60.0, 1.0, 1.0, 200),  // T-boss (index 6)
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
