package com.tomkestookit;

import com.tomkestookit.menu.PlayerDetailMenu;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

/**
 * Shift + 右键 Carpet 假人 → 直接打开假人详情/控制菜单。
 * 只对主手触发，避免重复；假人才会命中，普通玩家仍走 vanilla 行为。
 */
public final class BotInteractHandler {

    private BotInteractHandler() {}

    public static void register() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient) return ActionResult.PASS;
            if (!TomkesTookitConfig.enabled("world.bot_anchor")) return ActionResult.PASS;
            if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            if (!player.isSneaking()) return ActionResult.PASS;
            if (!(entity instanceof ServerPlayerEntity target)) return ActionResult.PASS;
            if (!CarpetBotUtil.isFakePlayer(target)) return ActionResult.PASS;

            PlayerDetailMenu.open(sp, target.getUuid());
            return ActionResult.SUCCESS;
        });
    }
}
