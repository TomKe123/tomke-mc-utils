package com.tomkestookit.boss;

import com.tomkestookit.TomkesTookitMod;
import com.tomkestookit.boss.BossTokenRegistry.BossDef;
import com.tomkestookit.reforge.ReforgeRegistry;
import com.tomkestookit.trinket.TrinketRegistry;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Boss 击杀掉落表：
 *   - 100% 随机饰品（TrinketRegistry.all() 加权，暂用均匀分布）
 *   - 100% 随机重铸石（ReforgeRegistry.all() 加权，暂用均匀分布）
 *   - 60% Boss 专属饰品（id = "boss_<bossId>"，需要在 TrinketRegistry 里预先注册）
 *   - 直接生成 xpReward 值的经验球（ExperienceOrbEntity.spawn 会自动拆分）
 */
public final class BossDropTable {

    private BossDropTable() {}

    public static void drop(BossDef def, ServerWorld world, Vec3d pos,
                            @Nullable ServerPlayerEntity killer) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        // 1) 随机饰品
        List<TrinketRegistry.TrinketDef> trinkets = TrinketRegistry.all();
        if (!trinkets.isEmpty()) {
            TrinketRegistry.TrinketDef pick = trinkets.get(rng.nextInt(trinkets.size()));
            ItemStack s = TrinketRegistry.buildStack(pick);
            if (s != null && !s.isEmpty()) spawnItem(world, pos, s);
        }

        // 2) 随机重铸石
        List<ReforgeRegistry.ReforgeDef> reforges = ReforgeRegistry.all();
        if (!reforges.isEmpty()) {
            ReforgeRegistry.ReforgeDef pick = reforges.get(rng.nextInt(reforges.size()));
            ItemStack s = ReforgeRegistry.buildStack(pick);
            if (s != null && !s.isEmpty()) spawnItem(world, pos, s);
        }

        // 3) Boss 专属饰品（60%）
        if (rng.nextDouble() < 0.6) {
            TrinketRegistry.TrinketDef exclusive = TrinketRegistry.byId("boss_" + def.bossId());
            if (exclusive != null) {
                ItemStack s = TrinketRegistry.buildStack(exclusive);
                if (s != null && !s.isEmpty()) spawnItem(world, pos, s);
            }
        }

        // 4) 经验球（vanilla split by orb value）
        if (def.xpReward() > 0) {
            ExperienceOrbEntity.spawn(world, pos, def.xpReward());
        }

        TomkesTookitMod.LOGGER.info("[BossDropTable] Boss {} 掉落发放 @ ({}, {}, {})",
            def.bossId(), (int) pos.x, (int) pos.y, (int) pos.z);
    }

    private static void spawnItem(ServerWorld world, Vec3d pos, ItemStack stack) {
        ItemEntity item = new ItemEntity(world, pos.x, pos.y + 0.5, pos.z, stack);
        item.setPickupDelay(10);
        world.spawnEntity(item);
    }
}
