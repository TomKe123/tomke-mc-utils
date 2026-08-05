package com.tomkestookit.reforge;

import com.tomkestookit.TomkesTookitConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 经验汲取（soul_mend）重铸石 tick 逻辑：
 *
 * 每 10 tick (0.5 秒) 扫描玩家全身装备（主手、副手、4 盔甲格），
 * 如果装备上有 soul_mend 属性且有损坏，消耗 1 点 XP 修 1 点耐久。
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

            // 扫描所有装备槽位
            ItemStack[] equipment = {
                player.getInventory().getStack(player.getInventory().selectedSlot), // 主手
                player.getOffHandStack(),                                           // 副手
                player.getInventory().getStack(36), // 靴
                player.getInventory().getStack(37), // 腿
                player.getInventory().getStack(38), // 胸
                player.getInventory().getStack(39)  // 头
            };

            for (ItemStack stack : equipment) {
                if (stack.isEmpty()) continue;
                if (!stack.isDamageable()) continue;
                if (!stack.isDamaged()) continue;
                if (!ReforgeRegistry.alreadyApplied(stack, soulMend)) continue;

                // 找到损坏的且有 soul_mend 的装备 → 修 1 点
                if (tryConsumeXp(player)) {
                    stack.setDamage(stack.getDamage() - 1);
                }
                // 每次 tick 只修一个装备的一点耐久（避免经验消耗太猛）
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
