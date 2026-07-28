package com.tomkestookit.menu;

import com.tomkestookit.AutoCrafterInventory;
import com.tomkestookit.AutoCrafterTracker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * 自动合成器 UI（GENERIC_9X6）。布局：
 *
 *   row0:  G G G . . . . . .     G = 配方槽（幽灵，玩家点击设置目标配方）
 *   row1:  G G G . ⇒ . . O .     ⇒ = 装饰箭头, O = 输出槽
 *   row2:  G G G . . . . . .
 *   row3:  I I I . . . . . .     I = 3x3 材料缓存（每格独立，只接受对应配方物品）
 *   row4:  I I I . . . . . .
 *   row5:  I I I . M . . . H     M = 触发模式, H = 说明
 */
public final class AutoCrafterScreenHandler extends ScreenHandler {
    private static final int[] RECIPE_UI_SLOTS = {
        0, 1, 2,
        9, 10, 11,
        18, 19, 20
    };
    /** 材料缓存横排 9 格（row4）。*/
    private static final int[] CACHE_UI_SLOTS = {
        36, 37, 38, 39, 40, 41, 42, 43, 44
    };
    private static final int OUTPUT_UI_SLOT = 16;
    private static final int ARROW_SLOT = 13;
    private static final int STATUS_SLOT = 22;
    private static final int MODE_SLOT = 45;
    private static final int HELP_SLOT = 53;

    private final AutoCrafterInventory inventory;
    private final AutoCrafterTracker.Config config;
    private final ServerWorld world;

    public AutoCrafterScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory,
                                    AutoCrafterTracker.Config config, ServerWorld world, BlockPos pos) {
        super(ScreenHandlerType.GENERIC_9X6, syncId);
        this.inventory = (AutoCrafterInventory) inventory;
        this.config = config;
        this.world = world;

        for (int i = 0; i < 54; i++) {
            int col = i % 9;
            int row = i / 9;
            int x = 8 + col * 18;
            int y = 18 + row * 18;
            int recipeIdx = indexOf(RECIPE_UI_SLOTS, i);
            int cacheIdx = indexOf(CACHE_UI_SLOTS, i);
            if (recipeIdx >= 0) {
                addSlot(new RecipeSlot(recipeIdx, x, y));
            } else if (cacheIdx >= 0) {
                addSlot(new CacheSlot(this.inventory, cacheIdx, x, y));
            } else if (i == OUTPUT_UI_SLOT) {
                addSlot(new OutputSlot(this.inventory, 9, x, y));
            } else {
                addSlot(new GhostSlot(i, x, y));
            }
        }

        int playerY = 140;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, playerY + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, playerY + 58));
        }

        refreshDecorations();
    }

    private static int indexOf(int[] arr, int v) {
        for (int i = 0; i < arr.length; i++) if (arr[i] == v) return i;
        return -1;
    }

    @Override
    public void sendContentUpdates() {
        refreshDecorations();
        super.sendContentUpdates();
    }

    private void refreshDecorations() {
        // flashTicks 是"闪光到期的绝对 tick"；服务端不再每 tick 递减
        boolean flash = config.flashTicks > world.getServer().getTicks();
        // 配方槽：幽灵展示 config.recipe
        for (int i = 0; i < RECIPE_UI_SLOTS.length; i++) {
            slots.get(RECIPE_UI_SLOTS[i]).setStack(AutoCrafterTracker.stackFromId(config.recipe[i]));
        }
        // 装饰
        for (int i = 0; i < 54 && i < slots.size(); i++) {
            if (isRealSlot(i)) continue;
            slots.get(i).setStack(decorationFor(i, flash));
        }
    }

    private boolean isRealSlot(int slot) {
        return indexOf(RECIPE_UI_SLOTS, slot) >= 0
            || indexOf(CACHE_UI_SLOTS, slot) >= 0
            || slot == OUTPUT_UI_SLOT;
    }

    private ItemStack decorationFor(int slot, boolean flash) {
        if (slot == ARROW_SLOT) return arrowStack(flash);
        if (slot == STATUS_SLOT) return statusStack(flash);
        if (slot == MODE_SLOT) return modeStack(flash);
        if (slot == HELP_SLOT) return helpStack(flash);
        return borderStack(flash);
    }

    private ItemStack arrowStack(boolean flash) {
        Item item = flash ? Items.LIME_STAINED_GLASS_PANE : Items.WHITE_STAINED_GLASS_PANE;
        return decor(item, "§f§l⇒ 合成", List.of(
            "§7左上：配方槽（幽灵，摆样品）",
            "§7左下：材料缓存 3×3",
            "§7右侧：输出格",
            flash ? "§a✓ 刚刚合成成功" : "§8等待中"
        ));
    }

    private ItemStack statusStack(boolean flash) {
        String status = config.status == null ? "空闲" : config.status;
        Item item;
        Formatting color;
        switch (status) {
            case "工作" -> { item = Items.LIME_STAINED_GLASS_PANE; color = Formatting.GREEN; }
            case "异常" -> { item = Items.RED_STAINED_GLASS_PANE; color = Formatting.RED; }
            default -> { item = Items.GRAY_STAINED_GLASS_PANE; color = Formatting.GRAY; }
        }
        if (flash) { item = Items.LIME_STAINED_GLASS_PANE; color = Formatting.GREEN; }
        Text name = Text.literal("● 状态：").formatted(Formatting.GOLD)
            .append(Text.literal(status).formatted(color));
        ItemStack stack = new ItemStack(item);
        stack.setCustomName(applyItalic(name));
        applyLore(stack, List.of(
            "§7空闲：等待材料或触发",
            "§7工作：本次合成成功",
            "§7异常：材料不满配方 / 输出满"
        ));
        return stack;
    }

    private ItemStack modeStack(boolean flash) {
        String mode = config.triggerMode == null ? "powered" : config.triggerMode;
        Item item = flash ? Items.LIME_STAINED_GLASS_PANE : switch (mode) {
            case "always" -> Items.CLOCK;
            case "pulse" -> Items.REPEATER;
            default -> Items.REDSTONE_TORCH;
        };
        String label = switch (mode) {
            case "always" -> "§a无需红石";
            case "pulse" -> "§d脉冲红石";
            default -> "§c持续红石";
        };
        ItemStack stack = new ItemStack(item);
        stack.setCustomName(applyItalic(Text.literal("§e§l⚙ 触发模式  " + label)));
        applyLore(stack, List.of(
            "§7左键切换：",
            (mode.equals("powered") ? "§a▶ " : "§8   ") + "持续红石   §8· 有电就跑",
            (mode.equals("pulse") ? "§a▶ " : "§8   ") + "脉冲红石   §8· 前沿触发一次",
            (mode.equals("always") ? "§a▶ " : "§8   ") + "无需红石   §8· 一直自动运行"
        ));
        return stack;
    }

    private ItemStack helpStack(boolean flash) {
        Item item = flash ? Items.LIME_STAINED_GLASS_PANE : Items.WRITTEN_BOOK;
        ItemStack stack = new ItemStack(item);
        stack.setCustomName(applyItalic(Text.literal("§b§l? 使用说明")));
        applyLore(stack, List.of(
            "§7配方槽（左上 3×3，幽灵）：",
            "§8· 手持样品左键：设置该格目标物品",
            "§8· 空手左键：清空该格",
            "§7材料缓存（左下 3×3）：",
            "§8· 配方里每种物品有一个专属缓存槽",
            "§8· 相同物品不会占用多个槽",
            "§8· 每次合成从各槽消耗 1 个",
            "§7输出：右上单格，满了会停机",
            "§7自动化：",
            "§8· 顶面 / 侧面漏斗 → 输入",
            "§8· 底部漏斗 → 抽出输出"
        ));
        return stack;
    }

    private static ItemStack borderStack(boolean flash) {
        Item item = flash ? Items.LIME_STAINED_GLASS_PANE : Items.GRAY_STAINED_GLASS_PANE;
        ItemStack stack = new ItemStack(item);
        stack.setCustomName(Text.literal(" "));
        return stack;
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity sp)) {
            super.onSlotClick(slotIndex, button, actionType, player);
            return;
        }
        // 触发模式按钮
        if (slotIndex == MODE_SLOT) {
            config.triggerMode = switch (config.triggerMode == null ? "powered" : config.triggerMode) {
                case "powered" -> "pulse";
                case "pulse" -> "always";
                default -> "powered";
            };
            config.pulseMode = "pulse".equals(config.triggerMode);
            AutoCrafterTracker.save();
            click(sp);
            refreshDecorations();
            return;
        }
        // 配方槽：手持样品点击 = 设置该格；空手点击 = 清空
        int recipeIdx = indexOf(RECIPE_UI_SLOTS, slotIndex);
        if (recipeIdx >= 0) {
            ItemStack cursor = getCursorStack();
            String newId = cursor.isEmpty() ? null : AutoCrafterTracker.idFromStack(cursor);
            String oldId = config.recipe[recipeIdx];
            config.recipe[recipeIdx] = newId;
            // 换配方后该缓存槽里若不再匹配，还给玩家（放到光标或掉在原地）
            if (!java.util.Objects.equals(oldId, newId)) {
                ItemStack cached = inventory.getStack(recipeIdx);
                if (!cached.isEmpty() && (newId == null
                        || !newId.equals(AutoCrafterTracker.idFromStack(cached)))) {
                    inventory.setStack(recipeIdx, ItemStack.EMPTY);
                    if (!sp.giveItemStack(cached)) {
                        sp.dropItem(cached, false);
                    }
                }
            }
            AutoCrafterTracker.save();
            click(sp);
            refreshDecorations();
            return;
        }
        // 其它装饰槽：只播音效
        if (slotIndex >= 0 && slotIndex < 54 && !isRealSlot(slotIndex)) {
            click(sp);
            return;
        }
        super.onSlotClick(slotIndex, button, actionType, player);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        Slot s = slots.get(slot);
        if (!s.hasStack()) return ItemStack.EMPTY;
        ItemStack stack = s.getStack();
        ItemStack copy = stack.copy();
        if (slot < 54) {
            if (!insertItem(stack, 54, 90, true)) return ItemStack.EMPTY;
        } else {
            if (!insertItemToCache(stack)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) s.setStack(ItemStack.EMPTY);
        else s.markDirty();
        return copy;
    }

    private boolean insertItemToCache(ItemStack stack) {
        String id = AutoCrafterTracker.idFromStack(stack);
        if (id == null) return false;
        // 必须是当前配方里出现过的物品，才能进缓存池
        boolean inRecipe = false;
        for (String r : config.recipe) if (r != null && r.equals(id)) { inRecipe = true; break; }
        if (!inRecipe) return false;

        boolean moved = false;
        // 1) 先尝试和已有相同物品堆叠
        for (int i = 0; i < 9 && !stack.isEmpty(); i++) {
            ItemStack existing = inventory.getStack(i);
            if (existing.isEmpty()) continue;
            if (!ItemStack.canCombine(existing, stack)) continue;
            int space = Math.min(existing.getMaxCount(), inventory.getMaxCountPerStack()) - existing.getCount();
            if (space <= 0) continue;
            int move = Math.min(space, stack.getCount());
            existing.increment(move);
            stack.decrement(move);
            inventory.markDirty();
            moved = true;
        }
        // 2) 再找空槽
        for (int i = 0; i < 9 && !stack.isEmpty(); i++) {
            if (!inventory.getStack(i).isEmpty()) continue;
            int move = Math.min(stack.getCount(), stack.getMaxCount());
            inventory.setStack(i, stack.split(move));
            moved = true;
        }
        return moved;
    }

    @Override public boolean canUse(PlayerEntity player) { return inventory.canPlayerUse(player); }

    private static void click(ServerPlayerEntity player) {
        player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), SoundCategory.MASTER, 0.5f, 1.0f);
    }

    private static Text applyItalic(Text text) {
        return Text.literal("").append(text).styled(s -> s.withItalic(false));
    }

    private static ItemStack decor(Item item, String name, List<String> lore) {
        ItemStack stack = new ItemStack(item);
        stack.setCustomName(applyItalic(Text.literal(name)));
        applyLore(stack, lore);
        return stack;
    }

    private static void applyLore(ItemStack stack, List<String> loreLines) {
        NbtCompound nbt = stack.getOrCreateNbt();
        NbtCompound display = nbt.getCompound("display");
        NbtList lore = new NbtList();
        for (String line : loreLines) {
            lore.add(NbtString.of(Text.Serializer.toJson(
                Text.literal(line).styled(s -> s.withItalic(false)))));
        }
        display.put("Lore", lore);
        nbt.put("display", display);
    }

    /** 幽灵配方槽：不接受插入 / 取出（点击行为在 onSlotClick 里手工处理）。*/
    private static final class RecipeSlot extends Slot {
        RecipeSlot(int index, int x, int y) { super(new net.minecraft.inventory.SimpleInventory(9), index, x, y); }
        @Override public boolean canInsert(ItemStack stack) { return false; }
        @Override public boolean canTakeItems(PlayerEntity playerEntity) { return false; }
    }

    /** 材料缓存槽：canInsert 由 AutoCrafterInventory#isValid 保证按格匹配。*/
    private static final class CacheSlot extends Slot {
        CacheSlot(Inventory inv, int index, int x, int y) { super(inv, index, x, y); }
        @Override public boolean canInsert(ItemStack stack) { return inventory.isValid(getIndex(), stack); }
    }

    private static final class GhostSlot extends Slot {
        GhostSlot(int index, int x, int y) { super(new net.minecraft.inventory.SimpleInventory(54), index, x, y); }
        @Override public boolean canInsert(ItemStack stack) { return false; }
        @Override public boolean canTakeItems(PlayerEntity playerEntity) { return false; }
    }

    private static final class OutputSlot extends Slot {
        OutputSlot(Inventory inv, int index, int x, int y) { super(inv, index, x, y); }
        @Override public boolean canInsert(ItemStack stack) { return false; }
    }
}
