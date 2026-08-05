package com.tomkestookit;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.tomkestookit.boss.BossSummonHandler;
import com.tomkestookit.boss.BossTokenRegistry;
import com.tomkestookit.elite.EliteMobHandler;
import com.tomkestookit.elite.ThreatRating;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.CompletableFuture;

/**
 * /tomkestookit elite ... 子命令组。
 *
 *   info [player]                 → 查看威胁评分/tier
 *   spawn <entity> [tier]         → OP，视线前生成精英怪
 *   summon <bossId> [player]      → OP，直接召唤 Boss（无视冷却）
 *   set <player> <rating>         → OP，调 rating
 *   clear <player>                → OP，清零 rating
 *   give-token <bossId> [player]  → OP，发一个 boss 召唤令牌
 */
public final class EliteCommand {

    private EliteCommand() {}

    private static final SuggestionProvider<ServerCommandSource> BOSS_ID_SUGGESTIONS =
        (ctx, builder) -> {
            for (BossTokenRegistry.BossDef def : BossTokenRegistry.all()) {
                builder.suggest(def.bossId());
            }
            return builder.buildFuture();
        };

    public static LiteralArgumentBuilder<ServerCommandSource> buildBranch() {
        return CommandManager.literal("elite")
            .then(CommandManager.literal("info")
                .executes(ctx -> infoSelf(ctx))
                .then(CommandManager.argument("player", EntityArgumentType.player())
                    .requires(src -> src.hasPermissionLevel(2))
                    .executes(ctx -> infoOther(ctx))))
            .then(CommandManager.literal("spawn")
                .requires(src -> src.hasPermissionLevel(2))
                .then(CommandManager.argument("entity", StringArgumentType.string())
                    .suggests((ctx, b) -> suggestEntityTypes(b))
                    .executes(ctx -> spawnElite(ctx, 3))
                    .then(CommandManager.argument("tier", IntegerArgumentType.integer(1, 5))
                        .executes(ctx -> spawnElite(ctx, IntegerArgumentType.getInteger(ctx, "tier"))))))
            .then(CommandManager.literal("summon")
                .requires(src -> src.hasPermissionLevel(2))
                .then(CommandManager.argument("bossId", StringArgumentType.word())
                    .suggests(BOSS_ID_SUGGESTIONS)
                    .executes(ctx -> summonBoss(ctx, null))
                    .then(CommandManager.argument("player", EntityArgumentType.player())
                        .executes(ctx -> summonBoss(ctx,
                            EntityArgumentType.getPlayer(ctx, "player"))))))
            .then(CommandManager.literal("set")
                .requires(src -> src.hasPermissionLevel(2))
                .then(CommandManager.argument("player", EntityArgumentType.player())
                    .then(CommandManager.argument("rating", DoubleArgumentType.doubleArg(0.0, 1.0))
                        .executes(ctx -> setRating(ctx)))))
            .then(CommandManager.literal("clear")
                .requires(src -> src.hasPermissionLevel(2))
                .then(CommandManager.argument("player", EntityArgumentType.player())
                    .executes(ctx -> clearRating(ctx))))
            .then(CommandManager.literal("give-token")
                .requires(src -> src.hasPermissionLevel(2))
                .then(CommandManager.argument("bossId", StringArgumentType.word())
                    .suggests(BOSS_ID_SUGGESTIONS)
                    .executes(ctx -> giveToken(ctx, null))
                    .then(CommandManager.argument("player", EntityArgumentType.player())
                        .executes(ctx -> giveToken(ctx,
                            EntityArgumentType.getPlayer(ctx, "player"))))));
    }

    // ---- info ----

    private static int infoSelf(CommandContext<ServerCommandSource> ctx) {
        try {
            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
            sendInfo(ctx.getSource(), p);
            return 1;
        } catch (Exception e) { return 0; }
    }

    private static int infoOther(CommandContext<ServerCommandSource> ctx) {
        try {
            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
            sendInfo(ctx.getSource(), target);
            return 1;
        } catch (Exception e) { return 0; }
    }

    private static void sendInfo(ServerCommandSource src, ServerPlayerEntity target) {
        double rating = ThreatRating.getRating(target.getUuid());
        int tier = ThreatRating.getTier(target.getUuid());
        Text msg = Text.literal(String.format("§e[精英] §f%s §7当前威胁 §f%.2f §7(T%d)",
            target.getGameProfile().getName(), rating, tier))
            .styled(s -> s.withItalic(false));
        src.sendFeedback(() -> msg, false);
    }

    // ---- spawn ----

    private static CompletableFuture<Suggestions> suggestEntityTypes(SuggestionsBuilder b) {
        String rem = b.getRemaining().toLowerCase();
        for (var id : Registries.ENTITY_TYPE.getIds()) {
            String s = id.toString();
            if (s.toLowerCase().contains(rem) || id.getPath().startsWith(rem)) {
                b.suggest(s);
            }
        }
        return b.buildFuture();
    }

    private static int spawnElite(CommandContext<ServerCommandSource> ctx, int tier) {
        try {
            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
            ServerWorld world = p.getServerWorld();
            String idStr = StringArgumentType.getString(ctx, "entity");
            Identifier id = idStr.contains(":") ? new Identifier(idStr) : new Identifier("minecraft", idStr);
            EntityType<?> type = Registries.ENTITY_TYPE.getOrEmpty(id).orElse(null);
            if (type == null) {
                ctx.getSource().sendError(Text.literal("§c未知实体类型：" + idStr));
                return 0;
            }

            // 玩家视线前方 5 格
            Vec3d eye = p.getEyePos();
            Vec3d dir = p.getRotationVec(1.0f).normalize();
            BlockPos pos = BlockPos.ofFloored(eye.add(dir.multiply(5)));

            Entity spawned = type.spawn(world, null, null, pos, SpawnReason.COMMAND, false, false);
            if (!(spawned instanceof MobEntity mob)) {
                ctx.getSource().sendError(Text.literal("§c该实体类型不是可精英化的 Mob"));
                if (spawned != null) spawned.discard();
                return 0;
            }
            EliteMobHandler.applyElite(mob, tier);
            ctx.getSource().sendFeedback(() ->
                Text.literal("§a[精英] 已生成 T" + tier + " 精英怪").styled(s -> s.withItalic(false)),
                true);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("§c生成失败：" + e.getMessage()));
            return 0;
        }
    }

    // ---- summon boss ----

    private static int summonBoss(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity target) {
        try {
        String bossId = StringArgumentType.getString(ctx, "bossId");
        BossTokenRegistry.BossDef def = BossTokenRegistry.byId(bossId);
        if (def == null) {
            ctx.getSource().sendError(Text.literal("§c未知的 bossId: " + bossId));
            return 0;
        }
        ServerPlayerEntity summoner = target != null ? target : ctx.getSource().getPlayerOrThrow();
        ServerWorld world = summoner.getServerWorld();
        Vec3d eye = summoner.getEyePos();
        Vec3d dir = summoner.getRotationVec(1.0f).normalize();
        BlockPos pos = BlockPos.ofFloored(eye.add(dir.multiply(5)));

        BossSummonHandler.summonDirect(bossId, world, pos, summoner);
        ctx.getSource().sendFeedback(() ->
            Text.literal("§a[Boss] 已直接召唤 " + def.displayName()).styled(s -> s.withItalic(false)),
            true);
        return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("§c召唤失败：" + e.getMessage()));
            return 0;
        }
    }

    // ---- set / clear rating ----

    private static int setRating(CommandContext<ServerCommandSource> ctx) {
        try {
            ServerPlayerEntity p = EntityArgumentType.getPlayer(ctx, "player");
            double r = DoubleArgumentType.getDouble(ctx, "rating");
            ThreatRating.setRating(p.getUuid(), r);
            ctx.getSource().sendFeedback(() ->
                Text.literal(String.format("§a[精英] 已设 %s 的 rating 为 %.2f",
                    p.getGameProfile().getName(), r)).styled(s -> s.withItalic(false)), true);
            return 1;
        } catch (Exception e) { return 0; }
    }

    private static int clearRating(CommandContext<ServerCommandSource> ctx) {
        try {
            ServerPlayerEntity p = EntityArgumentType.getPlayer(ctx, "player");
            ThreatRating.setRating(p.getUuid(), 0.0);
            ctx.getSource().sendFeedback(() ->
                Text.literal("§a[精英] 已清零 " + p.getGameProfile().getName() + " 的 rating")
                    .styled(s -> s.withItalic(false)), true);
            return 1;
        } catch (Exception e) { return 0; }
    }

    // ---- give token ----

    private static int giveToken(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity target) {
        try {
            String bossId = StringArgumentType.getString(ctx, "bossId");
            BossTokenRegistry.BossDef def = BossTokenRegistry.byId(bossId);
            if (def == null) {
                ctx.getSource().sendError(Text.literal("§c未知的 bossId: " + bossId));
                return 0;
            }
            ServerPlayerEntity p = target != null ? target : ctx.getSource().getPlayerOrThrow();
            var stack = BossTokenRegistry.buildTokenStack(def);
            if (!p.getInventory().insertStack(stack)) {
                p.dropItem(stack, false);
            }
            ctx.getSource().sendFeedback(() ->
                Text.literal("§a[Boss] 已发 1 个 " + def.displayName() + " 令牌给 "
                    + p.getGameProfile().getName()).styled(s -> s.withItalic(false)), true);
            return 1;
        } catch (Exception e) { return 0; }
    }
}
