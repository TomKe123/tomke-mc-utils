package com.starterkit;

import com.mojang.brigadier.context.CommandContext;
import com.starterkit.menu.MenuClock;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.UUID;

public class KitCommand {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                CommandManager.literal("starterkit")
                    .then(CommandManager.literal("give")
                        .executes(KitCommand::giveKit)
                    )
            );
        });
    }

    private static int giveKit(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity player;
        try {
            player = source.getPlayerOrThrow();
        } catch (Exception e) {
            return 0;
        }

        if (KitTracker.hasClaimed(player.getUuid())) {
            player.sendMessage(Text.literal("你已经领取过 Kit！").formatted(Formatting.RED), false);
            return 0;
        }

        giveKitItems(player);
        KitTracker.markClaimed(player.getUuid());
        player.sendMessage(Text.literal("已领取 Kit！").formatted(Formatting.GREEN), false);
        return 1;
    }

    /**
     * 只发放物品，不做已领取校验、不更新 tracker、不发消息。
     * 供菜单等其他入口在完成自己的校验后调用。
     */
    public static void giveKitItems(ServerPlayerEntity player) {
        // 反重力皮靴
        ItemStack boots = new ItemStack(Items.LEATHER_BOOTS);
        boots.setCustomName(Text.literal("反重力皮靴").formatted(Formatting.RED));

        NbtCompound nbt = boots.getOrCreateNbt();
        nbt.putBoolean("Unbreakable", true);
        // 反重力标记：由 AntigravBootsHandler 识别，赋予跳跃增益和摔落减伤
        nbt.putBoolean(AntigravBootsHandler.NBT_TAG, true);

        // 染红色
        NbtCompound display = nbt.contains("display") ? nbt.getCompound("display") : new NbtCompound();
        display.putInt("color", 0xFF0000);
        nbt.put("display", display);

        // 速度属性（x3 移动速度）
        NbtList attrList = new NbtList();
        NbtCompound speedAttr = new NbtCompound();
        speedAttr.putString("AttributeName", "generic.movement_speed");
        speedAttr.putString("Name", "starterkit:speed");
        speedAttr.putDouble("Amount", 2.0);
        speedAttr.putInt("Operation", 2); // MULTIPLY_TOTAL
        speedAttr.putString("Slot", "feet");
        speedAttr.put("UUID", NbtHelper.fromUuid(UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890")));
        attrList.add(speedAttr);
        nbt.put("AttributeModifiers", attrList);

        List<ItemStack> items = List.of(
            boots,
            new ItemStack(Items.LEATHER_HELMET),
            new ItemStack(Items.LEATHER_CHESTPLATE),
            new ItemStack(Items.LEATHER_LEGGINGS),
            new ItemStack(Items.STONE_PICKAXE),
            new ItemStack(Items.STONE_AXE),
            new ItemStack(Items.BREAD, 16),
            MenuClock.create()
        );

        for (ItemStack item : items) {
            player.getInventory().insertStack(item);
        }
    }
}
