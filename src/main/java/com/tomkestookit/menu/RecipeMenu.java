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
 * 分 合成 / 切石 / 魔改配方 / 饰品 / 重铸石 五个 tab。仅展示，不发放物品。
 *
 * 布局 9x6：
 *   row 0: [tab 合成 0] [tab 切石 1] [tab 魔改 2] [tab 饰品 3] [tab 重铸石 4] . . . .
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
        STONECUTTING("切石", Items.STONECUTTER),
        SPECIAL("魔改配方", Items.NETHER_STAR),
        TRINKET("饰品", Items.AMETHYST_SHARD),
        REFORGE("重铸石", Items.ANVIL),
        WEAPON("武器", Items.NETHERITE_SWORD),
        BOSS("Boss令牌", Items.NETHER_STAR);

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

    /** 直接跳到"魔改配方"tab。给主菜单入口用。*/
    public static void openSpecial(ServerPlayerEntity viewer) {
        open(viewer, Tab.SPECIAL, 0);
    }

    private static void open(ServerPlayerEntity viewer, Tab tab, int page) {
        Inventory inv = MenuScreenHandler.buildInventory(6);
        Map<Integer, Consumer<ServerPlayerEntity>> actions = new HashMap<>();

        // tabs (5 个)
        Tab[] tabs = Tab.values();
        for (int t = 0; t < tabs.length; t++) {
            Tab tt = tabs[t];
            inv.setStack(t, tabButton(tt, countOf(viewer.getServer(), tt), tab == tt));
            actions.put(t, p -> open(p, tt, 0));
        }

        int totalPages;
        if (tab == Tab.SPECIAL || tab == Tab.TRINKET || tab == Tab.REFORGE
                || tab == Tab.WEAPON || tab == Tab.BOSS) {
            totalPages = renderSpecialTab(viewer, inv, actions, page, tab);
        } else {
            totalPages = renderRecipeTab(viewer, inv, tab, page);
        }
        page = Math.max(0, Math.min(page, totalPages - 1));

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
            default -> null;
        };
        if (type == null) return List.of();
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
        return switch (tab) {
            case SPECIAL -> SpecialRecipeCatalog.byCategory(SpecialRecipeCatalog.Category.CRAFTING).size();
            case TRINKET -> SpecialRecipeCatalog.byCategory(SpecialRecipeCatalog.Category.TRINKET).size();
            case REFORGE -> SpecialRecipeCatalog.byCategory(SpecialRecipeCatalog.Category.REFORGE).size();
            case WEAPON  -> SpecialRecipeCatalog.byCategory(SpecialRecipeCatalog.Category.WEAPON).size();
            case BOSS    -> SpecialRecipeCatalog.byCategory(SpecialRecipeCatalog.Category.BOSS).size();
            default -> collect(server, tab).size();
        };
    }

    /** 常规 tab 渲染到 inv，返回总页数。*/
    private static int renderRecipeTab(ServerPlayerEntity viewer, Inventory inv, Tab tab, int page) {
        List<Recipe<?>> list = collect(viewer.getServer(), tab);
        int totalPages = Math.max(1, (list.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, totalPages - 1));
        if (list.isEmpty()) {
            inv.setStack(22, MenuButtons.button(Items.PAPER, "§7暂无配方",
                List.of("§8该类型没有本包配方")));
            return totalPages;
        }
        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, list.size());
        DynamicRegistryManager drm = viewer.getServer().getRegistryManager();
        for (int i = start; i < end; i++) {
            inv.setStack(FIRST_SLOT + (i - start), recipeIcon(list.get(i), drm));
        }
        return totalPages;
    }

    /** 特殊 tab 渲染到 inv，返回总页数。槽位可以点击打开详情。*/
    private static int renderSpecialTab(ServerPlayerEntity viewer, Inventory inv,
                                        Map<Integer, Consumer<ServerPlayerEntity>> actions, int page, Tab tab) {
        SpecialRecipeCatalog.Category cat = switch (tab) {
            case TRINKET -> SpecialRecipeCatalog.Category.TRINKET;
            case REFORGE -> SpecialRecipeCatalog.Category.REFORGE;
            case WEAPON  -> SpecialRecipeCatalog.Category.WEAPON;
            case BOSS    -> SpecialRecipeCatalog.Category.BOSS;
            default -> SpecialRecipeCatalog.Category.CRAFTING;
        };
        List<SpecialRecipeEntry> list = SpecialRecipeCatalog.byCategory(cat);
        int totalPages = Math.max(1, (list.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, totalPages - 1));
        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, list.size());
        for (int i = start; i < end; i++) {
            SpecialRecipeEntry entry = list.get(i);
            int slot = FIRST_SLOT + (i - start);
            inv.setStack(slot, buildSpecialIcon(entry));
            final String key = entry.key();
            actions.put(slot, p -> openSpecialDetail(p, key));
        }
        return totalPages;
    }

    private static ItemStack buildSpecialIcon(SpecialRecipeEntry entry) {
        ItemStack icon = entry.resultSupplier().get().copy();
        List<String> lore = new ArrayList<>();
        lore.add("§7—— 3×3 合成图示 ——");
        if (entry.key().startsWith("reforge_")) {
            lore.add("§7合成得石头 §8· §7铁砧敲装备");
        }
        lore.addAll(entry.description());
        lore.add("");
        lore.add("§8点击查看详细做法");
        applyLoreOverride(icon, "§d§l" + icon.getName().getString(), lore);
        return icon;
    }

    /** special detail：把产物放中心 4，周围 3×3 显示配方，四角 lore 说明。*/
    public static void openSpecialDetail(ServerPlayerEntity viewer, String key) {
        SpecialRecipeEntry entry = null;
        for (SpecialRecipeEntry e : SpecialRecipeCatalog.all()) {
            if (e.key().equals(key)) { entry = e; break; }
        }
        if (entry == null) { open(viewer); return; }

        Inventory inv = MenuScreenHandler.buildInventory(6);
        Map<Integer, Consumer<ServerPlayerEntity>> actions = new HashMap<>();

        // 顶部：产物大图
        ItemStack productDisplay = entry.resultSupplier().get().copy();
        applyLoreOverride(productDisplay, "§d§l" + productDisplay.getName().getString(), entry.description());
        inv.setStack(4, productDisplay);

        // 中部 3×3 图示（位置 20-22 / 29-31 / 38-40）
        int[] gridSlots = {20, 21, 22, 29, 30, 31, 38, 39, 40};
        for (int i = 0; i < 9; i++) {
            String linkTarget = entry.gridLinkTarget(i);
            if (linkTarget != null) {
                // 有跳转链接：用目标配方的产物 ItemStack 作为 icon（带 NBT/附魔）
                SpecialRecipeEntry target = null;
                for (SpecialRecipeEntry e : SpecialRecipeCatalog.all()) {
                    if (e.key().equals(linkTarget)) { target = e; break; }
                }
                if (target != null) {
                    ItemStack icon = target.resultSupplier().get().copy();
                    // 追加一行提示，说明可点击
                    appendClickHint(icon, "§e▶ 点击查看该配方");
                    inv.setStack(gridSlots[i], icon);
                    final String targetKey = linkTarget;
                    actions.put(gridSlots[i], p -> openSpecialDetail(p, targetKey));
                    continue;
                }
            }
            ItemStack g = entry.gridStack(i);
            if (!g.isEmpty()) inv.setStack(gridSlots[i], g);
        }

        // 返回：根据 key 判断该配方在哪个 tab
        inv.setStack(BACK_SLOT, MenuButtons.backButton());
        SpecialRecipeEntry captured = entry;
        Tab returnTab;
        if (key.startsWith("trinket_")) returnTab = Tab.TRINKET;
        else if (key.startsWith("reforge_")) returnTab = Tab.REFORGE;
        else if (key.startsWith("weapon_")) returnTab = Tab.WEAPON;
        else if (key.startsWith("boss_")) returnTab = Tab.BOSS;
        else returnTab = Tab.SPECIAL;
        final Tab returnTabRef = returnTab;
        actions.put(BACK_SLOT, p -> open(p, returnTabRef, 0));

        MenuButtons.fillBackground(inv);

        viewer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, playerInv, ignored) -> new MenuScreenHandler(syncId, playerInv, inv, actions),
            Text.literal("§d§l§n" + captured.resultSupplier().get().getName().getString())
        ));
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

    /** 在物品末尾追加一行提示（不影响原名字）。*/
    private static void appendClickHint(ItemStack stack, String hint) {
        net.minecraft.nbt.NbtCompound nbt = stack.getOrCreateNbt();
        net.minecraft.nbt.NbtCompound display = nbt.getCompound("display");
        net.minecraft.nbt.NbtList lore = display.contains("Lore")
            ? display.getList("Lore", 8)
            : new net.minecraft.nbt.NbtList();
        lore.add(net.minecraft.nbt.NbtString.of(Text.Serializer.toJson(
            Text.literal(hint).styled(s -> s.withItalic(false)))));
        display.put("Lore", lore);
        nbt.put("display", display);
        nbt.putInt("HideFlags", 63);
    }
}
