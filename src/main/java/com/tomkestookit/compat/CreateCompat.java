package com.tomkestookit.compat;

import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.Item;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.condition.KilledByPlayerLootCondition;
import net.minecraft.loot.condition.RandomChanceWithLootingLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.function.ApplyBonusLootFunction;
import net.minecraft.loot.function.LootingEnchantLootFunction;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.loot.provider.number.UniformLootNumberProvider;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * Create 模组兼容层。
 *
 * 溺尸击杀时额外掉落 create:zinc_ingot：
 *   - 与铜锭同概率（11% base + looting_multiplier 0.02），独立 pool，可与铜锭同时掉。
 *   - 吃抢夺（looting_enchant 0-1）+ 幸运（apply_bonus fortune uniform）加成。
 *
 * 只在启动时检测到 create 已加载才注入 loot pool；否则跳过。
 */
public final class CreateCompat {

    private static final Identifier DROWNED_LOOT = new Identifier("minecraft", "entities/drowned");
    private static final Identifier ZINC_INGOT_ID = new Identifier("create", "zinc_ingot");

    private CreateCompat() {}

    public static void register() {
        if (!FabricLoader.getInstance().isModLoaded("create")) return;

        LootTableEvents.MODIFY.register((resourceManager, lootManager, id, tableBuilder, source) -> {
            if (!source.isBuiltin()) return;
            if (!DROWNED_LOOT.equals(id)) return;
            Item zinc = Registries.ITEM.get(ZINC_INGOT_ID);
            if (zinc == net.minecraft.item.Items.AIR) return;

            LootPool.Builder pool = LootPool.builder()
                .rolls(ConstantLootNumberProvider.create(1.0f))
                .conditionally(KilledByPlayerLootCondition.builder())
                .conditionally(RandomChanceWithLootingLootCondition.builder(0.11f, 0.02f))
                .with(ItemEntry.builder(zinc)
                    // 抢夺：额外掉落 0-1 每级
                    .apply(LootingEnchantLootFunction.builder(
                        UniformLootNumberProvider.create(0.0f, 1.0f)))
                    // 幸运：uniform_bonus_count (bonusMultiplier=1，与钻石矿石同公式)
                    .apply(ApplyBonusLootFunction.uniformBonusCount(Enchantments.FORTUNE, 1)));

            tableBuilder.pool(pool);
        });
    }
}
