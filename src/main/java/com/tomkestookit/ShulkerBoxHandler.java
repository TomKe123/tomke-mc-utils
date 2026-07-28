package com.tomkestookit;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;

/**
 * 手持潜影盒 shift + 右键 → 原地打开潜影盒 UI。
 *
 * 语义：
 *   - 打开：把 stack.BlockEntityTag.Items 反序列化到一个 27 格 SimpleInventory
 *   - 关闭：把 SimpleInventory 写回同一份 stack 的 NBT
 *   - 只允许该手持槽在 UI 存活期间不动：占用槽的 stack 在关闭前保持 UI 打开，
 *     玩家在盒里拖动物品仅影响临时 inventory，最终写回；
 *   - 玩家在 UI 里尝试点主手/副手对应的槽被 BusySlot 拦截，避免"打开时把盒本体拖走导致的丢失"。
 *
 * 使用方式：
 *   Shift + 右键任意位置（空气或方块）都可以触发，避免必须瞄准方块。
 */
public final class ShulkerBoxHandler {

    private static final String BE_TAG = "BlockEntityTag";
    private static final String ITEMS_KEY = "Items";
    private static final int SIZE = 27;

    private ShulkerBoxHandler() {}

    public static void register() {
        // 空气右键：Fabric 的 UseItemCallback；返回 SUCCESS + 该 stack 表示消费
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClient) return TypedActionResult.pass(player.getStackInHand(hand));
            if (!TomkesTookitConfig.enabled("item.shulker_handheld")) return TypedActionResult.pass(player.getStackInHand(hand));
            if (!player.isSneaking() || hand != Hand.MAIN_HAND) return TypedActionResult.pass(player.getStackInHand(hand));
            if (!(player instanceof ServerPlayerEntity sp)) return TypedActionResult.pass(player.getStackInHand(hand));
            ItemStack held = sp.getStackInHand(hand);
            if (!isShulkerBox(held)) return TypedActionResult.pass(held);
            openShulkerUi(sp, hand);
            return TypedActionResult.success(held, true);
        });
        // 瞄到方块的右键：也支持，抢在 vanilla 使用行为之前
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient) return ActionResult.PASS;
            if (!TomkesTookitConfig.enabled("item.shulker_handheld")) return ActionResult.PASS;
            if (!player.isSneaking() || hand != Hand.MAIN_HAND) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            ItemStack held = sp.getStackInHand(hand);
            if (!isShulkerBox(held)) return ActionResult.PASS;
            openShulkerUi(sp, hand);
            return ActionResult.SUCCESS;
        });
    }

    public static boolean isShulkerBox(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BlockItem bi
            && bi.getBlock() instanceof ShulkerBoxBlock;
    }

    private static void openShulkerUi(ServerPlayerEntity player, Hand hand) {
        ItemStack box = player.getStackInHand(hand);
        int slotIndex = (hand == Hand.MAIN_HAND) ? player.getInventory().selectedSlot : PlayerInventory.OFF_HAND_SLOT;

        SimpleInventory inv = new SimpleInventory(SIZE);
        loadIntoInventory(box, inv);

        Text title = box.hasCustomName() ? box.getName() : Text.translatable(box.getItem().getTranslationKey());

        NamedScreenHandlerFactory factory = new NamedScreenHandlerFactory() {
            @Override public Text getDisplayName() { return title; }
            @Override public ScreenHandler createMenu(int syncId, PlayerInventory playerInv, PlayerEntity p) {
                return new HandheldShulkerScreenHandler(syncId, playerInv, inv, slotIndex, hand, box);
            }
        };
        player.openHandledScreen(factory);
    }

    private static void loadIntoInventory(ItemStack stack, SimpleInventory dst) {
        NbtCompound nbt = stack.getNbt();
        if (nbt == null) return;
        NbtCompound be = nbt.getCompound(BE_TAG);
        if (!be.contains(ITEMS_KEY)) return;
        NbtList items = be.getList(ITEMS_KEY, 10);
        for (int i = 0; i < items.size(); i++) {
            NbtCompound tag = items.getCompound(i);
            int slot = tag.getByte("Slot") & 0xFF;
            if (slot < SIZE) {
                dst.setStack(slot, ItemStack.fromNbt(tag));
            }
        }
    }

    private static void saveFromInventory(ItemStack stack, Inventory inv) {
        NbtCompound root = stack.getOrCreateNbt();
        NbtCompound be = root.getCompound(BE_TAG);
        NbtList items = new NbtList();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty()) continue;
            NbtCompound tag = new NbtCompound();
            tag.putByte("Slot", (byte) i);
            s.writeNbt(tag);
            items.add(tag);
        }
        if (items.isEmpty()) {
            be.remove(ITEMS_KEY);
        } else {
            be.put(ITEMS_KEY, items);
        }
        // 保留 BlockEntityTag 里的 CustomName / LootTable 等其它字段
        if (be.isEmpty()) root.remove(BE_TAG);
        else root.put(BE_TAG, be);
    }

    /** 27 格 ScreenHandler，关闭时把 inv 写回 stack，同时阻止玩家在 UI 里操作盒本体所在的槽。*/
    public static final class HandheldShulkerScreenHandler extends GenericContainerScreenHandler {
        private final Inventory boxInventory;
        private final int lockedSlot;      // PlayerInventory 索引（主手 0..8，副手 40）
        private final Hand hand;
        private final ItemStack sourceStack;

        public HandheldShulkerScreenHandler(int syncId, PlayerInventory playerInventory,
                                            Inventory boxInventory, int lockedSlot, Hand hand,
                                            ItemStack sourceStack) {
            super(net.minecraft.screen.ScreenHandlerType.GENERIC_9X3, syncId, playerInventory, boxInventory, 3);
            this.boxInventory = boxInventory;
            this.lockedSlot = lockedSlot;
            this.hand = hand;
            this.sourceStack = sourceStack;
        }

        @Override
        public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
            if (slotIndex >= 0 && slotIndex < this.slots.size()) {
                Slot s = this.slots.get(slotIndex);
                // 如果玩家点击的槽正好对应"手上正在打开的这个潜影盒"，禁止任何操作，防止盒被拿走
                if (s.inventory instanceof PlayerInventory && s.getIndex() == lockedSlot) {
                    return;
                }
            }
            // 上面已经拦住了 lockedSlot（当前打开的盒本体）；shift-click 背包里另一个
            // 潜影盒进本盒的操作交给 vanilla 处理，保持原语义。
            super.onSlotClick(slotIndex, button, actionType, player);
        }

        @Override
        public void onClosed(PlayerEntity player) {
            super.onClosed(player);
            if (!(player instanceof ServerPlayerEntity sp)) return;
            // 关闭时，把当前 inv 写回原始 stack。原始 stack 引用是打开时抓到的；
            // 如果这期间盒被玩家丢弃/移出，sourceStack 已经不在玩家背包里，就跳过。
            ItemStack current = sp.getInventory().getStack(lockedSlot);
            if (current != sourceStack) {
                // 兜底：找找同一 hand
                current = sp.getStackInHand(hand);
                if (current != sourceStack) return;
            }
            saveFromInventory(current, boxInventory);
        }
    }
}
