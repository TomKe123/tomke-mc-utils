package com.tomkestookit;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeveledCauldronBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.potion.PotionUtil;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

/**
 * 炼药锅装药水 + 箭浸药水做药水箭。
 *
 * 交互规则（右键炼药锅）：
 *   1. 空/水炼药锅 + 主手药水（普通/喷溅/滞留任意）→ 变成"药水炼药锅"
 *      (视觉上是 3 级水炼药锅，但被 PotionCauldronTracker 记录，存储药水 NBT)
 *      返还玻璃瓶
 *   2. 药水炼药锅 + 主手箭 → 箭变药水箭，水位 -1（一层最多变 32 支）
 *      * 沿用 vanilla 的 "3 支箭 + 药水" 换算：一层水可以浸 32 支
 *      * 水位归零变回空炼药锅，从 tracker 移除
 *   3. 药水炼药锅 + 玻璃瓶 → 换回一瓶原药水，水位 -1
 *   4. 药水炼药锅（无匹配交互）不响应，交给 vanilla（vanilla 会把它当水锅处理）
 *
 * 视觉与数据分离：block state 仍是 vanilla WATER_CAULDRON，
 * 是否为"药水炼药锅"完全靠 tracker 里有无对应 key 判定。
 * 未在 tracker 里的 water cauldron 表现和 vanilla 一致。
 */
public final class PotionCauldronHandler {

    /** 每一层水位可浸的箭数量（对齐 vanilla 3 支 tipped arrow = 1 potion 的比率的约 10 倍）*/
    private static final int ARROWS_PER_LEVEL = 32;

    private PotionCauldronHandler() {}

    public static void register() {
        UseBlockCallback.EVENT.register(PotionCauldronHandler::onUseBlock);
        // 玩家破坏药水锅 → 清标签 + 清 tracker
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, be) -> {
            if (!(world instanceof ServerWorld sw)) return;
            if (state.getBlock() != Blocks.WATER_CAULDRON && state.getBlock() != Blocks.CAULDRON) return;
            String key = PotionCauldronTracker.key(sw.getRegistryKey().getValue().toString(), pos);
            if (!PotionCauldronTracker.has(key)) return;
            PotionCauldronLabel.removeLabel(sw, key);
            PotionCauldronTracker.remove(key);
        });
    }

    private static ActionResult onUseBlock(PlayerEntity player, net.minecraft.world.World world,
                                            Hand hand, BlockHitResult hit) {
        if (world.isClient) return ActionResult.PASS;
        if (!(world instanceof ServerWorld sw)) return ActionResult.PASS;
        if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
        // 只响应主手：副手使用不触发（避免误触）
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;

        BlockPos pos = hit.getBlockPos();
        BlockState state = world.getBlockState(pos);
        Block block = state.getBlock();

        // 只关心炼药锅（空的 CAULDRON 或 水的 WATER_CAULDRON）
        boolean isEmpty = block == Blocks.CAULDRON;
        boolean isWater = block == Blocks.WATER_CAULDRON;
        if (!isEmpty && !isWater) return ActionResult.PASS;

        ItemStack held = sp.getStackInHand(hand);
        if (held.isEmpty()) return ActionResult.PASS;

        String key = PotionCauldronTracker.key(sw.getRegistryKey().getValue().toString(), pos);
        boolean isPotionCauldron = isWater && PotionCauldronTracker.has(key);

        // 1) 空/水锅 + 药水 → 药水锅
        if (held.getItem() == Items.POTION
            || held.getItem() == Items.SPLASH_POTION
            || held.getItem() == Items.LINGERING_POTION) {
            return fillWithPotion(sp, sw, hand, held, state, pos, key, isPotionCauldron);
        }

        // 后续交互只对"药水锅"生效
        if (!isPotionCauldron) return ActionResult.PASS;

        // 2) 药水锅 + 箭 → 药水箭
        if (held.getItem() == Items.ARROW) {
            return dipArrows(sp, sw, hand, held, state, pos, key);
        }

        // 3) 药水锅 + 玻璃瓶 → 换回一瓶药水
        if (held.getItem() == Items.GLASS_BOTTLE) {
            return extractPotion(sp, sw, hand, held, state, pos, key);
        }

        return ActionResult.PASS;
    }

    // ----------------------------------------------------------------- 1) fill

    private static ActionResult fillWithPotion(ServerPlayerEntity player, ServerWorld world,
                                                Hand hand, ItemStack held, BlockState state,
                                                BlockPos pos, String key, boolean isPotionCauldron) {
        // 如果已经是药水锅但装的药水不同，禁止混装（避免 NBT 突变）
        if (isPotionCauldron) {
            NbtCompound stored = PotionCauldronTracker.getPotion(key);
            if (stored != null && !samePotion(stored, potionNbt(held))) {
                player.sendMessage(net.minecraft.text.Text.literal(
                    "炼药锅已装有不同药水，请先清空。").formatted(net.minecraft.util.Formatting.YELLOW), true);
                return ActionResult.SUCCESS; // 消费点击避免落到 vanilla
            }
        }
        // 如果是普通水锅（不在 tracker）而且不空，则不覆盖——避免抢 vanilla 的水锅行为
        if (state.getBlock() == Blocks.WATER_CAULDRON && !isPotionCauldron) {
            return ActionResult.PASS;
        }

        NbtCompound nbt = potionNbt(held);
        PotionCauldronTracker.setPotion(key, nbt);

        // 视觉：设为满水锅（level 3）
        world.setBlockState(pos, Blocks.WATER_CAULDRON.getDefaultState()
            .with(LeveledCauldronBlock.LEVEL, 3), Block.NOTIFY_ALL);

        // 悬浮字（新建或更新）
        PotionCauldronLabel.ensureLabel(world, pos, key, nbt, 3);

        // 替换玩家手中：药水 → 玻璃瓶
        if (!player.getAbilities().creativeMode) {
            held.decrement(1);
            ItemStack empty = new ItemStack(Items.GLASS_BOTTLE);
            if (held.isEmpty()) {
                player.setStackInHand(hand, empty);
            } else if (!player.getInventory().insertStack(empty)) {
                player.dropItem(empty, false);
            }
        }

        world.playSound(null, pos, SoundEvents.ITEM_BOTTLE_EMPTY, SoundCategory.BLOCKS, 1.0f, 1.0f);
        return ActionResult.SUCCESS;
    }

    // ----------------------------------------------------------------- 2) dip

    private static ActionResult dipArrows(ServerPlayerEntity player, ServerWorld world,
                                           Hand hand, ItemStack held, BlockState state,
                                           BlockPos pos, String key) {
        int level = state.get(LeveledCauldronBlock.LEVEL);
        if (level <= 0) return ActionResult.PASS;

        NbtCompound stored = PotionCauldronTracker.getPotion(key);
        if (stored == null) return ActionResult.PASS;

        int toConvert = Math.min(held.getCount(), ARROWS_PER_LEVEL);
        ItemStack tipped = new ItemStack(Items.TIPPED_ARROW, toConvert);
        applyPotionNbt(tipped, stored);

        // 消耗箭 + 生成药水箭
        held.decrement(toConvert);
        if (!player.getInventory().insertStack(tipped)) {
            player.dropItem(tipped, false);
        }

        // 水位 -1，归零时清空 + 移除标签
        int newLevel = level - 1;
        if (newLevel <= 0) {
            world.setBlockState(pos, Blocks.CAULDRON.getDefaultState(), Block.NOTIFY_ALL);
            PotionCauldronLabel.removeLabel(world, key);
            PotionCauldronTracker.remove(key);
        } else {
            world.setBlockState(pos, state.with(LeveledCauldronBlock.LEVEL, newLevel), Block.NOTIFY_ALL);
            PotionCauldronLabel.ensureLabel(world, pos, key, stored, newLevel);
        }

        world.playSound(null, pos, SoundEvents.BLOCK_BREWING_STAND_BREW, SoundCategory.BLOCKS, 0.8f, 1.0f);
        return ActionResult.SUCCESS;
    }

    // ----------------------------------------------------------------- 3) extract

    private static ActionResult extractPotion(ServerPlayerEntity player, ServerWorld world,
                                               Hand hand, ItemStack held, BlockState state,
                                               BlockPos pos, String key) {
        int level = state.get(LeveledCauldronBlock.LEVEL);
        if (level <= 0) return ActionResult.PASS;

        NbtCompound stored = PotionCauldronTracker.getPotion(key);
        if (stored == null) return ActionResult.PASS;

        ItemStack potion = new ItemStack(Items.POTION);
        applyPotionNbt(potion, stored);

        // 玻璃瓶 -1
        if (!player.getAbilities().creativeMode) {
            held.decrement(1);
        }
        if (!player.getInventory().insertStack(potion)) {
            player.dropItem(potion, false);
        }

        int newLevel = level - 1;
        if (newLevel <= 0) {
            world.setBlockState(pos, Blocks.CAULDRON.getDefaultState(), Block.NOTIFY_ALL);
            PotionCauldronLabel.removeLabel(world, key);
            PotionCauldronTracker.remove(key);
        } else {
            world.setBlockState(pos, state.with(LeveledCauldronBlock.LEVEL, newLevel), Block.NOTIFY_ALL);
            PotionCauldronLabel.ensureLabel(world, pos, key, stored, newLevel);
        }

        world.playSound(null, pos, SoundEvents.ITEM_BOTTLE_FILL, SoundCategory.BLOCKS, 1.0f, 1.0f);
        return ActionResult.SUCCESS;
    }

    // ----------------------------------------------------------------- helpers

    /** 从药水物品 NBT 里抽出 Potion + CustomPotionEffects + CustomPotionColor 到独立 compound。*/
    private static NbtCompound potionNbt(ItemStack potion) {
        NbtCompound out = new NbtCompound();
        NbtCompound src = potion.getNbt();
        if (src == null) return out;
        if (src.contains(PotionUtil.POTION_KEY)) {
            out.putString(PotionUtil.POTION_KEY, src.getString(PotionUtil.POTION_KEY));
        }
        if (src.contains(PotionUtil.CUSTOM_POTION_EFFECTS_KEY)) {
            out.put(PotionUtil.CUSTOM_POTION_EFFECTS_KEY,
                src.getList(PotionUtil.CUSTOM_POTION_EFFECTS_KEY, 10).copy());
        }
        if (src.contains(PotionUtil.CUSTOM_POTION_COLOR_KEY)) {
            out.putInt(PotionUtil.CUSTOM_POTION_COLOR_KEY,
                src.getInt(PotionUtil.CUSTOM_POTION_COLOR_KEY));
        }
        return out;
    }

    /** 把存储的药水 NBT 应用到物品栈上。*/
    private static void applyPotionNbt(ItemStack stack, NbtCompound stored) {
        NbtCompound tag = stack.getOrCreateNbt();
        if (stored.contains(PotionUtil.POTION_KEY)) {
            tag.putString(PotionUtil.POTION_KEY, stored.getString(PotionUtil.POTION_KEY));
        }
        if (stored.contains(PotionUtil.CUSTOM_POTION_EFFECTS_KEY)) {
            tag.put(PotionUtil.CUSTOM_POTION_EFFECTS_KEY,
                stored.getList(PotionUtil.CUSTOM_POTION_EFFECTS_KEY, 10).copy());
        }
        if (stored.contains(PotionUtil.CUSTOM_POTION_COLOR_KEY)) {
            tag.putInt(PotionUtil.CUSTOM_POTION_COLOR_KEY,
                stored.getInt(PotionUtil.CUSTOM_POTION_COLOR_KEY));
        }
    }

    private static boolean samePotion(NbtCompound a, NbtCompound b) {
        return a.toString().equals(b.toString());
    }
}
