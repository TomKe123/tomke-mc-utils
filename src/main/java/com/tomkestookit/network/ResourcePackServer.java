package com.tomkestookit.network;

import com.tomkestookit.TomkesTookitMod;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.List;

/**
 * 资源包 SHA1 计算 + server.properties 写入。
 *
 * 资源包 zip 通过 Cloudflare Pages 托管（外部 CDN），不再内建 HTTP 服务。
 * Mod 启动时：
 *   1. 读取 server.properties 中 resource-pack-url
 *   2. 从该 URL 下载 zip 并计算 SHA1（保证和客户端拿到的文件一致）
 *   3. 把 resource-pack / resource-pack-sha1 / require-resource-pack 写入 server.properties
 */
public final class ResourcePackServer {

    @Nullable private static String cachedSha1 = null;

    private ResourcePackServer() {}

    /**
     * 从 resource-pack-url 下载 zip 并计算 SHA1。SERVER_STARTED 时调用。
     */
    public static void start() {
        // SHA1 在 autoConfigureServer 中按需计算
    }

    public static void stop() {}

    @Nullable
    public static String getSha1() { return cachedSha1; }

    /**
     * 从 resource-pack-url 下载 zip 算 SHA1，写入 server.properties。
     */
    public static void autoConfigureServer(net.minecraft.server.MinecraftServer server) {
        Path propsPath = propsPath(server);
        try {
            String url = readProp(propsPath, "resource-pack-url");
            if (url == null || url.isEmpty()) {
                TomkesTookitMod.LOGGER.warn("[RP] server.properties 中未配置 resource-pack-url，跳过");
                return;
            }

            // 从 CDN 下载 zip 计算 SHA1
            String sha1 = fetchAndHash(url);
            if (sha1 == null) {
                TomkesTookitMod.LOGGER.error("[RP] 无法从 {} 下载资源包，SHA1 计算失败", url);
                return;
            }
            cachedSha1 = sha1;

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
    //  Download + SHA1
    // ========================================================================

    @Nullable
    private static String fetchAndHash(String urlStr) {
        try {
            TomkesTookitMod.LOGGER.info("[RP] 正在从 {} 下载资源包计算 SHA1...", urlStr);
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(30_000);
            conn.setRequestProperty("User-Agent", "TomkesTookit/1.0");

            int code = conn.getResponseCode();
            if (code != 200) {
                TomkesTookitMod.LOGGER.error("[RP] 下载失败 HTTP {}", code);
                conn.disconnect();
                return null;
            }

            MessageDigest md = MessageDigest.getInstance("SHA-1");
            try (InputStream in = conn.getInputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    md.update(buf, 0, n);
                }
            }
            conn.disconnect();

            byte[] sha1 = md.digest();
            StringBuilder sb = new StringBuilder(40);
            for (byte b : sha1) sb.append(String.format("%02x", b));
            String hash = sb.toString();
            TomkesTookitMod.LOGGER.info("[RP] 下载完成，SHA1={}", hash);
            return hash;
        } catch (Exception e) {
            TomkesTookitMod.LOGGER.error("[RP] 下载资源包异常", e);
            return null;
        }
    }

    // ========================================================================
    //  Properties helpers
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
}
