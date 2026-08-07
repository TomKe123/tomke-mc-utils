package com.tomkestookit;

import com.tomkestookit.menu.AutoCrafterScreenHandler;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeType;
import net.minecraft.registry.Registries;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.GameRules;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Optional;

/** 自动合成器：特殊工作台 + 9 配方槽 + 9 输入槽 + 9 输出槽。*/
public final class AutoCrafterHandler {
    public static final String STACK_NBT_KEY = "TomkesTookitAutoCrafter";
    public static final String MARKER_TAG = "tomkestookit_auto_crafter_marker";
    private static final int CRAFT_INTERVAL_TICKS = 8;
    private static final java.util.Map<String, Integer> RECENTLY_BROKEN = new java.util.HashMap<>();
    private static final java.util.Map<String, AutoCrafterInventory> INV_CACHE = new java.util.HashMap<>();

    public static AutoCrafterInventory sharedInventory(ServerWorld world, BlockPos pos, AutoCrafterTracker.Config config) {
        return INV_CACHE.computeIfAbsent(AutoCrafterTracker.key(world, pos),
            k -> new AutoCrafterInventory(config, world, pos));
    }

    /** 供漏斗 mixin 使用：返回该位置的共享库存；不是自动合成器则返回 null。*/
    public static AutoCrafterInventory sharedInventoryAt(ServerWorld world, BlockPos pos) {
        AutoCrafterTracker.Config config = AutoCrafterTracker.get(world, pos);
        if (config == null) return null;
        return sharedInventory(world, pos, config);
    }

    static void dropInventoryCache(String key) {
        INV_CACHE.remove(key);
    }

    private AutoCrafterHandler() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(AutoCrafterHandler::tick);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            // 关服时把内存里剩余的 dirty 状态强制落盘，避免丢失最后一次合成/放置
            AutoCrafterTracker.flush();
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> AutoCrafterTracker.flush());
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (!TomkesTookitConfig.enabled("item.auto_crafter_ops")) return true;
            if (!(world instanceof ServerWorld sw)) return true;
            if (!state.isOf(Blocks.CRAFTING_TABLE)) return true;
            if (!AutoCrafterTracker.isAutoCrafter(sw, pos)) return true;
            dropAutoCrafterBreakLoot(sw, pos, player);
            sw.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
            return false;
        });
    }

    public static ItemStack buildItem() {
        ItemStack stack = new ItemStack(Items.CRAFTING_TABLE);
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putBoolean(STACK_NBT_KEY, true);
        nbt.putInt("HideFlags", 1);
        NbtList ench = new NbtList();
        NbtCompound fake = new NbtCompound();
        fake.putString("id", Registries.ENCHANTMENT.getId(net.minecraft.enchantment.Enchantments.UNBREAKING).toString());
        fake.putInt("lvl", 1);
        ench.add(fake);
        nbt.put("Enchantments", ench);
        stack.setCustomName(Text.literal("自动合成器").formatted(Formatting.GOLD, Formatting.BOLD));
        NbtCompound display = nbt.getCompound("display");
        NbtList lore = new NbtList();
        for (String line : List.of(
            "§8「工作台通电了。」",
            "§7铁与红石缝进木纹，从此不再需要玩家双手",
            "§7右键打开：设配方、放材料、看输出",
            "§7支持红石 §f持续 §7/ §f脉冲 §7/ §f无红石 §7三档",
            "§8上方漏斗喂料 §8· §8下方漏斗抽货",
            "§8输出满时会礼貌地停下"
        )) {
            lore.add(NbtString.of(Text.Serializer.toJson(Text.literal(line).styled(s -> s.withItalic(false)))));
        }
        display.put("Lore", lore);
        nbt.put("display", display);
        com.tomkestookit.materials.ItemQualityTag.append(stack,
            com.tomkestookit.materials.ItemQualityTag.Quality.EPIC,
            "3×3 合成 §8· §7铁锭 + 红石 + 工作台");
        return stack;
    }

    public static boolean isStack(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.CRAFTING_TABLE && stack.getNbt() != null && stack.getNbt().contains(STACK_NBT_KEY);
    }

    public static void onPlaced(ServerWorld world, BlockPos pos, ItemStack stack) {
        if (!isStack(stack)) return;
        AutoCrafterTracker.Config config = AutoCrafterTracker.ensure(world, pos);
        ensureMarker(world, config, "空闲");
        AutoCrafterTracker.save();
    }

    public static boolean onUse(ServerWorld world, BlockPos pos, ServerPlayerEntity player) {
        AutoCrafterTracker.Config config = AutoCrafterTracker.get(world, pos);
        if (config == null) return false;
        AutoCrafterInventory inv = sharedInventory(world, pos, config);
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, playerInv, ignored) -> new AutoCrafterScreenHandler(syncId, playerInv, inv, config, world, pos),
            Text.literal("自动合成器").formatted(Formatting.GOLD)
        ));
        return true;
    }

    public static ActionResult tryUse(PlayerEntity player, net.minecraft.world.World world, Hand hand, BlockHitResult hit) {
        if (world.isClient || !(world instanceof ServerWorld sw) || !(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
        return onUse(sw, hit.getBlockPos(), sp) ? ActionResult.SUCCESS : ActionResult.PASS;
    }

    public static void onBroken(ServerWorld world, BlockPos pos) {
        AutoCrafterTracker.Config config = AutoCrafterTracker.get(world, pos);
        if (config != null) {
            String key = AutoCrafterTracker.key(world, pos);
            RECENTLY_BROKEN.put(key, 4);
            removeMarker(world.getServer(), config);
            dropInventory(world, pos, config);
            AutoCrafterTracker.remove(world, pos);
            INV_CACHE.remove(key);
        }
    }

    private static void dropAutoCrafterBreakLoot(ServerWorld world, BlockPos pos, PlayerEntity player) {
        if (player == null || !player.isCreative()) {
            if (world.getGameRules().getBoolean(GameRules.DO_TILE_DROPS)) {
                ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, buildItem());
            }
        }
        onBroken(world, pos);
    }

    public static boolean wasRecentlyBroken(ServerWorld world, BlockPos pos) {
        return RECENTLY_BROKEN.containsKey(AutoCrafterTracker.key(world, pos));
    }

    /** 落盘频率：200 tick = 10 秒。合成/破坏这类关键操作只写 dirty，磁盘 IO 批量做。*/
    private static final int SAVE_INTERVAL_TICKS = 200;
    /** sweep 频率：一次全表扫描不便宜（要查每个 crafter 的方块），拉稀一点。*/
    private static final int SWEEP_INTERVAL_TICKS = 40;

    private static void tick(MinecraftServer server) {
        if (!TomkesTookitConfig.enabled("item.auto_crafter_ops")) return;
        int now = server.getTicks();
        // 廉价的每 tick 任务
        if (!RECENTLY_BROKEN.isEmpty()) {
            RECENTLY_BROKEN.entrySet().removeIf(e -> e.setValue(e.getValue() - 1) <= 0);
        }

        // 定时批量落盘
        if (now % SAVE_INTERVAL_TICKS == 0 && AutoCrafterTracker.isDirty()) {
            AutoCrafterTracker.flush();
        }

        // 主循环：合成节奏
        if (now % CRAFT_INTERVAL_TICKS != 0) return;
        boolean doSweep = (now % SWEEP_INTERVAL_TICKS == 0);
        if (doSweep) sweepBrokenCrafters(server);

        java.util.List<String> remove = null;
        for (var entry : AutoCrafterTracker.entries()) {
            AutoCrafterTracker.Config config = entry.getValue();
            ServerWorld world = AutoCrafterTracker.resolveWorld(server, config.dim);
            if (world == null) continue;
            int cx = config.x >> 4, cz = config.z >> 4;
            if (!world.isChunkLoaded(cx, cz)) continue;
            // sweep 已经处理过方块丢失情况；如果没到 sweep tick，还是要惰性检查一次，避免抓到已消失的合成器
            if (!doSweep && !AutoCrafterTracker.pointsToCraftingTable(server, config)) {
                removeMarker(server, config);
                if (remove == null) remove = new java.util.ArrayList<>();
                remove.add(entry.getKey());
                continue;
            }
            BlockPos pos = AutoCrafterTracker.pos(config);
            AutoCrafterInventory inv = sharedInventory(world, pos, config);
            ensureMarker(world, config, config.status);
            boolean poweredNow = world.isReceivingRedstonePower(pos);
            boolean shouldCraft = switch (config.triggerMode == null ? "powered" : config.triggerMode) {
                case "always" -> true;
                case "pulse" -> poweredNow && !config.powered;
                default -> poweredNow;
            };
            if (config.powered != poweredNow) {
                config.powered = poweredNow;
                AutoCrafterTracker.save();
            }
            if (shouldCraft) craftOnce(world, inv, config);
            else updateStatus(world, config, "空闲");
        }
        if (remove != null) {
            for (String key : remove) AutoCrafterTracker.removeKey(key);
        }
    }

    public static boolean craftOnce(ServerWorld world, Inventory inv, AutoCrafterTracker.Config config) {
        // 1) 统计配方需求：每种物品 → 需要多少个
        java.util.Map<String, Integer> need = new java.util.HashMap<>();
        for (String id : config.recipe) {
            if (id == null || id.isBlank()) continue;
            need.merge(id, 1, Integer::sum);
        }
        if (need.isEmpty()) {
            updateStatus(world, config, "空闲");
            return false;
        }

        // 2) 用 recipe 摆位构造 3x3 虚拟输入
        AutoCrafterRecipeInput input = new AutoCrafterRecipeInput(new Inventory() {
            @Override public int size() { return 9; }
            @Override public boolean isEmpty() { return false; }
            @Override public ItemStack getStack(int s) { return AutoCrafterTracker.stackFromId(config.recipe[s]); }
            @Override public ItemStack removeStack(int s, int a) { return ItemStack.EMPTY; }
            @Override public ItemStack removeStack(int s) { return ItemStack.EMPTY; }
            @Override public void setStack(int s, ItemStack st) {}
            @Override public void markDirty() {}
            @Override public boolean canPlayerUse(net.minecraft.entity.player.PlayerEntity p) { return true; }
            @Override public void clear() {}
        });
        // 先查 vanilla + datapack 配方
        Optional<CraftingRecipe> match = world.getServer().getRecipeManager()
            .getFirstMatch(RecipeType.CRAFTING, input, world);
        ItemStack result;
        if (match.isPresent()) {
            result = match.get().craft(input, world.getRegistryManager());
        } else {
            // vanilla 未识别时 fallback 到本 mod 的魔改配方链
            result = SpecialCraftingCatalog.tryMatch(input);
        }
        if (result.isEmpty() || !canInsertOutput(inv, result, config)) {
            updateStatus(world, config, "异常");
            return false;
        }

        // 3) 缓存池按物品 id 统计库存，检查是否满足需求
        java.util.Map<String, Integer> pool = new java.util.HashMap<>();
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty()) continue;
            String id = AutoCrafterTracker.idFromStack(s);
            if (id == null) continue;
            pool.merge(id, s.getCount(), Integer::sum);
        }
        for (var e : need.entrySet()) {
            if (pool.getOrDefault(e.getKey(), 0) < e.getValue()) {
                updateStatus(world, config, "空闲");
                return false;
            }
        }

        // 4) 按需求数量从缓存池扣（从前到后遍历缓存槽）
        for (var e : need.entrySet()) {
            int remaining = e.getValue();
            for (int i = 0; i < 9 && remaining > 0; i++) {
                ItemStack s = inv.getStack(i);
                if (s.isEmpty()) continue;
                if (!e.getKey().equals(AutoCrafterTracker.idFromStack(s))) continue;
                int take = Math.min(remaining, s.getCount());
                s.decrement(take);
                remaining -= take;
            }
        }

        insertOutput(inv, result.copy(), config, world);
        inv.markDirty();
        config.flashTicks = world.getServer().getTicks() + 6;
        updateStatus(world, config, "工作");
        return true;
    }

    public static boolean canInsertInput(ServerWorld world, BlockPos pos, int slot, ItemStack stack) {
        AutoCrafterTracker.Config config = AutoCrafterTracker.get(world, pos);
        if (config == null) return true;
        if (slot < 0 || slot >= 9) return false;
        // 每个缓存槽只接受该槽绑定的物品
        String expected = AutoCrafterInventory.slotBindingId(config, slot);
        if (expected == null) return false;
        String id = AutoCrafterTracker.idFromStack(stack);
        return expected.equals(id);
    }

    public static boolean canExtract(ServerWorld world, BlockPos pos, int slot) {
        AutoCrafterTracker.Config config = AutoCrafterTracker.get(world, pos);
        if (config == null) return true;
        return slot == 9;
    }

    private static boolean canInsertOutput(Inventory inv, ItemStack result, AutoCrafterTracker.Config config) {
        if (isEjectMode(config)) return true; // 弹出模式：不受内部输出格限制
        ItemStack target = inv.getStack(9);
        if (target.isEmpty()) return result.getCount() <= result.getMaxCount();
        if (!ItemStack.canCombine(target, result)) return false;
        int space = Math.min(target.getMaxCount(), inv.getMaxCountPerStack()) - target.getCount();
        return space >= result.getCount();
    }

    private static void insertOutput(Inventory inv, ItemStack stack,
                                     AutoCrafterTracker.Config config, ServerWorld world) {
        if (isEjectMode(config)) {
            ejectOutput(world, config, stack);
            return;
        }
        ItemStack target = inv.getStack(9);
        if (target.isEmpty()) {
            inv.setStack(9, stack.copy());
        } else if (ItemStack.canCombine(target, stack)) {
            target.increment(stack.getCount());
        }
    }

    private static boolean isEjectMode(AutoCrafterTracker.Config config) {
        String dir = config == null ? null : config.ejectDirection;
        return dir != null && !dir.isBlank() && !"none".equalsIgnoreCase(dir);
    }

    private static net.minecraft.util.math.Direction parseDirection(String key) {
        if (key == null) return null;
        return switch (key.toLowerCase()) {
            case "up" -> net.minecraft.util.math.Direction.UP;
            case "down" -> net.minecraft.util.math.Direction.DOWN;
            case "north" -> net.minecraft.util.math.Direction.NORTH;
            case "south" -> net.minecraft.util.math.Direction.SOUTH;
            case "east" -> net.minecraft.util.math.Direction.EAST;
            case "west" -> net.minecraft.util.math.Direction.WEST;
            default -> null;
        };
    }

    /**
     * 从选定方向把产物直接以 ItemEntity 的形式弹出：
     *   - 出生点：方块中心 + 方向单位向量 * 0.6
     *   - 初速度：方向单位向量 * 0.2（up/down 更明显）
     */
    private static void ejectOutput(ServerWorld world, AutoCrafterTracker.Config config, ItemStack stack) {
        net.minecraft.util.math.Direction dir = parseDirection(config.ejectDirection);
        if (dir == null) return;
        BlockPos pos = AutoCrafterTracker.pos(config);
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;
        double dx = dir.getOffsetX();
        double dy = dir.getOffsetY();
        double dz = dir.getOffsetZ();
        // 出生点略微偏出方块，避免立刻卡在方块中间
        double spawnX = cx + dx * 0.6;
        double spawnY = cy + dy * 0.6;
        double spawnZ = cz + dz * 0.6;
        net.minecraft.entity.ItemEntity entity = new net.minecraft.entity.ItemEntity(
            world, spawnX, spawnY, spawnZ, stack.copy());
        double horiz = 0.2;
        double vertUp = 0.3;
        double vertDown = -0.1;
        double vx = dx * horiz;
        double vz = dz * horiz;
        double vy;
        if (dir == net.minecraft.util.math.Direction.UP) vy = vertUp;
        else if (dir == net.minecraft.util.math.Direction.DOWN) vy = vertDown;
        else vy = 0.1;
        entity.setVelocity(vx, vy, vz);
        entity.setPickupDelay(10);
        world.spawnEntity(entity);
    }

    public static ItemStack buttonStack(String triggerMode) {
        String mode = triggerMode == null ? "powered" : triggerMode;
        ItemStack stack = new ItemStack(switch (mode) {
            case "always" -> Items.CLOCK;
            case "pulse" -> Items.REPEATER;
            default -> Items.REDSTONE_TORCH;
        });
        String name = switch (mode) {
            case "always" -> "无需红石：自动运行";
            case "pulse" -> "红石脉冲触发";
            default -> "红石持续触发";
        };
        stack.setCustomName(Text.literal(name).formatted(Formatting.RED, Formatting.BOLD));
        return stack;
    }

    public static ItemStack resultPreview(ServerWorld world, AutoCrafterTracker.Config config) {
        for (int i = 0; i < 9; i++) {
            ItemStack target = AutoCrafterTracker.stackFromId(config.recipe[i]);
            if (!target.isEmpty()) {
                ItemStack preview = target.copy();
                preview.setCount(1);
                return preview;
            }
        }
        ItemStack barrier = new ItemStack(Items.BARRIER);
        barrier.setCustomName(Text.literal("未设置目标产物").formatted(Formatting.RED));
        return barrier;
    }

    public static ItemStack helpStack() {
        ItemStack stack = new ItemStack(Items.KNOWLEDGE_BOOK);
        stack.setCustomName(Text.literal("说明").formatted(Formatting.YELLOW, Formatting.BOLD));
        return stack;
    }

    private static void updateStatus(ServerWorld world, AutoCrafterTracker.Config config, String status) {
        if (!status.equals(config.status)) {
            config.status = status;
            AutoCrafterTracker.save();
        }
        ensureMarker(world, config, status);
    }

    public static int sweepOrphans(MinecraftServer server) {
        java.util.Set<java.util.UUID> alive = new java.util.HashSet<>();
        for (var entry : AutoCrafterTracker.entries()) {
            java.util.UUID uuid = AutoCrafterTracker.markerUuid(entry.getValue());
            if (uuid != null) alive.add(uuid);
        }
        return ArmorStandMarkerUtil.sweepOrphans(server, AutoCrafterHandler::isMarker, alive);
    }

    private static void sweepBrokenCrafters(MinecraftServer server) {
        java.util.List<String> remove = new java.util.ArrayList<>();
        for (var entry : AutoCrafterTracker.entries()) {
            AutoCrafterTracker.Config config = entry.getValue();
            ServerWorld world = AutoCrafterTracker.resolveWorld(server, config.dim);
            if (world == null) continue;
            if (!world.isChunkLoaded(config.x >> 4, config.z >> 4)) continue;
            BlockPos pos = AutoCrafterTracker.pos(config);
            if (world.getBlockState(pos).isOf(net.minecraft.block.Blocks.CRAFTING_TABLE)) continue;
            removeMarker(server, config);
            remove.add(entry.getKey());
        }
        for (String key : remove) AutoCrafterTracker.removeKey(key);
        sweepOrphans(server);
    }

    private static Text markerText(String status) {
        Formatting color = switch (status) {
            case "工作" -> Formatting.GREEN;
            case "异常" -> Formatting.RED;
            default -> Formatting.GRAY;
        };
        return Text.literal("合成器 - ").formatted(Formatting.GOLD)
            .append(Text.literal(status).formatted(color));
    }

    private static void ensureMarker(ServerWorld world, AutoCrafterTracker.Config config, String status) {
        java.util.UUID uuid = AutoCrafterTracker.markerUuid(config);
        Text text = markerText(status);
        if (uuid != null) {
            Entity e = world.getEntity(uuid);
            if (e instanceof ArmorStandEntity stand) {
                stand.setCustomName(text);
                stand.setCustomNameVisible(true);
                return;
            }
        }
        sweepMarkerAt(world, AutoCrafterTracker.pos(config));
        ArmorStandEntity stand = EntityType.ARMOR_STAND.create(world);
        if (stand == null) return;
        BlockPos p = AutoCrafterTracker.pos(config);
        NbtCompound nbt = new NbtCompound();
        NbtList pos = new NbtList();
        pos.add(NbtDouble.of(p.getX() + 0.5));
        pos.add(NbtDouble.of(p.getY() + 1.1));
        pos.add(NbtDouble.of(p.getZ() + 0.5));
        nbt.put("Pos", pos);
        nbt.putBoolean("Invisible", true);
        nbt.putBoolean("NoGravity", true);
        nbt.putBoolean("Marker", true);
        nbt.putBoolean("Small", true);
        nbt.putBoolean("Invulnerable", true);
        nbt.putBoolean("CustomNameVisible", true);
        nbt.putString("CustomName", Text.Serializer.toJson(text));
        NbtList tags = new NbtList();
        tags.add(NbtString.of(MARKER_TAG));
        nbt.put("Tags", tags);
        stand.readNbt(nbt);
        stand.addCommandTag(MARKER_TAG);
        stand.refreshPositionAndAngles(p.getX() + 0.5, p.getY() + 1.1, p.getZ() + 0.5, 0.0f, 0.0f);
        world.spawnEntity(stand);
        config.markerUuid = stand.getUuidAsString();
        AutoCrafterTracker.save();
    }

    private static void removeMarker(MinecraftServer server, AutoCrafterTracker.Config config) {
        ServerWorld world = AutoCrafterTracker.resolveWorld(server, config.dim);
        if (world == null) return;
        java.util.UUID uuid = AutoCrafterTracker.markerUuid(config);
        if (uuid != null) {
            Entity e = world.getEntity(uuid);
            if (e != null) e.discard();
        }
        sweepMarkerAt(world, AutoCrafterTracker.pos(config));
    }

    private static void sweepMarkerAt(ServerWorld world, BlockPos pos) {
        ArmorStandMarkerUtil.sweepAt(world, pos, 1.1, AutoCrafterHandler::isMarker);
    }

    private static boolean isMarker(ArmorStandEntity stand) {
        if (stand.getCommandTags().contains(MARKER_TAG)) return true;
        if (!stand.hasCustomName()) return false;
        Text name = stand.getCustomName();
        return name != null && name.getString().contains("合成器 - ");
    }

    private static void dropInventory(ServerWorld world, BlockPos pos, AutoCrafterTracker.Config config) {
        for (int i = 0; i < 10; i++) {
            ItemStack stack = AutoCrafterTracker.inventoryStack(config, i);
            if (!stack.isEmpty()) ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
        }
    }

}
