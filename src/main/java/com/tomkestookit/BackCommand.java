package com.tomkestookit;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/**
 * /back 快速返回上次死亡地点。全员可用。
 *
 * 三件事：
 *   1. 注册死亡事件 → 记录 死亡时的坐标+维度+朝向
 *   2. 玩家重生（re-join world）时发一条聊天可点击消息 → /back
 *   3. 注册 /back 命令 → 传送回死亡地点
 */
public final class BackCommand {

    private BackCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                CommandManager.literal("back")
                    .executes(BackCommand::execute)
            ));

        // 死亡时记录位置。ServerLivingEntityEvents.AFTER_DEATH 只在服务端触发，
        // 拿到的 living 已经被判定死亡但还没重生，getPos/getWorld 都是死亡瞬间的值。
        ServerLivingEntityEvents.AFTER_DEATH.register((living, damageSource) -> {
            if (!TomkesTookitConfig.enabled("cmd.back")) return;
            if (!(living instanceof ServerPlayerEntity player)) return;
            DeathLocationTracker.DeathLocation loc = new DeathLocationTracker.DeathLocation(
                player.getWorld().getRegistryKey().getValue().toString(),
                player.getX(), player.getY(), player.getZ(),
                player.getYaw(), player.getPitch()
            );
            DeathLocationTracker.record(player.getUuid(), loc);
        });

        // 重生后（AFTER_RESPAWN，alive=false 表示因死亡重生而非跨维度传送）
        // 发一条可点击的返回死亡地点提示。
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (!TomkesTookitConfig.enabled("cmd.back")) return;
            if (alive) return; // alive=true 是从末地返回主世界那种"活着重生"，不算死亡
            if (!DeathLocationTracker.has(newPlayer.getUuid())) return;
            sendDeathBackMessage(newPlayer);
        });
    }

    private static void sendDeathBackMessage(ServerPlayerEntity player) {
        DeathLocationTracker.DeathLocation loc = DeathLocationTracker.get(player.getUuid());
        if (loc == null) return;

        String dim = com.tomkestookit.landmark.LandmarkCommand.dimShort(loc.dimension);
        Text hover = Text.literal("§7返回死亡地点\n§f" + fmt(loc.x) + " " + fmt(loc.y) + " " + fmt(loc.z) + " §7@ §f" + dim);

        Text message = Text.empty()
            .append(Text.literal("[死亡] ").formatted(Formatting.DARK_RED, Formatting.BOLD))
            .append(Text.literal("点击返回死亡地点")
                .formatted(Formatting.RED)
                .styled(s -> s
                    .withUnderline(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/back"))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover))
                )
            )
            .append(Text.literal(" §8(" + fmt(loc.x) + " " + fmt(loc.y) + " " + fmt(loc.z) + " @ " + dim + ")"));

        player.sendMessage(message, false);
    }

    private static int execute(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        ServerPlayerEntity player;
        try {
            player = src.getPlayerOrThrow();
        } catch (Exception e) {
            return 0;
        }
        if (!TomkesTookitConfig.enabled("cmd.back")) {
            player.sendMessage(Text.literal("§c[/back] 该功能已被管理员禁用").formatted(Formatting.RED), false);
            return 0;
        }
        return teleportToDeath(player) ? 1 : 0;
    }

    /**
     * 传送玩家到死亡地点。菜单按钮也调这个。
     * 返回 true = 成功传送；false = 无记录或维度不存在。
     */
    public static boolean teleportToDeath(ServerPlayerEntity player) {
        DeathLocationTracker.DeathLocation loc = DeathLocationTracker.get(player.getUuid());
        if (loc == null) {
            player.sendMessage(Text.literal("没有死亡记录").formatted(Formatting.YELLOW), false);
            return false;
        }
        Identifier id = Identifier.tryParse(loc.dimension);
        if (id == null) {
            player.sendMessage(Text.literal("死亡地点维度标识非法：" + loc.dimension).formatted(Formatting.RED), false);
            return false;
        }
        RegistryKey<World> key = RegistryKey.of(RegistryKeys.WORLD, id);
        ServerWorld target = player.getServer().getWorld(key);
        if (target == null) {
            player.sendMessage(Text.literal("死亡地点所在维度不存在：" + loc.dimension).formatted(Formatting.RED), false);
            return false;
        }
        PlayerTeleport.teleport(player, target, loc.x, loc.y, loc.z, loc.yaw, loc.pitch);
        SpawnProtection.grant(player, "返回死亡地点");
        player.sendMessage(Text.literal("已返回死亡地点").formatted(Formatting.GREEN), true);
        return true;
    }

    private static String fmt(double v) {
        return String.valueOf((int) Math.floor(v));
    }

}
