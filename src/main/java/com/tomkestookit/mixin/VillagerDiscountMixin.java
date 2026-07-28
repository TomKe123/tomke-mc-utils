package com.tomkestookit.mixin;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.VillageGossipType;
import net.minecraft.village.VillagerGossips;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.UUID;

/**
 * 共享村民正面折扣，并让交易价格每次打开 GUI 时重新计算。
 *
 * vanilla 的 prepareOffersFor(player) 只按当前玩家自己的 gossip 写 specialPrice。
 * 这里保留 vanilla 的个人折扣，再额外比较全服所有玩家的正面 gossip，若有人
 * 治疗/交易过该村民，则其他玩家也获得相同的最高正面折扣。
 *
 * 负面 gossip 不共享：玩家自己攻击村民仍可能影响自己的价格，但不会连累其他人。
 */
@Mixin(VillagerEntity.class)
public abstract class VillagerDiscountMixin {

    @Shadow @Final private VillagerGossips gossip;

    private TradeOfferList offers() {
        return ((MerchantEntity) (Object) this).getOffers();
    }

    @Inject(method = "prepareOffersFor", at = @At("HEAD"))
    private void tomkestookit$clearPricesBeforeRecompute(PlayerEntity player, CallbackInfo ci) {
        if (!com.tomkestookit.TomkesTookitConfig.enabled("world.villager_gossip_share")) return;
        for (TradeOffer offer : offers()) {
            offer.clearSpecialPrice();
        }
    }

    @Inject(method = "prepareOffersFor", at = @At("TAIL"))
    private void tomkestookit$applySharedPositiveDiscount(PlayerEntity player, CallbackInfo ci) {
        if (!com.tomkestookit.TomkesTookitConfig.enabled("world.villager_gossip_share")) return;
        int ownPositive = positiveReputationFor(player.getUuid());
        int sharedPositive = maxPositiveReputation();
        if (sharedPositive <= ownPositive) return;

        for (TradeOffer offer : offers()) {
            int ownDiscount = MathHelper.floor(ownPositive * offer.getPriceMultiplier());
            int sharedDiscount = MathHelper.floor(sharedPositive * offer.getPriceMultiplier());
            int extraDiscount = sharedDiscount - ownDiscount;
            if (extraDiscount > 0) {
                offer.increaseSpecialPrice(-extraDiscount);
            }
        }
    }

    private int maxPositiveReputation() {
        int max = 0;
        for (UUID uuid : gossip.getEntityReputationAssociatedGossips().keySet()) {
            int value = positiveReputationFor(uuid);
            if (value > max) max = value;
        }
        return max;
    }

    private int positiveReputationFor(UUID uuid) {
        Map<UUID, Object2IntMap<VillageGossipType>> all = gossip.getEntityReputationAssociatedGossips();
        Object2IntMap<VillageGossipType> entries = all.get(uuid);
        if (entries == null || entries.isEmpty()) return 0;

        int sum = 0;
        for (Object2IntMap.Entry<VillageGossipType> entry : entries.object2IntEntrySet()) {
            VillageGossipType type = entry.getKey();
            if (type.multiplier <= 0) continue;
            sum += entry.getIntValue() * type.multiplier;
        }
        return Math.max(0, sum);
    }
}
