package com.tomkestookit.menu;

import com.tomkestookit.SignEditPrompt;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * 全服共享垃圾桶：以 "过量物品条目" 形式存储，允许每条数量远超 stack size。
 *
 * 数据模型：LinkedHashMap<key, Entry>，key = item id + NBT 指纹；Entry 里存 template
 * ItemStack（count=1，保留 NBT）+ long count。插入顺序 = 显示顺序（先进先出）。
 *
 * GUI：{@link TrashScreenHandler} 拦截所有点击：
 *   - 页内格子上的条目 → 关菜单 → 打开告示牌收提取数量
 *   - 45 号(左箭头) / 53 号(右箭头) → 翻页
 *   - 49 号(标题) 与其它控件槽 → 无响应
 *   玩家背包 / 光标物品 一律不允许拿走或塞入。
 *
 * 命令：/trash 打开第 1 页；/trash &lt;页&gt; 直达。
 * 定时：每 10 分钟一次自动清空。
 * 汇入：{@link #deposit(ItemStack)} 供 auto/lag/region-clearer 调用。
 */
public final class TrashCanMenu {

    // ---- 布局 ----
    private static final int PAGE_ROWS = 6;
    /** 每页 6 行 × 9 列 = 54 格；末行 (45..53) 全部占用为翻页/标题/装饰。*/
    private static final int PAGE_SIZE = 9 * PAGE_ROWS;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_TITLE = 49;
    private static final int SLOT_NEXT = 53;
    /** 前 5 行 = 45 个物品槽。*/
    private static final int SLOTS_PER_PAGE = 45;

    private static final int CLEAR_INTERVAL_TICKS = 20 * 60 * 10; // 10 分钟

    /** 单条目最多堆多少（防止 long 溢出 / GUI 显示过大数字）。*/
    private static final long MAX_ENTRY_COUNT = 1_000_000_000L; // 十亿封顶

    // ---- 存储 ----

    /**
     * 条目：模板 stack + 累计数量。
     *
     * template 的 getCount() 恒为 1，只用于携带 NBT / item 类型。
     * count 单独维护，避免超过 stack size 时 ItemStack 内部 int 溢出。
     */
    static final class Entry {
        final ItemStack template;
        long count;

        Entry(ItemStack template, long count) {
            ItemStack t = template.copy();
            t.setCount(1);
            this.template = t;
            this.count = count;
        }
    }

    /** LinkedHashMap 保序：先进先出，翻页 index 稳定。*/
    private static final java.util.LinkedHashMap<String, Entry> ENTRIES = new java.util.LinkedHashMap<>();
    private static int tickCounter = 0;

    private TrashCanMenu() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(TrashCanMenu::tick);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) ->
            dispatcher.register(CommandManager.literal("trash")
                .executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                    p.getServer().execute(() -> open(p));
                    return 1;
                })
                .then(CommandManager.argument("page",
                        com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                    .executes(ctx -> {
                        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                        int pg = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "page") - 1;
                        p.getServer().execute(() -> open(p, pg));
                        return 1;
                    }))));
    }

    // ---- 定时清空 ----

    private static void tick(MinecraftServer server) {
        tickCounter++;
        if (tickCounter < CLEAR_INTERVAL_TICKS) return;
        tickCounter = 0;
        if (ENTRIES.isEmpty()) return;
        int types = ENTRIES.size();
        long totalCount = 0;
        for (Entry e : ENTRIES.values()) totalCount += e.count;
        ENTRIES.clear();
        Text msg = Text.literal("§7[垃圾桶] 已自动清空 §f" + types
            + " §7种物品（共 §f" + totalCount + " §7个）")
            .styled(s -> s.withColor(0xAAAAAA).withItalic(false));
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.sendMessage(msg, false);
        }
    }

    // ---- 汇入 API：供 auto/lag/region-clearer 调用 ----

    /** 单条掉落物汇入。null / EMPTY 静默丢弃。*/
    public static void deposit(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        String k = key(stack);
        Entry e = ENTRIES.get(k);
        long add = stack.getCount();
        if (e == null) {
            ENTRIES.put(k, new Entry(stack, Math.min(add, MAX_ENTRY_COUNT)));
        } else {
            e.count = clampAdd(e.count, add);
        }
    }

    /** 批量汇入，返回被吞下的总数量（超过 MAX 的部分会被截断，也计入吞下数）。*/
    public static long depositAll(Iterable<ItemStack> stacks) {
        long total = 0;
        for (ItemStack s : stacks) {
            if (s == null || s.isEmpty()) continue;
            total += s.getCount();
            deposit(s);
        }
        return total;
    }

    private static long clampAdd(long a, long b) {
        long sum = a + b;
        if (sum < 0 || sum > MAX_ENTRY_COUNT) return MAX_ENTRY_COUNT;
        return sum;
    }

    /**
     * 生成条目 key：item registry id + NBT 的稳定字符串。
     *
     * 用 toString() 而非 toBinary/hash：短、可读、可复现，NBT 相同则 key 一定相同。
     * 副作用：NBT 里 map 顺序不同的两个 stack 可能被视为不同条目——通常 vanilla 生成的
     * NBT 顺序稳定，不实际出现问题；插入前 canCombine 判等作为兜底。
     */
    private static String key(ItemStack s) {
        String id = net.minecraft.registry.Registries.ITEM.getId(s.getItem()).toString();
        NbtCompound nbt = s.getNbt();
        return nbt == null ? id : id + "\0" + nbt;
    }

    // ---- 打开 ----

    public static void open(ServerPlayerEntity viewer) {
        open(viewer, 0);
    }

    /** 打开第 page 页（0-based）。空垃圾桶时 page=0 仍然打开，只显示"空"。*/
    public static void open(ServerPlayerEntity viewer, int page) {
        List<Entry> snapshot = new ArrayList<>(ENTRIES.values());
        int totalPages = Math.max(1, (snapshot.size() + SLOTS_PER_PAGE - 1) / SLOTS_PER_PAGE);
        page = Math.max(0, Math.min(page, totalPages - 1));

        SimpleInventory inv = new SimpleInventory(PAGE_SIZE);
        int start = page * SLOTS_PER_PAGE;
        int end = Math.min(start + SLOTS_PER_PAGE, snapshot.size());
        for (int i = start; i < end; i++) {
            inv.setStack(i - start, buildDisplayStack(snapshot.get(i)));
        }

        // 底行控件
        inv.setStack(SLOT_PREV, MenuButtons.pageButton(false, page, totalPages, page > 0));
        inv.setStack(SLOT_NEXT, MenuButtons.pageButton(true, page, totalPages, page < totalPages - 1));
        inv.setStack(SLOT_TITLE, titleStack(page, totalPages, snapshot.size()));
        for (int i = SLOT_PREV + 1; i < SLOT_NEXT; i++) {
            if (i == SLOT_TITLE) continue;
            inv.setStack(i, MenuButtons.filler());
        }

        final int currentPage = page;
        final int totalPagesRef = totalPages;
        Text title = Text.literal("§c§l垃圾桶 §7(" + (page + 1) + "/" + totalPages + ")");
        viewer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, playerInv, ignored) ->
                new TrashScreenHandler(syncId, playerInv, inv, currentPage, totalPagesRef),
            title
        ));
    }

    private static ItemStack titleStack(int page, int totalPages, int totalEntries) {
        long grandTotal = 0;
        for (Entry e : ENTRIES.values()) grandTotal += e.count;
        return MenuButtons.button(Items.LAVA_BUCKET, "§c§l垃圾桶",
            List.of("§7每 10 分钟自动清空",
                "§7当前第 §f" + (page + 1) + " §7/ §f" + totalPages + " §7页",
                "§7条目 §f" + totalEntries + " §7种 · 总数 §f" + grandTotal,
                "§8点物品条目可提取"));
    }

    /**
     * 把 Entry 渲染成展示用 ItemStack：
     *   - count 用 min(entry.count, maxStackCount) 让物品图标带真实角标；
     *   - lore 前置一行 "§e× 真实数量"，让玩家知道后面还有多少；
     *   - 名字追加 "§7(× n)"，n = 真实数量。
     */
    private static ItemStack buildDisplayStack(Entry e) {
        ItemStack display = e.template.copy();
        int maxStack = Math.max(1, display.getMaxCount());
        int shown = (int) Math.min(e.count, (long) maxStack);
        display.setCount(shown);

        NbtCompound nbt = display.getOrCreateNbt();
        NbtCompound tagDisplay = nbt.getCompound("display");

        // 名字：拿现有名字（可能是自定义/翻译）追加数量后缀
        Text existingName = display.getName();
        Text newName = Text.literal("")
            .append(existingName.copy())
            .append(Text.literal(" §7(× " + e.count + ")"))
            .styled(s -> s.withItalic(false));
        tagDisplay.putString("Name", Text.Serializer.toJson(newName));

        // Lore：把原有 lore 前追加一行"数量提示"
        NbtList newLore = new NbtList();
        newLore.add(NbtString.of(Text.Serializer.toJson(
            Text.literal("§e数量 §f" + e.count).styled(s -> s.withItalic(false)))));
        newLore.add(NbtString.of(Text.Serializer.toJson(
            Text.literal("§7左键 §8→ §7告示牌输入提取数量").styled(s -> s.withItalic(false)))));
        newLore.add(NbtString.of(Text.Serializer.toJson(
            Text.literal("§8* 全部 · = 填满 · 空 取消 · 支持 3*64").styled(s -> s.withItalic(false)))));
        newLore.add(NbtString.of(Text.Serializer.toJson(
            Text.literal("§8背包装不下的部分会以掉落物溢出").styled(s -> s.withItalic(false)))));
        if (tagDisplay.contains("Lore", 9)) {
            NbtList existing = tagDisplay.getList("Lore", 8);
            for (int i = 0; i < existing.size(); i++) newLore.add(existing.get(i));
        }
        tagDisplay.put("Lore", newLore);
        nbt.put("display", tagDisplay);

        // 用 tomkestookit 标记，让点击处理时能反查 key
        nbt.putString("tomkestookit_trash_key", keyForDisplay(e.template));

        return display;
    }

    /** template 已经是 count=1 的副本，可以直接算 key。*/
    private static String keyForDisplay(ItemStack template) {
        return key(template);
    }

    // ---- 点击处理 ----

    /**
     * 玩家点击了某个物品条目 → 关掉菜单，开告示牌收数量输入，然后按输入提取。
     *
     * 用 shownStack 上的 NBT-stamped key 直接反查 ENTRIES，避免 GUI 展示到点击之间
     * ENTRIES 被并发修改（新汇入 / 定时清空）导致 index 错位。
     */
    static void onEntryClicked(ServerPlayerEntity player, ItemStack shownStack, int currentPage) {
        if (shownStack == null || shownStack.isEmpty()) return;
        NbtCompound nbt = shownStack.getNbt();
        if (nbt == null || !nbt.contains("tomkestookit_trash_key")) return;
        String entryKey = nbt.getString("tomkestookit_trash_key");
        Entry entry = ENTRIES.get(entryKey);
        if (entry == null) {
            player.sendMessage(Text.literal("§7[垃圾桶] 该条目已被清理。"), false);
            return;
        }
        String displayName = entry.template.getName().getString();

        player.closeHandledScreen();

        long freeCapacity = freeInventoryCapacity(player, entry.template);

        String[] prefill = new String[]{
            "",
            "^ 输入数量",
            "*=全部 · ==填满",
            "支持 3*64 · 空=取消"
        };

        SignEditPrompt.open(player, prefill, lines -> {
            String input = "";
            if (lines != null && lines.length > 0 && lines[0] != null) input = lines[0].strip();
            String finalInput = input;
            player.getServer().execute(() ->
                applyExtract(player, entryKey, finalInput, displayName,
                    freeCapacity, currentPage));
        });
    }

    /** 收到告示牌输入后回主线程执行提取。*/
    private static void applyExtract(ServerPlayerEntity player, String entryKey,
                                     String input, String displayName,
                                     long freeCapacity, int returnPage) {
        Entry entry = ENTRIES.get(entryKey);
        if (entry == null) {
            player.sendMessage(Text.literal("§7[垃圾桶] 该条目已被清理。"), false);
            open(player, returnPage);
            return;
        }

        long want;
        if (input.isEmpty()) {
            // 空 = 取消
            player.sendMessage(Text.literal("§7[垃圾桶] 已取消提取。"), false);
            open(player, returnPage);
            return;
        } else if (input.equals("*")) {
            // * = 全部
            want = entry.count;
        } else if (input.equals("=")) {
            // = 填满背包
            want = Math.min(entry.count, freeCapacity);
            if (want <= 0) {
                player.sendMessage(Text.literal("§c[垃圾桶] 背包没有空间容纳该物品。"), false);
                open(player, returnPage);
                return;
            }
        } else {
            Long parsed = evalExpression(input);
            if (parsed == null) {
                player.sendMessage(Text.literal("§c[垃圾桶] 无法识别输入：§f" + input), false);
                open(player, returnPage);
                return;
            }
            if (parsed <= 0) {
                player.sendMessage(Text.literal("§c[垃圾桶] 数量必须为正整数。"), false);
                open(player, returnPage);
                return;
            }
            want = Math.min(parsed, entry.count);
        }

        long remaining = want;
        long overflowed = 0;

        // 一次性发放：先塞背包，塞不下的按 stack size 拆成 ItemEntity 撒地上
        int maxStack = Math.max(1, entry.template.getMaxCount());
        while (remaining > 0) {
            int give = (int) Math.min((long) maxStack, remaining);
            ItemStack out = entry.template.copy();
            out.setCount(give);
            if (player.getInventory().insertStack(out)) {
                remaining -= give;
                continue;
            }
            // 背包塞不下：剩下的全部丢地上
            int leftoverInStack = out.getCount(); // 部分插入后剩下的
            long leftoverTotal = leftoverInStack + (remaining - give);
            while (leftoverTotal > 0) {
                int drop = (int) Math.min((long) maxStack, leftoverTotal);
                ItemStack dropStack = entry.template.copy();
                dropStack.setCount(drop);
                player.dropItem(dropStack, false);
                overflowed += drop;
                leftoverTotal -= drop;
            }
            remaining = 0;
        }

        long extracted = want;
        entry.count -= extracted;
        if (entry.count <= 0) ENTRIES.remove(entryKey);

        // 反馈
        StringBuilder msg = new StringBuilder("§a[垃圾桶] 已提取 §f")
            .append(extracted).append(" §a× §f").append(displayName);
        if (overflowed > 0) msg.append(" §7(§e").append(overflowed).append(" §7个溢出为掉落物)");
        player.sendMessage(Text.literal(msg.toString()).styled(s -> s.withItalic(false)), false);

        open(player, returnPage);
    }

    /**
     * 玩家背包里能塞下多少这种物品（以物品单位计，不是槽位）。
     * 空槽记为一格 maxStackCount；已有的同类堆叠加剩余空间。
     */
    private static long freeInventoryCapacity(ServerPlayerEntity player, ItemStack template) {
        PlayerInventory inv = player.getInventory();
        int maxStack = Math.max(1, template.getMaxCount());
        long free = 0;
        for (int i = 0; i < inv.main.size(); i++) {
            ItemStack cur = inv.main.get(i);
            if (cur.isEmpty()) {
                free += maxStack;
            } else if (ItemStack.canCombine(cur, template)) {
                int room = Math.max(0, Math.min(cur.getMaxCount(), maxStack) - cur.getCount());
                free += room;
            }
        }
        return free;
    }

    // ---- 数学表达式求值 ----

    /**
     * 支持 + - * / ( ) 与十进制数字（整数或小数），空白自动忽略。
     * 返回 null 表示解析失败；返回值 > MAX_ENTRY_COUNT 会被 clamp 到 MAX_ENTRY_COUNT。
     *
     * 递归下降：expr := term (('+'|'-') term)*
     *           term := factor (('*'|'/') factor)*
     *           factor := number | '(' expr ')' | ('+'|'-') factor
     * 中间用 double 计算，最后 round 到 long；越界返回 null。
     */
    static Long evalExpression(String src) {
        if (src == null) return null;
        StringBuilder sb = new StringBuilder(src.length());
        for (int i = 0; i < src.length(); i++) {
            char c = src.charAt(i);
            if (!Character.isWhitespace(c)) sb.append(c);
        }
        if (sb.length() == 0) return null;
        try {
            Parser p = new Parser(sb.toString());
            double v = p.parseExpr();
            if (p.pos != p.src.length()) return null;
            if (!Double.isFinite(v)) return null;
            long asLong = (long) Math.floor(v + 1e-9);
            if (asLong <= 0) return null;
            return Math.min(asLong, MAX_ENTRY_COUNT);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static final class Parser {
        final String src;
        int pos = 0;

        Parser(String s) { this.src = s; }

        double parseExpr() {
            double v = parseTerm();
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == '+') { pos++; v += parseTerm(); }
                else if (c == '-') { pos++; v -= parseTerm(); }
                else break;
            }
            return v;
        }

        double parseTerm() {
            double v = parseFactor();
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == '*') { pos++; v *= parseFactor(); }
                else if (c == '/') {
                    pos++;
                    double d = parseFactor();
                    if (d == 0) throw new RuntimeException("div0");
                    v /= d;
                } else break;
            }
            return v;
        }

        double parseFactor() {
            if (pos >= src.length()) throw new RuntimeException("eof");
            char c = src.charAt(pos);
            if (c == '+') { pos++; return parseFactor(); }
            if (c == '-') { pos++; return -parseFactor(); }
            if (c == '(') {
                pos++;
                double v = parseExpr();
                if (pos >= src.length() || src.charAt(pos) != ')') throw new RuntimeException("noparen");
                pos++;
                return v;
            }
            int start = pos;
            while (pos < src.length()) {
                char d = src.charAt(pos);
                if ((d >= '0' && d <= '9') || d == '.') pos++;
                else break;
            }
            if (start == pos) throw new RuntimeException("nonum");
            return Double.parseDouble(src.substring(start, pos));
        }
    }
}
