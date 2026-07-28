package com.tomkestookit;

import com.tomkestookit.recipe.SugarTnt;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.TntEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;

/**
 * "大伊万"：一个爆炸威力 4 倍的强化 TNT。
 *
 * 触发路径：
 *   - 放地上：TntBlock 走 vanilla，但 TntEntity 的 power 被 SugarTntPowerMixin 提升
 *   - Shift + 右键：UseItemCallback 消耗 1 个 + spawn 一个已点燃的 TntEntity 前抛
 *
 * TntEntity 用 NBT 标记 {@link SugarTnt#NBT_KEY}，落地/引爆时通过 mixin 读取，
 * 决定用 4.0×4=16 的 explosion power。
 */
public final class SugarTntHandler {

    private SugarTntHandler() {}

    public static void register() {
        // shift + 右键任意位置（空气）→ 抛出大伊万
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClient) return TypedActionResult.pass(player.getStackInHand(hand));
            if (!TomkesTookitConfig.enabled("item.sugar_tnt_boom")) return TypedActionResult.pass(player.getStackInHand(hand));
            if (hand != Hand.MAIN_HAND) return TypedActionResult.pass(player.getStackInHand(hand));
            if (!player.isSneaking()) return TypedActionResult.pass(player.getStackInHand(hand));
            ItemStack held = player.getStackInHand(hand);
            if (!SugarTnt.isSugarTnt(held)) return TypedActionResult.pass(held);
            if (!(player instanceof ServerPlayerEntity sp)) return TypedActionResult.pass(held);
            throwSugarTnt(sp, held, (ServerWorld) world);
            return TypedActionResult.success(held, false);
        });
        // shift + 右键方块面：也走投掷；非 shift 右键方块面则放置为已点燃 TNT
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient) return ActionResult.PASS;
            if (!TomkesTookitConfig.enabled("item.sugar_tnt_boom")) return ActionResult.PASS;
            if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
            ItemStack held = player.getStackInHand(hand);
            if (!SugarTnt.isSugarTnt(held)) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            if (player.isSneaking()) {
                throwSugarTnt(sp, held, (ServerWorld) world);
                return ActionResult.SUCCESS;
            }
            // 非 shift：在瞄到方块的对面 spawn 已点燃 TntEntity，替代 vanilla 放 TntBlock
            BlockPos placePos = hit.getBlockPos().offset(hit.getSide());
            placeSugarTntEntity(sp, held, (ServerWorld) world,
                placePos.getX() + 0.5, placePos.getY(), placePos.getZ() + 0.5);
            return ActionResult.SUCCESS;
        });
    }

    private static void placeSugarTntEntity(ServerPlayerEntity player, ItemStack held,
                                            ServerWorld world, double x, double y, double z) {
        TntEntity tnt = new TntEntity(world, x, y, z, player);
        markAsSugarTnt(tnt);
        world.spawnEntity(tnt);
        world.playSound(null, x, y, z,
            net.minecraft.sound.SoundEvents.ENTITY_TNT_PRIMED,
            net.minecraft.sound.SoundCategory.BLOCKS, 1.0f, 1.0f);
        if (!player.getAbilities().creativeMode) held.decrement(1);
    }

    /** Shift + 右键手持大伊万 → 消耗 1 个 + 抛出一个已点燃的 TNT 实体。*/
    private static void throwSugarTnt(ServerPlayerEntity player, ItemStack held, ServerWorld world) {
        double x = player.getX();
        double y = player.getEyeY() - 0.1;
        double z = player.getZ();

        TntEntity tnt = new TntEntity(world,
            x, y, z, player);
        // 标记为大伊万
        markAsSugarTnt(tnt);
        // 投掷速度 = 玩家视线方向，速度 1.5
        float yaw = player.getYaw();
        float pitch = player.getPitch();
        double vx = -Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)) * 1.5;
        double vy = -Math.sin(Math.toRadians(pitch)) * 1.5;
        double vz = Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)) * 1.5;
        tnt.setVelocity(vx, vy, vz);
        world.spawnEntity(tnt);
        world.playSound(null, x, y, z,
            net.minecraft.sound.SoundEvents.ENTITY_TNT_PRIMED,
            net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.0f);

        if (!player.getAbilities().creativeMode) held.decrement(1);
    }

    /** 给 TntEntity 打上大伊万标记；SugarTntPowerMixin 读它决定 power。*/
    public static void markAsSugarTnt(TntEntity tnt) {
        // 内存态标记：TNT 4 秒内引爆，不会跨 chunk unload / server restart，
        // UUID 集合天然清理
        SUGAR_TNT_UUIDS.add(tnt.getUuid());
    }

    /** 判定实体是否大伊万。用弱 UUID 集合，实体被卸载后自然消失。*/
    private static final java.util.Set<java.util.UUID> SUGAR_TNT_UUIDS =
        java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    public static boolean isSugarTntEntity(TntEntity tnt) {
        return tnt != null && SUGAR_TNT_UUIDS.contains(tnt.getUuid());
    }

    public static void unregisterEntity(TntEntity tnt) {
        if (tnt != null) SUGAR_TNT_UUIDS.remove(tnt.getUuid());
    }

    /** vanilla TntEntity 引爆时调用；返回增强后的 power（普通 TNT 是 4.0f）。*/
    public static float amplifiedPower(TntEntity tnt) {
        return 4.0f * SugarTnt.POWER_MULTIPLIER;
    }

    /** 用于 mixin：探测 TntEntity 放置时是否来自大伊万 stack。*/
    public static boolean stackIsSugarTnt(ItemStack stack) {
        return SugarTnt.isSugarTnt(stack);
    }
}
