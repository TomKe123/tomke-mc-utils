package com.tomkestookit.infinitebucket;

import com.tomkestookit.PotionCauldronTracker;
import com.tomkestookit.TomkesTookitConfig;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeveledCauldronBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;

/**
 * 无限桶与炼药锅的交互修复。
 *
 * 背景：vanilla 空炼药锅 + 水桶/岩浆桶右键，走的是
 *   CauldronBlock.onUse → CauldronBehavior.FILL_WITH_WATER/LAVA
 *   → ItemUsage.exchangeStack(stack, player, new ItemStack(Items.BUCKET))
 * 完全不经过 BucketItem.use，所以 {@link com.tomkestookit.mixin.InfiniteBucketUseMixin}
 * 拦不到这次交互——玩家的无限桶被直接换成空桶，NBT 里的 Infinite 标记也丢了。
 *
 * 修复：注册 {@link UseBlockCallback} 抢在 vanilla 之前处理"无限桶 + 空炼药锅"，
 * 手动把方块状态改成对应的满炼药锅，不消耗桶，不换空桶。
 * 只处理 {@link Blocks#CAULDRON}（真正的空锅）：
 *   - 半水锅 / 岩浆锅 / 药水锅：跳过（vanilla 行为本身就不允许对它们再倒液体），
 *     此时 BucketItem.use 兜底路径的 mixin 仍能保住无限桶。
 */
public final class InfiniteBucketHandler {

    private InfiniteBucketHandler() {}

    public static void register() {
        UseBlockCallback.EVENT.register(InfiniteBucketHandler::onUseBlock);
    }

    private static ActionResult onUseBlock(PlayerEntity player, World world,
                                            Hand hand, BlockHitResult hit) {
        if (world.isClient) return ActionResult.PASS;
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (!TomkesTookitConfig.enabled("recipe.infinite_bucket")) return ActionResult.PASS;
        if (!(world instanceof ServerWorld sw)) return ActionResult.PASS;
        if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;

        ItemStack stack = sp.getStackInHand(hand);
        InfiniteBucket.BucketType type = InfiniteBucket.getType(stack);
        if (type == null) return ActionResult.PASS;

        BlockPos pos = hit.getBlockPos();
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() != Blocks.CAULDRON) return ActionResult.PASS;

        // 兜底：真的空锅永远不该在 potion tracker 里，但保险起见判一下
        String key = PotionCauldronTracker.key(sw.getRegistryKey().getValue().toString(), pos);
        if (PotionCauldronTracker.has(key)) return ActionResult.PASS;

        BlockState filled;
        if (type == InfiniteBucket.BucketType.WATER) {
            filled = Blocks.WATER_CAULDRON.getDefaultState().with(LeveledCauldronBlock.LEVEL, 3);
            sw.playSound(null, pos, SoundEvents.ITEM_BUCKET_EMPTY, SoundCategory.BLOCKS, 1.0f, 1.0f);
        } else {
            filled = Blocks.LAVA_CAULDRON.getDefaultState();
            sw.playSound(null, pos, SoundEvents.ITEM_BUCKET_EMPTY_LAVA, SoundCategory.BLOCKS, 1.0f, 1.0f);
        }
        sw.setBlockState(pos, filled, Block.NOTIFY_ALL);
        sw.emitGameEvent(null, GameEvent.FLUID_PLACE, pos);

        // 不消耗桶。SUCCESS 阻断 vanilla 的 CauldronBehavior 分派。
        return ActionResult.SUCCESS;
    }
}
