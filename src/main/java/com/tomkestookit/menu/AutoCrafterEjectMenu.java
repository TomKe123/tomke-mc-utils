package com.tomkestookit.menu;

import com.tomkestookit.AutoCrafterHandler;
import com.tomkestookit.AutoCrafterTracker;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 自动合成器·产物弹出方向选择菜单（9x3）。
 *
 * 布局：
 *   row 0: . . . . [北]  . . . [返回]
 *   row 1: . . [西] . [中心] . [东] . .
 *   row 2: . [关闭弹出] . [下] [南] [上] . . .
 *
 * 每个方向按钮左键选中该方向；反馈到 action bar；然后重开自己刷新高亮。
 */
public final class AutoCrafterEjectMenu {

    private static final Text TITLE = Text.literal("§6§l§n弹出方向设置");

    private AutoCrafterEjectMenu() {}

    public static void open(ServerPlayerEntity viewer, ServerWorld world, BlockPos pos) {
        AutoCrafterTracker.Config config = AutoCrafterTracker.get(world, pos);
        if (config == null) {
            viewer.sendMessage(Text.literal("§c[合成器] 该位置已不是自动合成器。")
                .formatted(Formatting.RED), true);
            return;
        }

        Inventory inv = MenuScreenHandler.buildInventory(3);
        Map<Integer, Consumer<ServerPlayerEntity>> actions = new HashMap<>();

        String current = config.ejectDirection == null ? "none" : config.ejectDirection;

        // 顶部：中心按钮 + 返回
        inv.setStack(4, buildDirectionButton("北", "north", current, Items.RED_BANNER));
        actions.put(4, p -> apply(p, world, pos, "north"));
        inv.setStack(8, MenuButtons.button(Items.ARROW, "§e◀ 返回合成器",
            List.of("§7回到合成器主界面")));
        actions.put(8, p -> {
            AutoCrafterHandler.onUse(world, pos, p);
        });

        // 中间行
        inv.setStack(11, buildDirectionButton("西", "west", current, Items.YELLOW_BANNER));
        actions.put(11, p -> apply(p, world, pos, "west"));
        inv.setStack(13, buildCenterCard(current));
        inv.setStack(15, buildDirectionButton("东", "east", current, Items.LIGHT_BLUE_BANNER));
        actions.put(15, p -> apply(p, world, pos, "east"));

        // 底部行
        inv.setStack(19, buildNoneButton(current));
        actions.put(19, p -> apply(p, world, pos, "none"));
        inv.setStack(21, buildDirectionButton("下", "down", current, Items.MAGENTA_BANNER));
        actions.put(21, p -> apply(p, world, pos, "down"));
        inv.setStack(22, buildDirectionButton("南", "south", current, Items.GREEN_BANNER));
        actions.put(22, p -> apply(p, world, pos, "south"));
        inv.setStack(23, buildDirectionButton("上", "up", current, Items.LIME_BANNER));
        actions.put(23, p -> apply(p, world, pos, "up"));

        MenuButtons.fillBackground(inv);

        viewer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, playerInv, ignored) -> new MenuScreenHandler(syncId, playerInv, inv, actions),
            TITLE
        ));
    }

    private static void apply(ServerPlayerEntity player, ServerWorld world, BlockPos pos, String direction) {
        AutoCrafterTracker.Config config = AutoCrafterTracker.get(world, pos);
        if (config == null) {
            player.sendMessage(Text.literal("§c[合成器] 该位置已不是自动合成器。")
                .formatted(Formatting.RED), true);
            player.closeHandledScreen();
            return;
        }
        config.ejectDirection = direction;
        AutoCrafterTracker.save();
        player.sendMessage(Text.literal("§a[合成器] 弹出方向：§f" + labelOf(direction))
            .formatted(Formatting.GREEN), true);
        open(player, world, pos);
    }

    private static ItemStack buildDirectionButton(String label, String key, String current, net.minecraft.item.Item icon) {
        boolean selected = key.equals(current);
        List<String> lore = List.of(
            selected ? "§a● 当前方向" : "§7方向 §f" + labelOf(key),
            "",
            selected ? "§8已选中" : "§e▶ 点击切换到此方向"
        );
        ItemStack stack = MenuButtons.button(icon,
            (selected ? "§a§l" : "§f§l") + label + " (" + key + ")", lore);
        if (selected) MenuButtons.withGlow(stack);
        return stack;
    }

    private static ItemStack buildNoneButton(String current) {
        boolean selected = "none".equals(current) || current == null;
        List<String> lore = List.of(
            selected ? "§a● 当前设置" : "§7不弹出，产物走内部输出",
            "§8顶部/侧面漏斗 输入，底部漏斗 抽取",
            "",
            selected ? "§8已选中" : "§e▶ 点击切换"
        );
        ItemStack stack = MenuButtons.button(Items.BARRIER,
            (selected ? "§a§l" : "§7§l") + "不弹出 (none)", lore);
        if (selected) MenuButtons.withGlow(stack);
        return stack;
    }

    private static ItemStack buildCenterCard(String current) {
        return MenuButtons.button(Items.CRAFTING_TABLE, "§6§l弹出方向",
            List.of(
                "§7合成完成后产物投放方式",
                "§7当前 §f" + labelOf(current),
                "",
                "§8· none：走内部输出 slot",
                "§8· 其它：产物直接从对应方向以掉落物弹出",
                "§8· 输出满时不停机（弹出模式）"
            ));
    }

    private static String labelOf(String key) {
        if (key == null) return "不弹出";
        return switch (key) {
            case "up" -> "上";
            case "down" -> "下";
            case "north" -> "北";
            case "south" -> "南";
            case "east" -> "东";
            case "west" -> "西";
            default -> "不弹出";
        };
    }
}
