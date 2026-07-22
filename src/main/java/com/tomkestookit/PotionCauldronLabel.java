package com.tomkestookit;

import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionUtil;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.UUID;

/**
 * 炼药锅上方悬浮字（TextDisplayEntity）的创建/更新/移除。
 *
 * 生命周期由 PotionCauldronHandler 主动驱动：
 *   - 装药水（fillWithPotion）→ ensureLabel（首次创建 or 已存在则更新文本）
 *   - 浸箭 / 取药水（level 下降）→ updateLabel（刷新剩余层数）
 *   - 锅归零（回退到 CAULDRON）→ removeLabel
 *   - 破坏事件（PotionCauldronHandler AFTER-break 挂钩）→ removeLabel
 *
 * 兜底：每次交互进入时验证 tracker 里的 pos 仍是 water_cauldron；
 * 不是则视为孤儿 → 清 tracker + 尝试删标签。
 *
 * 用 Entity.readNbt() 通过 NBT 全量配置实体，避开 TextDisplayEntity 私有 setter。
 */
public final class PotionCauldronLabel {

    private PotionCauldronLabel() {}

    /**
     * 保证 pos 处有一个和 tracker 匹配的标签实体（不存在则创建）。
     * 每次调用都会重写文本（药水类型 + 时长 + 剩余层数）。
     */
    public static void ensureLabel(ServerWorld world, BlockPos pos, String key,
                                    NbtCompound potionNbt, int level) {
        UUID existing = PotionCauldronTracker.getLabelUuid(key);
        Entity entity = existing == null ? null : world.getEntity(existing);
        if (entity instanceof DisplayEntity.TextDisplayEntity) {
            entity.readNbt(buildDisplayNbt(pos, potionNbt, level));
            return;
        }
        // 需要新建（旧引用不存在或类型错）
        DisplayEntity.TextDisplayEntity display = EntityType.TEXT_DISPLAY.create(world);
        if (display == null) return;
        NbtCompound nbt = buildDisplayNbt(pos, potionNbt, level);
        display.readNbt(nbt);
        display.refreshPositionAndAngles(labelX(pos), labelY(pos), labelZ(pos), 0.0f, 0.0f);
        world.spawnEntity(display);
        PotionCauldronTracker.setLabelUuid(key, display.getUuid());
    }

    /** 移除 pos 对应的标签实体（如果存在）。tracker 里的 uuid 置 null（by caller）。*/
    public static void removeLabel(ServerWorld world, String key) {
        UUID uuid = PotionCauldronTracker.getLabelUuid(key);
        if (uuid == null) return;
        Entity entity = world.getEntity(uuid);
        if (entity != null) entity.discard();
        PotionCauldronTracker.setLabelUuid(key, null);
    }

    /**
     * 服务器启动时的孤儿扫尾：
     *   - tracker 里的 pos 已不是 water_cauldron → 移除 entry + 尝试删标签
     *   - tracker 里的 labelUuid 找不到实体 → 交由后续 ensureLabel 重新创建
     */
    public static void cleanupOrphans(MinecraftServer server) {
        var snapshot = PotionCauldronTracker.allEntries();
        for (var e : snapshot.entrySet()) {
            String key = e.getKey();
            ServerWorld world = resolveWorld(server, key);
            if (world == null) {
                // 维度不存在了，直接扔
                PotionCauldronTracker.remove(key);
                continue;
            }
            BlockPos pos = parsePos(key);
            if (pos == null) {
                PotionCauldronTracker.remove(key);
                continue;
            }
            if (!world.getBlockState(pos).isOf(Blocks.WATER_CAULDRON)) {
                // pos 已不是水锅：删标签、删条目
                UUID uuid = PotionCauldronTracker.getLabelUuid(key);
                if (uuid != null) {
                    Entity entity = world.getEntity(uuid);
                    if (entity != null) entity.discard();
                }
                PotionCauldronTracker.remove(key);
            }
        }
    }

    // ------------------------------------------------------------------

    private static NbtCompound buildDisplayNbt(BlockPos pos, NbtCompound potionNbt, int level) {
        NbtCompound nbt = new NbtCompound();
        // 位置（Entity.readNbt 会消费）
        var posList = new net.minecraft.nbt.NbtList();
        posList.add(net.minecraft.nbt.NbtDouble.of(labelX(pos)));
        posList.add(net.minecraft.nbt.NbtDouble.of(labelY(pos)));
        posList.add(net.minecraft.nbt.NbtDouble.of(labelZ(pos)));
        nbt.put("Pos", posList);
        // 无重力，防止漂移
        nbt.putBoolean("NoGravity", true);
        // TextDisplay 属性
        nbt.putString("billboard", "center");        // 永远面向玩家
        nbt.putString("alignment", "center");
        nbt.putByte("shadow", (byte) 1);              // 加阴影更清楚
        nbt.putInt("background", 0x40000000);         // 半透明黑底
        nbt.putFloat("view_range", 1.0f);             // 视距倍数（默认 1）
        // 文本
        Text text = buildText(potionNbt, level);
        nbt.putString("text", Text.Serializer.toJson(text));
        return nbt;
    }

    /** 生成显示文本：药水名（含时长）+ 剩余层数。*/
    private static Text buildText(NbtCompound potionNbt, int level) {
        MutableText line1 = potionName(potionNbt);
        MutableText line2 = Text.literal("剩余 " + level + " 层").formatted(Formatting.GRAY);
        return Text.empty()
            .append(line1)
            .append(Text.literal("\n"))
            .append(line2);
    }

    /**
     * 从存储的 NBT 反推药水显示名（含时长/等级）。
     * 优先用 vanilla PotionUtil.buildTooltip 一样的效果，但我们需要文本形式。
     */
    private static MutableText potionName(NbtCompound potionNbt) {
        // 用 vanilla Potion 注册表拿到药水基础名字，然后附加等级/时长
        Potion potion = PotionUtil.getPotion(potionNbt);
        List<net.minecraft.entity.effect.StatusEffectInstance> effects =
            PotionUtil.getPotionEffects(potionNbt);

        MutableText name;
        if (potion != null && potion != Registries.POTION.get(Registries.POTION.getDefaultId())) {
            // 有基础药水类型：用它的翻译 key
            var id = Registries.POTION.getId(potion);
            if (id != null) {
                name = Text.translatable("item.minecraft.potion.effect." + id.getPath())
                    .formatted(Formatting.LIGHT_PURPLE);
            } else {
                name = Text.literal("药水").formatted(Formatting.LIGHT_PURPLE);
            }
        } else if (!effects.isEmpty()) {
            var eff = effects.get(0);
            MutableText effName = Text.translatable(eff.getTranslationKey())
                .formatted(eff.getEffectType().getCategory().getFormatting());
            if (eff.getAmplifier() > 0) {
                effName.append(" ").append(Text.translatable(
                    "potion.potency." + eff.getAmplifier()));
            }
            name = effName;
        } else {
            name = Text.literal("水").formatted(Formatting.AQUA);
        }

        // 附加时长（若有 effect）
        if (!effects.isEmpty()) {
            int dur = effects.get(0).getDuration();
            if (dur > 20) {
                name.append(Text.literal(" (" + formatDuration(dur) + ")").formatted(Formatting.GRAY));
            }
        }
        return name;
    }

    private static String formatDuration(int ticks) {
        int seconds = ticks / 20;
        int m = seconds / 60;
        int s = seconds % 60;
        return String.format("%d:%02d", m, s);
    }

    // 标签位置：炼药锅方块中心正上方 1 格（视觉舒适）
    private static double labelX(BlockPos pos) { return pos.getX() + 0.5; }
    private static double labelY(BlockPos pos) { return pos.getY() + 1.3; }
    private static double labelZ(BlockPos pos) { return pos.getZ() + 0.5; }

    private static ServerWorld resolveWorld(MinecraftServer server, String key) {
        int colon = key.lastIndexOf(':');
        int firstColon = key.indexOf(':');
        if (colon < 0 || firstColon == colon) return null;
        String dimId = key.substring(0, colon);
        var id = net.minecraft.util.Identifier.tryParse(dimId);
        if (id == null) return null;
        var wk = net.minecraft.registry.RegistryKey.of(
            net.minecraft.registry.RegistryKeys.WORLD, id);
        return server.getWorld(wk);
    }

    private static BlockPos parsePos(String key) {
        int colon = key.lastIndexOf(':');
        if (colon < 0) return null;
        String coords = key.substring(colon + 1);
        String[] parts = coords.split(",");
        if (parts.length != 3) return null;
        try {
            return new BlockPos(
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
