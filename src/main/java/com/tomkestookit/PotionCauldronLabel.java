package com.tomkestookit;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

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
 * 兜底：区块加载时检查该区块内记录；周期清理只处理已加载区块。
 * 未加载区块绝不 getBlockState，避免把远处有内容的锅误判成孤儿。
 *
 * 用 Entity.readNbt() 通过 NBT 全量配置实体，避开 TextDisplayEntity 私有 setter。
 */
public final class PotionCauldronLabel {

    /** 周期扫尾间隔（tick）。5 分钟一次。*/
    private static final int CLEANUP_INTERVAL_TICKS = 20 * 60 * 5;

    private PotionCauldronLabel() {}

    /**
     * 注册两类兜底清理：
     *   - 区块加载时：只清该区块里的记录（玩家靠近/服务器加载区块时立即修正）
     *   - 每 5 分钟：扫一遍 tracker，但只处理已加载区块；未加载区块跳过，避免误删
     */
    public static void register() {
        ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            if (!TomkesTookitConfig.enabled("item.potion_cauldron")) return;
            cleanupChunk(world, chunk);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!TomkesTookitConfig.enabled("item.potion_cauldron")) return;
            if (server.getTicks() % CLEANUP_INTERVAL_TICKS != 0) return;
            cleanupLoadedOrphans(server);
        });
    }

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

    /** 启动时/周期时的兜底清理：只处理已加载区块，未加载区块跳过。*/
    public static void cleanupLoadedOrphans(MinecraftServer server) {
        var snapshot = PotionCauldronTracker.allEntries();
        for (var e : snapshot.entrySet()) {
            String key = e.getKey();
            ServerWorld world = resolveWorld(server, key);
            if (world == null) {
                PotionCauldronTracker.remove(key);
                continue;
            }
            BlockPos pos = parsePos(key);
            if (pos == null) {
                PotionCauldronTracker.remove(key);
                continue;
            }
            // 关键：未加载区块跳过，绝不通过 world.getBlockState 触发同步等区块
            if (!isChunkLoaded(world, pos)) continue;
            WorldChunk chunk = world.getChunkManager().getWorldChunk(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) continue;
            cleanupIfInvalid(world, key, pos, chunk);
        }
    }

    /** 区块加载时，只检查该区块内的 potion cauldron 记录。*/
    public static void cleanupChunk(ServerWorld world, WorldChunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        String dim = world.getRegistryKey().getValue().toString();
        var snapshot = PotionCauldronTracker.allEntries();
        for (var e : snapshot.entrySet()) {
            String key = e.getKey();
            if (!key.startsWith(dim + ":")) continue;
            BlockPos pos = parsePos(key);
            if (pos == null) {
                PotionCauldronTracker.remove(key);
                continue;
            }
            if (pos.getX() >> 4 != chunkPos.x || pos.getZ() >> 4 != chunkPos.z) continue;
            cleanupIfInvalid(world, key, pos, chunk);
        }
    }

    /** pos 已确认处于传入 chunk 内，检查是否仍是药水锅；不是则删标签+删 entry。*/
    private static void cleanupIfInvalid(ServerWorld world, String key, BlockPos pos, WorldChunk chunk) {
        if (chunk.getBlockState(pos).isOf(Blocks.WATER_CAULDRON)) return;
        UUID uuid = PotionCauldronTracker.getLabelUuid(key);
        if (uuid != null) {
            Entity entity = world.getEntity(uuid);
            if (entity != null) entity.discard();
        }
        PotionCauldronTracker.remove(key);
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

    /**
     * 生成显示文本：多效果时逐行列出，末尾一行是剩余层数。
     *   力量 II (3:00)
     *   跳跃提升 (1:30)
     *   剩余 2 层
     *
     * 无效果场景（base = water / mundane / awkward / thick 或裸 vanilla 药水）：
     *   直接读 base Potion 的显示名，退回 "水"。之前一刀切显示"水"是显示 BUG 的根因。
     */
    private static Text buildText(NbtCompound potionNbt, int level) {
        List<net.minecraft.entity.effect.StatusEffectInstance> effects =
            PotionUtil.getPotionEffects(potionNbt);

        MutableText result = Text.empty();
        if (effects.isEmpty()) {
            // 无 effect：显示 base Potion 名（"水" / "粗制的药水" / "浑浊的药水" / "浓稠的药水" 等）
            result.append(basePotionName(potionNbt));
        } else if (effects.size() == 1) {
            // 单效果：优先用 base Potion 名（"力量药水"）而非效果名（"力量"）
            result.append(potionName(potionNbt, effects.get(0)));
        } else {
            // 多效果：逐个列 effect 名 + 时长
            for (int i = 0; i < effects.size(); i++) {
                if (i > 0) result.append(Text.literal("\n"));
                result.append(effectLine(effects.get(i)));
            }
        }
        result.append(Text.literal("\n"))
            .append(Text.literal("剩余 " + level + " 层").formatted(Formatting.GRAY));
        return result;
    }

    /**
     * 无效果药水的显示名：读 base Potion → 用其 translation key，
     * 找不到就退回"水"。这样 mundane / thick / awkward 都能显示成对应名字。
     */
    private static MutableText basePotionName(NbtCompound potionNbt) {
        Potion potion = PotionUtil.getPotion(potionNbt);
        if (potion == null) return Text.literal("水").formatted(Formatting.AQUA);
        var id = Registries.POTION.getId(potion);
        if (id == null) return Text.literal("水").formatted(Formatting.AQUA);
        String path = id.getPath();
        // vanilla 药水 translation key：item.minecraft.potion.effect.<path>
        return Text.translatable("item.minecraft.potion.effect." + path).formatted(Formatting.AQUA);
    }

    /** 单效果行：效果名 + 强度 + 时长。*/
    private static MutableText effectLine(net.minecraft.entity.effect.StatusEffectInstance eff) {
        MutableText line = Text.translatable(eff.getTranslationKey())
            .formatted(eff.getEffectType().getCategory().getFormatting());
        if (eff.getAmplifier() > 0) {
            line.append(" ").append(Text.translatable("potion.potency." + eff.getAmplifier()));
        }
        int dur = eff.getDuration();
        if (dur > 20) {
            line.append(Text.literal(" (" + formatDuration(dur) + ")").formatted(Formatting.GRAY));
        }
        return line;
    }

    /**
     * 单效果药水的显示名：有 base Potion 用配方名（"力量药水"），否则退回效果名。
     * 尾部附加时长。
     */
    private static MutableText potionName(NbtCompound potionNbt,
                                          net.minecraft.entity.effect.StatusEffectInstance eff) {
        Potion potion = PotionUtil.getPotion(potionNbt);
        MutableText name;
        if (potion != null && potion != Registries.POTION.get(Registries.POTION.getDefaultId())) {
            var id = Registries.POTION.getId(potion);
            if (id != null) {
                name = Text.translatable("item.minecraft.potion.effect." + id.getPath())
                    .formatted(Formatting.LIGHT_PURPLE);
            } else {
                name = Text.literal("药水").formatted(Formatting.LIGHT_PURPLE);
            }
        } else {
            // 无 base Potion：用效果名 + 强度
            name = Text.translatable(eff.getTranslationKey())
                .formatted(eff.getEffectType().getCategory().getFormatting());
            if (eff.getAmplifier() > 0) {
                name.append(" ").append(Text.translatable(
                    "potion.potency." + eff.getAmplifier()));
            }
        }
        int dur = eff.getDuration();
        if (dur > 20) {
            name.append(Text.literal(" (" + formatDuration(dur) + ")").formatted(Formatting.GRAY));
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

    private static boolean isChunkLoaded(ServerWorld world, BlockPos pos) {
        return world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4);
    }

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
