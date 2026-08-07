package com.tomkestookit.trinket;

import net.minecraft.entity.EntityStatuses;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

/**
 * 播放不死图腾特效（金色粒子放射动画 + 音效）用于饰品触发保命效果时。
 *
 * 服务端可通过 EntityStatuses.USE_TOTEM_OF_UNDYING = 35 广播给客户端。
 * 附带播放 ITEM_TOTEM_USE 音效，避免只有粒子无声的突兀感。
 */
public final class TotemFxHelper {

    private TotemFxHelper() {}

    /** 在玩家位置触发不死图腾的金色粒子放射动画 + 音效。*/
    public static void playTotem(ServerPlayerEntity player) {
        if (player == null || player.getServer() == null) return;
        // 让所有能看到的客户端播放金色粒子放射
        ServerWorld world = player.getServerWorld();
        world.sendEntityStatus(player, EntityStatuses.USE_TOTEM_OF_UNDYING);
        // 音效
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 1.0f, 1.0f);
    }
}
