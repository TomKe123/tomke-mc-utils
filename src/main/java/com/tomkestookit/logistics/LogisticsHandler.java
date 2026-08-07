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
            for (LogisticsPair.Endpoint e : pair.endpoints) {
                addUuid(set, e);
            }
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

            if (!player.isCreative()) {
                // 掉落带 pairId 的物流终端 stack（保留身份）
                ItemStack drop = buildResultForExistingPair(pair.pairId);
                net.minecraft.block.Block.dropStack(sw, pos, drop);

                // 只有当这是该编号最后一个终端时，才掉落内容物
                boolean isLastEndpoint = pair.endpoints.size() <= 1;
                if (isLastEndpoint) {
                    dumpContentsOnce(sw, pos, be, pair);
                }
                // 不是最后一个：不掉内容物，其它终端继续共享该库存
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
        LAST_SNAPSHOTS.remove(pair.pairId);
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
        com.tomkestookit.materials.ItemQualityTag.append(stack,
            com.tomkestookit.materials.ItemQualityTag.Quality.EPIC,
            "3×3 合成 §8· §7铁块 + 磁石（一次出 2 个）");
        return stack;
    }

    /** 复制合成结果：生成 1 个同 pairId 的物流终端。*/
    public static ItemStack buildResultForCopy(String pairId) {
        ItemStack stack = buildResultForExistingPair(pairId);
        // lore 追加复制说明
        NbtCompound nbt = stack.getOrCreateNbt();
        NbtCompound display = nbt.getCompound("display");
        NbtList lore = display.contains("Lore") ? display.getList("Lore", 8) : new NbtList();
        lore.add(NbtString.of(Text.Serializer.toJson(
            Text.literal("§a复制终端 §8- 与原终端互通").styled(s -> s.withItalic(false)))));
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
        com.tomkestookit.materials.ItemQualityTag.append(stack,
            com.tomkestookit.materials.ItemQualityTag.Quality.EPIC,
            "3×3 合成 §8· §7铁块 + 磁石（一次出 2 个）");
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

        if (pair == null) {
            pair = new LogisticsPair(pairId);
        }
        if (pair.endpoints == null) pair.endpoints = new java.util.ArrayList<>();
        // 检查该位置是否已经存在
        if (pair.containsPos(newEnd)) return;

        pair.endpoints.add(newEnd);
        LogisticsTracker.put(pair);

        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof BarrelBlockEntity) {
            be.markDirty();
        }

        int idx = pair.endpoints.size();
        UUID markerUuid = LogisticsMarker.create(world, pos,
            defaultMarkerText(pairId, idx, pair.endpoints.size()));
        newEnd.markerUuid = markerUuid == null ? null : markerUuid.toString();
        LogisticsTracker.save();
    }

    // ------------------------------------------------------------------ 打开拦截

    /** 由 mixin BarrelBlock.onUse 调用。true = 允许打开；false = 拒绝并给玩家消息。*/
    public static boolean canOpenLocal(ServerWorld world, BlockPos pos,
                                       net.minecraft.server.network.ServerPlayerEntity player) {
        String dim = world.getRegistryKey().getValue().toString();
        LogisticsPair pair = LogisticsTracker.findByPos(dim, pos);
        if (pair == null) return true;
        if (!pair.isComplete()) {
            player.sendMessage(Text.literal("§c[物流终端] 需要至少 2 个同编号终端。").formatted(Formatting.RED), true);
            return false;
        }
        LogisticsPair.Endpoint self = LogisticsTracker.endpointAt(pair, dim, pos);
        java.util.List<LogisticsPair.Endpoint> others = pair.others(self);
        // 检查是否至少有一个对端加载
        boolean anyLoaded = false;
        for (LogisticsPair.Endpoint other : others) {
            ServerWorld otherWorld = LogisticsMarker.resolveWorld(world.getServer(), other.dim);
            if (otherWorld != null && otherWorld.isChunkLoaded(other.x >> 4, other.z >> 4)) {
                BlockEntity otherBe = otherWorld.getBlockEntity(new BlockPos(other.x, other.y, other.z));
                if (otherBe instanceof BarrelBlockEntity) {
                    anyLoaded = true;
                    break;
                }
            }
        }
        if (!anyLoaded) {
            player.sendMessage(Text.literal("§c[物流终端] 所有对端均未加载。").formatted(Formatting.RED), true);
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ tick 同步 + marker 状态

    /** 同步用的”上次快照”，按 pairId 存。每个 pairId 存一份 27 格快照。*/
    private static final java.util.Map<String, java.util.List<ItemStack>> LAST_SNAPSHOTS = new java.util.HashMap<>();

    private static void tick(MinecraftServer server) {
        if (!com.tomkestookit.TomkesTookitConfig.enabled("item.logistics_ops")) return;
        if ((server.getTicks() & 0x1) != 0) return;

        java.util.List<LogisticsPair> snapshot = new java.util.ArrayList<>(LogisticsTracker.all());
        for (LogisticsPair pair : snapshot) {
            if (pair == null || pair.endpoints.isEmpty()) continue;
            sweepBrokenEndpoints(server, pair);
            if (pair.endpoints.isEmpty()) continue;

            // 找所有加载且存在的 barrel
            java.util.List<BarrelBlockEntity> loaded = new java.util.ArrayList<>();
            int unloadedCount = 0;
            for (LogisticsPair.Endpoint e : pair.endpoints) {
                ServerWorld w = LogisticsMarker.resolveWorld(server, e.dim);
                if (w == null || !w.isChunkLoaded(e.x >> 4, e.z >> 4)) {
                    unloadedCount++;
                    continue;
                }
                BlockEntity be = w.getBlockEntity(new BlockPos(e.x, e.y, e.z));
                if (be instanceof BarrelBlockEntity barrel) loaded.add(barrel);
            }

            // 更新所有端的 marker
            updateAllMarkers(server, pair, unloadedCount);

            // 同步：>=2 个加载端才做
            if (loaded.size() < 2) continue;
            syncMulti(pair, loaded);
        }
    }

    /**
     * 兜底清理：检查每端 endpoint 是否还是 barrel（要求 chunk 已加载才判定，
     * 未加载不动，避免误判）。发现方块没了就销毁 marker、从 pair 里移除该端。
     */
    private static void sweepBrokenEndpoints(MinecraftServer server, LogisticsPair pair) {
        boolean changed = false;
        var it = pair.endpoints.iterator();
        while (it.hasNext()) {
            LogisticsPair.Endpoint e = it.next();
            if (shouldSweep(server, e)) {
                destroyMarker(server, e);
                it.remove();
                changed = true;
            }
        }
        if (pair.endpoints.isEmpty()) {
            LogisticsTracker.remove(pair.pairId);
            LAST_SNAPSHOTS.remove(pair.pairId);
        } else if (changed) {
            LogisticsTracker.save();
        }
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

    /** 多端同步：用快照比对，找到变化源，向所有端广播。*/
    private static void syncMulti(LogisticsPair pair, java.util.List<BarrelBlockEntity> loaded) {
        java.util.List<ItemStack> last = LAST_SNAPSHOTS.get(pair.pairId);

        // 找哪个端变了（第一个不匹配 last 的）
        BarrelBlockEntity source = null;
        for (BarrelBlockEntity barrel : loaded) {
            java.util.List<ItemStack> cur = snapshot(barrel);
            if (last == null || !stacksEqual(last, cur)) {
                source = barrel;
                break;
            }
        }
        if (source == null) {
            // 没有端变化，但检查是否一致（首次 tick 或首次加载）
            java.util.List<ItemStack> first = snapshot(loaded.get(0));
            for (int i = 1; i < loaded.size(); i++) {
                if (!stacksEqual(first, snapshot(loaded.get(i)))) {
                    source = loaded.get(0); // 不一致，以第一个为源
                    break;
                }
            }
        }
        if (source == null) {
            // 全部一致且没变化
            LAST_SNAPSHOTS.put(pair.pairId, snapshot(loaded.get(0)));
            return;
        }

        // 把 source 广播到所有其它加载端
        for (BarrelBlockEntity barrel : loaded) {
            if (barrel == source) continue;
            copy(source, barrel);
            barrel.markDirty();
        }
        LAST_SNAPSHOTS.put(pair.pairId, snapshot(source));
    }

    private static void updateAllMarkers(MinecraftServer server, LogisticsPair pair, int unloadedCount) {
        int total = pair.endpoints.size();
        for (int i = 0; i < total; i++) {
            LogisticsPair.Endpoint e = pair.endpoints.get(i);
            Text text;
            if (total < 2) {
                text = Text.literal("§c[未配对] §7#§f" + pair.pairId);
            } else if (unloadedCount > 0) {
                text = Text.literal(String.format("§b终端 §7#§f%s §8[%d/%d] §c%d 未加载",
                    pair.pairId, i + 1, total, unloadedCount));
            } else {
                text = Text.literal(String.format("§b终端 §7#§f%s §8[%d/%d]",
                    pair.pairId, i + 1, total));
            }
            maintainMarker(server, pair, e, text);
        }
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
    // (已被 syncMulti 替代，保留方法签名以防编译依赖)

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
        LogisticsMarker.sweepAt(server, dim, pos);

        // 从 pair 端点列表里去掉这端
        if (broken != null) pair.endpoints.remove(broken);

        if (pair.endpoints.isEmpty()) {
            LogisticsTracker.remove(pair.pairId);
            LAST_SNAPSHOTS.remove(pair.pairId);
        } else {
            LogisticsTracker.save();
        }
    }

    // ------------------------------------------------------------------ helpers

    private static Text defaultMarkerText(String pairId, int index, int total) {
        return Text.literal(String.format("§b终端 §7#§f%s §8[%d/%d]", pairId, index, total));
    }
}
