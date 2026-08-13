package com.tomkestookit.mixin;

import com.tomkestookit.materials.OriginalWeaponRegistry;
import com.tomkestookit.reforge.ReforgeRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.Property;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 铁砧重铸：装备(左) + 重铸石(右) → 装备追加属性。
 *
 * v2 新增：跨装备重铸合并
 *   - 装备A(左) + 装备B(右)，两者都有重铸 → 输出槽 = A 合并 B 的重铸（剔除不适配A的）
 *   - 特殊武器禁止改名：铁砧输入框输入任何名字都不生效，保留原有格式化名称
 *
 * 兼容性：
 *   - 不覆盖已有属性，追加到 AttributeModifiers 列表末尾
 *   - 每块重铸石通过 UUID 去重，同一块不能打两次
 *   - 对任意 mod 装备生效（只要通过 canApply 判定）
 *   - priority = 1100，在 AnvilAntigravMixin(1000) 之后运行
 */
@Mixin(value = AnvilScreenHandler.class, priority = 1100)
public abstract class AnvilReforgeMixin {

    @Shadow @Final private Property levelCost;
    @Shadow private String newItemName;

    @Inject(method = "updateResult", at = @At("RETURN"))
    private void tomkestookit$reforgeAnvil(CallbackInfo ci) {
        if (!com.tomkestookit.TomkesTookitConfig.enabled("item.reforge")) return;

        AnvilScreenHandler self = (AnvilScreenHandler) (Object) this;
        ItemStack left = self.getSlot(0).getStack();
        ItemStack right = self.getSlot(1).getStack();

        boolean leftIsSpecial = !left.isEmpty() && OriginalWeaponRegistry.getId(left) != null;

        if (left.isEmpty() || right.isEmpty()) {
            // 特殊武器单独放入铁砧改名 → 无右侧物品时也强制保留原名
            if (leftIsSpecial) {
                ItemStack out = self.getSlot(2).getStack();
                if (!out.isEmpty() && OriginalWeaponRegistry.getId(out) != null) {
                    restoreFormattedName(out, left);
                    self.getSlot(2).setStack(out); // 触发同步
                }
            }
            return;
        }

        // ---- 路径 1：左装备 + 右重铸石 → 追加属性 ----
        String reforgeId = ReforgeRegistry.getId(right);
        if (reforgeId != null) {
            ReforgeRegistry.ReforgeDef def = ReforgeRegistry.byId(reforgeId);
            if (def != null && def.canApply().test(left) && !ReforgeRegistry.alreadyApplied(left, def)) {
                ItemStack result = ReforgeRegistry.applyReforge(left, def);

                if (leftIsSpecial) restoreFormattedName(result, left);

                self.getSlot(2).setStack(result);
                this.levelCost.set(def.anvilCost());
                return;
            }
        }

        // ---- 路径 2：左装备A + 右装备B（B有重铸） → 把B的适配重铸合并到A ----
        NbtCompound rightNbt = right.getNbt();
        if (rightNbt != null && rightNbt.contains(ReforgeRegistry.APPLIED_TAG)) {

            NbtList rightApplied = rightNbt.getList(ReforgeRegistry.APPLIED_TAG, 8);

            java.util.Set<String> leftIds = new java.util.HashSet<>();
            NbtCompound leftNbt = left.getNbt();
            if (leftNbt != null && leftNbt.contains(ReforgeRegistry.APPLIED_TAG)) {
                NbtList leftApplied = leftNbt.getList(ReforgeRegistry.APPLIED_TAG, 8);
                for (int i = 0; i < leftApplied.size(); i++) {
                    leftIds.add(leftApplied.getString(i));
                }
            }

            java.util.List<ReforgeRegistry.ReforgeDef> toMerge = new java.util.ArrayList<>();
            for (int i = 0; i < rightApplied.size(); i++) {
                String rightId = rightApplied.getString(i);
                if (!leftIds.contains(rightId)) {
                    ReforgeRegistry.ReforgeDef def = ReforgeRegistry.byId(rightId);
                    if (def != null && def.canApply().test(left)) {
                        toMerge.add(def);
                    }
                }
            }

            if (!toMerge.isEmpty()) {
                ItemStack result = left.copy();
                int totalCost = 0;
                for (ReforgeRegistry.ReforgeDef def : toMerge) {
                    result = ReforgeRegistry.applyReforge(result, def);
                    totalCost += def.anvilCost();
                }

                if (leftIsSpecial) restoreFormattedName(result, left);

                self.getSlot(2).setStack(result);
                this.levelCost.set(Math.min(totalCost, 39));
                return;
            }
        }

        // ---- 兜底：左侧是特殊武器但没走上面任何一条路径，仍然强制恢复输出的名称 ----
        // 例如 vanilla 生成了合并结果（同类型装备耐久合并），我们只改名不改结果
        if (leftIsSpecial) {
            ItemStack out = self.getSlot(2).getStack();
            if (!out.isEmpty() && OriginalWeaponRegistry.getId(out) != null) {
                restoreFormattedName(out, left);
                self.getSlot(2).setStack(out);
            }
        }
    }

    /**
     * 恢复特殊武器的格式化名称（带颜色、加粗等）。
     * 从 source 读取原始 display.Name，覆盖到 target。
     */
    private void restoreFormattedName(ItemStack target, ItemStack source) {
        NbtCompound srcNbt = source.getNbt();
        if (srcNbt == null || !srcNbt.contains("display")) return;

        NbtCompound srcDisplay = srcNbt.getCompound("display");
        if (!srcDisplay.contains("Name")) return;

        NbtCompound targetNbt = target.getOrCreateNbt();
        NbtCompound targetDisplay = targetNbt.contains("display")
            ? targetNbt.getCompound("display")
            : new NbtCompound();

        // 复制原始格式化 Name（JSON Text Component）
        targetDisplay.put("Name", srcDisplay.get("Name").copy());
        targetNbt.put("display", targetDisplay);

        // 清空铁砧改名字段，防止下次 updateResult 覆盖
        this.newItemName = "";
    }
}
