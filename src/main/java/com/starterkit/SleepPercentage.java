package com.starterkit;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.GameRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 把 playersSleepingPercentage 设为 0：任意一个玩家躺床就能跳过夜晚
 * （vanilla 保底至少需要 1 个玩家在睡，0% 就是"1 个人即可"）。
 *
 * 每次服务器启动都强制写一次，覆盖存档里可能残留的旧值。
 */
public final class SleepPercentage {

    private static final Logger LOGGER = LoggerFactory.getLogger(StarterKitMod.MOD_ID);
    private static final int TARGET = 0;

    private SleepPercentage() {}

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(SleepPercentage::apply);
    }

    private static void apply(MinecraftServer server) {
        GameRules.IntRule rule = server.getGameRules().get(GameRules.PLAYERS_SLEEPING_PERCENTAGE);
        if (rule.get() != TARGET) {
            rule.set(TARGET, server);
            LOGGER.info("[StarterKit] playersSleepingPercentage 已设为 {}（一人睡即跳夜）。", TARGET);
        }
    }
}
