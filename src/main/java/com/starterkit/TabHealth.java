package com.starterkit;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;

/**
 * Tab 菜单玩家血量可视化：注册一个 HEALTH criterion 的 objective 到 LIST 显示槽，
 * 使用 HEARTS 渲染类型。vanilla 会自动同步每个玩家的血量到对应 score，
 * 客户端在 tab 列表玩家名旁渲染红心图标。
 *
 *   - HEALTH 是内置只读 criterion；服务端每 tick 自动写入所有玩家的当前血量。
 *   - 效果等同于原版命令：
 *       /scoreboard objectives add hp health
 *       /scoreboard objectives modify hp rendertype hearts
 *       /scoreboard objectives setdisplay list hp
 */
public final class TabHealth {

    private static final String OBJECTIVE_ID = "starterkit_tab_health";

    private TabHealth() {}

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(TabHealth::install);
    }

    private static void install(MinecraftServer server) {
        ServerScoreboard board = server.getScoreboard();
        ScoreboardObjective existing = board.getNullableObjective(OBJECTIVE_ID);
        if (existing != null) board.removeObjective(existing);

        ScoreboardObjective obj = board.addObjective(
            OBJECTIVE_ID,
            ScoreboardCriterion.HEALTH,
            Text.literal("血量"),
            ScoreboardCriterion.RenderType.HEARTS
        );
        board.setObjectiveSlot(Scoreboard.LIST_DISPLAY_SLOT_ID, obj);
    }
}
