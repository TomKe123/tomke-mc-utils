package com.tomkestookit.mixin;

import com.tomkestookit.GossipBroadcast;
import net.minecraft.entity.mob.ZombieVillagerEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.UUID;

/**
 * 僵尸村民被治愈完成后，立即广播 gossip 给附近村民。
 *
 * 背景：VillagerBrainFreezeMixin 冻结了远处村民的 brain tick，导致 vanilla 里
 * MajorPositiveGossipTask 慢传播断链。这里在治愈瞬间直接把玩家的功劳广播出去，
 * 让村民折扣按 vanilla 语义正确扩散。
 *
 * hook 点：{@link ZombieVillagerEntity#finishConversion(ServerWorld)} TAIL
 *   - 此时新 VillagerEntity 已被 spawn 到世界，zombie 已 discard
 *   - converter UUID 存在 zombie 的字段里（HEAD 之前读，避免被清）
 */
@Mixin(ZombieVillagerEntity.class)
public abstract class ZombieVillagerCureGossipMixin {

    @Shadow private UUID converter;

    @Inject(method = "finishConversion", at = @At("HEAD"))
    private void tomkestookit$captureAndBroadcast(ServerWorld world, CallbackInfo ci) {
        try {
            //noinspection DataFlowIssue -- mixin 标准写法，运行时 this 就是 ZombieVillagerEntity 实例
            ZombieVillagerEntity self = (ZombieVillagerEntity) (Object) this;
            if (converter == null) return;
            if (!(world.getPlayerByUuid(converter) instanceof ServerPlayerEntity healer)) return;

            // 立即查找附近的 VillagerEntity 并加入 gossip
            double x = self.getX(), y = self.getY(), z = self.getZ();
            double r = 48.0;
            Box box = new Box(x - r, y - r, z - r, x + r, y + r, z + r);
            List<VillagerEntity> nearby = world.getEntitiesByClass(VillagerEntity.class, box,
                v -> !v.isBaby() && v.isAlive());
            for (VillagerEntity v : nearby) {
                v.getGossip().startGossip(healer.getUuid(),
                    net.minecraft.village.VillageGossipType.MAJOR_POSITIVE, 20);
                v.getGossip().startGossip(healer.getUuid(),
                    net.minecraft.village.VillageGossipType.MINOR_POSITIVE, 25);
            }
        } catch (Throwable ignored) {}
    }
}
