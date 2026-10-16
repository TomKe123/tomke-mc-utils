package com.tomkestookit.reforge;

import com.tomkestookit.materials.CustomModelDataRegistry;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.text.Text;

import java.util.*;
import java.util.function.Predicate;

/**
 * 重铸石注册表：每种石头定义 id / 中文名 / 属性 / 适用装备 / 铁砧费用 / 文案。
 *
 * 兼容性设计：
 *   - 不覆盖现有 AttributeModifiers，而是追加到已有列表末尾
 *   - 每块石头有唯一 UUID，同一石头不会叠加（检测已有 UUID）
 *   - 适用范围用 Predicate<ItemStack> 判定，兼容任何 mod 的装备
 *   - 不限制装备必须是 vanilla Item
 */
public final class ReforgeRegistry {

    public static final String NBT_KEY = "TomkesTookitReforgeStone";
    public static final String APPLIED_TAG = "TomkesTookitReforges";
    /** applyReforge 追加到装备 lore 的前缀（"§6⚒ " + 名称）。*/
    public static final String APPLIED_LORE_PREFIX = "§6⚒ ";

    public record ReforgeDef(
        String id, String name, Item icon,
        String attribute, double amount, int operation, String slot,
        UUID uuid, int anvilCost,
        Predicate<ItemStack> canApply,
        List<String> lore,
        Item recipeCenter, Item recipeSurround
    ) {}

    private static final List<ReforgeDef> ALL = new ArrayList<>();

    static {
        // 其余重铸石已撤销回原版（见 ReforgeRefreshHandler 的旧数据清理）；只保留经验汲取。

        // 中心 experience_bottle（图标），围 8 lapis_block → "经验=青金石=附魔"
        // 特殊重铸石：不加属性（amount=0），tick 逻辑在 AutoMenderHandler 里处理
        reg("soul_mend", "经验汲取", Items.EXPERIENCE_BOTTLE,
            "generic.luck", 0.0, 0, "any",
            "b1a00001-0000-0000-0000-00000000000c", 25,
            s -> true, // 任何装备都可以
            List.of("§8「经验是最好的修理工。」",
                "§7装备损坏时自动消耗经验点修复",
                "§7每 0.2 秒修最多 §f8 §7点耐久",
                "§8只扣经验条绿色部分，扣完顶到 0 就停",
                "§5史诗",
                "§7适用于：§f任意可装备物品"),
            Items.EXPERIENCE_BOTTLE, Items.LAPIS_BLOCK);
    }

    public static List<ReforgeDef> all() { return Collections.unmodifiableList(ALL); }

    public static ReforgeDef byId(String id) {
        for (ReforgeDef d : ALL) if (d.id.equals(id)) return d;
        return null;
    }

    public static ReforgeDef byName(String name) {
        for (ReforgeDef d : ALL) if (d.name.equals(name)) return d;
        return null;
    }

    /** 生成重铸石 ItemStack。*/
    public static ItemStack buildStack(ReforgeDef def) {
        ItemStack stack = new ItemStack(def.icon);
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putString(NBT_KEY, def.id);
        // CustomModelData 用于资源包贴图识别
        int cmd = CustomModelDataRegistry.forReforge(def.id);
        if (cmd != 0) nbt.putInt("CustomModelData", cmd);
        stack.setCustomName(Text.literal("§6§l" + def.name + " §7[重铸石]").styled(s -> s.withItalic(false)));
        NbtCompound display = nbt.getCompound("display");
        NbtList lore = new NbtList();
        for (String line : def.lore) {
            lore.add(NbtString.of(Text.Serializer.toJson(
                Text.literal(line).styled(s -> s.withItalic(false)))));
        }
        lore.add(NbtString.of(Text.Serializer.toJson(
            Text.literal("§6重铸石 §8- 铁砧 + 装备使用").styled(s -> s.withItalic(false)))));
        lore.add(NbtString.of(Text.Serializer.toJson(
            Text.literal("§8铁砧费用 " + def.anvilCost + " 级").styled(s -> s.withItalic(false)))));
        display.put("Lore", lore);
        nbt.put("display", display);

        // 追加品质 + 获取途径
        com.tomkestookit.materials.ItemQualityTag.append(stack,
            reforgeQuality(def.id),
            reforgeSource(def.id));
        return stack;
    }

    /** 根据 reforge id 推断品质。*/
    private static com.tomkestookit.materials.ItemQualityTag.Quality reforgeQuality(String id) {
        if ("soul_mend".equals(id)) return com.tomkestookit.materials.ItemQualityTag.Quality.EPIC;
        return com.tomkestookit.materials.ItemQualityTag.Quality.COMMON;
    }

    /** 根据 reforge id 描述获取途径。*/
    private static String reforgeSource(String id) {
        return "3×3 合成 §8· §7铁砧敲装备生效";
    }

    /** 从 ItemStack 读取重铸石 id。非重铸石返回 null。*/
    public static String getId(ItemStack stack) {
        if (stack.isEmpty()) return null;
        NbtCompound nbt = stack.getNbt();
        if (nbt == null || !nbt.contains(NBT_KEY)) return null;
        return nbt.getString(NBT_KEY);
    }

    /**
     * 检查装备上是否已有某块重铸石。
     * 优先看 APPLIED_TAG（id 字符串），命中直接判定已应用；
     * 再回退到 UUID 检测（同时也检查 per-slot 派生 UUID）。
     */
    public static boolean alreadyApplied(ItemStack equipment, ReforgeDef def) {
        NbtCompound nbt = equipment.getNbt();
        if (nbt == null) return false;
        // 优先：applied id 列表
        if (nbt.contains(APPLIED_TAG)) {
            NbtList applied = nbt.getList(APPLIED_TAG, 8);
            for (int i = 0; i < applied.size(); i++) {
                if (def.id.equals(applied.getString(i))) return true;
            }
        }
        // 回退：UUID（涵盖旧数据 + per-slot 派生 UUID）
        if (!nbt.contains("AttributeModifiers")) return false;
        NbtList attrs = nbt.getList("AttributeModifiers", 10);
        String slotStr = resolveSlot(def, equipment);
        UUID slotUuid = perSlotUuid(def, slotStr);
        for (int i = 0; i < attrs.size(); i++) {
            NbtCompound m = attrs.getCompound(i);
            if (m.contains("UUID")) {
                try {
                    UUID existing = NbtHelper.toUuid(m.get("UUID"));
                    if (def.uuid.equals(existing) || slotUuid.equals(existing)) return true;
                } catch (Exception ignored) { /* 非标准 UUID 格式，跳过 */ }
            }
        }
        return false;
    }

    /**
     * 派生 per-slot UUID：对 any_armor 类重铸石，让不同 slot 得到不同 UUID，
     * 使得同一 def 可以叠加在多件护甲上（修复泰坦铭板等只能生效一次的 BUG）。
     * 对 mainhand / offhand / feet 等固定 slot 定义，返回原始 UUID 保持兼容。
     */
    private static UUID perSlotUuid(ReforgeDef def, String slotStr) {
        if (!"any_armor".equals(def.slot) && !"any".equals(def.slot)) {
            return def.uuid;
        }
        // 只对 4 件护甲 slot 派生；其他情况返回原 UUID
        return switch (slotStr) {
            case "head"  -> deriveUuid(def.uuid, 1);
            case "chest" -> deriveUuid(def.uuid, 2);
            case "legs"  -> deriveUuid(def.uuid, 3);
            case "feet"  -> deriveUuid(def.uuid, 4);
            default      -> def.uuid;
        };
    }

    /** 从 base UUID 派生一个稳定的变体（异或 low 64 位的一个盐）。*/
    private static UUID deriveUuid(UUID base, int salt) {
        return new UUID(base.getMostSignificantBits(), base.getLeastSignificantBits() ^ ((long) salt * 0x9E3779B97F4A7C15L));
    }

    /** 把重铸石属性追加到装备的 AttributeModifiers。*/
    public static ItemStack applyReforge(ItemStack equipment, ReforgeDef def) {
        ItemStack result = equipment.copy();
        NbtCompound nbt = result.getOrCreateNbt();

        // 如果装备还没有 NBT AttributeModifiers，先把 vanilla 默认属性复制进去
        if (!nbt.contains("AttributeModifiers")) {
            NbtList defaultAttrs = buildVanillaDefaults(equipment);
            nbt.put("AttributeModifiers", defaultAttrs);
        }

        NbtList attrs = nbt.getList("AttributeModifiers", 10);

        // 确定 slot 字符串
        String slotStr = resolveSlot(def, equipment);

        // 对 any_armor 类重铸石，把 UUID 按 slot 派生成不同值，
        // 避免同一 def 在多件装备上因 UUID 相同而被 Minecraft 去重（叠加修复）。
        UUID modifierUuid = perSlotUuid(def, slotStr);

        NbtCompound modifier = new NbtCompound();
        modifier.putString("AttributeName", def.attribute);
        modifier.putString("Name", "tomkestookit:reforge_" + def.id);
        modifier.putDouble("Amount", def.amount);
        modifier.putInt("Operation", def.operation);
        modifier.putString("Slot", slotStr);
        modifier.put("UUID", NbtHelper.fromUuid(modifierUuid));
        attrs.add(modifier);

        nbt.put("AttributeModifiers", attrs);
        // 记录已应用的重铸到一个标记列表（给 lore 展示用）
        NbtList applied = nbt.contains(APPLIED_TAG) ? nbt.getList(APPLIED_TAG, 8) : new NbtList();
        applied.add(NbtString.of(def.id));
        nbt.put(APPLIED_TAG, applied);

        // 追加 lore
        NbtCompound display = nbt.getCompound("display");
        NbtList lore = display.contains("Lore") ? display.getList("Lore", 8) : new NbtList();
        lore.add(NbtString.of(Text.Serializer.toJson(
            Text.literal(APPLIED_LORE_PREFIX + def.name).styled(s -> s.withItalic(false)))));
        display.put("Lore", lore);
        nbt.put("display", display);

        return result;
    }

    // ---- helpers ----

    private static void reg(String id, String name, Item icon,
                            String attribute, double amount, int operation, String slot,
                            String uuid, int anvilCost,
                            Predicate<ItemStack> canApply, List<String> lore,
                            Item recipeCenter, Item recipeSurround) {
        ALL.add(new ReforgeDef(id, name, icon, attribute, amount, operation, slot,
            UUID.fromString(uuid), anvilCost, canApply, lore,
            recipeCenter, recipeSurround));
    }

    /** 匹配 3x3 输入是否为某个重铸石配方。命中返回 def，否则 null。*/
    public static ReforgeDef match3x3(net.minecraft.inventory.RecipeInputInventory inv) {
        if (inv.getWidth() != 3 || inv.getHeight() != 3) return null;
        for (ReforgeDef def : ALL) {
            if (matches(inv, def)) return def;
        }
        return null;
    }

    private static boolean matches(net.minecraft.inventory.RecipeInputInventory inv, ReforgeDef def) {
        ItemStack center = inv.getStack(4);
        if (center.isEmpty()) return false;
        // 排除已经是重铸石的（避免用一个重铸石当中心去合另一个）
        if (getId(center) != null) return false;

        // 中心：vanilla Item 匹配
        if (center.getItem() != def.recipeCenter) return false;

        // 外围 8 格：vanilla Item 匹配
        int[] surround = {0, 1, 2, 3, 5, 6, 7, 8};
        for (int i : surround) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty() || s.getItem() != def.recipeSurround) return false;
        }
        return true;
    }

    public static boolean isWeapon(ItemStack stack) {
        return stack.getItem() instanceof SwordItem
            || stack.getItem() instanceof AxeItem
            || stack.getItem() instanceof TridentItem;
    }

    public static boolean isArmor(ItemStack stack) {
        return stack.getItem() instanceof ArmorItem;
    }

    public static boolean isTool(ItemStack stack) {
        return stack.getItem() instanceof ToolItem;
    }

    private static String resolveSlot(ReforgeDef def, ItemStack equipment) {
        if ("any_armor".equals(def.slot) || "any".equals(def.slot)) {
            if (equipment.getItem() instanceof ArmorItem armor) {
                return switch (armor.getSlotType()) {
                    case HEAD -> "head";
                    case CHEST -> "chest";
                    case LEGS -> "legs";
                    case FEET -> "feet";
                    default -> "chest";
                };
            }
            // "any" 允许非防具，走 mainhand
            if ("any".equals(def.slot)) return "mainhand";
            return "chest";
        }
        return def.slot;
    }

    /**
     * attrs 是否与 buildVanillaDefaults 生成的列表完全一致（UUID / 属性 / 数值 / 运算 / 槽位），
     * 即去掉重铸后 NBT 里只剩当初复制进来的原版默认属性。
     */
    public static boolean isVanillaDefaults(ItemStack stack, NbtList attrs) {
        NbtList defaults = buildVanillaDefaults(stack);
        if (defaults.size() != attrs.size()) return false;
        for (int i = 0; i < defaults.size(); i++) {
            NbtCompound d = defaults.getCompound(i);
            boolean found = false;
            for (int j = 0; j < attrs.size(); j++) {
                if (sameModifier(d, attrs.getCompound(j))) { found = true; break; }
            }
            if (!found) return false;
        }
        return true;
    }

    private static boolean sameModifier(NbtCompound a, NbtCompound b) {
        if (!Objects.equals(a.get("UUID"), b.get("UUID"))) return false;
        if (!normalizeAttr(a.getString("AttributeName")).equals(normalizeAttr(b.getString("AttributeName")))) return false;
        if (Math.abs(a.getDouble("Amount") - b.getDouble("Amount")) > 1e-6) return false;
        if (a.getInt("Operation") != b.getInt("Operation")) return false;
        return a.getString("Slot").equals(b.getString("Slot"));
    }

    private static String normalizeAttr(String id) {
        return id.contains(":") ? id : "minecraft:" + id;
    }

    /** 把装备 vanilla 默认属性序列化为 NbtList（防止 NBT 覆盖后丢默认护甲等）。*/
    private static NbtList buildVanillaDefaults(ItemStack stack) {
        NbtList list = new NbtList();
        EquipmentSlot slot;
        String slotStr;
        if (stack.getItem() instanceof ArmorItem armor) {
            slot = armor.getSlotType();
            slotStr = switch (slot) {
                case HEAD -> "head"; case CHEST -> "chest";
                case LEGS -> "legs"; case FEET -> "feet"; default -> "chest";
            };
        } else {
            // 武器 / 工具 / 三叉戟 / mod 添加的手持物：统一走主手
            // 覆盖所有非 ToolItem 但仍带默认属性的物品（如 TridentItem）
            slot = EquipmentSlot.MAINHAND;
            slotStr = "mainhand";
        }
        var map = stack.getItem().getAttributeModifiers(slot);
        for (var entry : map.entries()) {
            NbtCompound m = new NbtCompound();
            m.putString("AttributeName",
                net.minecraft.registry.Registries.ATTRIBUTE.getId(entry.getKey()).toString());
            m.putString("Name", entry.getValue().getName());
            m.putDouble("Amount", entry.getValue().getValue());
            m.putInt("Operation", entry.getValue().getOperation().getId());
            m.putString("Slot", slotStr);
            m.put("UUID", NbtHelper.fromUuid(entry.getValue().getId()));
            list.add(m);
        }
        return list;
    }
}
