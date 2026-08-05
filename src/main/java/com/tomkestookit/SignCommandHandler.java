package com.tomkestookit;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.DyeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SignItem;
import net.minecraft.item.HangingSignItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 告示牌指令绑定：
 *
 *   - 玩家 主手拿告示牌 + shift+F → 进入“聊天输入命令”模式
 *   - 玩家下一条聊天消息（60 秒内）会被拦截当作命令内容
 *   - 通过白名单前缀校验后：写到手上告示牌 stack 的 NBT (STACK_PENDING_KEY)
 *   - 玩家放下这块告示牌 → SignItem 放置钩子把 stack 上的 pending 命令写到
 *     SignBlockEntity + 自动蜡封
 *   - 任意玩家右键该告示牌 → 以自己身份执行绑定命令
 *   - 主/副手拿染料 / 发光墨囊 / 液体墨囊 / 蜂巢 时，右键让 vanilla 处理
 *
 * 前缀白名单：只允许项目内已开放的全员可用命令，防止玩家用 /op /gamemode 之类。
 */
public final class SignCommandHandler {

    /** 告示牌 BlockEntity 上存的最终命令。*/
    public static final String NBT_KEY = "TomkesTookitCommand";
    /** 告示牌 item stack 上的 pending 命令 / 自定义 label，放置时迁移到 BE。*/
    public static final String STACK_PENDING_KEY = "TomkesTookitPendingCommand";
    public static final String STACK_PENDING_LABEL = "TomkesTookitPendingLabel";
    /** 任务组告示牌的 block entity / item stack 数据。*/
    public static final String TASK_GROUP_NBT_KEY = "TomkesTookitTaskGroup";
    public static final String TASK_GROUP_NAME_NBT_KEY = "TomkesTookitTaskGroupName";

    public static final List<String> ALLOWED_PREFIXES = buildAllowedPrefixes();

    private static List<String> buildAllowedPrefixes() {
        java.util.ArrayList<String> list = new java.util.ArrayList<>();
        // 项目自身开放的
        list.add("back");
        list.add("cd");
        list.add("fly");
        list.add("kit");
        list.add("landmark");
        list.add("tomkestookit");
        list.add("veinmine");
        // CommandPermission 里放开的 vanilla
        list.add("tp");
        list.add("teleport");
        list.add("seed");
        list.add("locate");
        // CarpetPermission 里放开的 Carpet 全套
        java.util.Collections.addAll(list, CarpetPermission.CARPET_COMMANDS);
        return java.util.List.copyOf(list);
    }

    /** 每玩家的临时冷却，防止误触重复右键（500ms）。*/
    private static final long COOLDOWN_MS = 500;
    private static final Map<UUID, Long> LAST_USE = new HashMap<>();

    /** 聊天绑定超时（毫秒）。60 秒内玩家没发聊天 → 状态自动清空。*/
    private static final long BIND_TIMEOUT_MS = 60_000;

    /** 绑定两阶段：先输入命令，再输入自定义标签文字。*/
    public enum BindStage { AWAIT_COMMAND, AWAIT_LABEL }

    /** 正在等待玩家在聊天里输入命令/标签的绑定请求。*/
    public static final class PendingBind {
        public BindStage stage;
        public String cmd; // stage == AWAIT_LABEL 时已填
        public long expiresAt;

        PendingBind(BindStage stage, long expiresAt) {
            this.stage = stage;
            this.expiresAt = expiresAt;
        }
    }

    private static final Map<UUID, PendingBind> PENDING = new HashMap<>();

    private SignCommandHandler() {}

    public static void register() {
        UseBlockCallback.EVENT.register(SignCommandHandler::onUseBlock);
    }

    // ------------------------------------------------------------------ shift+F 入口

    /** ServerPlayNetworkHandlerMixin 的 shift+F 处理调过来。返回 true 表示我们消费了。*/
    public static boolean tryStartItemBinding(ServerPlayerEntity player) {
        if (!TomkesTookitConfig.enabled("item.sign_command")) return false;
        ItemStack main = player.getMainHandStack();
        if (!isSignItem(main)) return false;
        NbtCompound nbt = main.getNbt();
        if (nbt != null && nbt.contains(STACK_PENDING_KEY)) {
            player.sendMessage(Text.literal("§7[告示牌] 当前告示牌已绑定：§f/" + nbt.getString(STACK_PENDING_KEY))
                .formatted(Formatting.GRAY), false);
        }
        player.sendMessage(Text.literal("§b[告示牌] §7第 1 步：在聊天栏输入要绑定的命令（60 秒内）")
            .formatted(Formatting.AQUA), false);
        player.sendMessage(Text.literal("§7允许前缀：§f" + String.join(" ", ALLOWED_PREFIXES)).formatted(Formatting.GRAY), false);
        player.sendMessage(Text.literal("§7例：§fback§7、§f/landmark tp \"家\"§7、§ftp Alice").formatted(Formatting.GRAY), false);
        player.sendMessage(Text.literal("§8发送 §fcancel §8或 §f取消 §8可放弃绑定").formatted(Formatting.DARK_GRAY), false);

        PENDING.put(player.getUuid(),
            new PendingBind(BindStage.AWAIT_COMMAND, System.currentTimeMillis() + BIND_TIMEOUT_MS));
        return true;
    }

    public static boolean hasPending(UUID uuid) {
        return PENDING.containsKey(uuid);
    }

    /** mixin 从 onChatMessage 里调过来。*/
    public static boolean tryConsumeChat(ServerPlayerEntity player, String message) {
        PendingBind bind = PENDING.get(player.getUuid());
        if (bind == null) return false;
        if (System.currentTimeMillis() > bind.expiresAt) {
            PENDING.remove(player.getUuid());
            player.sendMessage(Text.literal("§c[告示牌] 绑定超时，请重新 shift+F。").formatted(Formatting.RED), false);
            return true;
        }
        String msg = message == null ? "" : message.strip();
        if (msg.isEmpty() || msg.equalsIgnoreCase("cancel") || msg.equals("取消")) {
            PENDING.remove(player.getUuid());
            player.sendMessage(Text.literal("§7[告示牌] 已取消绑定。").formatted(Formatting.GRAY), false);
            return true;
        }

        if (bind.stage == BindStage.AWAIT_COMMAND) {
            String cmd = normalize(msg);
            if (cmd == null) {
                PENDING.remove(player.getUuid());
                int sp = msg.startsWith("/") ? msg.indexOf(' ', 1) : msg.indexOf(' ');
                String head = sp < 0 ? (msg.startsWith("/") ? msg.substring(1) : msg) : msg.substring(msg.startsWith("/") ? 1 : 0, sp);
                player.sendMessage(Text.literal("§c[告示牌] 命令不在允许列表：/" + head).formatted(Formatting.RED), false);
                return true;
            }
            // 命令 OK，进入第二阶段：等标签
            bind.cmd = cmd;
            bind.stage = BindStage.AWAIT_LABEL;
            bind.expiresAt = System.currentTimeMillis() + BIND_TIMEOUT_MS;
            player.sendMessage(Text.literal("§a[告示牌] 命令已收到：§f/" + cmd).formatted(Formatting.GREEN), false);
            player.sendMessage(Text.literal("§b[告示牌] §7第 2 步：在聊天栏输入自定义文字（60 秒内）")
                .formatted(Formatting.AQUA), false);
            player.sendMessage(Text.literal("§8显示在告示牌第 2 行，第 1 行固定为 §f[命令告示牌]").formatted(Formatting.DARK_GRAY), false);
            player.sendMessage(Text.literal("§8发送 §fskip §8跳过；§fcancel §8取消整次绑定").formatted(Formatting.DARK_GRAY), false);
            return true;
        }

        // AWAIT_LABEL
        PENDING.remove(player.getUuid());
        String label = msg.equalsIgnoreCase("skip") || msg.equals("跳过") ? "" : msg;
        writeStackPending(player, bind.cmd, label);
        return true;
    }

    /** 把命令 + 自定义文字写到玩家主手告示牌 stack 上。*/
    private static void writeStackPending(ServerPlayerEntity player, String cmd, String label) {
        ItemStack main = player.getMainHandStack();
        if (!isSignItem(main)) {
            player.sendMessage(Text.literal("§c[告示牌] 主手不再是告示牌，绑定取消。").formatted(Formatting.RED), false);
            return;
        }
        NbtCompound nbt = main.getOrCreateNbt();
        nbt.remove(TASK_GROUP_NBT_KEY);
        nbt.remove(TASK_GROUP_NAME_NBT_KEY);
        nbt.putString(STACK_PENDING_KEY, cmd);
        nbt.putString(STACK_PENDING_LABEL, label == null ? "" : label);
        // 加 lore 提示玩家这块告示牌已绑定
        NbtCompound display = nbt.getCompound("display");
        net.minecraft.nbt.NbtList lore = new net.minecraft.nbt.NbtList();
        lore.add(net.minecraft.nbt.NbtString.of(Text.Serializer.toJson(
            Text.literal("§b已绑定 §f/" + cmd).styled(s -> s.withItalic(false)))));
        if (label != null && !label.isEmpty()) {
            lore.add(net.minecraft.nbt.NbtString.of(Text.Serializer.toJson(
                Text.literal("§7显示文字：§f" + label).styled(s -> s.withItalic(false)))));
        }
        lore.add(net.minecraft.nbt.NbtString.of(Text.Serializer.toJson(
            Text.literal("§8放置后自动蜡封").styled(s -> s.withItalic(false)))));
        display.put("Lore", lore);
        nbt.put("display", display);
        player.getInventory().markDirty();

        String labelSummary = (label == null || label.isEmpty()) ? "§8无" : "§f" + label;
        player.sendMessage(Text.literal("§a[告示牌] 已绑定 §f/" + cmd + "§a，文字：" + labelSummary
            + "§a。放下时自动蜡封。").formatted(Formatting.GREEN), false);
    }

    // ------------------------------------------------------------------ 放置时迁移 stack NBT → BlockEntity NBT

    /** SignItem 放置钩子从 mixin 调过来，把 stack pending 命令写到 barrel be + 蜡封。*/
    public static void bindOnPlace(net.minecraft.world.World world,
                                   net.minecraft.util.math.BlockPos pos, ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        if (nbt == null) return;

        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof SignBlockEntity sign)) return;
        SignCommandBlockEntityAccess access = (SignCommandBlockEntityAccess) sign;

        if (nbt.contains(TASK_GROUP_NBT_KEY)) {
            String groupId = nbt.getString(TASK_GROUP_NBT_KEY);
            if (groupId == null || groupId.isEmpty()) return;
            String groupName = nbt.contains(TASK_GROUP_NAME_NBT_KEY) ? nbt.getString(TASK_GROUP_NAME_NBT_KEY) : groupId;
            access.tomkestookit$setTaskGroup(groupId);
            access.tomkestookit$setTaskGroupName(groupName);
            access.tomkestookit$setCommand(null);

            net.minecraft.text.Text line1 = net.minecraft.text.Text.literal("[任务组告示牌]")
                .styled(s -> s.withItalic(false).withColor(net.minecraft.util.Formatting.AQUA));
            net.minecraft.text.Text line2 = net.minecraft.text.Text.literal(groupName)
                .styled(s -> s.withItalic(false).withColor(net.minecraft.util.Formatting.WHITE));

            var front = sign.getFrontText().withMessage(0, line1).withMessage(1, line2);
            sign.setText(front, true);
            var back = sign.getBackText().withMessage(0, line1).withMessage(1, line2);
            sign.setText(back, false);
            sign.setWaxed(true);
            sign.markDirty();
            var pkt = sign.toUpdatePacket();
            if (pkt != null && world instanceof net.minecraft.server.world.ServerWorld sw) {
                for (var p : sw.getServer().getPlayerManager().getPlayerList()) {
                    if (p.getServerWorld() == sw) {
                        p.networkHandler.sendPacket(pkt);
                    }
                }
            }
            return;
        }

        if (!nbt.contains(STACK_PENDING_KEY)) return;
        String cmd = nbt.getString(STACK_PENDING_KEY);
        if (cmd == null || cmd.isEmpty()) return;
        // 白名单再校验
        int sp = cmd.indexOf(' ');
        String head = sp < 0 ? cmd : cmd.substring(0, sp);
        boolean allowed = false;
        for (String p : ALLOWED_PREFIXES) {
            if (p.equals(head)) { allowed = true; break; }
        }
        if (!allowed) return;

        access.tomkestookit$setCommand(cmd);
        access.tomkestookit$setTaskGroup(null);
        access.tomkestookit$setTaskGroupName(null);

        // 第 1 行固定：[命令告示牌]  第 2 行：玩家自定义文字
        String label = nbt.contains(STACK_PENDING_LABEL) ? nbt.getString(STACK_PENDING_LABEL) : "";
        net.minecraft.text.Text line1 = net.minecraft.text.Text.literal("[命令告示牌]")
            .styled(s -> s.withItalic(false).withColor(net.minecraft.util.Formatting.AQUA));
        net.minecraft.text.Text line2 = net.minecraft.text.Text.literal(label == null ? "" : label)
            .styled(s -> s.withItalic(false).withColor(net.minecraft.util.Formatting.WHITE));

        var front = sign.getFrontText().withMessage(0, line1).withMessage(1, line2);
        sign.setText(front, true);
        var back = sign.getBackText().withMessage(0, line1).withMessage(1, line2);
        sign.setText(back, false);
        sign.setWaxed(true);
        sign.markDirty();
        // 广播 update packet 让附近客户端刷新文字
        var pkt = sign.toUpdatePacket();
        if (pkt != null && world instanceof net.minecraft.server.world.ServerWorld sw) {
            for (var p : sw.getServer().getPlayerManager().getPlayerList()) {
                if (p.getServerWorld() == sw) {
                    p.networkHandler.sendPacket(pkt);
                }
            }
        }
    }

    // ------------------------------------------------------------------ 右键执行

    private static ActionResult onUseBlock(PlayerEntity player, net.minecraft.world.World world,
                                           Hand hand, BlockHitResult hit) {
        if (world.isClient) return ActionResult.PASS;
        if (!TomkesTookitConfig.enabled("item.sign_command")) return ActionResult.PASS;
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;

        BlockEntity be = world.getBlockEntity(hit.getBlockPos());
        if (!(be instanceof SignBlockEntity sign)) return ActionResult.PASS;

        // 手上拿 vanilla 交互物品 → 让 vanilla 处理（染色 / 发光 / 蜡封）
        ItemStack main = player.getMainHandStack();
        ItemStack off = player.getOffHandStack();
        if (isSignInteractionItem(main) || isSignInteractionItem(off)) {
            return ActionResult.PASS;
        }

        SignCommandBlockEntityAccess access = (SignCommandBlockEntityAccess) sign;
        String currentGroup = access.tomkestookit$getTaskGroup();
        if (currentGroup != null && !currentGroup.isEmpty()) {
            return executeTaskGroup(sp, currentGroup, access.tomkestookit$getTaskGroupName());
        }

        String currentCmd = access.tomkestookit$getCommand();
        if (currentCmd == null || currentCmd.isEmpty()) return ActionResult.PASS;

        return executeBound(sp, currentCmd);
    }

    private static ActionResult executeBound(ServerPlayerEntity sp, String cmd) {
        long now = System.currentTimeMillis();
        Long last = LAST_USE.get(sp.getUuid());
        if (last != null && now - last < COOLDOWN_MS) return ActionResult.SUCCESS;
        LAST_USE.put(sp.getUuid(), now);

        int spIdx = cmd.indexOf(' ');
        String head = spIdx < 0 ? cmd : cmd.substring(0, spIdx);
        boolean allowed = false;
        for (String p : ALLOWED_PREFIXES) {
            if (p.equals(head)) { allowed = true; break; }
        }
        if (!allowed) {
            sp.sendMessage(Text.literal("§c[告示牌] 命令不在允许列表：/" + head).formatted(Formatting.RED), true);
            return ActionResult.SUCCESS;
        }
        sp.getServer().getCommandManager().executeWithPrefix(sp.getCommandSource(), "/" + cmd);
        sp.sendMessage(Text.literal("§7[告示牌] 已执行 §f/" + cmd).formatted(Formatting.GRAY), true);
        return ActionResult.SUCCESS;
    }

    private static ActionResult executeTaskGroup(ServerPlayerEntity sp, String groupId, String groupName) {
        long now = System.currentTimeMillis();
        Long last = LAST_USE.get(sp.getUuid());
        if (last != null && now - last < COOLDOWN_MS) return ActionResult.SUCCESS;
        LAST_USE.put(sp.getUuid(), now);

        com.tomkestookit.commandtask.CommandTaskStore.TaskGroup group =
            com.tomkestookit.commandtask.CommandTaskStore.group(groupId);
        if (group == null) {
            sp.sendMessage(Text.literal("§c[任务组] 未找到任务组：" + groupId).formatted(Formatting.RED), true);
            return ActionResult.SUCCESS;
        }
        com.tomkestookit.commandtask.CommandTaskRunner.runGroup(
            sp, group, "任务组告示牌 " + (groupName == null || groupName.isEmpty() ? group.name : groupName));
        sp.sendMessage(Text.literal("§7[任务组] 已执行 §f" + (groupName == null || groupName.isEmpty() ? group.name : groupName))
            .formatted(Formatting.GRAY), true);
        return ActionResult.SUCCESS;
    }

    // ------------------------------------------------------------------ helpers

    public static String normalize(String raw) {
        if (raw == null) return null;
        String s = raw.strip();
        if (s.isEmpty()) return null;
        if (s.startsWith("/")) s = s.substring(1).strip();
        if (s.isEmpty()) return null;
        int sp = s.indexOf(' ');
        String head = sp < 0 ? s : s.substring(0, sp);
        for (String p : ALLOWED_PREFIXES) {
            if (p.equals(head)) return s;
        }
        return null;
    }

    private static boolean isSignInteractionItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.getItem() instanceof DyeItem) return true;
        if (stack.isOf(Items.GLOW_INK_SAC)) return true;
        if (stack.isOf(Items.INK_SAC)) return true;
        if (stack.isOf(Items.HONEYCOMB)) return true;
        return false;
    }

    public static boolean isSignItem(ItemStack stack) {
        return stack.getItem() instanceof SignItem || stack.getItem() instanceof HangingSignItem;
    }
}
