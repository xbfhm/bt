# 更新日志

## v0.9.41-plus — 2026-10-18

**增强版**：在 API 22 适配版基础上新增两个功能，并修复上一版安装失败的问题。

### 🐛 修复

| # | 问题 | 原因 | 修法 |
|---|---|---|---|
| 1 | **安装失败** | 上一版只用 `jarsigner` 做了 V1 签名，而 `targetSdk=35` 的包在 Android 11+ 必须带 V2 签名 | 改用 `uber-apk-signer` 做 zipalign + **V1/V2/V3 三重签名** |

### ✨ 新增功能

| # | 功能 | 说明 |
|---|---|---|
| 1 | **免费影视库** | 接入非凡资源接口（`api.ffzyapi.com`），5 万+ 部影视，10 个分类 + 搜索，直连 m3u8 播放。B 站没有的番剧、需要大会员的番剧，只要有源就能免费看 |
| 2 | **刷视频** | 仿抖音竖屏推荐流，数据来自 B 站官方推荐接口，上下键切换视频，720P 直链播放 |

两个功能都做在独立的 `classes3.dex`（包名 `com.yzb.btplus`）里，在电视桌面**独立显示为两个图标**，不影响原 App 的任何功能。

### 🏗 技术细节

- 新代码只用系统类（`HttpURLConnection` / `VideoView` / 系统控件），因为原版经 R8 混淆后 `okhttp3.*`、`androidx.media3.*` 的类名已不可用
- 自写 `Ui.java` 做图片加载（`BitmapFactory` + 二级内存缓存），避免引入 Glide 造成类名冲突
- 新增 `tools/axml.py`：完整的 AXML 解析 / 重建器，可在 `AndroidManifest.xml` 里精确插入节点而不破坏原有结构
- 新增 `tools/verify_plus.py`：12 项构建后自检

### 📦 产物

| 项 | 值 |
|---|---|
| 文件 | `BT_Plus_0.9.41_API22.apk` |
| 大小 | 3,600,139 字节 |
| SHA-256 | `ddcaab3a51d76e299d29663b11523560d465bfbe262869976891ce0e59103c6d` |
| MD5 | `1d6ba87d056c8ba54a2cbae801a9aa1d` |

### 已知问题

- 签名变更：必须卸载原版 / 上一版才能安装
- 免费源为第三方公开接口，若接口变更或下线，功能会失效（可在 `Api.java` 里换源）
- 刷视频依赖 B 站推荐接口，B 站风控收紧时可能拉不到数据

---

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

- 只有 V1 签名，Android 11+ 可能安装失败（v0.9.41-plus 已修复）
- 签名变更：必须卸载原版才能安装