package com.starterkit.menu;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;

/**
 * /cd 命令 + 右键"菜单时钟"打开主菜单。
 *
 *   - NBT 标记：只有 nbt.StarterKitMenuClock == 1b 的时钟才触发菜单，
 *     防止玩家手上原有的普通时钟被劫持右键行为。
 *   - 命令 /cd：权限 0，全员可用。
 *   - 事件监听在服务端触发；返回 SUCCESS 阻止后续原版逻辑
 *     （比如告知客户端播放使用动作）。
 */
public final class MenuClock {

    /** 用于识别"菜单时钟"的 NBT 键。*/
    public static final String NBT_TAG = "StarterKitMenuClock";

    private MenuClock() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                CommandManager.literal("cd")
                    .requires(source -> true)
                    .executes(ctx -> {
                        ServerPlayerEntity player;
                        try {
                            player = ctx.getSource().getPlayerOrThrow();
                        } catch (Exception e) {
                            return 0;
                        }
                        MenuUi.open(player);
                        return 1;
                    })
            );
        });

        // UseItemCallback 会为主手和副手各触发一次；无论菜单时钟在哪只手，
        // 都要打开菜单一次，另一只手当次的物品使用也要一起吃掉。
        //
        // 分支矩阵（M=主手事件, O=副手事件, ✓=菜单时钟, ✗=非时钟）：
        //   触发手  另一手  行为
        //   M ✓    O 任意   打开菜单 + fail（同时短路后续副手事件）
        //   M ✗    O ✓     pass（让主手正常交互，菜单交给副手事件处理）
        //   M ✗    O ✗     pass
        //   O ✓    M 任意*  打开菜单 + fail
        //   O ✗    M ✓     fail（M 已开过菜单，副手事件必须吃掉方块使用）
        //   * "主手也是菜单时钟"时 M 事件已经开过菜单了，O 事件跑到这里再开一次
        //     不合适——用 lastOpenTick 去重，同 tick 已开则只 fail 不再开。
        // vanilla 派发右键的顺序是：主手事件 → 若 pass 才尝试副手。
        // 因此我们必须在**主手事件**里就检测"任一手是不是菜单时钟"：
        //   任一手是时钟 → 立即打开菜单 + fail 掉主手，vanilla 就不会派发副手
        //     事件，也就不会放副手方块。
        //   都不是时钟 → pass。
        // 副手事件正常跑不到，仅作为 defensive path 保留。
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClient) return TypedActionResult.pass(player.getStackInHand(hand));
            if (!(player instanceof ServerPlayerEntity serverPlayer)) {
                return TypedActionResult.pass(player.getStackInHand(hand));
            }

            boolean mainIsClock = isMenuClock(player.getMainHandStack());
            boolean offIsClock = isMenuClock(player.getOffHandStack());

            if (hand == Hand.MAIN_HAND) {
                if (mainIsClock || offIsClock) {
                    openOnce(serverPlayer);
                    return TypedActionResult.fail(player.getMainHandStack());
                }
                return TypedActionResult.pass(player.getMainHandStack());
            }

            // hand == OFF_HAND（防御性路径）
            if (offIsClock) {
                openOnce(serverPlayer);
                return TypedActionResult.fail(player.getOffHandStack());
            }
            return TypedActionResult.pass(player.getOffHandStack());
        });
    }

    /**
     * 同一玩家在同一 tick 内多次触发（主手 + 副手都点了菜单时钟，或
     * 主手时钟 + 副手时钟共存）只开一次菜单。
     */
    private static final java.util.WeakHashMap<ServerPlayerEntity, Long> LAST_OPEN_TICK =
        new java.util.WeakHashMap<>();

    private static void openOnce(ServerPlayerEntity player) {
        long tick = player.getServer().getTicks();
        Long prev = LAST_OPEN_TICK.get(player);
        if (prev != null && prev == tick) return;
        LAST_OPEN_TICK.put(player, tick);
        MenuUi.open(player);
    }

    public static boolean isMenuClock(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() != Items.CLOCK) return false;
        NbtCompound nbt = stack.getNbt();
        return nbt != null && nbt.getBoolean(NBT_TAG);
    }

    /** 生成一枚菜单时钟：带 NBT 标记、专属名字、lore 提示。*/
    public static ItemStack create() {
        ItemStack stack = new ItemStack(Items.CLOCK);
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putBoolean(NBT_TAG, true);

        stack.setCustomName(Text.literal("菜单时钟")
            .formatted(Formatting.AQUA)
            .styled(s -> s.withItalic(false)));

        NbtCompound display = nbt.getCompound("display");
        NbtList lore = new NbtList();
        lore.add(NbtString.of(Text.Serializer.toJson(
            Text.literal("右键打开主菜单").formatted(Formatting.GRAY)
                .styled(s -> s.withItalic(false)))));
        lore.add(NbtString.of(Text.Serializer.toJson(
            Text.literal("或使用 /cd 命令").formatted(Formatting.DARK_GRAY)
                .styled(s -> s.withItalic(false)))));
        display.put("Lore", lore);
        nbt.put("display", display);

        return stack;
    }
}
