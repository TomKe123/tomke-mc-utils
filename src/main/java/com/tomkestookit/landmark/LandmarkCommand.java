package com.tomkestookit.landmark;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.ItemStackArgumentType;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * /landmark 命令族。全员可用：
 *   /landmark add "&lt;name&gt;"                在当前位置创建地标（名字用引号包裹以支持中文/空格）
 *   /landmark remove "&lt;name&gt;"             删除地标
 *   /landmark rename "&lt;old&gt;" "&lt;new&gt;"   重命名
 *   /landmark icon "&lt;name&gt;" &lt;item&gt;      修改图标
 *   /landmark tp "&lt;name&gt;"                 传送到地标
 *   /landmark list                        列出所有地标
 *
 * 名字参数使用 StringArgumentType.string()：支持中文、空格（用引号），支持自动补全。
 */
public final class LandmarkCommand {

    private LandmarkCommand() {}

    private static final SuggestionProvider<ServerCommandSource> NAME_SUGGESTIONS =
        (ctx, builder) -> {
            String remaining = builder.getRemaining().toLowerCase();
            for (Landmark lm : LandmarkStore.all()) {
                if (lm.name.toLowerCase().startsWith(remaining)) {
                    // 用 "\"" 包起来，让玩家可以补出带引号的名字
                    builder.suggest("\"" + lm.name + "\"");
                }
            }
            return builder.buildFuture();
        };

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                CommandManager.literal("landmark")
                    .requires(src -> true)
                    .then(CommandManager.literal("add")
                        .then(CommandManager.argument("name", StringArgumentType.string())
                            .executes(LandmarkCommand::add)))
                    .then(CommandManager.literal("remove")
                        .then(CommandManager.argument("name", StringArgumentType.string())
                            .suggests(NAME_SUGGESTIONS)
                            .executes(LandmarkCommand::remove)))
                    .then(CommandManager.literal("rename")
                        .then(CommandManager.argument("old", StringArgumentType.string())
                            .suggests(NAME_SUGGESTIONS)
                            .then(CommandManager.argument("new", StringArgumentType.string())
                                .executes(LandmarkCommand::rename))))
                    .then(CommandManager.literal("icon")
                        .then(CommandManager.argument("name", StringArgumentType.string())
                            .suggests(NAME_SUGGESTIONS)
                            .then(CommandManager.argument("item", ItemStackArgumentType.itemStack(registryAccess))
                                .executes(LandmarkCommand::setIcon))))
                    .then(CommandManager.literal("tp")
                        .then(CommandManager.argument("name", StringArgumentType.string())
                            .suggests(NAME_SUGGESTIONS)
                            .executes(LandmarkCommand::tp)))
                    .then(CommandManager.literal("list")
                        .executes(LandmarkCommand::list))
            ));
    }

    /** 关闭时统一提示；返回 true 表示已被禁用（调用方应立即 return 0）。*/
    private static boolean disabled(ServerCommandSource src) {
        if (com.tomkestookit.TomkesTookitConfig.enabled("cmd.landmark")) return false;
        src.sendFeedback(() -> Text.literal("§c[/landmark] 该命令族已被管理员禁用").formatted(Formatting.RED), false);
        return true;
    }

    private static int add(CommandContext<ServerCommandSource> ctx) {
        if (disabled(ctx.getSource())) return 0;
        ServerPlayerEntity player = tryPlayer(ctx);
        if (player == null) return 0;
        String name = StringArgumentType.getString(ctx, "name").strip();
        if (!LandmarkStore.isValidName(name)) {
            player.sendMessage(Text.literal("地标名字非法（长度 1.." + LandmarkStore.MAX_NAME_LEN + "）。")
                .formatted(Formatting.RED), false);
            return 0;
        }
        if (LandmarkStore.exists(name)) {
            player.sendMessage(Text.literal("地标 \"" + name + "\" 已存在。").formatted(Formatting.RED), false);
            return 0;
        }
        Landmark lm = new Landmark(
            name,
            player.getWorld().getRegistryKey().getValue().toString(),
            player.getX(), player.getY(), player.getZ(),
            player.getYaw(), player.getPitch(),
            player.getGameProfile().getName()
        );
        LandmarkStore.add(lm);
        player.sendMessage(Text.literal("已添加地标 \"" + name + "\"。").formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int remove(CommandContext<ServerCommandSource> ctx) {
        if (disabled(ctx.getSource())) return 0;
        ServerPlayerEntity player = tryPlayer(ctx);
        String name = StringArgumentType.getString(ctx, "name");
        if (!LandmarkStore.remove(name)) {
            if (player != null) player.sendMessage(
                Text.literal("地标 \"" + name + "\" 不存在。").formatted(Formatting.RED), false);
            return 0;
        }
        if (player != null) player.sendMessage(
            Text.literal("已删除地标 \"" + name + "\"。").formatted(Formatting.YELLOW), false);
        return 1;
    }

    private static int rename(CommandContext<ServerCommandSource> ctx) {
        if (disabled(ctx.getSource())) return 0;
        ServerPlayerEntity player = tryPlayer(ctx);
        if (player == null) return 0;
        String oldName = StringArgumentType.getString(ctx, "old");
        String newName = StringArgumentType.getString(ctx, "new").strip();
        if (!LandmarkStore.isValidName(newName)) {
            player.sendMessage(Text.literal("新名字非法（长度 1.." + LandmarkStore.MAX_NAME_LEN + "）。")
                .formatted(Formatting.RED), false);
            return 0;
        }
        if (LandmarkStore.exists(newName)) {
            player.sendMessage(Text.literal("地标 \"" + newName + "\" 已存在。").formatted(Formatting.RED), false);
            return 0;
        }
        if (!LandmarkStore.rename(oldName, newName)) {
            player.sendMessage(Text.literal("地标 \"" + oldName + "\" 不存在。").formatted(Formatting.RED), false);
            return 0;
        }
        player.sendMessage(Text.literal("已重命名 \"" + oldName + "\" → \"" + newName + "\"。")
            .formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int setIcon(CommandContext<ServerCommandSource> ctx) {
        if (disabled(ctx.getSource())) return 0;
        ServerPlayerEntity player = tryPlayer(ctx);
        if (player == null) return 0;
        String name = StringArgumentType.getString(ctx, "name");
        Item item;
        try {
            item = ItemStackArgumentType.getItemStackArgument(ctx, "item").getItem();
        } catch (Exception e) {
            player.sendMessage(Text.literal("物品参数非法。").formatted(Formatting.RED), false);
            return 0;
        }
        String iconId = Registries.ITEM.getId(item).toString();
        if (!LandmarkStore.setIcon(name, iconId)) {
            player.sendMessage(Text.literal("地标 \"" + name + "\" 不存在。").formatted(Formatting.RED), false);
            return 0;
        }
        player.sendMessage(Text.literal("已将 \"" + name + "\" 图标设为 " + iconId + "。")
            .formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int tp(CommandContext<ServerCommandSource> ctx) {
        if (disabled(ctx.getSource())) return 0;
        ServerPlayerEntity player = tryPlayer(ctx);
        if (player == null) return 0;
        String name = StringArgumentType.getString(ctx, "name");
        Landmark lm = LandmarkStore.get(name);
        if (lm == null) {
            player.sendMessage(Text.literal("地标 \"" + name + "\" 不存在。").formatted(Formatting.RED), false);
            return 0;
        }
        return LandmarkTeleport.execute(player, lm) ? 1 : 0;
    }

    private static int list(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        if (disabled(src)) return 0;
        var all = LandmarkStore.all();
        if (all.isEmpty()) {
            src.sendFeedback(() -> Text.literal("暂无地标。").formatted(Formatting.GRAY), false);
            return 0;
        }
        src.sendFeedback(() -> Text.literal("共 " + all.size() + " 个地标：").formatted(Formatting.GOLD), false);
        for (Landmark lm : all) {
            src.sendFeedback(() -> Text.literal(String.format("  §e%s §7[%s @ %.0f, %.0f, %.0f] §8by %s",
                lm.name, dimShort(lm.dimension), lm.x, lm.y, lm.z, lm.creator)), false);
        }
        return all.size();
    }

    public static String dimShort(String key) {
        if ("minecraft:overworld".equals(key)) return "主世界";
        if ("minecraft:the_nether".equals(key)) return "下界";
        if ("minecraft:the_end".equals(key)) return "末地";
        return key;
    }

    private static ServerPlayerEntity tryPlayer(CommandContext<ServerCommandSource> ctx) {
        try {
            return ctx.getSource().getPlayerOrThrow();
        } catch (Exception e) {
            return null;
        }
    }
}
