package com.tomkestookit;

import net.minecraft.server.network.ServerPlayerEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Carpet 假人动作反射桥。
 *
 * 项目不把 carpet 作为 compile 依赖，所以不能直接 import carpet.helpers.EntityPlayerActionPack。
 * 运行时如果 Carpet 存在，优先反射调用 action pack 的 once/continuous/interval；
 * 若签名因版本差异找不到，再 fallback 到 Carpet 的 /player 命令，保证可用。
 */
public final class CarpetBotAction {
    private CarpetBotAction() {}

    public enum Action { ATTACK, USE, JUMP }

    public static boolean once(ServerPlayerEntity bot, Action action) {
        if (bot == null) return false;
        return callAction(bot, action, "once", 0);
    }

    public static boolean continuous(ServerPlayerEntity bot, Action action) {
        if (bot == null) return false;
        return callAction(bot, action, "continuous", 0);
    }

    public static boolean interval(ServerPlayerEntity bot, Action action, int ticks) {
        if (bot == null) return false;
        return callAction(bot, action, "interval", Math.max(1, ticks));
    }

    public static boolean stop(ServerPlayerEntity bot) {
        if (bot == null) return false;
        try {
            Object pack = actionPack(bot);
            if (pack == null) return fallback(bot, "stop");
            for (Method m : pack.getClass().getMethods()) {
                if (!m.getName().equals("stop")) continue;
                if (m.getParameterCount() == 0) {
                    m.invoke(pack);
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        return fallback(bot, "stop");
    }

    private static boolean callAction(ServerPlayerEntity bot, Action action, String mode, int intervalTicks) {
        try {
            Object pack = actionPack(bot);
            if (pack != null) {
                Object actionType = actionType(action);
                if (actionType != null) {
                    // Carpet 主线常见签名：start(ActionType, Action)
                    Object carpetAction = carpetAction(mode, intervalTicks);
                    if (carpetAction != null && tryStartAction(pack, actionType, carpetAction)) return true;
                    // 兼容其它 fork 的直接签名
                    if (tryInvokePack(pack, action, mode, intervalTicks, actionType)) return true;
                }
                // 也尝试无 enum 的具名方法，如 attackOnce / startAttack
                if (tryInvokeNamed(pack, action, mode, intervalTicks)) return true;
            }
        } catch (Throwable ignored) {}
        return fallback(bot, action.name().toLowerCase() + " " + mode + ("interval".equals(mode) ? " " + intervalTicks : ""));
    }

    private static Object actionPack(ServerPlayerEntity bot) throws ReflectiveOperationException {
        // Carpet 假人类常见字段：actionPack
        Class<?> c = bot.getClass();
        while (c != null && c != Object.class) {
            try {
                Field f = c.getDeclaredField("actionPack");
                f.setAccessible(true);
                Object pack = f.get(bot);
                if (pack != null) return pack;
            } catch (NoSuchFieldException ignored) {}
            c = c.getSuperclass();
        }
        // 常见 getter：getActionPack()
        c = bot.getClass();
        while (c != null && c != Object.class) {
            try {
                Method m = c.getDeclaredMethod("getActionPack");
                m.setAccessible(true);
                Object pack = m.invoke(bot);
                if (pack != null) return pack;
            } catch (NoSuchMethodException ignored) {}
            c = c.getSuperclass();
        }
        return null;
    }

    private static Object actionType(Action action) {
        String[] enumClasses = {
            "carpet.helpers.EntityPlayerActionPack$ActionType",
            "carpet.helpers.EntityPlayerActionPack$Action",
            "carpet.patches.EntityPlayerActionPack$ActionType"
        };
        for (String cn : enumClasses) {
            try {
                Class<?> cls = Class.forName(cn);
                if (!cls.isEnum()) continue;
                for (Object e : cls.getEnumConstants()) {
                    String n = ((Enum<?>) e).name();
                    if (n.equalsIgnoreCase(action.name())) return e;
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static Object carpetAction(String mode, int intervalTicks) {
        String[] classes = {
            "carpet.helpers.EntityPlayerActionPack$Action",
            "carpet.patches.EntityPlayerActionPack$Action"
        };
        for (String cn : classes) {
            try {
                Class<?> cls = Class.forName(cn);
                String methodName = switch (mode) {
                    case "once" -> "once";
                    case "continuous" -> "continuous";
                    case "interval" -> "interval";
                    default -> mode;
                };
                for (Method m : cls.getDeclaredMethods()) {
                    if (!m.getName().equals(methodName)) continue;
                    m.setAccessible(true);
                    if (m.getParameterCount() == 0) return m.invoke(null);
                    if (m.getParameterCount() == 1) return m.invoke(null, intervalTicks);
                    if (m.getParameterCount() == 2) return m.invoke(null, intervalTicks, 0);
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static boolean tryStartAction(Object pack, Object actionType, Object carpetAction) {
        for (Method m : pack.getClass().getMethods()) {
            if (!m.getName().equals("start")) continue;
            Class<?>[] p = m.getParameterTypes();
            if (p.length != 2) continue;
            if (!p[0].isInstance(actionType) || !p[1].isInstance(carpetAction)) continue;
            try {
                m.invoke(pack, actionType, carpetAction);
                return true;
            } catch (Throwable ignored) {}
        }
        return false;
    }

    private static boolean tryInvokePack(Object pack, Action action, String mode, int intervalTicks, Object actionType) {
        // 1) methods taking (ActionType, int) or (ActionType)
        for (Method m : pack.getClass().getMethods()) {
            String name = m.getName().toLowerCase();
            if (!(name.contains(mode) || name.contains(action.name().toLowerCase()) || name.equals("start"))) continue;
            Class<?>[] p = m.getParameterTypes();
            try {
                if (p.length == 1 && p[0].isInstance(actionType)) {
                    m.invoke(pack, actionType);
                    return true;
                }
                if (p.length == 2 && p[0].isInstance(actionType) && (p[1] == int.class || p[1] == Integer.class)) {
                    m.invoke(pack, actionType, intervalTicks);
                    return true;
                }
                if (p.length == 3 && p[0].isInstance(actionType)) {
                    // 部分版本可能是 (ActionType, boolean continuous, int interval)
                    Object[] args = new Object[]{actionType, "continuous".equals(mode), intervalTicks};
                    m.invoke(pack, args);
                    return true;
                }
            } catch (Throwable ignored) {}
        }
        return false;
    }

    private static boolean tryInvokeNamed(Object pack, Action action, String mode, int intervalTicks) {
        String a = action.name().toLowerCase();
        String camel = Character.toUpperCase(a.charAt(0)) + a.substring(1);
        String[] names = switch (mode) {
            case "once" -> new String[]{a + "Once", "once" + camel};
            case "continuous" -> new String[]{a + "Continuous", "start" + camel, "continuous" + camel};
            case "interval" -> new String[]{a + "Interval", "interval" + camel};
            default -> new String[0];
        };
        for (String n : names) {
            for (Method m : pack.getClass().getMethods()) {
                if (!m.getName().equals(n)) continue;
                try {
                    if (m.getParameterCount() == 0) {
                        m.invoke(pack);
                        return true;
                    }
                    if (m.getParameterCount() == 1 && (m.getParameterTypes()[0] == int.class || m.getParameterTypes()[0] == Integer.class)) {
                        m.invoke(pack, intervalTicks);
                        return true;
                    }
                } catch (Throwable ignored) {}
            }
        }
        return false;
    }

    private static boolean fallback(ServerPlayerEntity bot, String suffix) {
        return CarpetBotUtil.runAsServer(bot, "player " + bot.getGameProfile().getName() + " " + suffix);
    }
}
