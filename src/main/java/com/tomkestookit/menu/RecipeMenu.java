package com.tomkestookit.menu;

import com.tomkestookit.TomkesTookitMod;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.recipe.ShapelessRecipe;
import net.minecraft.recipe.StonecuttingRecipe;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * "本包配方" 子菜单：展示 namespace = tomkestookit 的所有配方，
 * 分 crafting / stonecutting 两个 tab。仅展示，不发放物品。
 *
 * 布局 9x6：
 *   row 0: [tab 合成 0] [tab 切石 1] . . . . . . .
 *   row 1-4: 配方格 9-44 共 36 个
 *   row 5: [◀ 45] . . . [返回 49] . . . [▶ 53]
 */
public final class RecipeMenu {

    private static final int PAGE_SIZE = 36;
    private static final int FIRST_SLOT = 9;
    private static final int PREV_SLOT = 45;
    private static final int BACK_SLOT = 49;
    private static final int NEXT_SLOT = 53;
    private static final Text TITLE = Text.literal("§b§l§n本包配方");

    private enum Tab {
        CRAFTING("合成", Items.CRAFTING_TABLE),
        STONECUTTING("切石", Items.STONECUTTER);

        final String label;
        final net.minecraft.item.Item icon;

        Tab(String label, net.minecraft.item.Item icon) {
            this.label = label;
            this.icon = icon;
        }
    }

    private RecipeMenu() {}

    public static void open(ServerPlayerEntity viewer) {
        open(viewer, Tab.CRAFTING, 0);
    }

    public static void open(ServerPlayerEntity viewer, Tab tab, int page) {
        List<Recipe<?>> list = collect(viewer.getServer(), tab);
        int totalPages = Math.max(1, (list.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, totalPages - 1));

        Inventory inv = MenuScreenHandler.buildInventory(6);
        Map<Integer, Consumer<ServerPlayerEntity>> actions = new HashMap<>();

        // tabs
        inv.setStack(0, tabButton(Tab.CRAFTING,
            countOf(viewer.getServer(), Tab.CRAFTING), tab == Tab.CRAFTING));
        actions.put(0, p -> open(p, Tab.CRAFTING, 0));
        inv.setStack(1, tabButton(Tab.STONECUTTING,
            countOf(viewer.getServer(), Tab.STONECUTTING), tab == Tab.STONECUTTING));
        actions.put(1, p -> open(p, Tab.STONECUTTING, 0));

        // 页内容
        if (list.isEmpty()) {
            inv.setStack(22, MenuButtons.button(Items.PAPER, "§7暂无配方",
                List.of("§8该类型没有本包配方")));
        } else {
            int start = page * PAGE_SIZE;
            int end = Math.min(start + PAGE_SIZE, list.size());
            DynamicRegistryManager drm = viewer.getServer().getRegistryManager();
            for (int i = start; i < end; i++) {
                Recipe<?> recipe = list.get(i);
                int slot = FIRST_SLOT + (i - start);
                inv.setStack(slot, recipeIcon(recipe, drm));
                // 无 action：只展示
            }
        }

        // 分页 & 返回
        Tab tabRef = tab;
        boolean hasPrev = page > 0;
        boolean hasNext = page < totalPages - 1;
        inv.setStack(PREV_SLOT, MenuButtons.pageButton(false, page, totalPages, hasPrev));
        if (hasPrev) {
            final int prev = page - 1;
            actions.put(PREV_SLOT, p -> open(p, tabRef, prev));
        }
        inv.setStack(NEXT_SLOT, MenuButtons.pageButton(true, page, totalPages, hasNext));
        if (hasNext) {
            final int next = page + 1;
            actions.put(NEXT_SLOT, p -> open(p, tabRef, next));
        }

        inv.setStack(BACK_SLOT, MenuButtons.backButton());
        actions.put(BACK_SLOT, MenuUi::open);

        MenuButtons.fillBackground(inv);

        viewer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, playerInv, ignored) -> new MenuScreenHandler(syncId, playerInv, inv, actions),
            TITLE
        ));
    }

    // ------------------------------------------------------------------ data

    private static List<Recipe<?>> collect(MinecraftServer server, Tab tab) {
        RecipeType<?> type = switch (tab) {
            case CRAFTING -> RecipeType.CRAFTING;
            case STONECUTTING -> RecipeType.STONECUTTING;
        };
        List<Recipe<?>> result = new ArrayList<>();
        for (Recipe<?> r : server.getRecipeManager().values()) {
            if (!r.getType().equals(type)) continue;
            if (!TomkesTookitMod.MOD_ID.equals(r.getId().getNamespace())) continue;
            result.add(r);
        }
        // 按 id path 升序，展示顺序稳定
        result.sort(Comparator.comparing(r -> r.getId().getPath()));
        return result;
    }

    private static int countOf(MinecraftServer server, Tab tab) {
        return collect(server, tab).size();
    }

    // ------------------------------------------------------------------ items

    private static ItemStack tabButton(Tab tab, int count, boolean selected) {
        List<String> lore = List.of(
            "§7配方 §f" + count + " §7个",
            selected ? "§a● 当前 tab" : "§8○ 点击切换"
        );
        ItemStack stack = MenuButtons.button(tab.icon, "§f§l" + tab.label, lore);
        if (selected) MenuButtons.withGlow(stack);
        return stack;
    }

    private static ItemStack recipeIcon(Recipe<?> recipe, DynamicRegistryManager drm) {
        ItemStack output = recipe.getOutput(drm).copy();
        Identifier id = recipe.getId();

        List<String> lore = new ArrayList<>();
        lore.add("§7类型 §f" + describeType(recipe));

        List<String> inputs = describeIngredients(recipe);
        if (!inputs.isEmpty()) {
            lore.add("§7输入：");
            for (String s : inputs) lore.add("  §f" + s);
        }

        if (recipe instanceof ShapedRecipe shaped) {
            lore.add("§7形状 §f" + shaped.getWidth() + "×" + shaped.getHeight());
        }

        lore.add("");
        lore.add("§8" + id);

        // 保留输出 ItemStack 的原有 name / NBT，用新的 lore 覆盖显示
        applyLoreOverride(output, "§e§l" + output.getItem().getName().getString()
            + " §7× " + output.getCount(), lore);
        return output;
    }

    private static String describeType(Recipe<?> recipe) {
        if (recipe instanceof ShapedRecipe) return "有序合成";
        if (recipe instanceof ShapelessRecipe) return "无序合成";
        if (recipe instanceof StonecuttingRecipe) return "切石";
        return recipe.getType().toString();
    }

    /**
     * 把每种输入物品 + 数量归并到一起，返回可读列表。
     * Ingredient 里 alternatives 用第一个 matchingStack 代表。
     */
    private static List<String> describeIngredients(Recipe<?> recipe) {
        DefaultedList<Ingredient> ingredients;
        try {
            ingredients = recipe.getIngredients();
        } catch (Exception e) {
            return List.of();
        }

        Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (Ingredient ing : ingredients) {
            if (ing == null || ing.isEmpty()) continue;
            ItemStack[] alts = ing.getMatchingStacks();
            if (alts.length == 0) continue;
            String label = alts[0].getItem().getName().getString();
            if (alts.length > 1) label += " §8等" + alts.length + "种";
            counts.merge(label, 1, Integer::sum);
        }
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            out.add(e.getValue() == 1 ? e.getKey() : e.getKey() + " §7× " + e.getValue());
        }
        return out;
    }

    /**
     * 覆盖物品的 CustomName 和 Lore，用于菜单展示——不修改物品本身逻辑，
     * 但因为 output.copy() 已经复制过，直接改这个 stack 不会影响真实配方产物。
     */
    private static void applyLoreOverride(ItemStack stack, String name, List<String> loreLines) {
        stack.setCustomName(Text.literal(name).styled(s -> s.withItalic(false)));
        net.minecraft.nbt.NbtCompound nbt = stack.getOrCreateNbt();
        net.minecraft.nbt.NbtCompound display = nbt.getCompound("display");
        net.minecraft.nbt.NbtList lore = new net.minecraft.nbt.NbtList();
        for (String line : loreLines) {
            lore.add(net.minecraft.nbt.NbtString.of(Text.Serializer.toJson(
                Text.literal(line).styled(s -> s.withItalic(false)))));
        }
        display.put("Lore", lore);
        nbt.put("display", display);
        nbt.putInt("HideFlags", 63);
    }
}
