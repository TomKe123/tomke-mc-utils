package com.tomkestookit;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 处理反重力皮靴的两个被动效果：
 *   - 100% 跳跃加成：通过持续刷新 Jump Boost II（amplifier=1，即 2 级）实现，
 *     跳跃高度大约翻倍。effect 无粒子无 HUD 图标。
 *   - 75% 摔落减伤：在 ALLOW_DAMAGE 里拦截 FALL 类伤害，直接以 0.25 倍手动施加。
 */
public final class AntigravBootsHandler {

    public static final String NBT_TAG = "TomkesTookitAntigrav";
    private static final float FALL_DAMAGE_MULTIPLIER = 0.25f; // 75% 减伤
    private static final int JUMP_AMPLIFIER = 1;               // Jump Boost II
    private static final int JUMP_DURATION_TICKS = 40;         // 2 秒，覆盖刷新间隔
    private static final int REFRESH_INTERVAL_TICKS = 20;      // 每秒刷一次

    private AntigravBootsHandler() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % REFRESH_INTERVAL_TICKS != 0) return;
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (isWearingAntigravBoots(player)) {
                    player.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.JUMP_BOOST,
                        JUMP_DURATION_TICKS,
                        JUMP_AMPLIFIER,
                        true,   // ambient：不显示强粒子效果
                        false,  // showParticles：不显示粒子
                        false   // showIcon：不显示 HUD 图标
                    ));
                }
            }
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayerEntity player)) return true;
            if (!isFallDamage(source)) return true;
            if (!isWearingAntigravBoots(player)) return true;

            // 手动施加 25% 伤害，然后阻止原始伤害。用带绕过的伤害源避免再次被本 handler 拦截。
            float reduced = amount * FALL_DAMAGE_MULTIPLIER;
            if (reduced > 0.0f) {
                DamageSource bypass = player.getDamageSources().create(DamageTypes.GENERIC);
                player.damage(bypass, reduced);
            }
            return false;
        });
    }

    private static boolean isWearingAntigravBoots(LivingEntity entity) {
        ItemStack boots = entity.getEquippedStack(EquipmentSlot.FEET);
        if (boots.isEmpty() || boots.getItem() != Items.LEATHER_BOOTS) return false;
        NbtCompound nbt = boots.getNbt();
        return nbt != null && nbt.getBoolean(NBT_TAG);
    }

    private static boolean isFallDamage(DamageSource source) {
        return source.isIn(net.minecraft.registry.tag.DamageTypeTags.IS_FALL);
    }
}
