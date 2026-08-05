package com.tomkestookit.mixin;

import com.tomkestookit.TomkesTookitConfig;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public abstract class EliteShieldDamageMixin {

    @ModifyVariable(
        method = "damage",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private float tomkestookit$eliteShieldHalve(float amount, DamageSource source) {
        if (!TomkesTookitConfig.enabled("world.elite_mobs")) return amount;
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof MobEntity mob)) return amount;
        NbtCompound nbt = new NbtCompound();
        mob.writeCustomDataToNbt(nbt);
        if (!nbt.contains("TomkesTookitEliteShield")) return amount;
        long shieldEnd = nbt.getLong("TomkesTookitEliteShield");
        if (self.getWorld().getTime() < shieldEnd) {
            return amount * 0.5f;
        }
        return amount;
    }
}
