package com.tomkestookit.menu;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 记录 “观察者 → 正在观察的假人 UUID”，当假人下线时把观察者的屏幕关掉。
 * 观察者关闭菜单时会自动清理（BotInventoryScreenHandler.onClosed 里调用 unregister）。
 */
public final class BotMenuTracker {

    /** viewer UUID → 正在观察的 bot UUID */
    private static final Map<UUID, UUID> WATCHING = new HashMap<>();

    private BotMenuTracker() {}

    public static void register() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity disconnected = handler.player;
            UUID leavingUuid = disconnected.getUuid();
            // 该假人下线：所有正在看它的玩家把屏幕关掉
            java.util.List<UUID> toClose = new java.util.ArrayList<>();
            for (Map.Entry<UUID, UUID> e : WATCHING.entrySet()) {
                if (leavingUuid.equals(e.getValue())) toClose.add(e.getKey());
            }
            for (UUID viewerUuid : toClose) {
                WATCHING.remove(viewerUuid);
                ServerPlayerEntity viewer = server.getPlayerManager().getPlayer(viewerUuid);
                if (viewer != null && viewer.currentScreenHandler != viewer.playerScreenHandler) {
                    viewer.closeHandledScreen();
                    viewer.sendMessage(net.minecraft.text.Text.literal(
                        "§7[假人] 目标已下线，菜单已关闭。"), false);
                }
            }
            // 观察者自己下线：清掉这个观察者的记录
            WATCHING.remove(leavingUuid);
        });
    }

    /** 玩家打开某个假人菜单。*/
    public static void watch(UUID viewer, UUID target) {
        WATCHING.put(viewer, target);
    }

    /** 玩家关闭菜单 / 换菜单 → 停止观察。*/
    public static void unwatch(UUID viewer) {
        WATCHING.remove(viewer);
    }

    /** 检查目标假人是否还在线。用于开菜单前先校验。*/
    public static boolean isOnline(MinecraftServer server, UUID target) {
        return server.getPlayerManager().getPlayer(target) != null;
    }
}
