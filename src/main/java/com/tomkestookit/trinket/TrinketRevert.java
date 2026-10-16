package com.tomkestookit.trinket;

import com.tomkestookit.reforge.ReforgeRefreshHandler;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 饰品系统已撤销回原版：玩家上线时把饰品袋里的东西全部还给本人，然后清空饰品袋。
 *
 * 归还的物品会先过一遍 {@link ReforgeRefreshHandler#refreshStack}，饰品 / 重铸 NBT 被剥掉，
 * 变回底材对应的 vanilla 物品；背包满了掉在脚下。
 *
 * 先发物品再清存档：中途崩服最坏是下次上线再发一次，不会吞物品。
 */
public final class TrinketRevert {

    private TrinketRevert() {}

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
            server.execute(() -> returnBag(handler.player)));
    }

    private static void returnBag(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack s : TrinketStore.readCopy(uuid)) {
            if (s != null && !s.isEmpty()) items.add(s);
        }
        if (items.isEmpty()) return;

        for (ItemStack s : items) {
            ReforgeRefreshHandler.refreshStack(s);
            player.getInventory().offerOrDrop(s);
        }
        // 先把玩家存档（含刚退回的物品）落盘，再清饰品袋，崩服时最多重复退回而不会丢物品。
        // 背包满掉在脚下的掉落物跟随区块保存，与 vanilla 掉落物同等风险。
        player.getServer().getPlayerManager().saveAllPlayerData();
        TrinketStore.write(uuid, new ItemStack[0]);

        player.sendMessage(Text.literal("[饰品] 饰品系统已移除，饰品袋里的 " + items.size()
            + " 件物品已退回背包（背包满时掉在脚下）").formatted(Formatting.YELLOW), false);
    }
}
