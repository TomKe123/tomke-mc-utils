package com.starterkit;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.util.Hand;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;

/**
 * Shift + 右键任意生物 → 将其收进对应刷怪蛋，右键刷怪蛋放置时原样还原。
 *
 *   - 原理：把实体完整 NBT 写入刷怪蛋的 EntityTag，原版 SpawnEggItem 在放置时
 *     会读取该 tag 并应用到新生成的实体（交易/驯服/命名/装备/乘客都保留）。
 *   - 没有对应刷怪蛋的实体（矿车/画/末影龙/凋灵等）跳过，交给原版逻辑处理。
 *   - 玩家不物品化。
 *   - 原版 sneak + 右键村民本身不会打开交易 GUI，所以拦截不会与原版冲突。
 *   - 所有玩家均可使用；服务端逻辑，客户端无需 mod。
 */
public final class EntityCaptureHandler {

    private EntityCaptureHandler() {}

    public static void register() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient) return ActionResult.PASS;
            if (!player.isSneaking()) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;
            if (entity instanceof PlayerEntity) return ActionResult.PASS;

            // 只有双手都空才触发。UseEntityCallback 会以主手和副手分别触发一次，
            // 我们只在主手事件里判断，副手事件直接跳过。
            //   - 主手非空：走原版交互（右键喂食、给命名牌等）
            //   - 副手非空：也走原版交互，避免"副手拿蓝宝石 + 主手空 shift+右键村民"
            //     被误判为收纳；玩家想开交易时只需主手空、副手拿物品即可正常交易。
            if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
            if (!player.getMainHandStack().isEmpty()) return ActionResult.PASS;
            if (!player.getOffHandStack().isEmpty()) return ActionResult.PASS;

            SpawnEggItem egg = SpawnEggItem.forEntity(entity.getType());
            if (egg == null) return ActionResult.PASS;

            ItemStack stack = buildCapturedEgg(egg, entity);

            if (!serverPlayer.getInventory().insertStack(stack)) {
                serverPlayer.dropItem(stack, false);
            }

            world.playSound(null, entity.getBlockPos(),
                SoundEvents.ITEM_BUCKET_FILL, SoundCategory.PLAYERS, 1.0f, 1.2f);
            entity.discard();
            return ActionResult.SUCCESS;
        });
    }

    private static ItemStack buildCapturedEgg(SpawnEggItem egg, Entity entity) {
        ItemStack stack = new ItemStack(egg);

        NbtCompound entityNbt = new NbtCompound();
        entity.writeNbt(entityNbt);
        // 位置 / 朝向 / UUID 应由放置时重新决定：避免回到原地或 UUID 冲突
        entityNbt.remove("UUID");
        entityNbt.remove("Pos");
        entityNbt.remove("Motion");
        entityNbt.remove("Rotation");

        NbtCompound tag = stack.getOrCreateNbt();
        tag.put("EntityTag", entityNbt);

        // 不给物品 setCustomName：一旦物品有 CustomName，SpawnEggItem 放置时会
        // 把它写入生成的实体，等于给原本没名字的实体加了名字。命名牌命名过的
        // 实体 CustomName 已在 entityNbt 里，会随 EntityTag 正确还原。
        // 通过 lore 区分普通刷怪蛋。
        NbtCompound display = tag.getCompound("display");
        NbtList lore = new NbtList();
        lore.add(NbtString.of(Text.Serializer.toJson(
            Text.literal("已收纳").formatted(Formatting.GRAY, Formatting.ITALIC))));
        display.put("Lore", lore);
        tag.put("display", display);

        return stack;
    }
}
