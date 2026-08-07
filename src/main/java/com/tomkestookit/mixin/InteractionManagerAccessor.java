package com.tomkestookit.mixin;

import net.minecraft.server.network.ServerPlayerInteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor mixin：暴露 ServerPlayerInteractionManager 的 private mining 字段。
 * 用于 AutoMenderHandler 判断玩家是否正在破坏方块（避免修复打断挖掘）。
 */
@Mixin(ServerPlayerInteractionManager.class)
public interface InteractionManagerAccessor {
    @Accessor("mining")
    boolean isMining();
}
