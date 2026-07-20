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

        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClient) return TypedActionResult.pass(player.getStackInHand(hand));
            if (!(player instanceof ServerPlayerEntity serverPlayer)) {
                return TypedActionResult.pass(player.getStackInHand(hand));
            }
            ItemStack stack = player.getStackInHand(hand);
            if (!isMenuClock(stack)) return TypedActionResult.pass(stack);

            // 只主手触发，避免副手空点也开
            if (hand != Hand.MAIN_HAND) return TypedActionResult.pass(stack);

            MenuUi.open(serverPlayer);
            return TypedActionResult.success(stack, false);
        });
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
