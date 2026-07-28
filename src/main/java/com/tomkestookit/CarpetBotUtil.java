package com.tomkestookit;

import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Carpet 假人识别工具：不硬依赖 Carpet 类，用反射判类型。
 * 判定：ServerPlayerEntity 是不是 carpet.helpers.EntityPlayerMPFake 或其子类。
 */
public final class CarpetBotUtil {

    /** 已知假人类的常见全限定名。运行时按顺序尝试反射加载。*/
    private static final String[] KNOWN_CLASSES = new String[] {
        "carpet.helpers.EntityPlayerMPFake",       // fabric-carpet 主分支
        "carpet.entities.EntityPlayerMPFake",      // 部分 fork
        "carpet.patches.EntityPlayerMPFake",       // 老版本
        "carpet.CarpetSettings$EntityPlayerMPFake" // 极少数分发
    };

    private static java.util.List<Class<?>> fakeClasses;
    private static volatile boolean initialized = false;
    /** 每玩家 Class → 是否假人 的缓存；Class 天然不变，用 ConcurrentHashMap 无锁读。*/
    private static final java.util.concurrent.ConcurrentHashMap<Class<?>, Boolean> STRICT_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<Class<?>, Boolean> LOOSE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private CarpetBotUtil() {}

    private static void init() {
        if (initialized) return;
        synchronized (CarpetBotUtil.class) {
            if (initialized) return;
            java.util.List<Class<?>> list = new java.util.ArrayList<>();
            for (String name : KNOWN_CLASSES) {
                try { list.add(Class.forName(name)); } catch (ClassNotFoundException ignored) {}
            }
            fakeClasses = list;
            initialized = true;
        }
    }

    /**
     * 判定假人。两重启发（不再用连接地址判定，防止在登录握手瞬间误判真玩家）：
     *   1) 已知类反射匹配
     *   2) 类名包含 "Fake" 或 "Bot"（覆盖未知的假人 mod）
     */
    public static boolean isFakePlayer(ServerPlayerEntity p) {
        if (p == null) return false;
        Class<?> pc = p.getClass();
        Boolean cached = LOOSE_CACHE.get(pc);
        if (cached != null) return cached;
        boolean result = false;
        try {
            init();
            for (Class<?> fake : fakeClasses) {
                if (fake.isInstance(p)) { result = true; break; }
            }
            if (!result) {
                Class<?> cls = pc;
                while (cls != null && cls != ServerPlayerEntity.class && cls != Object.class) {
                    String simple = cls.getSimpleName();
                    if (simple.contains("Fake") || simple.contains("Bot")) { result = true; break; }
                    cls = cls.getSuperclass();
                }
            }
        } catch (Throwable ignored) {}
        LOOSE_CACHE.put(pc, result);
        return result;
    }

    /**
     * 严格假人判定：只认已知 Carpet 假人类，不做名称模糊匹配。
     * 用于登录/伤害等热路径，不承担误判真玩家的风险。
     */
    public static boolean isFakePlayerStrict(ServerPlayerEntity p) {
        if (p == null) return false;
        Class<?> pc = p.getClass();
        Boolean cached = STRICT_CACHE.get(pc);
        if (cached != null) return cached;
        boolean result = false;
        try {
            init();
            for (Class<?> fake : fakeClasses) {
                if (fake.isInstance(p)) { result = true; break; }
            }
        } catch (Throwable ignored) {}
        STRICT_CACHE.put(pc, result);
        return result;
    }

    /** 以 OP 身份跑一个命令。用于 /player &lt;bot&gt; ... 的调用。返回是否成功。*/
    public static boolean runAsServer(ServerPlayerEntity actor, String command) {
        try {
            actor.getServer().getCommandManager().executeWithPrefix(
                actor.getServer().getCommandSource().withLevel(2).withSilent(),
                command);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
