package com.tomkestookit;

import com.tomkestookit.recipe.ThrownExplosive;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 手雷 / 蹦蹦炸弹：右键手持抛出，飞行后爆炸。
 *
 *   - 爆炸只造成伤害（{@code ExplosionSourceType.NONE} → 不破坏方块）
 *   - 蹦蹦炸弹落地会弹跳（在 {@code ThrownExplosiveBounceMixin} 里实现）
 *
 * 使用 TntEntity 作为载体，靠 UUID 集合记忆哪些是我们的实体 + 类型。
 */
public final class ThrownExplosiveHandler {

    private ThrownExplosiveHandler() {}

    // entity uuid → 炸弹类型；实体消失时手动清理（explode / discard）
    private static final Map<UUID, ThrownExplosive.Type> TRACKED =
        Collections.synchronizedMap(new HashMap<>());

    public static void register() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClient) return TypedActionResult.pass(player.getStackInHand(hand));
            if (!TomkesTookitConfig.enabled("item.thrown_explosive"))
                return TypedActionResult.pass(player.getStackInHand(hand));
            if (hand != Hand.MAIN_HAND) return TypedActionResult.pass(player.getStackInHand(hand));

            ItemStack held = player.getStackInHand(hand);
            ThrownExplosive.Type type = ThrownExplosive.getType(held);
            if (type == null) return TypedActionResult.pass(held);
            if (!(player instanceof ServerPlayerEntity sp)) return TypedActionResult.pass(held);

            throwExplosive(sp, held, (ServerWorld) world, type);
            return TypedActionResult.success(held, false);
        });
    }

    private static void throwExplosive(ServerPlayerEntity player, ItemStack held,
                                       ServerWorld world, ThrownExplosive.Type type) {
        double x = player.getX();
        double y = player.getEyeY() - 0.1;
        double z = player.getZ();

        TntEntity tnt = new TntEntity(world, x, y, z, player);
        tnt.setFuse(type.fuseTicks);

        float yaw = player.getYaw();
        float pitch = player.getPitch();
        double speed = type.throwSpeed;
        double vx = -Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)) * speed;
        double vy = -Math.sin(Math.toRadians(pitch)) * speed + 0.15; // 小抛物线
        double vz = Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)) * speed;
        tnt.setVelocity(vx, vy, vz);

        TRACKED.put(tnt.getUuid(), type);
        world.spawnEntity(tnt);
        world.playSound(null, x, y, z, SoundEvents.ENTITY_TNT_PRIMED,
            SoundCategory.PLAYERS, 1.0f, type == ThrownExplosive.Type.BOUNCY_BOMB ? 1.4f : 1.0f);

        if (!player.getAbilities().creativeMode) held.decrement(1);
    }

    /** 判定 TntEntity 是否我们的投掷炸弹；是则返回类型，否则 null。*/
    public static ThrownExplosive.Type getEntityType(TntEntity tnt) {
        if (tnt == null) return null;
        return TRACKED.get(tnt.getUuid());
    }

    /** 是否为蹦蹦炸弹（供 bounce mixin 快速判定）。*/
    public static boolean isBouncy(TntEntity tnt) {
        return getEntityType(tnt) == ThrownExplosive.Type.BOUNCY_BOMB;
    }

    /** 引爆后清理追踪。*/
    public static void unregister(TntEntity tnt) {
        if (tnt != null) TRACKED.remove(tnt.getUuid());
    }

    /** 消耗合成输入的水桶：普通桶留一个空桶给玩家（走 vanilla remainder 逻辑），
     *  附魔/无限桶不消耗。返回 true 表示这个桶应当被"完全保留"（不 decrement）。
     *  给 FakeCraftTakeMixin 调用。 */
    public static boolean shouldPreserveInput(ItemStack stack) {
        return ThrownExplosive.isPreservedBucket(stack);
    }
}
