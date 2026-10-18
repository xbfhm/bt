# 更新日志

## v0.9.41 — 2026-10-18

首个 TV 适配版。基于官方 `com.chinasoul.bt` v0.9.40（versionCode 59）逆向修改。

### 新增

- 支持 Android 5.1.1（API 22）及以上系统安装运行
- 可复现的一键构建脚本 `tools/build.sh`
- GitHub Actions 自动构建并发布到 Release

### 修改

| # | 改动 | 原因 |
|---|---|---|
| 1 | `minSdkVersion` `23` → `21` | 原版要求 Android 6.0，老电视装不上 |
| 2 | `La/a0;` 新增 `D()` / `E()` 权限检查兼容方法 | `Context.checkSelfPermission` 是 API 23 才有的 |
| 3 | `a/GI.smali`、`a/MK.smali` 共 3 处 `checkSelfPermission` → `a0.D` | 同上，且 `WakeLockManager` 在后台播放必经路径上 |
| 4 | `BackgroundAudioService` 3 处 `getSystemService(Class)` → `getSystemService(String)` | `getSystemService(Class)` 是 API 23 才有的 |
| 5 | `La/po;` 的 `requestPermissions` 加 `SDK_INT >= 23` 短路 | 该方法 API 23 才有；Android 5.1 是安装即授权，无需申请 |
| 6 | 版本号 `0.9.40` → `0.9.41` | 与原版区分 |

### 未改动（已核查确认安全）

- `NotificationChannel` / `Notification.Builder(ctx, channelId)` —— 已有 `SDK_INT >= 26` 守卫
- `createDeviceProtectedStorageContext` —— 已有 `SDK_INT >= 24` 守卫
- `java.util.function` / `java.util.stream` —— R8 desugar backport（`j$/` 包），主入口类零引用
- 全部资源文件（`res/`、`resources.arsc`）—— 一字节未改
- 全部原生库 —— 原版本来就没有 `.so`，纯 Java/Kotlin

### 已知问题

- **签名变更**：必须卸载原版才能安装本版本（详见 README）
- 未在真机长期验证稳定性，仅完成静态分析与兼容性补丁
