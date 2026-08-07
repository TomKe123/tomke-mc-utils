package com.tomkestookit.reforge;

import com.tomkestookit.TomkesTookitConfig;
import com.tomkestookit.mixin.InteractionManagerAccessor;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 经验汲取（soul_mend）重铸石 tick 逻辑：
 *
 * 每 10 tick (0.5 秒) 扫描玩家 **整个物品栏**（36 主栏 + 副手 + 4 盔甲格），
 * 如果物品上有 soul_mend 属性且有损坏，消耗 1 点 XP 修 1 点耐久。
 *
 * 安全逻辑：如果待修复物品是主手且玩家正在破坏方块，跳过该物品避免打断挖掘。
 *
 * 经验消耗：从 progress 小数减（与 FlyCommand 类似），不够一级时扣等级。
 * 优先修损坏最重的。
 */
public final class AutoMenderHandler {

    private static final int INTERVAL_TICKS = 10; // 0.5 秒

    private AutoMenderHandler() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(AutoMenderHandler::tick);
    }

    private static void tick(MinecraftServer server) {
        if (!TomkesTookitConfig.enabled("item.reforge")) return;
        int ticks = server.getTicks();
        if (ticks % INTERVAL_TICKS != 0) return;

        ReforgeRegistry.ReforgeDef soulMend = ReforgeRegistry.byId("soul_mend");
        if (soulMend == null) return;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.isCreative() || player.isSpectator()) continue;
            // 没有经验就不处理
            if (player.experienceLevel <= 0 && player.experienceProgress <= 0.0f) continue;

            int mainHandSlot = player.getInventory().selectedSlot;
            boolean isBreaking = ((InteractionManagerAccessor) player.interactionManager).isMining();

            // 扫描整个物品栏：36 主栏 + 副手(40) + 4 盔甲(36-39)
            for (int slot = 0; slot < 41; slot++) {
                ItemStack stack = player.getInventory().getStack(slot);
                if (stack.isEmpty()) continue;
                if (!stack.isDamageable()) continue;
                if (!stack.isDamaged()) continue;
                if (!ReforgeRegistry.alreadyApplied(stack, soulMend)) continue;

                // 如果是主手物品且玩家正在挖方块，跳过（避免打断挖掘进度）
                if (slot == mainHandSlot && isBreaking) continue;

                // 找到损坏的且有 soul_mend 的物品 → 修 1 点
                if (tryConsumeXp(player)) {
                    stack.setDamage(stack.getDamage() - 1);
                }
                // 每次 tick 只修一个物品的一点耐久（避免经验消耗太猛）
                break;
            }
        }
    }

    /** 消耗约 1 点 XP；成功返回 true。*/
    private static boolean tryConsumeXp(ServerPlayerEntity player) {
        int level = player.experienceLevel;
        float progress = player.experienceProgress;
        if (level <= 0 && progress <= 0.0f) return false;

        int nextLevelXp = player.getNextLevelExperience();
        if (nextLevelXp <= 0) nextLevelXp = 1;
        float cost = 1.0f / nextLevelXp; // 1 点 XP 在 progress 中的比例

        progress -= cost;
        if (progress < 0.0f && level > 0) {
            level--;
            progress += 1.0f;
        }
        if (level == 0 && progress < 0.0f) {
            progress = 0.0f;
        }

        player.experienceLevel = level;
        player.experienceProgress = Math.max(0.0f, progress);
        player.totalExperience = computeTotalXp(level, player.experienceProgress);
        return true;
    }

    private static int computeTotalXp(int level, float progress) {
        int total = 0;
        for (int i = 0; i < level; i++) total += xpForLevelUp(i);
        total += Math.round(progress * xpForLevelUp(level));
        return total;
    }

    private static int xpForLevelUp(int level) {
        if (level >= 30) return 112 + (level - 30) * 9;
        if (level >= 15) return 37 + (level - 15) * 5;
        return 7 + level * 2;
    }
}
