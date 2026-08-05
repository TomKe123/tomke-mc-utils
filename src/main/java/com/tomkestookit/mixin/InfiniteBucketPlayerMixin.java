package com.tomkestookit.mixin;

import com.tomkestookit.infinitebucket.InfiniteBucketHolder;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PlayerEntity.class)
public abstract class InfiniteBucketPlayerMixin implements InfiniteBucketHolder {

    @Unique
    private ItemStack tomkestookit$infiniteBucketTemp;

    @Override
    public void tomkestookit$setInfiniteBucket(ItemStack stack) {
        this.tomkestookit$infiniteBucketTemp = stack;
    }

    @Override
    public ItemStack tomkestookit$getInfiniteBucket() {
        return this.tomkestookit$infiniteBucketTemp;
    }
}
