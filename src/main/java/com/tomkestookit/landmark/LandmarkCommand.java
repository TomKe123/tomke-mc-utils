package com.tomkestookit.landmark;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * /landmark 命令族。全员可用：
 *   /landmark add <name>   在当前位置创建地标
 *   /landmark remove <name> 删除地标
 *   /landmark list         列出所有地标
 *   /landmark tp <name>    传送到地标
 */
public final class LandmarkCommand {

    private LandmarkCommand() {}

    private static final SuggestionProvider<ServerCommandSource> NAME_SUGGESTIONS =
        (ctx, builder) -> {
            for (Landmark lm : LandmarkStore.all()) {
                if (lm.name.toLowerCase().startsWith(builder.getRemaining().toLowerCase())) {
                    builder.suggest(lm.name);
                }
            }
            return builder.buildFuture();
        };

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                CommandManager.literal("landmark")
                    .requires(src -> true)
                    .then(CommandManager.literal("add")
                        .then(CommandManager.argument("name", StringArgumentType.word())
                            .executes(LandmarkCommand::add)))
                    .then(CommandManager.literal("remove")
                        .then(CommandManager.argument("name", StringArgumentType.word())
                            .suggests(NAME_SUGGESTIONS)
                            .executes(LandmarkCommand::remove)))
                    .then(CommandManager.literal("tp")
                        .then(CommandManager.argument("name", StringArgumentType.word())
                            .suggests(NAME_SUGGESTIONS)
                            .executes(LandmarkCommand::tp)))
                    .then(CommandManager.literal("list")
                        .executes(LandmarkCommand::list))
            );
        });
    }

    private static int add(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = tryPlayer(ctx);
        if (player == null) return 0;
        String name = StringArgumentType.getString(ctx, "name");
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

    private static int tp(CommandContext<ServerCommandSource> ctx) {
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
