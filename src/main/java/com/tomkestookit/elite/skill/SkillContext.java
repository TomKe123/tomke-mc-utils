package com.tomkestookit.elite.skill;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import java.util.List;

public record SkillContext(
    MobEntity attacker,
    ServerWorld world,
    List<ServerPlayerEntity> targets,
    int tier,
    long currentTick
) {}
