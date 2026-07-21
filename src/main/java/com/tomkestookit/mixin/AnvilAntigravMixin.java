package com.tomkestookit.mixin;

import com.tomkestookit.AntigravBootsHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.Property;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 铁砧特殊配方：任意靴子（左槽）+ 反重力皮靴（右槽）→ 该靴子获得反重力属性。
 *
 * 拦截点：AnvilScreenHandler.updateResult @At("RETURN") —— 挂在所有 return 前，
 * 覆盖 vanilla 已经算完的输出。必须用 RETURN 而不是 TAIL：vanilla 遇到
 * 不兼容组合（如铁靴 + 皮靴）会走"清空输出 + 提前 return"路径，TAIL 抓不到；
 * 用 RETURN 覆盖所有 return 点，保证我们能拿到最后决策权。
 *
 * 属性构造统一走 AntigravBootsHandler.applyAntigrav —— 与 Kit 掉落一致：
 *   - NBT: TomkesTookitAntigrav = true, Unbreakable = true
 *   - AttributeModifiers: 复制目标靴的 vanilla 护甲/韧性/抗击退 + 速度 x3
 *
 * 保留物品：目标靴的名字/附魔/耐久/自定义 NBT 全部保留（在 copy 后加属性），
 * 由 vanilla ItemStack.copy() 保证；仅覆盖 AttributeModifiers。
 *
 * 消耗与费用：
 *   - onTakeOutput 走 vanilla：从两个输入槽各扣一份（左靴变成结果放到输出，
 *     右反重力皮靴消耗），符合铁砧标准行为
 *   - levelCost = 10
 */
@Mixin(AnvilScreenHandler.class)
public abstract class AnvilAntigravMixin {

    @Shadow @Final private Property levelCost;

    /**
     * 通过 ForgingScreenHandler 提供的 protected 字段访问 input/output。
     * mixin 无法直接 @Shadow 父类的 protected final 字段，改用类型转换调用
     * ScreenHandler.getSlot() 拿槽内容，语义与直接读 input/output 一致。
     */
    @Inject(method = "updateResult", at = @At("RETURN"))
    private void tomkestookit$antigravAnvil(CallbackInfo ci) {
        AnvilScreenHandler self = (AnvilScreenHandler) (Object) this;
        ItemStack left = self.getSlot(0).getStack();
        ItemStack right = self.getSlot(1).getStack();

        if (!AntigravBootsHandler.isBoots(left)) return;
        if (!AntigravBootsHandler.hasAntigrav(right)) return;
        if (AntigravBootsHandler.hasAntigrav(left)) return; // 已经反重力过，不重复
        // 不检查 vanilla 是否已给出结果——因为左是皮靴时 vanilla 会当作
        // "合并附魔"给出普通皮靴，我们要覆盖成反重力版

        ItemStack result = left.copy();
        AntigravBootsHandler.applyAntigrav(result);

        // 名字：若目标没自定义名，加上"反重力 "前缀作为提示；有则保留
        if (!result.hasCustomName()) {
            result.setCustomName(
                Text.literal("反重力").formatted(Formatting.RED)
                    .append(Text.literal(" ").formatted(Formatting.RESET))
                    .append(result.getItem().getName().copy().formatted(Formatting.WHITE))
            );
        }

        // 覆盖 vanilla 已经写入的输出槽内容 + 设置 XP 费用
        // Slot.setStack 修改底层 inventory，next tick 会自动同步给客户端；
        // Property.set 有 Property 自带的 change-detection 同步机制
        self.getSlot(2).setStack(result);
        this.levelCost.set(10);
        self.sendContentUpdates();
    }
}
