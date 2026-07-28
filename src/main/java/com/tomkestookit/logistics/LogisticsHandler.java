package com.tomkestookit.logistics;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * 物流终端主逻辑：
 *
 *   - buildResultPair()：合成结果 stack（count=2，共享 pairId）
 *   - onPlaced()：放置钩子从 mixin 调；写入 tracker 与 BlockEntity NBT + 生成 marker
 *   - tick()：遍历 pair，两端加载则复制 A → B（然后 B → A 冲突时以 A 优先），更新 marker 文本
 *   - onBreak()：破坏端点 → tracker 保留另一端但显示 “❌ 对端已破坏”
 *   - canOpenLocal()：拦截打开 GUI（对端未加载或未完整时返回 false）
 */
public final class LogisticsHandler {

    /** 木桶 stack 上的 NBT 键：pairId。*/
    public static final String STACK_NBT_KEY = "TomkesTookitLogisticsId";
    /** BlockEntity NBT 键：pairId + 端角色（A/B）。*/
    public static final String BE_NBT_ID = "TomkesTookitLogisticsPairId";
    public static final String BE_NBT_ROLE = "TomkesTookitLogisticsRole";

    private static final Random RNG = new Random();

    private LogisticsHandler() {}

    /** 收集 tracker 中所有活着的 marker UUID，用于孤儿扫描。*/
    public static java.util.Set<UUID> collectAliveMarkerUuids() {
        java.util.Set<UUID> set = new java.util.HashSet<>();
        for (LogisticsPair pair : LogisticsTracker.all()) {
            addUuid(set, pair.a);
            addUuid(set, pair.b);
        }
        return set;
    }

    private static void addUuid(java.util.Set<UUID> set, LogisticsPair.Endpoint e) {
        if (e == null || e.markerUuid == null) return;
        try {
            set.add(UUID.fromString(e.markerUuid));
        } catch (IllegalArgumentException ignored) {}
    }

    /** 清除所有孤儿 marker（tag 匹配但不在 tracker 里的）。*/
    public static int sweepOrphans(MinecraftServer server) {
        return LogisticsMarker.sweepOrphans(server, collectAliveMarkerUuids());
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(LogisticsHandler::tick);

        // 破坏端点：BEFORE 里手动掉落带 pair NBT 的 barrel（保留物流终端身份），
        // 并把 barrel 内 inventory 掉在原地；然后清 pair、setBlockState AIR，
        // 阻止 vanilla 走默认 breakBlock（会掉普通 barrel）。
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, be) -> {
            if (!com.tomkestookit.TomkesTookitConfig.enabled("item.logistics_ops")) return true;
            if (!(world instanceof ServerWorld sw)) return true;
            if (state.getBlock() != Blocks.BARREL) return true;
            String dim = sw.getRegistryKey().getValue().toString();
            LogisticsPair pair = LogisticsTracker.findByPos(dim, pos);
            if (pair == null) return true;

            // 创造模式：不掉物品，但仍要清 pair
            if (!player.isCreative()) {
                // 两端都掉带 pairId 的物流终端 stack（保留身份）
                ItemStack drop = buildResultForExistingPair(pair.pairId);
                net.minecraft.block.Block.dropStack(sw, pos, drop);

                // 内部物品：因为两端 tick 实时同步，两个 barrel 的 27 格内容一致。
                // 挖任一端就把两端 inventory 都清空，只掉一次内容物，避免复制。
                dumpContentsOnce(sw, pos, be, pair);
            }

            // 清 pair 端点 + marker
            onBreak(sw.getServer(), pair, dim, pos);

            // 手动 setBlockState AIR 并阻止 vanilla 继续掉物品
            sw.setBlockState(pos, net.minecraft.block.Blocks.AIR.getDefaultState(),
                net.minecraft.block.Block.NOTIFY_LISTENERS | net.minecraft.block.Block.FORCE_STATE);
            return false;
        });
    }

    /**
     * 破坏端点时把内容物在破坏点掉一次，并把对端 barrel 也清空，避免复制。
     * 优先使用本端 barrel 的物品（因为两端实时同步，两端内容一致）；
     * 若本端 barrel 为空但对端有物品（少见但可能——例如同步 tick 未跑到），
     * 用对端物品掉在本端。
     */
    private static void dumpContentsOnce(ServerWorld sw, BlockPos pos, BlockEntity localBe, LogisticsPair pair) {
        // 收集本端 stacks
        BarrelBlockEntity local = (localBe instanceof BarrelBlockEntity b) ? b : null;
        // 找对端 barrel
        BarrelBlockEntity remote = null;
        String dim = sw.getRegistryKey().getValue().toString();
        LogisticsPair.Endpoint self = LogisticsTracker.endpointAt(pair, dim, pos);
        LogisticsPair.Endpoint other = null;
        if (self != null) {
            if (pair.a != null && pair.a.equalsTo(self)) other = pair.b;
            else if (pair.b != null && pair.b.equalsTo(self)) other = pair.a;
        }
        if (other != null) {
            ServerWorld remoteWorld = LogisticsMarker.resolveWorld(sw.getServer(), other.dim);
            if (remoteWorld != null
                && remoteWorld.isChunkLoaded(other.x >> 4, other.z >> 4)) {
                if (remoteWorld.getBlockEntity(new BlockPos(other.x, other.y, other.z))
                    instanceof BarrelBlockEntity be) {
                    remote = be;
                }
            }
        }

        // 掉落哪端的内容？优先本端；本端为空且对端存在时用对端。
        BarrelBlockEntity source = local;
        boolean localEmpty = isEmpty(local);
        if (localEmpty && remote != null && !isEmpty(remote)) {
            source = remote;
        }
        if (source != null) {
            for (int i = 0; i < source.size(); i++) {
                ItemStack s = source.getStack(i);
                if (!s.isEmpty()) {
                    net.minecraft.block.Block.dropStack(sw, pos, s.copy());
                }
            }
        }
        // 清空两端 barrel，避免另一端还留着内容再挖一次复制
        clearInventory(local);
        clearInventory(remote);
        // 清同步快照，避免下 tick sync 把 stale 状态又写回
        LAST_A.remove(pair.pairId);
        LAST_B.remove(pair.pairId);
    }

    private static boolean isEmpty(BarrelBlockEntity be) {
        if (be == null) return true;
        for (int i = 0; i < be.size(); i++) {
            if (!be.getStack(i).isEmpty()) return false;
        }
        return true;
    }

    private static void clearInventory(BarrelBlockEntity be) {
        if (be == null) return;
        for (int i = 0; i < be.size(); i++) {
            if (!be.getStack(i).isEmpty()) be.setStack(i, ItemStack.EMPTY);
        }
        be.markDirty();
    }

    /** 破坏端点时用：生成带 pair NBT 的 barrel item（数量 1）。*/
    private static ItemStack buildResultForExistingPair(String pairId) {
        ItemStack stack = new ItemStack(Items.BARREL, 1);
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putString(STACK_NBT_KEY, pairId);

        NbtList encList = new NbtList();
        NbtCompound ench = new NbtCompound();
        ench.putString("id", Registries.ENCHANTMENT.getId(Enchantments.UNBREAKING).toString());
        ench.putInt("lvl", 1);
        encList.add(ench);
        nbt.put("Enchantments", encList);
        nbt.putInt("HideFlags", 1);

        stack.setCustomName(Text.literal("物流终端 §7#§b" + pairId)
            .formatted(Formatting.WHITE, Formatting.BOLD));

        NbtCompound display = nbt.getCompound("display");
        NbtList lore = new NbtList();
        for (String line : List.of(
            "§8「磁石不会说谎——它们知道彼此在哪。」",
            "§7成对使用，物品实时同步",
            "§7两端区块都得加载，否则货物打瞌睡",
            "§8上方盔甲架会告诉你它现在心情如何"
        )) {
            lore.add(NbtString.of(Text.Serializer.toJson(
                Text.literal(line).styled(s -> s.withItalic(false)))));
        }
        display.put("Lore", lore);
        nbt.put("display", display);
        return stack;
    }

    // ------------------------------------------------------------------ item 构造

    /** 合成结果：一个 count=2 的木桶 stack，NBT 里带 pairId、发光、专属名字。*/
    public static ItemStack buildResultPair() {
        String pairId = LogisticsTracker.newPairId(RNG);
        ItemStack stack = new ItemStack(Items.BARREL, 2);
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putString(STACK_NBT_KEY, pairId);

        // 弱附魔发光 (Unbreaking 1) + 隐藏附魔 tooltip
        NbtList encList = new NbtList();
        NbtCompound ench = new NbtCompound();
        ench.putString("id", Registries.ENCHANTMENT.getId(Enchantments.UNBREAKING).toString());
        ench.putInt("lvl", 1);
        encList.add(ench);
        nbt.put("Enchantments", encList);
        nbt.putInt("HideFlags", 1);

        stack.setCustomName(Text.literal("物流终端 §7#§b" + pairId)
            .formatted(Formatting.WHITE, Formatting.BOLD));

        NbtCompound display = nbt.getCompound("display");
        NbtList lore = new NbtList();
        for (String line : List.of(
            "§8「磁石不会说谎——它们知道彼此在哪。」",
            "§7成对使用，物品实时同步",
            "§7两端区块都得加载，否则货物打瞌睡",
            "§8上方盔甲架会告诉你它现在心情如何"
        )) {
            lore.add(NbtString.of(Text.Serializer.toJson(
                Text.literal(line).styled(s -> s.withItalic(false)))));
        }
        display.put("Lore", lore);
        nbt.put("display", display);
        return stack;
    }

    // ------------------------------------------------------------------ 放置钩子

    /** stack 是否带 pairId。*/
    public static boolean isLogisticsBarrelStack(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() != Items.BARREL) return false;
        NbtCompound nbt = stack.getNbt();
        return nbt != null && nbt.contains(STACK_NBT_KEY);
    }

    /** mixin 从 BlockItem.postPlacement HEAD 调过来。*/
    public static void onPlaced(ServerWorld world, BlockPos pos, ItemStack stack) {
        String pairId = stack.getNbt().getString(STACK_NBT_KEY);
        if (pairId == null || pairId.isEmpty()) return;

        LogisticsPair pair = LogisticsTracker.get(pairId);
        String dim = world.getRegistryKey().getValue().toString();
        LogisticsPair.Endpoint newEnd = new LogisticsPair.Endpoint(dim, pos.getX(), pos.getY(), pos.getZ());
        boolean isA;
        if (pair == null) {
            // 全新 pair
            pair = new LogisticsPair(pairId);
            pair.a = newEnd;
            isA = true;
        } else if (pair.a == null) {
            // A 端曾被破坏，本次补 A
            pair.a = newEnd;
            isA = true;
        } else if (pair.b == null && !pair.a.equalsTo(newEnd)) {
            // 首次放 B 端
            pair.b = newEnd;
            isA = false;
        } else {
            // pair 已完整（两端都在）或 重复放置：此方块降级为普通 barrel（不写 BE NBT）
            return;
        }
        LogisticsTracker.put(pair);

        // 标记 barrel 为 pair 端点。我们不写 BlockEntity NBT 字段（避免 readNbt 覆盖 inventory）；
        // pair 的归属完全由 LogisticsTracker 按 pos 索引决定，BlockEntity 无需知道自己是哪个 pair 的哪一端。
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof BarrelBlockEntity) {
            be.markDirty();
        }

        // Marker
        UUID markerUuid = LogisticsMarker.create(world, pos, defaultMarkerText(pairId, isA));
        LogisticsPair.Endpoint self = isA ? pair.a : pair.b;
        self.markerUuid = markerUuid == null ? null : markerUuid.toString();
        LogisticsTracker.save();
    }

    // ------------------------------------------------------------------ 打开拦截

    /** 由 mixin BarrelBlock.onUse 调用。true = 允许打开；false = 拒绝并给玩家消息。*/
    public static boolean canOpenLocal(ServerWorld world, BlockPos pos,
                                       net.minecraft.server.network.ServerPlayerEntity player) {
        String dim = world.getRegistryKey().getValue().toString();
        LogisticsPair pair = LogisticsTracker.findByPos(dim, pos);
        if (pair == null) return true; // 不是我们管的方块，vanilla 正常
        // 不完整
        if (!pair.isComplete()) {
            player.sendMessage(Text.literal("§c[物流终端] 对端尚未放置。").formatted(Formatting.RED), true);
            return false;
        }
        LogisticsPair.Endpoint self = LogisticsTracker.endpointAt(pair, dim, pos);
        LogisticsPair.Endpoint other = pair.other(self);
        if (other == null) return true;
        // 对端 world / chunk 状态
        ServerWorld otherWorld = LogisticsMarker.resolveWorld(world.getServer(), other.dim);
        if (otherWorld == null) {
            player.sendMessage(Text.literal("§c[物流终端] 对端所在维度不存在。").formatted(Formatting.RED), true);
            return false;
        }
        if (!otherWorld.isChunkLoaded(other.x >> 4, other.z >> 4)) {
            player.sendMessage(Text.literal("§c[物流终端] 对端区块未加载。").formatted(Formatting.RED), true);
            return false;
        }
        // 对端方块是否还是 barrel
        BlockEntity otherBe = otherWorld.getBlockEntity(new BlockPos(other.x, other.y, other.z));
        if (!(otherBe instanceof BarrelBlockEntity)) {
            player.sendMessage(Text.literal("§c[物流终端] 对端方块已丢失。").formatted(Formatting.RED), true);
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ tick 同步 + marker 状态

    /** 同步用的“上次快照”，用于检测哪端刚变化。*/
    private static final java.util.Map<String, java.util.List<ItemStack>> LAST_A = new java.util.HashMap<>();
    private static final java.util.Map<String, java.util.List<ItemStack>> LAST_B = new java.util.HashMap<>();

    private static void tick(MinecraftServer server) {
        if (!com.tomkestookit.TomkesTookitConfig.enabled("item.logistics_ops")) return;
        if ((server.getTicks() & 0x1) != 0) return; // 每 2 tick 一次，节流

        // 遍历拿快照，允许在 sweepBrokenEndpoints 里修改 tracker
        java.util.List<LogisticsPair> snapshot = new java.util.ArrayList<>(LogisticsTracker.all());
        for (LogisticsPair pair : snapshot) {
            if (pair == null) continue;
            // 兜底清理：BEFORE 事件没跑到（爆炸/piston/mod 破坏等）导致端点方块没了
            // 但 marker 还在 —— 这里检测并清理。
            sweepBrokenEndpoints(server, pair);
            // 若 pair 已经被 sweep 清空，跳过
            if (pair.a == null && pair.b == null) continue;

            State st = evaluate2(server, pair);
            updateMarkers(server, pair, st);
            if (st == State.OK) {
                syncInventories(server, pair);
            }
        }
    }

    /**
     * 兜底清理：检查每端 endpoint 是否还是 barrel（要求 chunk 已加载才判定，
     * 未加载不动，避免误判）。发现方块没了就销毁 marker、从 pair 里移除该端。
     */
    private static void sweepBrokenEndpoints(MinecraftServer server, LogisticsPair pair) {
        if (pair.a != null && shouldSweep(server, pair.a)) {
            destroyMarker(server, pair.a);
            pair.a = null;
        }
        if (pair.b != null && shouldSweep(server, pair.b)) {
            destroyMarker(server, pair.b);
            pair.b = null;
        }
        if (pair.a == null && pair.b == null) {
            LogisticsTracker.remove(pair.pairId);
            LAST_A.remove(pair.pairId);
            LAST_B.remove(pair.pairId);
        }
        // 单端还在的情况下不主动保存，避免每 tick 磁盘 IO
    }

    private static boolean shouldSweep(MinecraftServer server, LogisticsPair.Endpoint e) {
        ServerWorld world = LogisticsMarker.resolveWorld(server, e.dim);
        if (world == null) return false; // 维度不存在也保守不动
        if (!world.isChunkLoaded(e.x >> 4, e.z >> 4)) return false; // chunk 未加载不判定
        BlockEntity be = world.getBlockEntity(new BlockPos(e.x, e.y, e.z));
        return !(be instanceof BarrelBlockEntity);
    }

    private static void destroyMarker(MinecraftServer server, LogisticsPair.Endpoint e) {
        if (e.markerUuid != null) {
            try {
                LogisticsMarker.remove(server, e.dim, UUID.fromString(e.markerUuid));
            } catch (IllegalArgumentException ignored) {}
            e.markerUuid = null;
        }
        // 兜底：按位置扫描 discard 掉附近 marker，防 UUID 丢失残留
        LogisticsMarker.sweepAt(server, e.dim, new BlockPos(e.x, e.y, e.z));
    }

    private enum State { OK, INCOMPLETE, OTHER_UNLOADED, OTHER_MISSING }

    private static State checkEndpoint(MinecraftServer server, LogisticsPair.Endpoint e) {
        ServerWorld world = LogisticsMarker.resolveWorld(server, e.dim);
        if (world == null) return State.OTHER_MISSING;
        if (!world.isChunkLoaded(e.x >> 4, e.z >> 4)) return State.OTHER_UNLOADED;
        BlockEntity be = world.getBlockEntity(new BlockPos(e.x, e.y, e.z));
        if (!(be instanceof BarrelBlockEntity)) return State.OTHER_MISSING;
        return State.OK;
    }

    /** State 合并规则：任意一端不 OK 就非 OK，MISSING > UNLOADED > OK。*/
    private static State and(State a, State b) {
        if (a == State.OTHER_MISSING || b == State.OTHER_MISSING) return State.OTHER_MISSING;
        if (a == State.OTHER_UNLOADED || b == State.OTHER_UNLOADED) return State.OTHER_UNLOADED;
        if (a == State.INCOMPLETE || b == State.INCOMPLETE) return State.INCOMPLETE;
        return State.OK;
    }

    // 用 & 的辅助 hack —— 上面 evaluate 里写 `checkEndpoint(a) & checkEndpoint(b)` 是打字省事，
    // 实际得手动合并，Java 不支持 enum &。改回正常合并：
    // 这里保留一个方法链接
    // （见 evaluate2）

    private static State evaluate2(MinecraftServer server, LogisticsPair pair) {
        if (!pair.isComplete()) return State.INCOMPLETE;
        return and(checkEndpoint(server, pair.a), checkEndpoint(server, pair.b));
    }

    private static void updateMarkers(MinecraftServer server, LogisticsPair pair, State real) {
        Text msgA;
        Text msgB;
        switch (real) {
            case OK -> {
                msgA = defaultMarkerText(pair.pairId, true);
                msgB = defaultMarkerText(pair.pairId, false);
            }
            case INCOMPLETE -> {
                Text t = Text.literal("§c[未配对] §7#§f" + pair.pairId);
                msgA = t; msgB = t;
            }
            case OTHER_UNLOADED -> {
                Text t = Text.literal("§c[对端未加载] §7#§f" + pair.pairId);
                msgA = t; msgB = t;
            }
            case OTHER_MISSING -> {
                Text t = Text.literal("§c[对端丢失] §7#§f" + pair.pairId);
                msgA = t; msgB = t;
            }
            default -> { msgA = Text.empty(); msgB = Text.empty(); }
        }
        maintainMarker(server, pair, pair.a, msgA);
        maintainMarker(server, pair, pair.b, msgB);
    }

    /** 确保 endpoint 上的盔甲架存在且文本一致；不在则尝试重建。*/
    private static void maintainMarker(MinecraftServer server, LogisticsPair pair,
                                       LogisticsPair.Endpoint end, Text text) {
        if (end == null) return;
        UUID uuid = null;
        try {
            if (end.markerUuid != null) uuid = UUID.fromString(end.markerUuid);
        } catch (IllegalArgumentException ignored) {}
        ServerWorld world = LogisticsMarker.resolveWorld(server, end.dim);
        if (world == null) return;
        if (!world.isChunkLoaded(end.x >> 4, end.z >> 4)) return;

        boolean ok = LogisticsMarker.updateText(server, end.dim, uuid, text);
        if (!ok) {
            // 重建
            UUID newUuid = LogisticsMarker.create(world, new BlockPos(end.x, end.y, end.z), text);
            if (newUuid != null) {
                end.markerUuid = newUuid.toString();
                LogisticsTracker.save();
            }
        }
    }

    /** 两端同步。以 A 为主，比较 last snapshot：谁变了拷谁到对端。冲突时 A 优先。*/
    private static void syncInventories(MinecraftServer server, LogisticsPair pair) {
        ServerWorld wa = LogisticsMarker.resolveWorld(server, pair.a.dim);
        ServerWorld wb = LogisticsMarker.resolveWorld(server, pair.b.dim);
        if (wa == null || wb == null) return;
        if (!(wa.getBlockEntity(new BlockPos(pair.a.x, pair.a.y, pair.a.z)) instanceof BarrelBlockEntity bea)) return;
        if (!(wb.getBlockEntity(new BlockPos(pair.b.x, pair.b.y, pair.b.z)) instanceof BarrelBlockEntity beb)) return;

        // 判断哪端刚变化：与上次快照比较
        java.util.List<ItemStack> curA = snapshot(bea);
        java.util.List<ItemStack> curB = snapshot(beb);
        java.util.List<ItemStack> lastA = LAST_A.get(pair.pairId);
        java.util.List<ItemStack> lastB = LAST_B.get(pair.pairId);

        boolean aChanged = lastA == null || !stacksEqual(lastA, curA);
        boolean bChanged = lastB == null || !stacksEqual(lastB, curB);

        if (aChanged && !bChanged) {
            copy(bea, beb);
            beb.markDirty();
            curB = snapshot(beb);
        } else if (bChanged && !aChanged) {
            copy(beb, bea);
            bea.markDirty();
            curA = snapshot(bea);
        } else if (aChanged) {
            // 双端都变 → 冲突：A 优先
            copy(bea, beb);
            beb.markDirty();
            curB = snapshot(beb);
        } else if (!stacksEqual(curA, curB)) {
            // 未变但不同（首次 tick）：以 A 为主同步 B
            copy(bea, beb);
            beb.markDirty();
            curB = snapshot(beb);
        }

        LAST_A.put(pair.pairId, curA);
        LAST_B.put(pair.pairId, curB);
    }

    private static java.util.List<ItemStack> snapshot(Inventory inv) {
        java.util.ArrayList<ItemStack> list = new java.util.ArrayList<>(inv.size());
        for (int i = 0; i < inv.size(); i++) list.add(inv.getStack(i).copy());
        return list;
    }

    private static boolean stacksEqual(java.util.List<ItemStack> a, java.util.List<ItemStack> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            ItemStack sa = a.get(i);
            ItemStack sb = b.get(i);
            if (!ItemStack.areEqual(sa, sb)) return false;
        }
        return true;
    }

    private static void copy(Inventory from, Inventory to) {
        int n = Math.min(from.size(), to.size());
        for (int i = 0; i < n; i++) {
            to.setStack(i, from.getStack(i).copy());
        }
    }

    // ------------------------------------------------------------------ 破坏

    private static void onBreak(MinecraftServer server, LogisticsPair pair, String dim, BlockPos pos) {
        // 移除破坏端的 marker
        LogisticsPair.Endpoint broken = LogisticsTracker.endpointAt(pair, dim, pos);
        if (broken != null && broken.markerUuid != null) {
            try {
                LogisticsMarker.remove(server, broken.dim, UUID.fromString(broken.markerUuid));
            } catch (IllegalArgumentException ignored) {}
        }
        // 兜底扫描：按位置清理原地的 marker，防 UUID 不匹配残留
        LogisticsMarker.sweepAt(server, dim, pos);

        // 从 pair 里去掉这端
        if (pair.a != null && pair.a.equalsTo(broken)) pair.a = null;
        else if (pair.b != null && pair.b.equalsTo(broken)) pair.b = null;

        // 如果两端都没了：整个 pair 移除
        if (pair.a == null && pair.b == null) {
            LogisticsTracker.remove(pair.pairId);
            LAST_A.remove(pair.pairId);
            LAST_B.remove(pair.pairId);
        } else {
            LogisticsTracker.save();
        }
    }

    // ------------------------------------------------------------------ helpers

    private static Text defaultMarkerText(String pairId, boolean isA) {
        return Text.literal(String.format("§b物流终端 §7#§f%s §8(%s)", pairId, isA ? "A" : "B"));
    }
}
