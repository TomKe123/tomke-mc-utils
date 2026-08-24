package com.tomkestookit.trainingdummy;

import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Map;

/**
 * 训练假人 DPS 测试菜单。
 *
 * 提供三个时长选项：10秒 / 30秒 / 60秒
 */
public final class TrainingDummyMenu {

    private TrainingDummyMenu() {}

    /** 打开 DPS 测试菜单 */
    public static void open(PlayerEntity player, ArmorStandEntity dummy) {
        if (!(player instanceof ServerPlayerEntity sp)) return;

        SimpleInventory inv = new SimpleInventory(9);
        inv.setStack(2, createDurationButton(10));
        inv.setStack(4, createDurationButton(30));
        inv.setStack(6, createDurationButton(60));

        Map<Integer, java.util.function.Consumer<ServerPlayerEntity>> actions = Map.of(
            2, p -> {
                TrainingDummyHandler.startDpsTest(p, 10);
                p.closeHandledScreen();
            },
            4, p -> {
                TrainingDummyHandler.startDpsTest(p, 30);
                p.closeHandledScreen();
            },
            6, p -> {
                TrainingDummyHandler.startDpsTest(p, 60);
                p.closeHandledScreen();
            }
        );

        sp.openHandledScreen(new net.minecraft.screen.SimpleNamedScreenHandlerFactory(
            (syncId, playerInv, playerEntity) -> new TrainingDummyMenuHandler(syncId, playerInv, inv, actions),
            Text.literal("DPS 测试时长").formatted(Formatting.GOLD, Formatting.BOLD)
        ));
    }

    private static ItemStack createDurationButton(int seconds) {
        ItemStack stack = new ItemStack(Items.CLOCK);
        stack.setCustomName(Text.literal(seconds + " 秒").formatted(Formatting.YELLOW, Formatting.BOLD));

        // 1.20.1 使用 NBT 方式设置 lore
        NbtCompound nbt = stack.getOrCreateNbt();
        NbtCompound display = nbt.getCompound("display");
        NbtList lore = new NbtList();

        for (String line : java.util.List.of(
            "§7点击开始 " + seconds + " 秒 DPS 测试",
            "",
            "§8测试期间对假人造成的所有伤害",
            "§8将被累加并计算平均 DPS"
        )) {
            lore.add(NbtString.of(Text.Serializer.toJson(Text.literal(line).styled(s -> s.withItalic(false)))));
        }

        display.put("Lore", lore);
        nbt.put("display", display);

        return stack;
    }
}
