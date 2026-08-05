package com.tomkestookit.elite.skill;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MobSkillState {
    public final Map<String, Long> cooldownEndTick = new HashMap<>();
    // For normal mobs: single pending. For boss: up to 2 pending.
    public final List<PendingSkill> pendingSkills = new ArrayList<>(2);

    public record PendingSkill(EliteSkill skill, SkillContext ctx, long executeAtTick) {}

    public boolean hasPending() { return !pendingSkills.isEmpty(); }

    public boolean canAddPending(boolean isBoss) {
        return isBoss ? pendingSkills.size() < 2 : pendingSkills.isEmpty();
    }
}
