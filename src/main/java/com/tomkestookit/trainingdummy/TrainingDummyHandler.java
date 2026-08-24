package com.tomkestookit.trainingdummy;

import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 训练假人（Training Dummy）核心逻辑。
 *
 * 特性：
 *   - 合成：中心盔甲架 + 周围 8 干草块 → 带附魔光效的训练假人物品
 *   - 放置后：与盔甲架实体 uuid 绑定，记录 NBT 标记
 *   - 玩家击打：显示单体伤害和溅射伤害（横扫）
 *   - 右键菜单：DPS 测试模式（选定时长，计算 DPS）
 *   - 拆除：必须用斧子连续攻击 5 次（与 ArmorStandDurableMixin 共用逻辑）
 *
 * 数据存储：
 *   - 物品 NBT: trainingDummy=1b 标识训练假人物品
 *   - 实体 NBT: trainingDummy=1b 标识放置后的训练假人实体
 *   - 内存缓存: 每个假人的 DPS 测试状态（测试中/结束）
 */
public final class TrainingDummyHandler {

    private TrainingDummyHandler() {}

    /** 物品 NBT 标记 */
    public static final String NBT_MARKER = "trainingDummy";
    /** 实体 scoreboard tag（Tags 字段，readNbt/writeNbt 自动持久化，不会被过滤） */
    private static final String ENTITY_TAG = "tomkestookit_training_dummy";

    /** DPS 测试会话：玩家 UUID → 测试状态 */
    private static final Map<UUID, DpsSession> DPS_SESSIONS = new ConcurrentHashMap<>();

    /** 记录玩家最近一次主攻击伤害，用于判断横扫 */
    private static final Map<UUID, Float> LAST_MAIN_DAMAGE = new ConcurrentHashMap<>();

    public static void register() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(TrainingDummyHandler::tick);

        // 监听盔甲架生成，设置初始头顶显示
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof ArmorStandEntity stand && isTrainingDummyEntity(stand)) {
                resetDisplayName(stand);
            }
        });
    }

    /** 判定 3x3 合成输入是否匹配训练假人配方。*/
    public static boolean matches3x3(net.minecraft.inventory.RecipeInputInventory input) {
        if (input.getWidth() != 3 || input.getHeight() != 3) return false;
        // 中心必须是盔甲架
        if (input.getStack(4).getItem() != Items.ARMOR_STAND) return false;
        // 周围 8 格必须全是干草块
        int[] surround = {0, 1, 2, 3, 5, 6, 7, 8};
        for (int i : surround) {
            if (input.getStack(i).getItem() != Items.HAY_BLOCK) return false;
        }
        return true;
    }

    /** 构造训练假人物品（带附魔光效 + 自定义名称和 lore）。*/
    public static ItemStack buildResult() {
        ItemStack stack = new ItemStack(Items.ARMOR_STAND);

        // 附魔光效（无实际附魔）
        stack.addEnchantment(net.minecraft.enchantment.Enchantments.UNBREAKING, 1);
        stack.addHideFlag(ItemStack.TooltipSection.ENCHANTMENTS);

        // 自定义名称
        stack.setCustomName(Text.literal("训练假人")
            .formatted(Formatting.GOLD, Formatting.BOLD)
            .styled(s -> s.withItalic(false)));

        // Lore 描述
        NbtCompound nbt = stack.getOrCreateNbt();
        NbtCompound display = nbt.getCompound("display");
        NbtList lore = new NbtList();
        for (String line : List.of(
            "§8「稻草人的升级版，专门挨打。」",
            "§7击打显示：§c单体伤害 §7/ §6溅射伤害",
            "§7右键打开：§bDPS测试菜单",
            "§7拆除需要：§e斧子连续攻击5次",
            "§8不会还手，也不会跑"
        )) {
            lore.add(NbtString.of(Text.Serializer.toJson(
                Text.literal(line).styled(s -> s.withItalic(false)))));
        }
        display.put("Lore", lore);
        nbt.put("display", display);

        // 训练假人标记（关键：vanilla ArmorStandItem.useOnBlock() 会调用
        // EntityType.create(world, stack.getNbt(), ...) 把物品根 NBT 完整复制到
        // 生成的盔甲架实体上，所以这里只写物品根 NBT 即可，无需额外 Mixin）
        nbt.putBoolean(NBT_MARKER, true);

        // 品质标签
        com.tomkestookit.materials.ItemQualityTag.append(stack,
            com.tomkestookit.materials.ItemQualityTag.Quality.RARE,
            "3×3 合成 §8· §7盔甲架 + 8 干草块");

        return stack;
    }

    /** 判定物品是否为训练假人物品。*/
    public static boolean isTrainingDummyItem(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() != Items.ARMOR_STAND) return false;
        NbtCompound nbt = stack.getNbt();
        return nbt != null && nbt.getBoolean(NBT_MARKER);
    }

    /** 判定实体是否为训练假人实体（用 command tag，readNbt 自动持久化到 Tags 字段）。*/
    public static boolean isTrainingDummyEntity(ArmorStandEntity stand) {
        return stand != null && stand.getCommandTags().contains(ENTITY_TAG);
    }

    /** 标记实体为训练假人（放置时调用）。command tag 会随实体 NBT 持久化。*/
    public static void markEntity(ArmorStandEntity stand) {
        stand.addCommandTag(ENTITY_TAG);
        // 设置头顶名称显示
        resetDisplayName(stand);
    }

    /** 更新训练假人头顶显示状态（DPS 测试时调用）。*/
    public static void updateDisplayName(ArmorStandEntity stand, PlayerEntity tester, int remainingSeconds) {
        if (!isTrainingDummyEntity(stand)) return;
        Text name = Text.empty()
            .append(Text.literal("训练假人").formatted(Formatting.GOLD, Formatting.BOLD))
            .append(Text.literal("\n§7测试者: §e" + tester.getName().getString()))
            .append(Text.literal("\n§7剩余: §c" + remainingSeconds + "s"));
        stand.setCustomName(name);
        stand.setCustomNameVisible(true);
    }

    /** 重置训练假人头顶显示（无 DPS 测试时）。*/
    public static void resetDisplayName(ArmorStandEntity stand) {
        if (!isTrainingDummyEntity(stand)) return;
        Text name = Text.empty()
            .append(Text.literal("训练假人").formatted(Formatting.GOLD, Formatting.BOLD))
            .append(Text.literal("\n§8右键打开菜单"));
        stand.setCustomName(name);
        stand.setCustomNameVisible(true);
    }

    /** 玩家击打训练假人时调用，显示伤害反馈。*/
    public static void onHit(ArmorStandEntity dummy, PlayerEntity attacker, float damage, float sweepDamage) {
        if (!isTrainingDummyEntity(dummy)) return;

        UUID attackerId = attacker.getUuid();
        DpsSession session = DPS_SESSIONS.get(attackerId);

        // 判断是否是横扫攻击：当前伤害显著小于上次主攻击（阈值 0.6）
        Float lastMain = LAST_MAIN_DAMAGE.get(attackerId);
        boolean isSweep = lastMain != null && damage < lastMain * 0.6f;

        // 更新主攻击记录（只记录较大的伤害）
        if (!isSweep) {
            LAST_MAIN_DAMAGE.put(attackerId, damage);
        }

        // 如果正在 DPS 测试，累加伤害并更新假人头顶显示
        if (session != null && session.isActive()) {
            session.addDamage(damage);
            long now = System.currentTimeMillis();
            int remaining = session.getRemainingSeconds(now);
            updateDisplayName(dummy, attacker, remaining);
        }

        // 显示伤害反馈（悬浮文字在假人位置上方）
        spawnDamageText(dummy, attacker, damage, isSweep);
    }

    /** 在训练假人上方生成悬浮伤害文字（盔甲架实体）。*/
    private static void spawnDamageText(ArmorStandEntity dummy, PlayerEntity attacker, float damage, boolean isSweep) {
        if (dummy.getWorld().isClient) return;

        // 在假人上方 1.5 格生成一个不可见的盔甲架，作为伤害文字载体
        ArmorStandEntity textStand = new ArmorStandEntity(
            net.minecraft.entity.EntityType.ARMOR_STAND,
            dummy.getWorld()
        );

        // 位置：假人正上方 1.5 格 + 随机偏移（防止重叠）
        double offsetX = (Math.random() - 0.5) * 0.3;
        double offsetZ = (Math.random() - 0.5) * 0.3;
        textStand.setPosition(
            dummy.getX() + offsetX,
            dummy.getY() + 1.5,
            dummy.getZ() + offsetZ
        );

        // 配置：不可见、无重力、无碰撞、显示自定义名称
        textStand.setInvisible(true);
        textStand.setNoGravity(true);
        textStand.setInvulnerable(true);
        // setMarker 是 private，用 NBT 直接设置
        NbtCompound nbt = textStand.writeNbt(new NbtCompound());
        nbt.putBoolean("Marker", true);
        textStand.readNbt(nbt);

        // 自定义名称显示伤害数字
        Text damageText = isSweep
            ? Text.literal("⚔ " + String.format("%.1f", damage)).formatted(Formatting.GOLD)
            : Text.literal("❤ " + String.format("%.1f", damage)).formatted(Formatting.RED, Formatting.BOLD);
        textStand.setCustomName(damageText);
        textStand.setCustomNameVisible(true);

        // 生成到世界
        dummy.getWorld().spawnEntity(textStand);

        // 1 秒后自动删除（延迟任务）
        scheduleRemove(textStand, 20); // 20 ticks = 1 秒
    }

    /** 延迟删除实体（通过 scheduler）。*/
    private static void scheduleRemove(ArmorStandEntity entity, int delayTicks) {
        net.minecraft.server.MinecraftServer server = entity.getServer();
        if (server == null) return;

        // 记录目标删除时间
        final long removeTime = server.getTicks() + delayTicks;
        final java.util.UUID entityId = entity.getUuid();

        // 注册到 server tick 事件
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(s -> {
            if (s != server) return;
            if (s.getTicks() >= removeTime) {
                // 通过 UUID 查找实体并删除
                server.getWorlds().forEach(world -> {
                    net.minecraft.entity.Entity e = world.getEntity(entityId);
                    if (e != null) e.discard();
                });
            }
        });
    }

    /** 玩家右键训练假人时打开 DPS 测试菜单。*/
    public static void openDpsMenu(PlayerEntity player, ArmorStandEntity dummy) {
        if (!isTrainingDummyEntity(dummy)) return;
        TrainingDummyMenu.open(player, dummy);
    }

    /** 开始 DPS 测试（指定时长，秒）。*/
    public static void startDpsTest(PlayerEntity player, int durationSeconds) {
        UUID playerId = player.getUuid();
        DPS_SESSIONS.put(playerId, new DpsSession(durationSeconds, player.getUuid()));
        player.sendMessage(Text.literal("DPS测试开始！持续 " + durationSeconds + " 秒")
            .formatted(Formatting.GREEN));
    }

    /** 结束 DPS 测试，计算并显示结果。*/
    public static void endDpsTest(PlayerEntity player) {
        UUID playerId = player.getUuid();
        DpsSession session = DPS_SESSIONS.remove(playerId);
        if (session == null) {
            player.sendMessage(Text.literal("没有进行中的DPS测试").formatted(Formatting.RED));
            return;
        }

        // 重置所有训练假人的头顶显示（因为不知道是哪个假人，全部重置）
        if (player.getServer() != null) {
            player.getServer().getWorlds().forEach(world -> {
                world.getEntitiesByClass(ArmorStandEntity.class,
                    player.getBoundingBox().expand(50),
                    TrainingDummyHandler::isTrainingDummyEntity
                ).forEach(TrainingDummyHandler::resetDisplayName);
            });
        }

        double dps = session.calculateDps();
        player.sendMessage(Text.empty()
            .append(Text.literal("DPS测试结束！").formatted(Formatting.GOLD, Formatting.BOLD))
            .append(Text.literal("\n总伤害: ").formatted(Formatting.GRAY))
            .append(Text.literal(String.format("%.1f", session.totalDamage)).formatted(Formatting.RED))
            .append(Text.literal("\n时长: ").formatted(Formatting.GRAY))
            .append(Text.literal(session.durationSeconds + "s").formatted(Formatting.YELLOW))
            .append(Text.literal("\nDPS: ").formatted(Formatting.GRAY))
            .append(Text.literal(String.format("%.2f", dps)).formatted(Formatting.AQUA, Formatting.BOLD)));
    }

    /** Tick 更新：检查 DPS 测试是否超时，并每秒更新训练假人头顶显示。*/
    public static void tick(net.minecraft.server.MinecraftServer server) {
        long now = System.currentTimeMillis();

        // 每 20 ticks（1秒）更新一次头顶显示
        if (server.getTicks() % 20 == 0) {
            for (Map.Entry<UUID, DpsSession> entry : DPS_SESSIONS.entrySet()) {
                UUID playerId = entry.getKey();
                DpsSession session = entry.getValue();
                PlayerEntity player = server.getPlayerManager().getPlayer(playerId);

                if (player != null) {
                    int remaining = session.getRemainingSeconds(now);
                    // 更新玩家附近所有训练假人的头顶显示
                    server.getWorlds().forEach(world -> {
                        world.getEntitiesByClass(ArmorStandEntity.class,
                            player.getBoundingBox().expand(50),
                            TrainingDummyHandler::isTrainingDummyEntity
                        ).forEach(dummy -> updateDisplayName(dummy, player, remaining));
                    });
                }
            }
        }

        // 检查超时的测试会话
        DPS_SESSIONS.entrySet().removeIf(entry -> {
            DpsSession session = entry.getValue();
            if (session.isExpired(now)) {
                UUID playerId = entry.getKey();
                PlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                if (player != null) {
                    endDpsTest(player);
                }
                return true;
            }
            return false;
        });
    }

    /** DPS 测试会话 */
    private static class DpsSession {
        final int durationSeconds;
        final long startTime;
        final UUID testerUuid;
        double totalDamage = 0;

        DpsSession(int durationSeconds, UUID testerUuid) {
            this.durationSeconds = durationSeconds;
            this.startTime = System.currentTimeMillis();
            this.testerUuid = testerUuid;
        }

        void addDamage(float damage) {
            totalDamage += damage;
        }

        boolean isActive() {
            return !isExpired(System.currentTimeMillis());
        }

        boolean isExpired(long now) {
            return (now - startTime) >= durationSeconds * 1000L;
        }

        int getRemainingSeconds(long now) {
            long elapsed = now - startTime;
            long remaining = (durationSeconds * 1000L - elapsed) / 1000L;
            return (int) Math.max(0, remaining);
        }

        double calculateDps() {
            return totalDamage / durationSeconds;
        }
    }
}
