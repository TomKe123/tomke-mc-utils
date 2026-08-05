package com.tomkestookit.mixin;

import com.tomkestookit.SignCommandHandler;
import com.tomkestookit.SignEditPrompt;
import com.tomkestookit.menu.MenuUi;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSignC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shift+F 打开主菜单 + 告示牌输入拦截。
 *
 * 注意：聊天拦截已移至 {@link com.tomkestookit.ChatInterceptHandler}，
 * 使用 Fabric ServerMessageEvents.ALLOW_CHAT_MESSAGE，避免签名链断裂踢人。
 */
@Mixin(ServerPlayNetworkHandler.class)
public class ServerPlayNetworkHandlerMixin {

    @Shadow @Final public ServerPlayerEntity player;

    @Inject(method = "onPlayerAction", at = @At("HEAD"), cancellable = true)
    private void tomkestookit$shiftFOpensMenu(PlayerActionC2SPacket packet, CallbackInfo ci) {
        if (packet.getAction() != PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND) return;
        if (!player.isSneaking()) return;

        if (SignCommandHandler.isSignItem(player.getMainHandStack())) {
            if (!com.tomkestookit.TomkesTookitConfig.enabled("item.sign_command")) return;
            SignCommandHandler.tryStartItemBinding(player);
            ci.cancel();
            return;
        }
        if (!com.tomkestookit.TomkesTookitConfig.enabled("cmd.menu")) return;
        MenuUi.open(player);
        ci.cancel();
    }

    @Inject(method = "onUpdateSign", at = @At("HEAD"), cancellable = true)
    private void tomkestookit$signPromptSubmit(UpdateSignC2SPacket packet, CallbackInfo ci) {
        if (SignEditPrompt.submit(player.getUuid(), packet.getPos(), packet.getText())) {
            ci.cancel();
        }
    }
}
