# TomkesTookit Resource Pack Deployment

本文档说明资源包如何部署到 Cloudflare Pages 并让 mod 引用。

## 架构

- **resourcepack/** 目录：包含所有资源包文件（`pack.mcmeta` + `assets/`）
- **Cloudflare Pages**：托管资源包 zip 的 CDN，提供公网 HTTPS 下载地址
- **Mod 端**：启动时读取 `server.properties` 中的 `resource-pack-url`，计算 jar 内 zip 的 SHA1，写入 `resource-pack-sha1`

## 部署流程

### 1. Cloudflare Pages 配置

1. 在 Cloudflare Dashboard 创建 Pages 项目
2. 关联 Git 仓库（GitHub/GitLab）
3. **Build configuration**:
   - **Build command**: `sh build-resourcepack.sh`
   - **Build output directory**: `public`
   - **Root directory**: `/` (项目根目录)
4. 首次部署后，记录下 Pages URL，如：`https://tomkestookit-rp.pages.dev/TomkesTookit-ResourcePack.zip`

### 2. 自定义域名（可选）

如果你有自己的域名（如 `rp.example.com`）：

1. 在 Cloudflare Pages 项目 **Custom domains** 中添加域名
2. 按提示配置 DNS（通常是 CNAME 记录）
3. 最终下载链接变成 `https://rp.example.com/TomkesTookit-ResourcePack.zip`

### 3. Mod 端配置

在 `run/server.properties` 中添加：

```properties
# 资源包下载地址（Cloudflare Pages 部署后的 URL）
resource-pack-url=https://tomkestookit-rp.pages.dev/TomkesTookit-ResourcePack.zip

# 下面两行 mod 启动时自动填写，不需要手动改
resource-pack-sha1=<自动计算>
resource-pack=<从 resource-pack-url 读取>
require-resource-pack=true
```

Mod 启动时会：
1. 读取 `resource-pack-url`
2. 从 jar 内 `/assets/tomkestookit/resourcepack.zip` 计算 SHA1
3. 把 `resource-pack` 和 `resource-pack-sha1` 写入 `server.properties`

### 4. 更新资源包

1. 修改 `resourcepack/` 目录下的文件
2. Commit + Push 到 Git 仓库
3. Cloudflare Pages 自动触发重新构建
4. 重新构建 mod jar：`./gradlew build`（保证 jar 内的 zip 和 CF Pages 上的一致）
5. 重启服务器

## 目录结构

```
starterkit-mod/
├── resourcepack/                 # 资源包源文件
│   ├── pack.mcmeta
│   └── assets/
│       ├── minecraft/
│       └── tomkestookit/
├── build-resourcepack.sh         # CF Pages build 脚本
├── RESOURCEPACK.md               # 本文档
└── src/main/resources/assets/tomkestookit/
    └── resourcepack.zip          # Gradle 打包后的 zip（通过 bundleResourcePack task）
```

## 故障排查

### 客户端「无法应用资源包」

1. 检查 CF Pages 部署是否成功，访问 `https://<your-domain>/TomkesTookit-ResourcePack.zip` 能否下载
2. 检查 `server.properties` 中 `resource-pack` 和 `resource-pack-sha1` 是否正确
3. 重新构建 jar 保证内部 zip 和 CF Pages 上的一致
4. 检查客户端日志 `.minecraft/logs/latest.log` 中的资源包加载错误

### SHA1 不匹配

- jar 内的 zip 和 CF Pages 上的 zip 必须完全一致
- 每次改 `resourcepack/` 后都要：
  1. Push 触发 CF Pages 重新构建
  2. 本地 `./gradlew build` 重新打包 jar
  3. 重启服务器

### CF Pages 构建失败

- 检查 `build-resourcepack.sh` 是否有执行权限
- 检查 `resourcepack/pack.mcmeta` 格式是否正确
- 查看 CF Pages 构建日志定位具体错误
