package com.tomkestookit.elite.skill;

import java.util.function.Consumer;
import java.util.function.Predicate;

public record EliteSkill(
    String id,
    int cooldownTicks,
    int windupTicks,
    int minTierRequired,
    Predicate<SkillContext> canTrigger,
    Consumer<SkillContext> onWindup,
    Consumer<SkillContext> onExecute
) {}
