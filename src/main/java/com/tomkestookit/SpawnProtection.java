package com.tomkestookit;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * 玩家传送 / 重生后给 5 秒无敌保护，防止死后落回同一致命环境或传送到目标点被立即秒杀。
 *
 * 实现：给一段短时间的 RESISTANCE 4（减伤 80%）+ FIRE_RESISTANCE，不用 tick 计数器
 * 自己实现，直接借用 vanilla StatusEffect。RESISTANCE 4 不是完全免疫但对绝大多数
 * 一次性伤害（岩浆池 / 高处摔落 / 敌对 AI）都能扛住。
 *
 * 覆盖场景：
 *   - 重生（AFTER_RESPAWN 事件）
 *   - 项目内主动调 SpawnProtection.grant() 的传送入口（landmark / back / menu 传送等）
 *
 * 效果不显示粒子（isAmbient=false, showParticles=false, showIcon=true），
 * 玩家只在 buff 栏看到一个盾牌图标。
 */
public final class SpawnProtection {

    /** 5 秒 = 100 tick。*/
    public static final int DURATION_TICKS = 5 * 20;

    private SpawnProtection() {}

    public static void register() {
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            // alive=true 表示是"跨维度传送而不是死亡重生"；两种情况都给
            grant(newPlayer, alive ? "跨维度传送" : "重生");
        });
    }

    /** 给玩家 5 秒抗性 IV + 抗火。reason 用来发提示消息。*/
    public static void grant(ServerPlayerEntity player, String reason) {
        if (player == null) return;
        if (!TomkesTookitConfig.enabled("world.spawn_protection")) return;
        // RESISTANCE 4 = 减伤 80%；抗火独立处理岩浆场景
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE,
            DURATION_TICKS, 3, false, false, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE,
            DURATION_TICKS, 0, false, false, true));
        player.sendMessage(Text.literal("§7[保护] §f5 秒无敌 §7- " + reason)
            .formatted(Formatting.GRAY), true);
    }

    /** 无 reason 版本，用于不需要显示原因的地方。*/
    public static void grant(ServerPlayerEntity player) {
        grant(player, "传送");
    }
}
