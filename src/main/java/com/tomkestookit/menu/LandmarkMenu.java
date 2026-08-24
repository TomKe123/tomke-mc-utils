package com.tomkestookit.menu;

import com.tomkestookit.SignEditPrompt;
import com.tomkestookit.landmark.Landmark;
import com.tomkestookit.landmark.LandmarkStore;
import com.tomkestookit.landmark.LandmarkTeleport;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 地标子菜单。9x6 布局：
 *
 *   row 0: [◈ all] [◈ 主] [◈ 下] [◈ 末]  维度筛选 tab (槽 0-3)
 *   row 1-4: 地标格 9-44 共 36 个
 *   row 5: [◀ 45]  .  .  .  [返回 49]  .  .  .  [▶ 53]
 *
 * 选中 tab 加发光光效。
 * 列表条目：左键 传送；右键 进入详细页（重命名 / 换图标 / 删除）。
 */
public final class LandmarkMenu {

    private static final int PAGE_SIZE = 36;
    private static final int FIRST_SLOT = 9;
    private static final int PREV_SLOT = 45;
    private static final int BACK_SLOT = 49;
    private static final int NEXT_SLOT = 53;
    private static final Text TITLE = Text.literal("§b§l§n服务器地标");

    private static final String FILTER_ALL = "";
    private static final String FILTER_OVERWORLD = "minecraft:overworld";
    private static final String FILTER_NETHER = "minecraft:the_nether";
    private static final String FILTER_END = "minecraft:the_end";

    private LandmarkMenu() {}

    public static void open(ServerPlayerEntity viewer) {
        open(viewer, FILTER_ALL, 0);
    }

    public static void open(ServerPlayerEntity viewer, String filter, int page) {
        List<Landmark> filtered = new ArrayList<>();
        for (Landmark lm : LandmarkStore.all()) {
            if (filter.isEmpty() || filter.equals(lm.dimension)) filtered.add(lm);
        }
        int totalPages = Math.max(1, (filtered.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, totalPages - 1));
        final int currentPage = page;
        final String filterRef = filter;

        Inventory inv = MenuScreenHandler.buildInventory(6);
        Map<Integer, Consumer<ServerPlayerEntity>> actions = new HashMap<>();
        Map<Integer, Consumer<ServerPlayerEntity>> rightActions = new HashMap<>();

        // 维度 tab
        inv.setStack(0, tabButton(Items.NETHER_STAR, "§f全部",
            LandmarkStore.all().size(), filter.equals(FILTER_ALL)));
        actions.put(0, p -> open(p, FILTER_ALL, 0));

        inv.setStack(1, tabButton(Items.GRASS_BLOCK, "§a主世界",
            countIn(FILTER_OVERWORLD), filter.equals(FILTER_OVERWORLD)));
        actions.put(1, p -> open(p, FILTER_OVERWORLD, 0));

        inv.setStack(2, tabButton(Items.NETHERRACK, "§c下界",
            countIn(FILTER_NETHER), filter.equals(FILTER_NETHER)));
        actions.put(2, p -> open(p, FILTER_NETHER, 0));

        inv.setStack(3, tabButton(Items.END_STONE, "§d末地",
            countIn(FILTER_END), filter.equals(FILTER_END)));
        actions.put(3, p -> open(p, FILTER_END, 0));

        // 快捷添加：使用当前位置创建，名字用告示牌输入
        inv.setStack(8, MenuButtons.button(Items.EMERALD, "§a§l+ 添加地标",
            List.of("§7在你当前位置创建新地标",
                "§7点击后打开告示牌",
                "§7在首行输入名字",
                "§8长度 1..§f" + LandmarkStore.MAX_NAME_LEN)));
        actions.put(8, p -> promptAdd(p, filterRef, currentPage));

        // 页面内容
        if (filtered.isEmpty()) {
            inv.setStack(22, MenuButtons.button(Items.PAPER, "§7暂无地标",
                filter.isEmpty()
                    ? List.of("§8使用 §f/landmark add \"<名字>\" §8添加")
                    : List.of("§8该维度还没有地标",
                        "§8/landmark add \"<名字>\"")));
        } else {
            int start = page * PAGE_SIZE;
            int end = Math.min(start + PAGE_SIZE, filtered.size());
            for (int i = start; i < end; i++) {
                Landmark lm = filtered.get(i);
                int slot = FIRST_SLOT + (i - start);
                inv.setStack(slot, landmarkIcon(lm));
                final String lmName = lm.name;
                actions.put(slot, p -> {
                    Landmark cur = LandmarkStore.get(lmName);
                    if (cur == null) { open(p, filterRef, currentPage); return; }
                    LandmarkTeleport.execute(p, cur);
                    p.closeHandledScreen();
                });
                rightActions.put(slot, p -> openDetail(p, lmName, filterRef, currentPage));
            }
        }

        // 分页 & 返回
        boolean hasPrev = page > 0;
        boolean hasNext = page < totalPages - 1;
        inv.setStack(PREV_SLOT, MenuButtons.pageButton(false, page, totalPages, hasPrev));
        if (hasPrev) {
            final int prev = page - 1;
            actions.put(PREV_SLOT, p -> open(p, filterRef, prev));
        }
        inv.setStack(NEXT_SLOT, MenuButtons.pageButton(true, page, totalPages, hasNext));
        if (hasNext) {
            final int next = page + 1;
            actions.put(NEXT_SLOT, p -> open(p, filterRef, next));
        }

        inv.setStack(BACK_SLOT, MenuButtons.backButton());
        actions.put(BACK_SLOT, MenuUi::open);

        MenuButtons.fillBackground(inv);

        viewer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, playerInv, ignored) -> new MenuScreenHandler(syncId, playerInv, inv, actions, rightActions, null),
            TITLE
        ));
    }

    // ------------------------------------------------------------------ detail

    /**
     * 详细页：改名 / 换图标 / 传送 / 删除。
     * 打开时挂上一个玩家背包点击回调：点物品栏里的方块/物品 → 直接设为新图标。
     */
    public static void openDetail(ServerPlayerEntity viewer, String lmName,
                                  String returnFilter, int returnPage) {
        Landmark lm = LandmarkStore.get(lmName);
        if (lm == null) { open(viewer, returnFilter, returnPage); return; }

        Inventory inv = MenuScreenHandler.buildInventory(6);
        Map<Integer, Consumer<ServerPlayerEntity>> actions = new HashMap<>();

        // 顶部：地标信息卡（大图标）
        inv.setStack(4, infoIcon(lm));

        // 中央按钮行
        inv.setStack(19, MenuButtons.button(Items.NAME_TAG, "§a§l修改名称",
            List.of("§7当前 §f" + lm.name,
                "§7点击后打开告示牌编辑",
                "§7在首行输入新名字",
                "§8名字长度 1..§f" + LandmarkStore.MAX_NAME_LEN,
                "",
                "§a▶ 点击修改")));
        actions.put(19, p -> promptRename(p, lmName, returnFilter, returnPage));

        inv.setStack(21, MenuButtons.button(Items.ITEM_FRAME, "§b§l修改图标",
            List.of("§7当前 §f" + lm.getIconId(),
                "§7点击下方物品栏里的",
                "§7任意物品作为新图标",
                "",
                "§8物品不会被消耗")));
        // 无按钮 action：换图标靠 playerInventoryClick 回调（详细页整个页面都监听）

        inv.setStack(23, MenuButtons.button(Items.ENDER_PEARL, "§a§l传送",
            List.of("§7维度 §f" + com.tomkestookit.landmark.LandmarkCommand.dimShort(lm.dimension),
                String.format("§7坐标 §f%.0f, %.0f, %.0f", lm.x, lm.y, lm.z),
                "",
                "§a▶ 点击传送")));
        actions.put(23, p -> {
            Landmark cur = LandmarkStore.get(lmName);
            if (cur != null) {
                LandmarkTeleport.execute(p, cur);
                p.closeHandledScreen();
            } else {
                open(p, returnFilter, returnPage);
            }
        });

        inv.setStack(25, MenuButtons.button(Items.BARRIER, "§c§l删除",
            List.of("§7地标 §f" + lm.name, "", "§c▶ 点击立即删除")));
        actions.put(25, p -> {
            LandmarkStore.remove(lmName);
            p.sendMessage(Text.literal("已删除地标 \"" + lmName + "\"。").formatted(Formatting.YELLOW), true);
            open(p, returnFilter, returnPage);
        });

        // 迁移到玩家当前位置：覆盖维度 + 坐标 + 朝向
        inv.setStack(31, MenuButtons.button(Items.COMPASS, "§6§l迁移到当前位置",
            List.of("§7当前 §f" + com.tomkestookit.landmark.LandmarkCommand.dimShort(lm.dimension)
                + String.format(" §7@ §f%.0f, %.0f, %.0f", lm.x, lm.y, lm.z),
                "§7点击后覆盖为你所在的位置",
                "§8朝向 / 维度也一同更新",
                "",
                "§6▶ 点击立即迁移")));
        actions.put(31, p -> applyMigrate(p, lmName, returnFilter, returnPage));

        // 返回列表
        inv.setStack(BACK_SLOT, MenuButtons.backButton());
        actions.put(BACK_SLOT, p -> open(p, returnFilter, returnPage));

        MenuButtons.fillBackground(inv);

        viewer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, playerInv, ignored) -> new MenuScreenHandler(syncId, playerInv, inv, actions,
                (p, clicked) -> onIconClicked(p, lmName, clicked, returnFilter, returnPage)),
            Text.literal("§b§l§n地标 - " + lm.name)
        ));
    }

    private static void onIconClicked(ServerPlayerEntity player, String lmName,
                                      ItemStack clicked, String returnFilter, int returnPage) {
        if (clicked.isEmpty()) {
            player.sendMessage(Text.literal("请点击物品栏里的物品作为新图标。").formatted(Formatting.YELLOW), true);
            return;
        }
        Identifier id = Registries.ITEM.getId(clicked.getItem());
        if (id == null) return;
        if (!LandmarkStore.setIcon(lmName, id.toString())) {
            player.sendMessage(Text.literal("地标 \"" + lmName + "\" 不存在。").formatted(Formatting.RED), true);
            return;
        }
        player.sendMessage(Text.literal("已更换图标：" + id).formatted(Formatting.GREEN), true);
        openDetail(player, lmName, returnFilter, returnPage);
    }

    /** 快捷添加：告示牌输入新名字，在玩家当前位置创建。*/
    public static void promptAdd(ServerPlayerEntity player, String returnFilter, int returnPage) {
        player.closeHandledScreen();
        String[] prefill = new String[]{"", "^ 新地标名字", "长度 1.." + LandmarkStore.MAX_NAME_LEN, ""};
        // 快照玩家当前位置，避免玩家移动后位置变化
        double x = player.getX(), y = player.getY(), z = player.getZ();
        float yaw = player.getYaw(), pitch = player.getPitch();
        String dim = player.getWorld().getRegistryKey().getValue().toString();
        String creator = player.getGameProfile().getName();
        SignEditPrompt.open(player, prefill, lines ->
            player.getServer().execute(() -> applyAdd(player, lines, dim, x, y, z, yaw, pitch, creator,
                returnFilter, returnPage)));
    }

    private static void applyAdd(ServerPlayerEntity player, String[] lines,
                                 String dim, double x, double y, double z, float yaw, float pitch,
                                 String creator, String returnFilter, int returnPage) {
        String name = null;
        if (lines != null && lines.length > 0 && lines[0] != null) name = lines[0].strip();
        if (name == null || name.isEmpty()) {
            player.sendMessage(Text.literal("§c[地标] 未输入名字，取消。"), false);
            open(player, returnFilter, returnPage);
            return;
        }
        if (!LandmarkStore.isValidName(name)) {
            player.sendMessage(Text.literal("§c[地标] 名字长度非法（1.." + LandmarkStore.MAX_NAME_LEN + "）。"), false);
            open(player, returnFilter, returnPage);
            return;
        }
        if (LandmarkStore.exists(name)) {
            player.sendMessage(Text.literal("§c[地标] \"" + name + "\" 已存在。"), false);
            open(player, returnFilter, returnPage);
            return;
        }
        Landmark lm = new Landmark(name, dim, x, y, z, yaw, pitch, creator);
        LandmarkStore.add(lm);
        player.sendMessage(Text.literal("§a[地标] 已添加 \"" + name + "\"。"), false);
        open(player, returnFilter, returnPage);
    }

    /** 迁移：把地标覆盖为玩家当前维度 + 坐标 + 朝向。*/
    private static void applyMigrate(ServerPlayerEntity player, String lmName,
                                     String returnFilter, int returnPage) {
        Landmark cur = LandmarkStore.get(lmName);
        if (cur == null) {
            player.sendMessage(Text.literal("§c[地标] \"" + lmName + "\" 已不存在。"), false);
            open(player, returnFilter, returnPage);
            return;
        }
        String dim = player.getWorld().getRegistryKey().getValue().toString();
        double x = player.getX(), y = player.getY(), z = player.getZ();
        float yaw = player.getYaw(), pitch = player.getPitch();
        if (!LandmarkStore.setLocation(lmName, dim, x, y, z, yaw, pitch)) {
            player.sendMessage(Text.literal("§c[地标] 迁移失败。"), false);
            openDetail(player, lmName, returnFilter, returnPage);
            return;
        }
        player.sendMessage(Text.literal(String.format(
            "§a[地标] 已把 \"%s\" 迁移到 §f%s §a@ §f%.0f, %.0f, %.0f",
            lmName, com.tomkestookit.landmark.LandmarkCommand.dimShort(dim), x, y, z)), false);
        openDetail(player, lmName, returnFilter, returnPage);
    }

    private static void promptRename(ServerPlayerEntity player, String lmName,
                                     String returnFilter, int returnPage) {
        player.closeHandledScreen();
        String[] prefill = new String[]{lmName, "^ 新名字", "长度 1.." + LandmarkStore.MAX_NAME_LEN, ""};
        SignEditPrompt.open(player, prefill, lines ->
            player.getServer().execute(() -> applyRename(player, lmName, lines, returnFilter, returnPage)));
    }

    private static void applyRename(ServerPlayerEntity player, String lmName, String[] lines,
                                    String returnFilter, int returnPage) {
        String newName = null;
        if (lines != null && lines.length > 0 && lines[0] != null) newName = lines[0].strip();
        if (newName == null || newName.isEmpty()) {
            player.sendMessage(Text.literal("§c[地标] 未输入新名字，取消。"), false);
            openDetail(player, lmName, returnFilter, returnPage);
            return;
        }
        if (!LandmarkStore.isValidName(newName)) {
            player.sendMessage(Text.literal("§c[地标] 名字长度非法（1..").append(String.valueOf(LandmarkStore.MAX_NAME_LEN)).append("）。"), false);
            openDetail(player, lmName, returnFilter, returnPage);
            return;
        }
        if (newName.equals(lmName)) {
            openDetail(player, lmName, returnFilter, returnPage);
            return;
        }
        if (LandmarkStore.exists(newName)) {
            player.sendMessage(Text.literal("§c[地标] 名字 \"" + newName + "\" 已存在。"), false);
            openDetail(player, lmName, returnFilter, returnPage);
            return;
        }
        if (!LandmarkStore.rename(lmName, newName)) {
            player.sendMessage(Text.literal("§c[地标] 重命名失败。"), false);
            openDetail(player, lmName, returnFilter, returnPage);
            return;
        }
        player.sendMessage(Text.literal("§a[地标] 已重命名 \"" + lmName + "\" → \"" + newName + "\"。"), false);
        openDetail(player, newName, returnFilter, returnPage);
    }

    // ------------------------------------------------------------------ items

    private static ItemStack tabButton(net.minecraft.item.Item item,
                                       String name, int count, boolean selected) {
        List<String> lore = List.of(
            "§7地标 §f" + count + " §7个",
            selected ? "§a● 当前筛选" : "§8○ 点击筛选"
        );
        ItemStack stack = MenuButtons.button(item, name, lore);
        if (selected) MenuButtons.withGlow(stack);
        return stack;
    }

    private static ItemStack landmarkIcon(Landmark lm) {
        List<String> lore = new ArrayList<>();
        lore.add("§7维度 §f" + com.tomkestookit.landmark.LandmarkCommand.dimShort(lm.dimension));
        lore.add(String.format("§7坐标 §f%.0f, %.0f, %.0f", lm.x, lm.y, lm.z));
        if (lm.creator != null && !lm.creator.isEmpty()) {
            lore.add("§8添加者 " + lm.creator);
        }
        lore.add("");
        lore.add("§a左键 §7→ 传送");
        lore.add("§b右键 §7→ 详细 / 改名 / 换图标 / 删除");
        Item iconItem = resolveIcon(lm.getIconId());
        ItemStack stack = MenuButtons.button(iconItem, "§e§l" + lm.name, lore);
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putInt("HideFlags", 63);
        return stack;
    }

    private static ItemStack infoIcon(Landmark lm) {
        List<String> lore = new ArrayList<>();
        lore.add("§7维度 §f" + com.tomkestookit.landmark.LandmarkCommand.dimShort(lm.dimension));
        lore.add(String.format("§7坐标 §f%.0f, %.0f, %.0f", lm.x, lm.y, lm.z));
        if (lm.creator != null && !lm.creator.isEmpty()) {
            lore.add("§8添加者 " + lm.creator);
        }
        lore.add("§8图标 " + lm.getIconId());
        Item iconItem = resolveIcon(lm.getIconId());
        ItemStack stack = MenuButtons.button(iconItem, "§e§l" + lm.name, lore);
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putInt("HideFlags", 63);
        return stack;
    }

    private static Item resolveIcon(String id) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null || !Registries.ITEM.containsId(identifier)) return Items.FILLED_MAP;
        Item item = Registries.ITEM.get(identifier);
        return item == Items.AIR ? Items.FILLED_MAP : item;
    }

    private static int countIn(String dim) {
        int n = 0;
        for (Landmark lm : LandmarkStore.all()) if (dim.equals(lm.dimension)) n++;
        return n;
    }

}
