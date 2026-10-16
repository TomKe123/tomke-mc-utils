package com.tomkestookit.reforge;

import com.tomkestookit.trinket.TrinketRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * 重铸效果热更新 + 已撤销重铸的旧数据清理：
 *
 * 修改 ReforgeRegistry 里某个 def 的 amount/operation 之后，玩家背包里已经打过这个重铸
 * 的旧装备不会自动获得新数值——因为属性是通过 AttributeModifier 存进 NBT 的。
 * 这个 handler 每 60 tick (3 秒) 扫玩家物品栏 + 末影箱，对每片装备上以 "tomkestookit:reforge_"
 * 命名的 modifier，用当前 ReforgeRegistry 的最新值覆盖。UUID 保持不变，Minecraft 会
 * 自然应用新的 Amount / Operation。
 *
 * 已从 ReforgeRegistry 移除的重铸（只剩经验汲取）：
 *   - 装备上对应的 modifier、APPLIED_TAG 条目、"⚒ 名称" lore 行一并删掉
 *   - 删完后若剩下的 AttributeModifiers 恰好等于 vanilla 默认值，整个 tag 去掉，装备回到原版
 *   - 遗留的重铸石本体（纯 vanilla 底材 + 本 mod NBT）清空 NBT，变回普通物品
 *   - 饰品系统同样已撤销：遗留饰品也按上一条处理
 * 装备上的附魔、自定义名称、其他 mod 的属性都不动。
 *
 * 只处理 tomkestookit 命名前缀的 modifier，不动 vanilla / mod 属性。
 * 有差异才写回，避免每次扫都触发 NBT dirty。
 * 清理不受 item.reforge 开关影响：关掉重铸系统也应该让旧属性失效。
 */
public final class ReforgeRefreshHandler {

    private static final int INTERVAL_TICKS = 60; // 3 秒
    private static final String MODIFIER_NAME_PREFIX = "tomkestookit:reforge_";

    private ReforgeRefreshHandler() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(ReforgeRefreshHandler::tick);
    }

    private static void tick(MinecraftServer server) {
        int ticks = server.getTicks();
        if (ticks % INTERVAL_TICKS != 0) return;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            // 物品栏：36 主栏 + 4 盔甲 + 副手
            refreshInventory(player.getInventory());
            refreshInventory(player.getEnderChestInventory());
        }
    }

    private static void refreshInventory(Inventory inv) {
        for (int slot = 0; slot < inv.size(); slot++) {
            ItemStack stack = inv.getStack(slot);
            if (stack.isEmpty()) continue;
            if (refreshStack(stack)) inv.markDirty();
        }
    }

    /** 对单个 ItemStack 做重铸 modifier 热更新 / 旧重铸清理。返回是否修改过。*/
    public static boolean refreshStack(ItemStack stack) {
        if (stack.isEmpty()) return false;
        NbtCompound nbt = stack.getNbt();
        if (nbt == null) return false;

        boolean changed = false;

        // 已撤销的饰品 / 重铸石本体：只剥本 mod 生成的标记（id、贴图、名称、lore、饰品的假附魔光效），
        // 玩家后来加上的附魔、耐久、经验汲取等原样保留（横扫系重铸石的底材就是真剑）。
        if (TrinketRegistry.getId(nbt) != null) {
            stripGeneratedTags(nbt, TrinketRegistry.NBT_KEY);
            nbt.remove(TrinketRegistry.NBT_DISABLED);
            stripFakeGlint(nbt);
            changed = true;
        } else {
            String stoneId = nbt.getString(ReforgeRegistry.NBT_KEY);
            if (!stoneId.isEmpty() && ReforgeRegistry.byId(stoneId) == null) {
                stripGeneratedTags(nbt, ReforgeRegistry.NBT_KEY);
                changed = true;
            }
        }

        if (nbt.contains(ReforgeRegistry.APPLIED_TAG)) {
            changed |= stripApplied(stack, nbt);
        }

        if (changed) stack.setNbt(nbt.isEmpty() ? null : nbt);
        return changed;
    }

    /** 去掉生成物品时写入的 id / CustomModelData / 自定义名 / lore。*/
    private static void stripGeneratedTags(NbtCompound nbt, String idKey) {
        nbt.remove(idKey);
        nbt.remove("CustomModelData");
        nbt.remove("display");
    }

    /** 饰品 buildStack 加的"耐久 I + HideFlags 1"假附魔；只有完全一致时才去掉，避免误删真附魔。*/
    private static void stripFakeGlint(NbtCompound nbt) {
        if (nbt.getInt("HideFlags") != 1) return;
        NbtList ench = nbt.getList("Enchantments", NbtElement.COMPOUND_TYPE);
        if (ench.size() != 1) return;
        NbtCompound e = ench.getCompound(0);
        if (!"minecraft:unbreaking".equals(e.getString("id")) || e.getInt("lvl") != 1) return;
        nbt.remove("Enchantments");
        nbt.remove("HideFlags");
    }

    /** 处理装备上已应用的重铸：热更新仍注册的，删除已撤销的。返回是否修改过。*/
    private static boolean stripApplied(ItemStack stack, NbtCompound nbt) {
        boolean changed = stripRemovedApplied(nbt);
        changed |= stripRemovedLore(nbt);

        if (nbt.contains("AttributeModifiers", NbtElement.LIST_TYPE)) {
            NbtList attrs = nbt.getList("AttributeModifiers", NbtElement.COMPOUND_TYPE);
            boolean attrsChanged = false;

            for (int i = attrs.size() - 1; i >= 0; i--) {
                NbtCompound m = attrs.getCompound(i);
                String name = m.getString("Name");
                if (!name.startsWith(MODIFIER_NAME_PREFIX)) continue;
                String reforgeId = name.substring(MODIFIER_NAME_PREFIX.length());
                ReforgeRegistry.ReforgeDef def = ReforgeRegistry.byId(reforgeId);
                if (def == null) {
                    attrs.remove(i);
                    attrsChanged = true;
                    continue;
                }

                // 拿现有值
                double oldAmount = m.getDouble("Amount");
                int oldOp = m.getInt("Operation");
                String oldAttr = m.getString("AttributeName");

                boolean mismatch = Math.abs(oldAmount - def.amount()) > 1e-6
                    || oldOp != def.operation()
                    || !def.attribute().equals(oldAttr);
                if (!mismatch) continue;

                m.putDouble("Amount", def.amount());
                m.putInt("Operation", def.operation());
                m.putString("AttributeName", def.attribute());
                attrsChanged = true;
            }

            if (attrsChanged) {
                // 剩下的若只是 applyReforge 当初复制进来的 vanilla 默认属性，就整个去掉
                if (ReforgeRegistry.isVanillaDefaults(stack, attrs)) nbt.remove("AttributeModifiers");
                else nbt.put("AttributeModifiers", attrs);
                changed = true;
            }
        }
        return changed;
    }

    /** 从 APPLIED_TAG 去掉已不存在的重铸 id；列表空了就删 tag。*/
    private static boolean stripRemovedApplied(NbtCompound nbt) {
        NbtList applied = nbt.getList(ReforgeRegistry.APPLIED_TAG, NbtElement.STRING_TYPE);
        boolean changed = false;
        for (int i = applied.size() - 1; i >= 0; i--) {
            if (ReforgeRegistry.byId(applied.getString(i)) == null) {
                applied.remove(i);
                changed = true;
            }
        }
        if (applied.isEmpty()) {
            nbt.remove(ReforgeRegistry.APPLIED_TAG);
            return true;
        }
        if (changed) nbt.put(ReforgeRegistry.APPLIED_TAG, applied);
        return changed;
    }

    /** 删掉 applyReforge 追加的 "§6⚒ 名称" lore 行（仍注册的重铸保留）；lore / display 空了一并删。*/
    private static boolean stripRemovedLore(NbtCompound nbt) {
        if (!nbt.contains("display", NbtElement.COMPOUND_TYPE)) return false;
        NbtCompound display = nbt.getCompound("display");
        if (!display.contains("Lore", NbtElement.LIST_TYPE)) return false;
        NbtList lore = display.getList("Lore", NbtElement.STRING_TYPE);
        boolean changed = false;
        for (int i = lore.size() - 1; i >= 0; i--) {
            String line;
            try {
                Text t = Text.Serializer.fromJson(lore.getString(i));
                line = t == null ? "" : t.getString();
            } catch (Exception e) {
                continue;
            }
            if (!line.startsWith(ReforgeRegistry.APPLIED_LORE_PREFIX)) continue;
            String name = line.substring(ReforgeRegistry.APPLIED_LORE_PREFIX.length());
            if (ReforgeRegistry.byName(name) != null) continue;
            lore.remove(i);
            changed = true;
        }
        if (!changed) return false;
        if (lore.isEmpty()) display.remove("Lore");
        else display.put("Lore", lore);
        if (display.isEmpty()) nbt.remove("display");
        return true;
    }
}
