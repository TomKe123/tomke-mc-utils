package com.tomkestookit.reforge;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.Comparator;
import java.util.List;

/**
 * 掠食本能（target_rush）逻辑：
 *
 * 触发时机：玩家挥剑动作（左键 swing），不需要命中实体。
 * 只对当前把玩家作为仇恨目标的敌对生物生效（{@code mob.getTarget() == player}）。
 * 在 5 格内寻找最近的仇恨怪，检查视线 + 突进路径：
 *   - 玩家眼睛 → 怪物中心 视线可达
 *   - 玩家当前位置 → 突进落点 直线路径无方块阻挡
 * 满足条件则传送到目标面前，造成一次伤害。
 *
 * 无冷却——每次挥剑都能重新触发。
 */
public final class TargetRushHandler {

    private static final double RUSH_RADIUS = 5.0;

    private TargetRushHandler() {}

    /**
     * 玩家挥手时由 ServerPlayNetworkHandlerMixin.onHandSwing 调用。
     * @return true = 已触发突进并攻击了目标
     */
    public static boolean tryRush(ServerPlayerEntity player) {
        if (!com.tomkestookit.TomkesTookitConfig.enabled("item.reforge")) return false;

        // 检查主手是否有 target_rush 重铸
        net.minecraft.item.ItemStack main = player.getMainHandStack();
        if (main.isEmpty()) return false;
        ReforgeRegistry.ReforgeDef rushDef = ReforgeRegistry.byId("target_rush");
        if (rushDef == null) return false;
        if (!ReforgeRegistry.alreadyApplied(main, rushDef)) return false;

        ServerWorld world = player.getServerWorld();

        // 搜索范围内最近的、对玩家有仇恨的目标（严格条件：mob.getTarget() 必须是本玩家）
        Box searchBox = player.getBoundingBox().expand(RUSH_RADIUS);
        List<MobEntity> candidates = world.getEntitiesByClass(MobEntity.class, searchBox,
            mob -> mob.isAlive()
                && mob.getTarget() == player            // 必须对玩家有仇恨
                && mob.squaredDistanceTo(player) <= RUSH_RADIUS * RUSH_RADIUS
                && canSee(world, player, mob));

        if (candidates.isEmpty()) return false;

        // 选最近的
        MobEntity target = candidates.stream()
            .min(Comparator.comparingDouble(m -> m.squaredDistanceTo(player)))
            .orElse(null);
        if (target == null) return false;

        // 如果已经很近（< 1.5 格），跳过传送直接攻击
        double dist = player.distanceTo(target);
        if (dist > 1.5) {
            // 传送到目标面前 1.2 格（从目标指向玩家的方向）
            Vec3d toPlayer = player.getPos().subtract(target.getPos()).normalize();
            Vec3d rushPos = target.getPos().add(toPlayer.multiply(1.2));
            BlockPos landPos = BlockPos.ofFloored(rushPos.x, rushPos.y, rushPos.z);
            if (!world.getBlockState(landPos).getCollisionShape(world, landPos).isEmpty()) {
                landPos = landPos.up();
                rushPos = new Vec3d(rushPos.x, landPos.getY(), rushPos.z);
                if (!world.getBlockState(landPos).getCollisionShape(world, landPos).isEmpty()) {
                    return false; // 没有安全位置
                }
            }

            // 突进路径检查：玩家当前位置 → 落点，中间不能有方块阻挡
            if (isPathBlocked(world, player, rushPos)) {
                return false;
            }

            player.requestTeleport(rushPos.x, rushPos.y, rushPos.z);
            player.setVelocity(0, 0, 0);
            player.networkHandler.sendPacket(
                new net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket(player));
        }

        // 对目标造成一次攻击
        player.attack(target);
        return true;
    }

    /** 简易视线检查：从玩家眼睛到目标中心是否有方块遮挡。*/
    private static boolean canSee(ServerWorld world, ServerPlayerEntity player, MobEntity target) {
        Vec3d eyePos = player.getEyePos();
        Vec3d targetCenter = target.getPos().add(0, target.getHeight() * 0.5, 0);
        HitResult result = world.raycast(new RaycastContext(
            eyePos, targetCenter,
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            player));
        return result.getType() == HitResult.Type.MISS
            || result.getPos().squaredDistanceTo(targetCenter) < 1.0;
    }

    /**
     * 突进路径障碍检查：玩家当前身体中心 → 落点身体中心，若中间有方块碰撞则视为路径被挡。
     * 分别检查脚部（+0.1y）和头部（+height-0.1y）两条线，任意一条被挡则整个突进取消。
     */
    private static boolean isPathBlocked(ServerWorld world, ServerPlayerEntity player, Vec3d rushPos) {
        double h = player.getHeight();
        // 脚线
        Vec3d fromLow = player.getPos().add(0, 0.1, 0);
        Vec3d toLow = rushPos.add(0, 0.1, 0);
        // 头线
        Vec3d fromHigh = player.getPos().add(0, h - 0.1, 0);
        Vec3d toHigh = rushPos.add(0, h - 0.1, 0);

        HitResult low = world.raycast(new RaycastContext(
            fromLow, toLow,
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            player));
        if (low.getType() != HitResult.Type.MISS) return true;

        HitResult high = world.raycast(new RaycastContext(
            fromHigh, toHigh,
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            player));
        return high.getType() != HitResult.Type.MISS;
    }
}
