package com.tomkestookit.mixin;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.village.VillageGossipType;
import net.minecraft.village.VillagerGossips;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * 共享村民打折数据：一个村民的 gossip 对所有玩家可见。
 *
 * vanilla 的 VillagerGossips.entityReputation 是 Map&lt;UUID, Reputation&gt;，每个
 * 玩家一份。玩家 A 治愈僵尸村民、多次交易得到的好感度，玩家 B 交易时看不到。
 *
 * 拦截 getReputationFor(UUID, Predicate)：不再按查询的 UUID 单独返回，
 * 改成遍历该村民持有的所有玩家 gossip 记录，取 filter 过滤后加权 sum 的
 * 最大值——一份好感度对全服玩家生效。
 *
 * 副作用与设计权衡：
 *   - 正面 gossip 全服共享（治愈僵尸村民/正常交易累积的折扣人人有份）
 *   - 负面 gossip 天然不被 max 选中（-5 vs 0，max = 0），即单人攻击村民
 *     不会连累其他玩家的价格。这是有意的 QoL 倾向。
 *   - 若村民从未接触任何玩家，entityReputation 为空 → 返回 0（vanilla 默认）。
 *
 * 用公共 API getEntityReputationAssociatedGossips() 而非私有的 Reputation
 * 内部类，避免 mixin 触碰私有内嵌类型。计算逻辑复刻 Reputation.getValueFor：
 *   sum over gossip entries: filter.test(type) ? value * type.multiplier : 0
 */
@Mixin(VillagerGossips.class)
public abstract class VillagerGossipsSharedMixin {

    @Inject(
        method = "getReputationFor(Ljava/util/UUID;Ljava/util/function/Predicate;)I",
        at = @At("HEAD"),
        cancellable = true
    )
    private void tomkestookit$sharedReputation(
        UUID target,
        Predicate<VillageGossipType> filter,
        CallbackInfoReturnable<Integer> cir
    ) {
        VillagerGossips self = (VillagerGossips) (Object) this;
        Map<UUID, Object2IntMap<VillageGossipType>> allGossips =
            self.getEntityReputationAssociatedGossips();
        if (allGossips.isEmpty()) {
            cir.setReturnValue(0);
            return;
        }

        int max = 0;
        for (Object2IntMap<VillageGossipType> gossips : allGossips.values()) {
            int sum = 0;
            for (Object2IntMap.Entry<VillageGossipType> entry : gossips.object2IntEntrySet()) {
                VillageGossipType type = entry.getKey();
                if (filter.test(type)) {
                    sum += entry.getIntValue() * type.multiplier;
                }
            }
            if (sum > max) max = sum;
        }
        cir.setReturnValue(max);
    }
}
