package com.starterkit.mixin;

import net.minecraft.command.EntitySelector;
import net.minecraft.server.command.ServerCommandSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让 @a / @e / @r / @p / @s 等选择器对所有玩家可用。
 *
 * 原版在 EntitySelector.getEntities/getPlayers 内部会先调用 checkSourcePermission，
 * 该方法要求 source.hasPermissionLevel(2)，否则抛 NOT_ALLOWED_EXCEPTION。
 * 我们在方法头 return 掉，绕开该检查。
 *
 * 触发原因：兼容 Xaero's World Map 之类的客户端 mod 生成的 tp 指令，
 *   例如 /execute in minecraft:overworld run tp @s 100 64 100
 * ——运行时的 @s 会被上述检查拦下。
 *
 * 影响范围：全局，任何用到实体选择器的命令都不再需要 OP 权限。
 * 由于本 mod 的整体策略已经是"服务端命令对所有玩家开放"（/tp、/carpet 等
 * 也已放开），这与既有取向一致。
 */
@Mixin(EntitySelector.class)
public class EntitySelectorPermissionMixin {

    @Inject(method = "checkSourcePermission", at = @At("HEAD"), cancellable = true)
    private void starterkit$allowSelectorForAll(ServerCommandSource source, CallbackInfo ci) {
        ci.cancel();
    }
}
