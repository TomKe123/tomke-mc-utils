package com.tomkestookit.network;

import com.tomkestookit.TomkesTookitMod;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.List;

/**
 * 资源包 SHA1 计算 + server.properties 写入。
 *
 * 资源包 zip 通过 Cloudflare Pages 托管（外部 CDN），不再内建 HTTP 服务。
 * Mod 只负责：
 *   1. 读取 jar 内 /assets/tomkestookit/resourcepack.zip 计算 SHA1
 *   2. 读取 server.properties 中 resource-pack-url（用户手动配的 CF Pages 地址）
 *   3. 把 resource-pack / resource-pack-sha1 / require-resource-pack 写入 server.properties
 */
public final class ResourcePackServer {

    private static final String ZIP_RESOURCE = "/assets/tomkestookit/resourcepack.zip";

    @Nullable private static String cachedSha1 = null;

    private ResourcePackServer() {}

    /**
     * 加载 zip 并计算 SHA1。SERVER_STARTED 时调用。
     */
    public static void start() {
        if (!loadZip()) {
            TomkesTookitMod.LOGGER.error("[RP] zip 加载失败，资源包 SHA1 无法计算");
        }
    }

    /** 不再需要清理工作，保留空方法以免调用处报错。 */
    public static void stop() {}

    @Nullable
    public static String getSha1() { return cachedSha1; }

    /**
     * 把资源包 URL 和 SHA1 写入 server.properties。
     * URL 来源：server.properties 的 resource-pack-url 字段（用户配置的 CF Pages 地址）。
     */
    public static void autoConfigureServer(net.minecraft.server.MinecraftServer server) {
        String sha1 = getSha1();
        if (sha1 == null) return;

        Path propsPath = propsPath(server);
        try {
            String url = readProp(propsPath, "resource-pack-url");
            if (url == null || url.isEmpty()) {
                TomkesTookitMod.LOGGER.warn("[RP] server.properties 中未配置 resource-pack-url，跳过");
                return;
            }

            List<String> lines = Files.exists(propsPath)
                ? new java.util.ArrayList<>(Files.readAllLines(propsPath))
                : new java.util.ArrayList<>();

            writeProp(lines, "resource-pack", url);
            writeProp(lines, "resource-pack-sha1", sha1);
            writeProp(lines, "require-resource-pack", "true");

            Files.write(propsPath, lines);
            TomkesTookitMod.LOGGER.info("[RP] server.properties → {} (SHA1={})", url, sha1);
        } catch (IOException e) {
            TomkesTookitMod.LOGGER.warn("[RP] 写入 server.properties 失败", e);
        }
    }

    // ========================================================================
    //  Internal
    // ========================================================================

    private static Path propsPath(net.minecraft.server.MinecraftServer server) {
        return server.getFile("").getAbsoluteFile().getParentFile()
            .toPath().resolve("server.properties").toAbsolutePath();
    }

    private static String readProp(Path p, String key) {
        try {
            if (Files.exists(p)) {
                for (String line : Files.readAllLines(p)) {
                    if (line.startsWith(key + "=")) {
                        String v = line.substring(key.length() + 1).trim();
                        if (!v.isEmpty()) return v;
                    }
                }
            }
        } catch (IOException ignored) {}
        return null;
    }

    private static void writeProp(List<String> lines, String key, String val) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!line.startsWith("#") && line.replaceAll("\\s", "").startsWith(key + "=")) {
                lines.set(i, key + "=" + val);
                return;
            }
        }
        lines.add(key + "=" + val);
    }

    private static boolean loadZip() {
        try {
            byte[] zipBytes;
            InputStream is = ResourcePackServer.class.getResourceAsStream(ZIP_RESOURCE);
            if (is != null) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream(128 * 1024);
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
                is.close();
                zipBytes = bos.toByteArray();
            } else {
                Path runZip = FabricLoader.getInstance().getGameDir()
                    .resolve("TomkesTookit-ResourcePack.zip");
                if (Files.exists(runZip)) zipBytes = Files.readAllBytes(runZip);
                else {
                    TomkesTookitMod.LOGGER.error("[RP] 找不到 zip（classpath 和 gameDir 都没有）");
                    return false;
                }
            }
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] sha1 = md.digest(zipBytes);
            StringBuilder sb = new StringBuilder(40);
            for (byte b : sha1) sb.append(String.format("%02x", b));
            cachedSha1 = sb.toString();
            TomkesTookitMod.LOGGER.info("[RP] SHA1={} ({}B)", cachedSha1, zipBytes.length);
            return true;
        } catch (Exception e) {
            TomkesTookitMod.LOGGER.error("[RP] 加载 zip 失败", e);
            return false;
        }
    }
}
