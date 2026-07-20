package com.starterkit.recipe;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.potion.PotionUtil;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.List;

/**
 * 特殊配方：3x3 全部填 minecraft:bread 合成一瓶带"永久饱和"效果的喷溅药水。
 *
 *   - 走 SpecialCraftingRecipe 是因为 vanilla 的 crafting_shaped json 无法
 *     在 result 里带自定义 NBT，而喷溅药水的自定义效果必须放 NBT。
 *   - 效果用 StatusEffectInstance(SATURATION, Integer.MAX_VALUE) 表示"永久"，
 *     与 /effect give … saturation infinite 等价。
 *   - amplifier=0（1 级）就够——SATURATION 每 tick 恢复 1 饱和度 + 0.4 饥饿。
 */
public final class SplashSaturationRecipe extends SpecialCraftingRecipe {

    public SplashSaturationRecipe(Identifier id, CraftingRecipeCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(RecipeInputInventory inv, World world) {
        if (inv.size() < 9) return false;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isEmpty() || stack.getItem() != Items.BREAD) return false;
        }
        return true;
    }

    @Override
    public ItemStack craft(RecipeInputInventory inv, DynamicRegistryManager registries) {
        return buildResult();
    }

    @Override
    public boolean fits(int width, int height) {
        return width == 3 && height == 3;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return StarterKitRecipes.SPLASH_SATURATION;
    }

    /** 手动构造结果物品——菜单展示和实际合成用同一份，行为一致。*/
    public static ItemStack buildResult() {
        ItemStack stack = new ItemStack(Items.SPLASH_POTION);

        // Integer.MAX_VALUE = 永久（vanilla 客户端 tooltip 显示为 "**:**"）
        StatusEffectInstance saturation = new StatusEffectInstance(
            StatusEffects.SATURATION, Integer.MAX_VALUE, 0,
            false, false, true);
        PotionUtil.setCustomPotionEffects(stack, List.of(saturation));

        // 命名 + 说明 + 隐藏 vanilla 附加 tooltip（省得同时显示 Uncraftable Potion + 我们的效果）
        stack.setCustomName(Text.literal("面包精华")
            .styled(s -> s.withItalic(false).withBold(true)));

        NbtCompound nbt = stack.getOrCreateNbt();
        NbtCompound display = nbt.getCompound("display");
        NbtList lore = new NbtList();
        lore.add(NbtString.of(Text.Serializer.toJson(
            Text.literal("§7面粉与劳动的浓缩")
                .styled(s -> s.withItalic(false)))));
        display.put("Lore", lore);
        nbt.put("display", display);

        return stack;
    }
}
