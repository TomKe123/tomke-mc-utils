package com.tomkestookit;

import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeMatcher;

/** 供自动合成器查配方用的 3x3 虚拟合成输入。*/
public final class AutoCrafterRecipeInput implements RecipeInputInventory {
    private final ItemStack[] stacks = new ItemStack[9];

    public AutoCrafterRecipeInput(Inventory inv) {
        for (int i = 0; i < stacks.length; i++) {
            stacks[i] = inv.getStack(i);
        }
    }

    @Override public int getWidth() { return 3; }
    @Override public int getHeight() { return 3; }
    @Override public java.util.List<ItemStack> getInputStacks() { return java.util.Arrays.asList(stacks); }

    @Override public int size() { return stacks.length; }
    @Override public boolean isEmpty() {
        for (ItemStack stack : stacks) if (!stack.isEmpty()) return false;
        return true;
    }
    @Override public ItemStack getStack(int slot) { return stacks[slot]; }
    @Override public ItemStack removeStack(int slot, int amount) {
        return stacks[slot].split(amount);
    }
    @Override public ItemStack removeStack(int slot) {
        ItemStack result = stacks[slot];
        stacks[slot] = ItemStack.EMPTY;
        return result;
    }
    @Override public void setStack(int slot, ItemStack stack) { stacks[slot] = stack; }
    @Override public void markDirty() {}
    @Override public boolean canPlayerUse(net.minecraft.entity.player.PlayerEntity player) { return true; }
    @Override public void clear() { java.util.Arrays.fill(stacks, ItemStack.EMPTY); }
    @Override public void provideRecipeInputs(RecipeMatcher finder) {
        for (ItemStack stack : stacks) finder.addUnenchantedInput(stack);
    }
}
