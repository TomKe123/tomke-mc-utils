package com.tomkestookit;

import com.tomkestookit.menu.PlayerDetailMenu;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 普通玩家背包读写授权请求。*/
public final class PlayerInventoryAccessRequest {
    private static final long TIMEOUT_MS = 60_000;

    private record Request(String id, UUID viewer, UUID target, long expiresAt) {}

    private static final Map<String, Request> REQUESTS = new HashMap<>();
    private static int nextId = 1;

    private PlayerInventoryAccessRequest() {}

    public static void request(ServerPlayerEntity viewer, ServerPlayerEntity target) {
        String id = Integer.toString(nextId++);
        Request r = new Request(id, viewer.getUuid(), target.getUuid(), System.currentTimeMillis() + TIMEOUT_MS);
        REQUESTS.put(id, r);

        Text accept = Text.literal("[同意]").formatted(Formatting.GREEN, Formatting.BOLD)
            .styled(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tomkestookit invaccept " + id))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("允许对方查看并读写你的背包"))));
        Text deny = Text.literal(" [拒绝]").formatted(Formatting.RED, Formatting.BOLD)
            .styled(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tomkestookit invdeny " + id))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("拒绝本次请求"))));

        target.sendMessage(Text.literal("§b[背包请求] §f" + viewer.getGameProfile().getName()
            + " §7想查看并读写你的背包，60 秒内有效："), false);
        target.sendMessage(Text.empty().append(accept).append(deny), false);
        viewer.sendMessage(Text.literal("§7[背包请求] 已向 " + target.getGameProfile().getName() + " 发送确认请求。"), true);
    }

    public static int accept(ServerPlayerEntity target, String id) {
        Request r = REQUESTS.remove(id);
        if (r == null || expired(r)) {
            target.sendMessage(Text.literal("§c[背包请求] 请求不存在或已过期。"), true);
            return 0;
        }
        if (!r.target().equals(target.getUuid())) {
            target.sendMessage(Text.literal("§c[背包请求] 这不是发给你的请求。"), true);
            return 0;
        }
        ServerPlayerEntity viewer = target.getServer().getPlayerManager().getPlayer(r.viewer());
        if (viewer == null) {
            target.sendMessage(Text.literal("§c[背包请求] 对方已离线。"), true);
            return 0;
        }
        target.sendMessage(Text.literal("§a[背包请求] 已同意。"), true);
        viewer.sendMessage(Text.literal("§a[背包请求] " + target.getGameProfile().getName() + " 已同意。"), true);
        PlayerDetailMenu.openApprovedInventory(viewer, target.getUuid());
        return 1;
    }

    public static int deny(ServerPlayerEntity target, String id) {
        Request r = REQUESTS.remove(id);
        if (r == null || expired(r)) {
            target.sendMessage(Text.literal("§c[背包请求] 请求不存在或已过期。"), true);
            return 0;
        }
        if (!r.target().equals(target.getUuid())) {
            target.sendMessage(Text.literal("§c[背包请求] 这不是发给你的请求。"), true);
            return 0;
        }
        ServerPlayerEntity viewer = target.getServer().getPlayerManager().getPlayer(r.viewer());
        if (viewer != null) viewer.sendMessage(Text.literal("§c[背包请求] 对方拒绝了你的请求。"), true);
        target.sendMessage(Text.literal("§7[背包请求] 已拒绝。"), true);
        return 1;
    }

    private static boolean expired(Request r) {
        return System.currentTimeMillis() > r.expiresAt();
    }
}
