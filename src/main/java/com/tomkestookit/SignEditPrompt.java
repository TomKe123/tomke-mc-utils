package com.tomkestookit;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.network.packet.s2c.play.SignEditorOpenS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 用一块临时告示牌向玩家收集一行文本输入。
 *
 * 流程：
 *   1) 找玩家头顶第一格空气（或强占玩家头顶 3 格）放告示牌
 *   2) 首行预填提示 / 当前值
 *   3) 立即发 SignEditorOpenS2CPacket 让客户端弹编辑界面
 *   4) 玩家点“完成”→ ServerPlayNetworkHandlerMixin.onUpdateSign 拦截该 pos
 *      → SignEditPrompt.submit → 触发回调
 *   5) 回调结束后恢复原方块（在回调 wrapper 里）
 *
 * onUpdateSign 在网络线程回调，回调里的世界操作要 server.execute 送回主线程。
 */
public final class SignEditPrompt {

    public record Pending(UUID playerId, ServerWorld world, BlockPos pos, Consumer<String[]> onSubmit) {}

    private static final Map<UUID, Pending> byPlayer = new HashMap<>();

    private SignEditPrompt() {}

    public static void open(ServerPlayerEntity player, String[] prefill, Consumer<String[]> onSubmit) {
        cancel(player.getUuid()); // 清掉旧的
        ServerWorld world = player.getServerWorld();
        BlockPos pos = pickWorkspacePos(world, player);
        BlockState prev = world.getBlockState(pos);

        Pending pending = new Pending(player.getUuid(), world, pos, lines -> {
            try {
                onSubmit.accept(lines);
            } finally {
                world.setBlockState(pos, prev);
            }
        });

        world.setBlockState(pos, Blocks.OAK_SIGN.getDefaultState());
        if (world.getBlockEntity(pos) instanceof SignBlockEntity sign) {
            for (int i = 0; i < 4; i++) {
                String line = i < prefill.length && prefill[i] != null ? prefill[i] : "";
                sign.setText(sign.getFrontText().withMessage(i, Text.literal(line)), true);
            }
            sign.markDirty();
            world.getChunkManager().markForUpdate(pos);
        }

        byPlayer.put(player.getUuid(), pending);
        player.networkHandler.sendPacket(new SignEditorOpenS2CPacket(pos, true));
    }

    /** mixin 从 onUpdateSign 里调过来。返回 true 表示消费了这次 packet。*/
    public static boolean submit(UUID playerId, BlockPos pos, String[] lines) {
        Pending p = byPlayer.get(playerId);
        if (p == null || !p.pos().equals(pos)) return false;
        byPlayer.remove(playerId);
        p.onSubmit().accept(lines);
        return true;
    }

    public static void cancel(UUID playerId) {
        Pending p = byPlayer.remove(playerId);
        if (p == null) return;
        if (p.world().getBlockState(p.pos()).isOf(Blocks.OAK_SIGN)) {
            p.world().setBlockState(p.pos(), Blocks.AIR.getDefaultState());
        }
    }

    /**
     * 客户端要求告示牌在已加载区块 + 附近才会弹编辑 UI。玩家头顶 3-6 格取第一个空气。
     * 找不到就强占玩家上方 3 格；提交/取消后恢复原方块。
     */
    private static BlockPos pickWorkspacePos(ServerWorld world, ServerPlayerEntity player) {
        BlockPos base = player.getBlockPos();
        for (int dy = 3; dy <= 6; dy++) {
            BlockPos p = base.up(dy);
            if (world.getBlockState(p).isAir()) return p;
        }
        return base.up(3);
    }
}
