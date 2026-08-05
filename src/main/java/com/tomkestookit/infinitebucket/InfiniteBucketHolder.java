package com.tomkestookit.infinitebucket;

import net.minecraft.item.ItemStack;

/**
 * Duck interface 注入到 PlayerEntity，用于 InfiniteBucketUseMixin
 * 在 HEAD 和 RETURN 之间传递无限桶副本。
 */
public interface InfiniteBucketHolder {
    void tomkestookit$setInfiniteBucket(ItemStack stack);
    ItemStack tomkestookit$getInfiniteBucket();
}
