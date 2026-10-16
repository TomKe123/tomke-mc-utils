package com.tomkestookit;

import com.google.common.collect.Multimap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Map;
import java.util.UUID;

/**
 * 处理反重力皮靴的两个被动效果：
 *   - 100% 跳跃加成：通过持续刷新 Jump Boost II（amplifier=1，即 2 级）实现，
 *     跳跃高度大约翻倍。effect 无粒子无 HUD 图标。
 *   - 75% 摔落减伤：在 ALLOW_DAMAGE 里拦截 FALL 类伤害，直接以 0.25 倍手动施加。
 */
public final class AntigravBootsHandler {

    public static final String NBT_TAG = "TomkesTookitAntigrav";
    private static final float FALL_DAMAGE_MULTIPLIER = 0.25f; // 75% 减伤
    private static final int JUMP_AMPLIFIER = 1;               // Jump Boost II
    private static final int JUMP_DURATION_TICKS = 40;         // 2 秒，覆盖刷新间隔
    private static final int REFRESH_INTERVAL_TICKS = 20;      // 每秒刷一次

    /** 速度属性 UUID —— 保持稳定，一件靴子上只挂一份，多次施加也不会叠加。*/
    private static final UUID SPEED_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final double SPEED_MULTIPLIER = 2.0; // MULTIPLY_TOTAL x3 移动速度

    private AntigravBootsHandler() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!TomkesTookitConfig.enabled("item.antigrav_boots")) return;
            if (server.getTicks() % REFRESH_INTERVAL_TICKS != 0) return;
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (isWearingAntigravBoots(player)) {
                    player.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.JUMP_BOOST,
                        JUMP_DURATION_TICKS,
                        JUMP_AMPLIFIER,
                        true,   // ambient：不显示强粒子效果
                        false,  // showParticles：不显示粒子
                        false   // showIcon：不显示 HUD 图标
                    ));
                }
            }
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!TomkesTookitConfig.enabled("item.antigrav_boots")) return true;
            if (!(entity instanceof ServerPlayerEntity player)) return true;
            if (!isFallDamage(source)) return true;
            if (!isWearingAntigravBoots(player)) return true;

            // 手动施加 25% 伤害，然后阻止原始伤害。用带绕过的伤害源避免再次被本 handler 拦截。
            float reduced = amount * FALL_DAMAGE_MULTIPLIER;
            if (reduced > 0.0f) {
                DamageSource bypass = player.getDamageSources().create(DamageTypes.GENERIC);
                player.damage(bypass, reduced);
            }
            return false;
        });
    }

    /**
     * 把反重力属性套到目标靴子上（就地修改）：
     *   - NBT 标记 TomkesTookitAntigrav = true（触发 tick 和摔落减伤）
     *   - 可选 Unbreakable = true（Kit 版使用；铁砧转移版不继承不可破坏）
     *   - AttributeModifiers：把该靴材质的 vanilla 默认属性（护甲/韧性/抗击退）
     *     复制到 NBT，再补一份速度 modifier；否则一旦 NBT 里出现
     *     AttributeModifiers，vanilla 的默认护甲会被完全覆盖。
     *
     * 目标必须是穿在脚上的 ArmorItem（EquipmentSlot.FEET）。
     */
    public static void applyAntigrav(ItemStack boots) {
        applyAntigrav(boots, true);
    }

    /**
     * 把反重力属性套到目标靴子上（就地修改）。
     * @param unbreakable 是否附加 Unbreakable=true；铁砧转移时传 false。
     */
    public static void applyAntigrav(ItemStack boots, boolean unbreakable) {
        NbtCompound nbt = boots.getOrCreateNbt();
        nbt.putBoolean(NBT_TAG, true);
        if (unbreakable) {
            nbt.putBoolean("Unbreakable", true);
        } else {
            nbt.remove("Unbreakable");
        }

        NbtList attrList = new NbtList();

        // 复制 vanilla armor 的默认属性
        Multimap<EntityAttribute, EntityAttributeModifier> vanilla =
            boots.getItem().getAttributeModifiers(EquipmentSlot.FEET);
        for (Map.Entry<EntityAttribute, EntityAttributeModifier> e : vanilla.entries()) {
            NbtCompound m = new NbtCompound();
            m.putString("AttributeName", Registries.ATTRIBUTE.getId(e.getKey()).toString());
            m.putString("Name", e.getValue().getName());
            m.putDouble("Amount", e.getValue().getValue());
            m.putInt("Operation", e.getValue().getOperation().getId());
            m.putString("Slot", "feet");
            m.put("UUID", NbtHelper.fromUuid(e.getValue().getId()));
            attrList.add(m);
        }

        // 追加速度 modifier
        NbtCompound speed = new NbtCompound();
        speed.putString("AttributeName", "generic.movement_speed");
        speed.putString("Name", "tomkestookit:speed");
        speed.putDouble("Amount", SPEED_MULTIPLIER);
        speed.putInt("Operation", 2); // MULTIPLY_TOTAL
        speed.putString("Slot", "feet");
        speed.put("UUID", NbtHelper.fromUuid(SPEED_UUID));
        attrList.add(speed);

        nbt.put("AttributeModifiers", attrList);
    }

    /** 判定物品是否已具备反重力效果（挂 NBT 即算）。*/
    public static boolean hasAntigrav(ItemStack stack) {
        if (stack.isEmpty()) return false;
        NbtCompound nbt = stack.getNbt();
        return nbt != null && nbt.getBoolean(NBT_TAG);
    }

    /** 判定物品是否是穿在脚上的靴子（任意 ArmorItem 且槽位是 FEET）。*/
    public static boolean isBoots(ItemStack stack) {
        return !stack.isEmpty()
            && stack.getItem() instanceof ArmorItem armor
            && armor.getSlotType() == EquipmentSlot.FEET;
    }

    /**
     * 3×3 合成：中心皮靴 + 四周 8 幻翼膜 → 反重力皮靴。
     * 中心已经是反重力靴时不匹配，避免白白吃掉材料。
     */
    public static boolean matches3x3(RecipeInputInventory input) {
        if (input.getWidth() != 3 || input.getHeight() != 3) return false;
        ItemStack center = input.getStack(4);
        if (center.getItem() != Items.LEATHER_BOOTS || hasAntigrav(center)) return false;
        int[] surround = {0, 1, 2, 3, 5, 6, 7, 8};
        for (int i : surround) if (input.getStack(i).getItem() != Items.PHANTOM_MEMBRANE) return false;
        return true;
    }

    /**
     * 合成产物：在中心皮靴的副本上施加反重力（保留原有附魔 / 染色 / 命名），
     * 没命名的补上"反重力皮靴"，没染色的染成和旧 Kit 版一样的红色。
     */
    public static ItemStack buildRecipeResult(RecipeInputInventory input) {
        ItemStack boots = input.getStack(4).copyWithCount(1);
        applyAntigrav(boots);
        if (!boots.hasCustomName()) {
            boots.setCustomName(Text.literal("反重力皮靴").formatted(Formatting.RED));
        }
        NbtCompound display = boots.getOrCreateSubNbt("display");
        if (!display.contains("color")) display.putInt("color", 0xFF0000);
        return boots;
    }

    /** 配方菜单展示用的产物示意。*/
    public static ItemStack buildDisplayStack() {
        ItemStack boots = new ItemStack(Items.LEATHER_BOOTS);
        applyAntigrav(boots);
        boots.setCustomName(Text.literal("反重力皮靴").formatted(Formatting.RED));
        boots.getOrCreateSubNbt("display").putInt("color", 0xFF0000);
        return boots;
    }

    private static boolean isWearingAntigravBoots(LivingEntity entity) {
        // 任意脚部装备只要挂着反重力 NBT 就算，兼容铁砧转移到其他靴子的情况。
        return hasAntigrav(entity.getEquippedStack(EquipmentSlot.FEET));
    }

    private static boolean isFallDamage(DamageSource source) {
        return source.isIn(net.minecraft.registry.tag.DamageTypeTags.IS_FALL);
    }
}
