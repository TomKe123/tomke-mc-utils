package com.tomkestookit.reforge;

import com.tomkestookit.TomkesTookitConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

/**
 * 重铸效果热更新：
 *
 * 修改 ReforgeRegistry 里某个 def 的 amount/operation 之后，玩家背包里已经打过这个重铸
 * 的旧装备不会自动获得新数值——因为属性是通过 AttributeModifier 存进 NBT 的。
 * 这个 handler 每 60 tick (3 秒) 扫玩家整个物品栏，对每片装备上以 "tomkestookit:reforge_"
 * 命名的 modifier，用当前 ReforgeRegistry 的最新值覆盖。UUID 保持不变，Minecraft 会
 * 自然应用新的 Amount / Operation。
 *
 * 只处理 tomkestookit 命名前缀的 modifier，不动 vanilla / mod 属性。
 * amount 与 NBT 里的差异超过 1e-6 或 operation 不同才写回，避免每次扫都触发 NBT dirty。
 */
public final class ReforgeRefreshHandler {

    private static final int INTERVAL_TICKS = 60; // 3 秒
    private static final String MODIFIER_NAME_PREFIX = "tomkestookit:reforge_";

    private ReforgeRefreshHandler() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(ReforgeRefreshHandler::tick);
    }

    private static void tick(MinecraftServer server) {
        if (!TomkesTookitConfig.enabled("item.reforge")) return;
        int ticks = server.getTicks();
        if (ticks % INTERVAL_TICKS != 0) return;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            // 扫整个物品栏：36 主栏 + 副手 + 4 盔甲（0-39）
            for (int slot = 0; slot < player.getInventory().size(); slot++) {
                ItemStack stack = player.getInventory().getStack(slot);
                if (stack.isEmpty()) continue;
                refreshStack(stack);
            }
            // 饰品袋另外扫（如果启用）
            try {
                if (TomkesTookitConfig.enabled("item.trinket")) {
                    ItemStack[] bag = com.tomkestookit.trinket.TrinketStore.read(player.getUuid());
                    if (bag != null) {
                        for (ItemStack s : bag) {
                            if (s == null || s.isEmpty()) continue;
                            refreshStack(s);
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    /** 对单个 ItemStack 做重铸 modifier 热更新。返回是否修改过。*/
    public static boolean refreshStack(ItemStack stack) {
        if (stack.isEmpty()) return false;
        NbtCompound nbt = stack.getNbt();
        if (nbt == null || !nbt.contains("AttributeModifiers")) return false;
        // 快路径：装备根本没打过重铸，跳过
        if (!nbt.contains(ReforgeRegistry.APPLIED_TAG)) return false;

        NbtList attrs = nbt.getList("AttributeModifiers", 10);
        boolean changed = false;

        for (int i = 0; i < attrs.size(); i++) {
            NbtCompound m = attrs.getCompound(i);
            String name = m.getString("Name");
            if (name == null || !name.startsWith(MODIFIER_NAME_PREFIX)) continue;
            String reforgeId = name.substring(MODIFIER_NAME_PREFIX.length());
            ReforgeRegistry.ReforgeDef def = ReforgeRegistry.byId(reforgeId);
            if (def == null) continue;

            // 拿现有值
            double oldAmount = m.getDouble("Amount");
            int oldOp = m.getInt("Operation");
            String oldAttr = m.getString("AttributeName");

            boolean mismatch = Math.abs(oldAmount - def.amount()) > 1e-6
                || oldOp != def.operation()
                || !def.attribute().equals(oldAttr);
            if (!mismatch) continue;

            m.putDouble("Amount", def.amount());
            m.putInt("Operation", def.operation());
            m.putString("AttributeName", def.attribute());
            changed = true;
        }

        if (changed) {
            stack.setSubNbt("AttributeModifiers", attrs);
        }
        return changed;
    }
}
