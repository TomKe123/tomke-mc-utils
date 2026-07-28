package com.tomkestookit.mixin;

import com.tomkestookit.CarpetBotUtil;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 给 Carpet 假人的 tab 列表名字加 §c[假人] 前缀。
 * getPlayerListName 返回 null 时 vanilla 会 fallback 到玩家名字；
 * 我们在 RETURN 里改成 [假人] + 原名。
 */
@Mixin(ServerPlayerEntity.class)
public abstract class CarpetBotTabNameMixin {

    @Inject(method = "getPlayerListName", at = @At("RETURN"), cancellable = true)
    private void tomkestookit$prefixTag(CallbackInfoReturnable<Text> cir) {
        if (!com.tomkestookit.TomkesTookitConfig.enabled("world.bot_anchor")) return;
        ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;
        boolean bot = CarpetBotUtil.isFakePlayer(self);

        Text original = cir.getReturnValue();
        MutableText nameText;
        if (original != null) {
            nameText = original.copy();
        } else {
            nameText = Text.literal(self.getGameProfile().getName());
        }

        Text tag;
        if (bot) {
            tag = Text.literal("[假人] ").formatted(Formatting.RED, Formatting.BOLD);
        } else {
            tag = Text.literal("[玩家] ").formatted(Formatting.GREEN, Formatting.BOLD);
        }
        // 名字本身保持原色（假人灰，玩家白）
        MutableText body = bot
            ? nameText.copy().formatted(Formatting.GRAY)
            : nameText.copy().formatted(Formatting.WHITE);
        cir.setReturnValue(Text.empty().append(tag).append(body));
    }
}
