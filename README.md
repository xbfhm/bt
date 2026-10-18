# BT · Android TV 适配版

> 把原本只能装在 **Android 6.0+** 的 BT 客户端，改造成能在 **Android 5.1.1（API 22）** 老电视上正常安装运行的版本。

[![Build](https://github.com/xbfhm/bt/actions/workflows/build.yml/badge.svg)](https://github.com/xbfhm/bt/actions/workflows/build.yml)

---

## 📺 适配目标

| 项目 | 说明 |
|---|---|
| 测试设备 | 海信 VIDAA V1 智能电视 |
| 系统 | Android 5.1.1（API 22） |
| SoC | MStar MSD6A828（arm64-v8a，系统镜像 32 位） |
| 内存 | 2 GB |
| GPU | Mali-450 |

---

## ❓ 为什么原版装不上

原版 `com.chinasoul.bt` v0.9.40 的 `AndroidManifest.xml`：

```xml
<uses-sdk android:minSdkVersion="23" android:targetSdkVersion="35" />
```

**`minSdkVersion=23` 意味着 Android 6.0 起步。** 你的电视是 API 22，安装器会直接拒绝：

```
App not installed. This app isn't compatible with your device.
```

而且光把 `minSdk` 改成 21 也不够 —— 代码里还有一批 **API 23 才存在的方法**，在老系统上调用会抛 `NoSuchMethodError` 直接崩溃。

---

## 🔧 做了哪些改动

### 1. `minSdkVersion` 23 → 21

在 AXML 二进制里直接把 `uses-sdk/minSdkVersion` 的整数从 `0x17` 改成 `0x15`，其它资源一字节没动。

### 2. `Context.checkSelfPermission` → 兼容封装

这是最危险的一处。原版有 **3 个调用点**，全部**没有 SDK_INT 保护**：

| 文件 | 用途 |
|---|---|
| `a/GI.smali` | 读取相册（`ACCESS_MEDIA_LOCATION`） |
| `a/MK.smali` ×2 | `WakeLockManager` 后台保活锁（`WAKE_LOCK`） |

`WakeLockManager` 是 **后台下载 / 后台播放** 的必经之路 —— 不改的话，一进播放就崩。

处理方式：在已有的 synthetic 辅助类 `La/a0;` 里新增两个方法：

```smali
# D() —— 统一入口，按 SDK 版本分流
.method public static synthetic D(Landroid/content/Context;Ljava/lang/String;)I
    sget v0, Landroid/os/Build$VERSION;->SDK_INT:I
    const/16 v1, 0x17
    if-lt v0, v1, :cond_old
    # API >= 23：走原生 checkSelfPermission
    invoke-static {p0, p1}, La/a0;->E(Landroid/content/Context;Ljava/lang/String;)I
    move-result v0
    return v0
    :cond_old
    # API < 23：降级到 checkPermission(perm, pid, uid)
    invoke-static {}, Landroid/os/Process;->myPid()I
    move-result v0
    invoke-static {}, Landroid/os/Process;->myUid()I
    move-result v1
    invoke-virtual {p0, p1, v0, v1}, Landroid/content/Context;->checkPermission(Ljava/lang/String;II)I
    move-result v0
    return v0
.end method
```

然后 3 个调用点全部改成 `invoke-static {ctx, perm}, La/a0;->D(...)`。

> ✅ **语义没有降级**：老系统上依然做真实的权限检查（`checkPermission` 从 API 1 就有），不是硬编码返回"已授权"。

### 3. `Context.getSystemService(Class)` → `getSystemService(String)`

`getSystemService(Class<T>)` 是 **API 23** 新增的。`BackgroundAudioService` 里有 **3 处** 用它拿 `NotificationManager`：

```smali
const-class v2, Landroid/app/NotificationManager;
invoke-virtual {p0, v2}, Landroid/content/Context;->getSystemService(Ljava/lang/Class;)Ljava/lang/Object;
```

改为等价的字符串版（API 1 就有）：

```smali
const-string v2, "notification"
invoke-virtual {p0, v2}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;
```

返回值类型不变，后续 `check-cast` 不需要动。

### 4. `Activity.requestPermissions` 加 SDK 守卫

`requestPermissions` 是 API 23 新增的，在 `La/po;` 的 `y(Activity, String[], int)` 里被调用，**原本没有任何版本判断**。

在方法开头插入短路：

```smali
sget v0, Landroid/os/Build$VERSION;->SDK_INT:I
const/16 v1, 0x17
if-lt v0, v1, :cond_sdk23ok
return-void          # API < 23 直接返回：老系统装的时候权限已经全部授予
:cond_sdk23ok
```

> 这不是"跳过功能" —— Android 5.1 是**安装即授权**模型，不存在运行时权限弹窗，这个调用本来就无意义。

### 5. 其它已核查、**无需改动**的点

| API | 位置 | 结论 |
|---|---|---|
| `NotificationChannel` | `a/a0.smali`、`BackgroundAudioService` | 已有 `SDK_INT >= 26` 守卫 ✅ |
| `Notification.Builder(ctx, channelId)` | `a/a0.smali` | 已有 `SDK_INT >= 26` 守卫 ✅ |
| `createDeviceProtectedStorageContext` | `a/U.smali` | 已有 `SDK_INT >= 24` 守卫 ✅ |
| `java.util.function.*`（2000+ 处） | `j$/` desugar 包 | R8 backport，**主入口类零引用**，不会触发类加载 ✅ |
| `java.util.stream.*` | 仅 1 个文件 | 同上 ✅ |
| `AudioAttributes.Builder` | API 21 已有 | ✅ |
| `MediaSession` | API 21 已有 | ✅ |
| `resources.arsc` | — | 保持 `STORED` 不压缩 ✅ |

---

## 📦 下载

| 方式 | 地址 |
|---|---|
| **国内直连（推荐）** | https://xbfhm.xyz/downloads/BT_TV_0.9.41_API22.apk |
| GitHub Release | https://github.com/xbfhm/bt/releases/latest |

- 版本：`0.9.41`（原 0.9.40）
- 大小：3.42 MB
- SHA-256：`0527e531b50256f0ec2eb140b2aeb52b97f7ca8f2bb80b661ce689fce94e9d93`

---

## ⚠️ 安装前必读

**签名变了。** 原版是作者私钥签的，我重打包只能用自签名，所以：

```
必须先卸载原版 BT，再安装这个版本。
```

如果直接覆盖安装会报：

```
App not installed as package conflicts with an existing package.
```

卸载方法（电视上）：

1. 设置 → 应用管理 → 找到 **BT** → 卸载
2. 或者用当贝市场 / 电视管家卸载

> 卸载会清掉 App 的本地数据（观看记录、下载列表等），介意的话先截图记一下。

---

## 🎮 遥控器按键

| 按键 | 功能 |
|---|---|
| `OK` / `Enter` | 选中 / 播放暂停 |
| `←` `→` | 快退 / 快进 |
| `↑` `↓` | 上下切换焦点 |
| `BACK` | 返回上一级 |
| `MENU` | 部分页面呼出菜单 |

---

## 🛠 自己构建

```bash
git clone https://github.com/xbfhm/bt.git
cd bt
./tools/build.sh
```

产物在 `dist/BT_TV_0.9.41_API22.apk`。

依赖：`java`（11+）、`python3`、`curl`、`unzip`、`jarsigner`（JDK 自带）。

构建流程：

```
src/base.apk
   ↓ baksmali 反编译
smali1/ smali2/
   ↓ patch_api22.py 打补丁
   ↓ smali 重新汇编
out1.dex out2.dex
   ↓ repack.py 重打包（改 versionName）
bt_tv_unsigned.apk
   ↓ jarsigner（V1 签名）
dist/BT_TV_0.9.41_API22.apk
```

> 为什么用 **V1 签名**？Android 7.0 以下只认 V1（JAR 签名），V2/V3 是 7.0+ 才支持的。

---

## 📁 仓库结构

```
bt/
├── src/base.apk              # 原版 0.9.40（未修改）
├── tools/
│   ├── build.sh              # 一键构建
│   ├── patch_api22.py        # API 22 兼容补丁
│   └── repack.py             # 重打包
├── keystore/bt-release.jks   # 固定签名（口令 bt2026）
├── .github/workflows/build.yml
├── CHANGELOG.md
├── 合格证.md
└── README.md
```

---

## 📄 声明

- 本项目**仅做兼容性适配**，不修改任何业务逻辑、不破解会员、不去广告。
- `src/base.apk` 版权归原作者 **重庆魂搜网络科技（chinasoul）** 所有。
- 请勿用于商业分发。

---

## 相关项目

- [xbfhm/tv](https://github.com/xbfhm/tv) —— AnimeTV 追番电视应用
- [xbfhm/Small-TV-Beyond-the-Clouds](https://github.com/xbfhm/Small-TV-Beyond-the-Clouds) —— 云之彼端小电视
- [xbfhm/misaka-music](https://github.com/xbfhm/misaka-music) —— MisakaMusic 音乐播放器
