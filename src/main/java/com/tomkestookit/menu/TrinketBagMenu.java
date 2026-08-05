package com.tomkestookit.menu;

import com.tomkestookit.TomkesTookitConfig;
import com.tomkestookit.trinket.TrinketStore;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.UUID;

/**
 * 饰品袋 v3：分页 9×6 UI，支持最多 108 格（4 页 × 27 格）。
 *
 * 布局（54 格箱子）：
 *   row0: [头像] [装饰] [装饰] [装饰] [标题] [装饰] [装饰] [装饰] [帮助]
 *   row1: [装饰] [T0] [T1] [T2] [T3] [T4] [T5] [T6] [装饰]
 *   row2: [装饰] [T7] [T8] [T9] [T10] [T11] [T12] [T13] [装饰]
 *   row3: [装饰] [T14] [T15] [T16] [T17] [T18] [T19] [T20] [装饰]
 *   row4: [装饰] [T21] [T22] [T23] [T24] [T25] [T26] [装饰] [装饰]
 *   row5: [解锁] [上页] [装饰] [装饰] [信息] [装饰] [装饰] [下页] [关闭]
 */
public final class TrinketBagMenu {

    /** 饰品格在 54 slot inventory 中的位置。每页 27 个。*/
    private static final int[] TRINKET_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,   // row1: 7
        19, 20, 21, 22, 23, 24, 25,   // row2: 7
        28, 29, 30, 31, 32, 33, 34,   // row3: 7
        37, 38, 39, 40, 41, 42        // row4: 6
    };

    private static final int UNLOCK_SLOT = 45;
    private static final int PREV_PAGE_SLOT = 46;
    private static final int NEXT_PAGE_SLOT = 52;
    private static final int INFO_SLOT = 49;
    private static final int CLOSE_SLOT = 53;
    private static final int TITLE_SLOT = 4;
    private static final int HEAD_SLOT = 0;
    private static final int HELP_SLOT = 8;

    /** 空的已解锁格视觉标识。*/
    private static final ItemStack EMPTY_SLOT_ICON = makeEmptySlotIcon();

    private TrinketBagMenu() {}

    private static ItemStack makeEmptySlotIcon() {
        return MenuButtons.button(Items.LIME_STAINED_GLASS_PANE, "§a空饰品格",
            List.of("§7将饰品放入此格", "§7可获得被动效果"));
    }

    public static void open(ServerPlayerEntity player) {
        openFor(player, player.getGameProfile(), player.getUuid(), null);
    }

    /**
     * 打开某人的饰品袋。viewer 是操作者，target 是被查看/编辑的玩家。
     *   - viewer == target：本人打开，可扩容、关闭走 vanilla
     *   - viewer != target：他人查看/编辑，不能扩容，关闭走 onClose 回调
     *
     * onClose 为 null 时按 vanilla 关闭；非 null 时点关闭按钮触发（如返回上级菜单）。
     */
    public static void openFor(ServerPlayerEntity viewer,
                                com.mojang.authlib.GameProfile targetProfile,
                                UUID targetUuid,
                                java.util.function.Consumer<ServerPlayerEntity> onClose) {
        openPage(viewer, targetProfile, targetUuid, onClose, 0);
    }

    /** 打开指定页（0-based）。*/
    static void openPage(ServerPlayerEntity viewer,
                         com.mojang.authlib.GameProfile targetProfile,
                         UUID targetUuid,
                         java.util.function.Consumer<ServerPlayerEntity> onClose,
                         int page) {
        if (!TomkesTookitConfig.enabled("item.trinket")) {
            viewer.sendMessage(Text.literal("§c[饰品] 该功能已被管理员禁用")
                .formatted(Formatting.RED), true);
            return;
        }

        boolean isSelf = viewer.getUuid().equals(targetUuid);
        int capacity = TrinketStore.getCapacity(targetUuid);
        ItemStack[] stored = TrinketStore.readCopy(targetUuid);

        if (page < 0) page = 0;
        if (page >= TrinketStore.PAGE_COUNT) page = TrinketStore.PAGE_COUNT - 1;
        int pageStart = page * TrinketStore.PAGE_SIZE;

        SimpleInventory inv = new SimpleInventory(54);

        // 顶部：头像、标题、说明
        String targetName = targetProfile.getName();
        String headLabel = (isSelf ? "§5§l" : "§d§l[查看] ") + targetName;
        inv.setStack(HEAD_SLOT, MenuButtons.skull(targetProfile, headLabel,
            List.of("§7已解锁 §f" + capacity + "§7/§f" + TrinketStore.MAX_SIZE + " §7格",
                "§7当前 §e第 " + (page + 1) + "/" + TrinketStore.PAGE_COUNT + " 页")));
        String titleTip = isSelf
            ? "§7放入饰品获得被动效果"
            : "§d正在查看 §f" + targetName + " §d的饰品袋";
        inv.setStack(TITLE_SLOT, MenuButtons.button(Items.ENDER_EYE, "§5§l饰品袋",
            List.of(titleTip,
                "§7同种饰品不叠加",
                "§7已解锁 §f" + capacity + "§7/§f" + TrinketStore.MAX_SIZE + " §7格",
                "§e第 " + (page + 1) + "/" + TrinketStore.PAGE_COUNT + " 页")));
        inv.setStack(HELP_SLOT, MenuButtons.button(Items.BOOK, "§e§l说明",
            List.of("§7左键点击已解锁格放入/取出饰品",
                "§7只有饰品物品可以放入",
                isSelf ? "§7点击左下 §a[解锁] §7扩容" : "§8他人饰品袋不可扩容",
                "§7Shift+F 查看特殊配方")));

        // 饰品格内容（本页 27 格）
        for (int i = 0; i < TRINKET_SLOTS.length; i++) {
            int uiSlot = TRINKET_SLOTS[i];
            int globalIdx = pageStart + i;
            if (globalIdx < capacity) {
                if (stored[globalIdx] != null && !stored[globalIdx].isEmpty()) {
                    inv.setStack(uiSlot, stored[globalIdx].copy());
                } else {
                    inv.setStack(uiSlot, EMPTY_SLOT_ICON.copy());
                }
            } else {
                inv.setStack(uiSlot, lockedSlotIcon(globalIdx));
            }
        }

        // 底部按钮
        if (isSelf) {
            inv.setStack(UNLOCK_SLOT, unlockButton(capacity));
        } else {
            inv.setStack(UNLOCK_SLOT, MenuButtons.button(Items.BARRIER, "§8§l⇧ 解锁",
                List.of("§7只有本人可以扩容饰品袋")));
        }
        inv.setStack(INFO_SLOT, infoButton(capacity, stored));
        inv.setStack(CLOSE_SLOT, onClose != null
            ? MenuButtons.backButton()
            : MenuButtons.closeButton());

        // 翻页按钮
        if (page > 0) {
            inv.setStack(PREV_PAGE_SLOT, MenuButtons.button(Items.ARROW, "§a§l◀ 上一页",
                List.of("§7查看第 " + page + "/" + TrinketStore.PAGE_COUNT + " 页")));
        } else {
            inv.setStack(PREV_PAGE_SLOT, MenuButtons.button(Items.GRAY_DYE, "§8§l◀ 上一页",
                List.of("§8已是第一页")));
        }
        if (page < TrinketStore.PAGE_COUNT - 1) {
            inv.setStack(NEXT_PAGE_SLOT, MenuButtons.button(Items.ARROW, "§a§l▶ 下一页",
                List.of("§7查看第 " + (page + 2) + "/" + TrinketStore.PAGE_COUNT + " 页")));
        } else {
            inv.setStack(NEXT_PAGE_SLOT, MenuButtons.button(Items.GRAY_DYE, "§8§l▶ 下一页",
                List.of("§8已是最后一页")));
        }

        // 填充背景
        for (int i = 0; i < 54; i++) {
            if (inv.getStack(i).isEmpty()) {
                inv.setStack(i, MenuButtons.filler());
            }
        }

        Text title = Text.literal((isSelf ? "饰品袋" : "[查看] " + targetName + " 的饰品袋")
            + " (" + (page + 1) + "/" + TrinketStore.PAGE_COUNT + ")");
        int finalPage = page;
        viewer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, playerInv, ignored) ->
                new TrinketBagScreenHandler(syncId, playerInv, inv, targetProfile, targetUuid,
                    isSelf, onClose, finalPage),
            title
        ));
    }

    private static ItemStack lockedSlotIcon(int globalIdx) {
        int tier = globalIdx / 3;
        String requirement = unlockRequirement(tier);
        return MenuButtons.button(Items.BARRIER, "§c§l🔒 未解锁",
            List.of("§7全局第 §f" + (globalIdx + 1) + " §7格",
                "§7需要先解锁更多槽位", "§8解锁需要：" + requirement));
    }

    private static ItemStack unlockButton(int capacity) {
        if (capacity >= TrinketStore.MAX_SIZE) {
            ItemStack stack = MenuButtons.button(Items.NETHER_STAR, "§a§l已满级",
                List.of("§7全部 " + TrinketStore.MAX_SIZE + " 格已解锁！"));
            MenuButtons.withGlow(stack);
            return stack;
        }
        int nextTier = capacity / 3;
        String cost = unlockRequirement(nextTier);
        return MenuButtons.button(Items.EXPERIENCE_BOTTLE, "§a§l⇧ 解锁",
            List.of("§7当前 §f" + capacity + "§7/§f" + TrinketStore.MAX_SIZE + " §7格",
                "§7下一级 §f" + (capacity + 3) + " §7格",
                "§7需要 §f" + cost,
                "",
                "§e▶ 点击解锁（从背包扣材料）"));
    }

    private static ItemStack infoButton(int capacity, ItemStack[] stored) {
        int count = 0;
        for (ItemStack s : stored) if (s != null && !s.isEmpty()) count++;
        return MenuButtons.button(Items.PAINTING, "§b§l状态",
            List.of("§7容量 §f" + capacity + "§7/§f" + TrinketStore.MAX_SIZE,
                "§7已装备 §f" + count + " §7件饰品",
                "§7同种饰品不重复计效"));
    }

    static String unlockRequirement(int tier) {
        // tier 表示当前段：tier N 解锁 (N+1)*3 格
        // 前 9 段（0..8）沿用旧逻辑，达到 27 格；之后每 3 格递进为下界/末地系
        return switch (tier) {
            case 0 -> "免费（初始 3 格）";
            case 1 -> "4 皮革";
            case 2 -> "8 铁锭";
            case 3 -> "4 金锭 + 1 青金石";
            case 4 -> "4 钻石";
            case 5 -> "2 绿宝石块";
            case 6 -> "1 下界之星";
            case 7 -> "4 幽匿催发体";
            case 8 -> "1 潮涌核心";
            // 第二页（30-54 格）：下界系
            case 9 -> "8 磁石";
            case 10 -> "8 下界合金碎片";
            case 11 -> "1 下界合金锭";
            case 12 -> "1 龙息";
            case 13 -> "1 末影之眼 + 8 青金石块";
            case 14 -> "2 下界之星";
            case 15 -> "8 回响碎片";
            case 16 -> "2 潮涌核心";
            case 17 -> "2 下界合金锭";
            // 第三页（57-81 格）：末地/守卫者系
            case 18 -> "16 紫水晶碎片";
            case 19 -> "2 龙息";
            case 20 -> "4 下界合金锭";
            case 21 -> "1 海洋之心";
            case 22 -> "16 幽匿催发体";
            case 23 -> "3 下界之星";
            case 24 -> "1 龙蛋碎片(下界合金块)";
            case 25 -> "8 回响碎片 + 1 龙息";
            case 26 -> "1 潮涌核心 + 1 下界之星";
            // 第四页（84-108 格）：终极
            case 27 -> "8 下界合金锭";
            case 28 -> "1 龙头";
            case 29 -> "1 龙蛋";
            case 30 -> "16 龙息";
            case 31 -> "5 下界之星";
            case 32 -> "3 龙息 + 1 龙头";
            case 33 -> "5 龙息";
            case 34 -> "8 下界之星";
            case 35 -> "10 下界之星（终极）";
            default -> "已满级";
        };
    }

    /** 检查并扣除解锁材料。返回 true = 成功扣除。*/
    static boolean tryUnlock(ServerPlayerEntity player, int currentCapacity) {
        int tier = currentCapacity / 3; // tier 1 = 第一次升级(3→6)
        PlayerInventory pi = player.getInventory();
        return switch (tier) {
            case 1 -> consume(pi, Items.LEATHER, 4);
            case 2 -> consume(pi, Items.IRON_INGOT, 8);
            case 3 -> consumeMulti(pi, Items.GOLD_INGOT, 4, Items.LAPIS_LAZULI, 1);
            case 4 -> consume(pi, Items.DIAMOND, 4);
            case 5 -> consume(pi, Items.EMERALD_BLOCK, 2);
            case 6 -> consume(pi, Items.NETHER_STAR, 1);
            case 7 -> consume(pi, Items.SCULK_CATALYST, 4);
            case 8 -> consume(pi, Items.CONDUIT, 1);
            case 9 -> consume(pi, Items.LODESTONE, 8);
            case 10 -> consume(pi, Items.NETHERITE_SCRAP, 8);
            case 11 -> consume(pi, Items.NETHERITE_INGOT, 1);
            case 12 -> consume(pi, Items.DRAGON_BREATH, 1);
            case 13 -> consumeMulti(pi, Items.ENDER_EYE, 1, Items.LAPIS_BLOCK, 8);
            case 14 -> consume(pi, Items.NETHER_STAR, 2);
            case 15 -> consume(pi, Items.ECHO_SHARD, 8);
            case 16 -> consume(pi, Items.CONDUIT, 2);
            case 17 -> consume(pi, Items.NETHERITE_INGOT, 2);
            case 18 -> consume(pi, Items.AMETHYST_SHARD, 16);
            case 19 -> consume(pi, Items.DRAGON_BREATH, 2);
            case 20 -> consume(pi, Items.NETHERITE_INGOT, 4);
            case 21 -> consume(pi, Items.HEART_OF_THE_SEA, 1);
            case 22 -> consume(pi, Items.SCULK_CATALYST, 16);
            case 23 -> consume(pi, Items.NETHER_STAR, 3);
            case 24 -> consume(pi, Items.NETHERITE_BLOCK, 1);
            case 25 -> consumeMulti(pi, Items.ECHO_SHARD, 8, Items.DRAGON_BREATH, 1);
            case 26 -> consumeMulti(pi, Items.CONDUIT, 1, Items.NETHER_STAR, 1);
            case 27 -> consume(pi, Items.NETHERITE_INGOT, 8);
            case 28 -> consume(pi, Items.DRAGON_HEAD, 1);
            case 29 -> consume(pi, Items.DRAGON_EGG, 1);
            case 30 -> consume(pi, Items.DRAGON_BREATH, 16);
            case 31 -> consume(pi, Items.NETHER_STAR, 5);
            case 32 -> consumeMulti(pi, Items.DRAGON_BREATH, 3, Items.DRAGON_HEAD, 1);
            case 33 -> consume(pi, Items.DRAGON_BREATH, 5);
            case 34 -> consume(pi, Items.NETHER_STAR, 8);
            case 35 -> consume(pi, Items.NETHER_STAR, 10);
            default -> false;
        };
    }

    private static boolean consume(PlayerInventory inv, net.minecraft.item.Item item, int count) {
        int found = 0;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.getItem() == item) found += s.getCount();
        }
        if (found < count) return false;
        int remaining = count;
        for (int i = 0; i < inv.size() && remaining > 0; i++) {
            ItemStack s = inv.getStack(i);
            if (s.getItem() == item) {
                int take = Math.min(remaining, s.getCount());
                s.decrement(take);
                remaining -= take;
            }
        }
        return true;
    }

    /** 检查两种物品是否都够，先检查再一起扣。*/
    private static boolean consumeMulti(PlayerInventory inv,
                                        net.minecraft.item.Item item1, int count1,
                                        net.minecraft.item.Item item2, int count2) {
        int found1 = 0, found2 = 0;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.getItem() == item1) found1 += s.getCount();
            if (s.getItem() == item2) found2 += s.getCount();
        }
        if (found1 < count1 || found2 < count2) return false;
        consume(inv, item1, count1);
        consume(inv, item2, count2);
        return true;
    }

    /** 判断物品是否为有效饰品。*/
    private static boolean isTrinket(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        NbtCompound nbt = stack.getNbt();
        if (nbt == null) return false;
        return nbt.contains("TomkesTookitTrinket");
    }

    // ---- ScreenHandler ----

    public static final class TrinketBagScreenHandler extends ScreenHandler {
        private final SimpleInventory bagInventory;
        private final com.mojang.authlib.GameProfile targetProfile;
        private final UUID targetUuid;
        private final boolean isSelf;
        private final java.util.function.Consumer<ServerPlayerEntity> onClose;
        private final int page;
        private boolean navigating = false;

        TrinketBagScreenHandler(int syncId, PlayerInventory playerInventory,
                                SimpleInventory bagInventory,
                                com.mojang.authlib.GameProfile targetProfile,
                                UUID targetUuid,
                                boolean isSelf,
                                java.util.function.Consumer<ServerPlayerEntity> onClose,
                                int page) {
            super(ScreenHandlerType.GENERIC_9X6, syncId);
            this.bagInventory = bagInventory;
            this.targetProfile = targetProfile;
            this.targetUuid = targetUuid;
            this.isSelf = isSelf;
            this.onClose = onClose;
            this.page = page;

            // 54 container slots (index 0-53)
            for (int i = 0; i < 54; i++) {
                addSlot(new Slot(bagInventory, i, 8 + (i % 9) * 18, 18 + (i / 9) * 18));
            }
            // player inventory (index 54-80)
            for (int row = 0; row < 3; row++)
                for (int col = 0; col < 9; col++)
                    addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        8 + col * 18, 140 + row * 18));
            // hotbar (index 81-89)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(playerInventory, col, 8 + col * 18, 198));
        }

        @Override
        public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
            if (!(player instanceof ServerPlayerEntity sp)) return;

            // 拖拽操作全部禁用，避免混乱
            if (actionType == SlotActionType.QUICK_CRAFT || actionType == SlotActionType.PICKUP_ALL) {
                return;
            }

            // 玩家背包区域 (slot 54+)：允许自由操作
            if (slotIndex >= 54) {
                super.onSlotClick(slotIndex, button, actionType, player);
                return;
            }

            // 非法索引
            if (slotIndex < 0) return;

            // 解锁按钮（仅本人）
            if (slotIndex == UNLOCK_SLOT) {
                if (isSelf) handleUnlock(sp);
                else sp.sendMessage(Text.literal("§c[饰品] 只有本人可以扩容饰品袋")
                    .formatted(Formatting.RED), true);
                return;
            }
            // 翻页
            if (slotIndex == PREV_PAGE_SLOT) {
                if (page > 0) navigate(sp, page - 1);
                return;
            }
            if (slotIndex == NEXT_PAGE_SLOT) {
                if (page < TrinketStore.PAGE_COUNT - 1) navigate(sp, page + 1);
                return;
            }
            // 关闭 / 返回
            if (slotIndex == CLOSE_SLOT) {
                if (onClose != null) {
                    sp.closeHandledScreen();
                    var cb = onClose;
                    sp.getServer().execute(() -> cb.accept(sp));
                } else {
                    sp.closeHandledScreen();
                }
                return;
            }

            // 是否为饰品格
            int trinketIdx = trinketSlotIndex(slotIndex);
            if (trinketIdx < 0) return; // 装饰格无操作

            int globalIdx = page * TrinketStore.PAGE_SIZE + trinketIdx;
            int capacity = TrinketStore.getCapacity(targetUuid);

            // 未解锁格
            if (globalIdx >= capacity) {
                sp.sendMessage(Text.literal("§c[饰品] 该格位未解锁，请先点击左下角解锁按钮")
                    .formatted(Formatting.RED), true);
                return;
            }

            // --- 已解锁饰品格：手动实现放入/取出 ---
            handleTrinketSlotClick(sp, slotIndex);
        }

        /** 翻页：先保存当前页，再打开新页。*/
        private void navigate(ServerPlayerEntity sp, int newPage) {
            navigating = true;
            saveCurrentPage();
            sp.closeHandledScreen();
            sp.getServer().execute(() ->
                TrinketBagMenu.openPage(sp, targetProfile, targetUuid, onClose, newPage));
        }

        /** 手动处理饰品格点击：cursor ↔ slot 交换。*/
        private void handleTrinketSlotClick(ServerPlayerEntity sp, int slotIndex) {
            ItemStack cursor = getCursorStack();
            ItemStack inSlot = bagInventory.getStack(slotIndex);
            // 判断 slot 中物品是否为「空格标识」
            boolean slotIsEmpty = inSlot.isEmpty() || inSlot.getItem() == Items.LIME_STAINED_GLASS_PANE;

            if (cursor.isEmpty()) {
                // 光标为空 → 取出饰品
                if (!slotIsEmpty) {
                    setCursorStack(inSlot.copy());
                    bagInventory.setStack(slotIndex, EMPTY_SLOT_ICON.copy());
                    sp.playSound(SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.MASTER, 0.3f, 1.5f);
                }
            } else {
                // 光标有物品 → 检查是否为饰品
                if (!isTrinket(cursor)) {
                    sp.sendMessage(Text.literal("§c[饰品] 只能放入饰品物品")
                        .formatted(Formatting.RED), true);
                    return;
                }
                if (slotIsEmpty) {
                    bagInventory.setStack(slotIndex, cursor.copy());
                    setCursorStack(ItemStack.EMPTY);
                    sp.playSound(SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.MASTER, 0.5f, 1.2f);
                } else {
                    ItemStack old = inSlot.copy();
                    bagInventory.setStack(slotIndex, cursor.copy());
                    setCursorStack(old);
                    sp.playSound(SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.MASTER, 0.5f, 1.0f);
                }
            }
            sendContentUpdates();
        }

        @Override
        public ItemStack quickMove(PlayerEntity player, int slot) {
            // shift-click：如果是玩家背包的饰品，尝试放入本页第一个空的已解锁格
            if (slot >= 54 && player instanceof ServerPlayerEntity sp) {
                Slot src = slots.get(slot);
                ItemStack stack = src.getStack();
                if (isTrinket(stack)) {
                    int capacity = TrinketStore.getCapacity(targetUuid);
                    int pageStart = page * TrinketStore.PAGE_SIZE;
                    for (int i = 0; i < TRINKET_SLOTS.length; i++) {
                        int globalIdx = pageStart + i;
                        if (globalIdx >= capacity) break;
                        ItemStack existing = bagInventory.getStack(TRINKET_SLOTS[i]);
                        if (existing.isEmpty() || existing.getItem() == Items.LIME_STAINED_GLASS_PANE) {
                            bagInventory.setStack(TRINKET_SLOTS[i], stack.copy());
                            src.setStack(ItemStack.EMPTY);
                            sp.playSound(SoundEvents.BLOCK_CHAIN_PLACE,
                                SoundCategory.MASTER, 0.5f, 1.2f);
                            sendContentUpdates();
                            return ItemStack.EMPTY;
                        }
                    }
                    sp.sendMessage(Text.literal("§c[饰品] 本页没有空余已解锁格")
                        .formatted(Formatting.RED), true);
                }
            }
            return ItemStack.EMPTY;
        }

        @Override
        public boolean canUse(PlayerEntity player) { return true; }

        @Override
        public void onClosed(PlayerEntity player) {
            super.onClosed(player);
            // 光标上的物品归还玩家
            ItemStack cursor = getCursorStack();
            if (!cursor.isEmpty()) {
                player.dropItem(cursor, false);
                setCursorStack(ItemStack.EMPTY);
            }
            // 翻页时已通过 saveCurrentPage 保存过，不重复
            if (!navigating) {
                saveCurrentPage();
            }
        }

        /** 保存本页 27 格到全局 108 格数组。*/
        private void saveCurrentPage() {
            ItemStack[] stacks = TrinketStore.readCopy(targetUuid);
            if (stacks.length < TrinketStore.MAX_SIZE) {
                ItemStack[] expanded = new ItemStack[TrinketStore.MAX_SIZE];
                for (int i = 0; i < TrinketStore.MAX_SIZE; i++) {
                    expanded[i] = i < stacks.length && stacks[i] != null ? stacks[i] : ItemStack.EMPTY;
                }
                stacks = expanded;
            }
            int pageStart = page * TrinketStore.PAGE_SIZE;
            for (int i = 0; i < TRINKET_SLOTS.length; i++) {
                ItemStack s = bagInventory.getStack(TRINKET_SLOTS[i]);
                // 过滤掉占位图标
                if (s.getItem() == Items.LIME_STAINED_GLASS_PANE
                    || s.getItem() == Items.BARRIER
                    || s.getItem() == Items.GRAY_STAINED_GLASS_PANE) {
                    stacks[pageStart + i] = ItemStack.EMPTY;
                } else {
                    stacks[pageStart + i] = s;
                }
            }
            TrinketStore.write(targetUuid, stacks);
        }

        private void handleUnlock(ServerPlayerEntity sp) {
            int current = TrinketStore.getCapacity(targetUuid);
            if (current >= TrinketStore.MAX_SIZE) {
                sp.sendMessage(Text.literal("§a[饰品] 已满级！全部 "
                    + TrinketStore.MAX_SIZE + " 格已解锁").formatted(Formatting.GREEN), true);
                return;
            }
            if (TrinketBagMenu.tryUnlock(sp, current)) {
                int newCap = current + 3;
                TrinketStore.setCapacity(targetUuid, newCap);
                sp.sendMessage(Text.literal("§a[饰品] 已解锁到 §f" + newCap + " §a格！")
                    .formatted(Formatting.GREEN), true);
                sp.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 0.5f, 1.2f);
                // 重开菜单刷新UI（保持在当前页）
                navigating = true;
                saveCurrentPage();
                sp.closeHandledScreen();
                sp.getServer().execute(() ->
                    TrinketBagMenu.openPage(sp, targetProfile, targetUuid, onClose, page));
            } else {
                int tier = current / 3;
                sp.sendMessage(Text.literal("§c[饰品] 材料不足：§f"
                    + TrinketBagMenu.unlockRequirement(tier)).formatted(Formatting.RED), true);
                sp.playSound(SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 0.5f, 1.0f);
            }
        }

        private static int trinketSlotIndex(int uiSlot) {
            for (int i = 0; i < TRINKET_SLOTS.length; i++) {
                if (TRINKET_SLOTS[i] == uiSlot) return i;
            }
            return -1;
        }
    }
}

