package com.tomkestookit;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 假人「锁定位置」的内存态。只在服务器运行时维护；断线 / 重登假人会掉锁定，
 * 因为 Carpet 假人下线后并不真正保留实体，位置需要重新设置。
 *
 * 记录：假人 UUID → 目标 pos + yaw + pitch。锁定期间：
 *   - BotAnchorHandler 每 tick 拉回目标位置 / 视角
 *   - LivingEntityKnockbackMixin 屏蔽击退
 */
public final class BotAnchorTracker {

    private BotAnchorTracker() {}

    public record Anchor(String dim, double x, double y, double z, float yaw, float pitch) {}

    public enum TargetMode {
        NONE("关闭"),
        ARMOR_STAND("盔甲架"),
        PASSIVE("友好生物"),
        HOSTILE("敌对生物");

        public final String zh;
        TargetMode(String zh) { this.zh = zh; }

        public TargetMode next() {
            return switch (this) {
                case NONE -> ARMOR_STAND;
                case ARMOR_STAND -> PASSIVE;
                case PASSIVE -> HOSTILE;
                case HOSTILE -> NONE;
            };
        }
    }

    private static final Map<UUID, Anchor> ANCHORS = new HashMap<>();

    /**
     * 无击退状态。默认所有假人为 on，map 里只记录"显式关闭"的 UUID。
     * 这样新假人生成时无需任何事件挂点，第一次被查询就直接返回 true。
     */
    private static final Map<UUID, Boolean> KNOCKBACK_OFF_EXPLICIT = new HashMap<>();
    private static final Map<UUID, TargetMode> TARGET_MODES = new HashMap<>();
    private static final Map<UUID, Integer> ATTACK_INTERVALS = new HashMap<>();
    private static final Map<UUID, Integer> ATTACK_TICKERS = new HashMap<>();

    public static void setAnchor(UUID bot, Anchor a) { ANCHORS.put(bot, a); }
    public static void clearAnchor(UUID bot) { ANCHORS.remove(bot); }
    public static Anchor anchor(UUID bot) { return ANCHORS.get(bot); }
    public static boolean isAnchored(UUID bot) { return ANCHORS.containsKey(bot); }

    /** on=开启无击退（默认状态，从 map 里移除即可）；off=显式关闭无击退。*/
    public static void setNoKnockback(UUID bot, boolean on) {
        if (on) KNOCKBACK_OFF_EXPLICIT.remove(bot);
        else KNOCKBACK_OFF_EXPLICIT.put(bot, Boolean.TRUE);
    }

    /** 假人默认无击退 = true；被玩家显式关掉后 = false。*/
    public static boolean noKnockback(UUID bot) {
        return !KNOCKBACK_OFF_EXPLICIT.containsKey(bot);
    }

    public static TargetMode targetMode(UUID bot) {
        return TARGET_MODES.getOrDefault(bot, TargetMode.NONE);
    }

    public static void setTargetMode(UUID bot, TargetMode mode) {
        if (mode == null || mode == TargetMode.NONE) TARGET_MODES.remove(bot);
        else TARGET_MODES.put(bot, mode);
    }

    public static TargetMode cycleTargetMode(UUID bot) {
        TargetMode next = targetMode(bot).next();
        setTargetMode(bot, next);
        return next;
    }

    public static void setAttackInterval(UUID bot, int ticks) {
        if (ticks <= 0) {
            ATTACK_INTERVALS.remove(bot);
            ATTACK_TICKERS.remove(bot);
        } else {
            ATTACK_INTERVALS.put(bot, ticks);
            ATTACK_TICKERS.put(bot, 0);
        }
    }

    public static int attackInterval(UUID bot) {
        return ATTACK_INTERVALS.getOrDefault(bot, 0);
    }

    /** 返回本 tick 是否该攻击一次。*/
    public static boolean tickAttackInterval(UUID bot) {
        int interval = attackInterval(bot);
        if (interval <= 0) return false;
        int left = ATTACK_TICKERS.getOrDefault(bot, 0) - 1;
        if (left <= 0) {
            ATTACK_TICKERS.put(bot, interval);
            return true;
        }
        ATTACK_TICKERS.put(bot, left);
        return false;
    }

    /** 断线/下线时清理，避免陈旧 UUID 无限累积。*/
    public static void onDisconnect(UUID bot) {
        ANCHORS.remove(bot);
        KNOCKBACK_OFF_EXPLICIT.remove(bot);
        TARGET_MODES.remove(bot);
        ATTACK_INTERVALS.remove(bot);
        ATTACK_TICKERS.remove(bot);
    }

    /** 管理员关掉 world.bot_anchor 时的清理：清所有 tracker 状态。*/
    public static void clearAll() {
        ANCHORS.clear();
        KNOCKBACK_OFF_EXPLICIT.clear();
        TARGET_MODES.clear();
        ATTACK_INTERVALS.clear();
        ATTACK_TICKERS.clear();
    }
}
