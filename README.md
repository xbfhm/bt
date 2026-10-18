# BT · Android TV 增强版

基于官方 `com.chinasoul.bt` **v0.9.40** 逆向修改的 TV 版本，做了两件事：

1. **兼容 Android 5.1.1（API 22）** —— 原版要求 Android 6.0，老电视装不上
2. **新增两个功能** —— 免费影视库、刷视频

---

## 📥 下载

| 渠道 | 地址 |
|---|---|
| 直链（推荐） | https://xbfhm.xyz/downloads/BT_Plus_0.9.41_API22.apk |
| GitHub Release | https://github.com/xbfhm/bt/releases/download/latest/BT_Plus_0.9.41_API22.apk |

> GitHub Release 里的 APK 由 GitHub Actions 自动重新构建（源码与补丁完全相同，但 D8 编译器版本、签名时间戳不同，所以字节内容和 SHA-256 与网站直链版本不一致）。两者功能一致，**优先用网站直链**，下表的校验值也对应网站直链版本。

| 项 | 值 |
|---|---|
| 版本 | `0.9.41`（versionCode 59） |
| 大小 | 3,600,139 字节（3.43 MB） |
| SHA-256 | `c4ca4313c65e887dd0273155981351e65edf06dae79732b0ab55c7ddf85e3018` |
| MD5 | `8a67e86f5f04b40482532f1f1f6f8529` |
| minSdk | 21（Android 5.0） |
| targetSdk | 35 |
| 签名 | V1 + V2 + V3 |

> ⚠️ **签名与官方版不同，必须先卸载原版 BT 再安装**。卸载会清掉本地数据（观看记录、下载列表），装之前先截图记一下。

---

## ✨ 新增功能

**入口在 BT 原来的左侧菜单栏里**——打开 BT，左侧侧边栏底部就是「看番」和「刷视频」两项，遥控器上下选到、按 OK 进入。

桌面图标仍然只有 BT 一个，不会多出别的 App，原 App 的所有功能（推荐 / 动态 / 关注 / 历史 / 直播 / 番剧 / 我的 / 搜索 / 设置）完全不变。

### 1. 免费影视库 —— 播放 B 站没有的番剧 / 会员番剧

用的是你网站同款的 **非凡资源** 接口（`api.ffzyapi.com`），全部免费直连 m3u8：

- 5 万多部影视，每天更新
- 分类：日韩动漫 / 国产动漫 / 国产剧 / 美剧 / 日剧 / 韩剧 / 港台剧 / 泰剧 / 欧美剧 / 综艺
- 支持搜索
- 选集后直接播放（WebView 播 m3u8）
- **只取 `ffm3u8` 线路**：非凡资源返回 `feifan$$$ffm3u8` 两路，实测只有 ffm3u8 是能播的 `.m3u8` 直链，代码按 `vod_play_from` 对齐强制选它

对于 B 站需要大会员才能看的番剧，只要这边有这个资源，就能直接免费看。

### 2. 刷视频 —— 像抖音一样的竖屏推荐流

- 全屏播放，上下键切视频
- 数据来自 B 站官方推荐接口（`/x/web-interface/wbi/index/top/feed/rcmd`）
- 自动取 720P 直链播放
- 遥控器：`上/下` 切换视频，`OK` 暂停/继续，`返回` 退出
- **按【菜单】键或点「原版播放页 ▸」按钮** → 用 BT 自带的播放器打开当前视频，**评论 / 投币 / 收藏 / UP主主页 / 弹幕 / 倍速 全都有**

---

## 📺 遥控器按键

### 免费影视库 / 搜索页

| 按键 | 作用 |
|---|---|
| 方向键 | 移动焦点 |
| OK | 进入 / 播放 |
| 返回 | 返回上一级 |

### 详情页（选集）

| 按键 | 作用 |
|---|---|
| 方向键 | 在剧集网格中移动 |
| OK | 播放该集 |

### 播放页

| 按键 | 作用 |
|---|---|
| OK | 暂停 / 继续 |
| 左 / 右 | 快退 / 快进 10 秒 |
| 返回 | 退出播放 |

### 刷视频

| 按键 | 作用 |
|---|---|
| 上 / 下 | 上一个 / 下一个视频 |
| OK | 暂停 / 继续 |
| 返回 | 退出 |

---

## 🔧 兼容性改动（API 22）

原版 `minSdkVersion="23"`，只降这个数字是不够的 —— 代码里还有一批 API 23 才有的方法，在没有版本守卫的情况下调用会立刻抛异常闪退。

| # | 改动 | 不改会怎样 |
|---|---|---|
| 1 | `minSdkVersion` `23` → `21` | 根本装不上 |
| 2 | `La/a0;` 新增 `D()` / `E()` 权限检查兼容层，3 处 `checkSelfPermission` 改走它 | **一进后台播放 / 下载就崩**（`WakeLockManager` 是必经路径，且无任何版本判断） |
| 3 | `BackgroundAudioService` 3 处 `getSystemService(Class)` → `(String)` | 后台播放服务启动崩溃 |
| 4 | `La/po;` 的 `requestPermissions` 加 `SDK_INT >= 23` 短路 | 权限申请调用崩溃 |
| 5 | 补上 **V2 + V3 签名** | Android 11+ 安装器拒绝安装（报"安装包无效"） |
| 6 | 版本号 `0.9.40` → `0.9.41` | 与原版区分 |

`a0.D()` 在 API 22 上会降级到 `Context.checkPermission(perm, myPid(), myUid())`，**仍然执行真实的权限检查**，不是硬编码返回"已授权"。

已核查确认**不需要**动的：`NotificationChannel` / `Notification.Builder(ctx, channelId)`（都有 `SDK_INT >= 26` 守卫）、`createDeviceProtectedStorageContext`（有 `>= 24` 守卫）、`java.util.function`（R8 desugar 的 `j$/` 包，主入口类零引用）、`resources.arsc` 保持不压缩。所有原有资源文件一字节没改。

---

## 🏗 技术实现

新功能做在**独立的 `classes3.dex`** 里，包名 `com.yzb.btplus`，与原 App 完全隔离：

```
com.yzb.btplus
├── PlusActivity      免费影视库首页（分类 + 网格）
├── DetailActivity    详情页（线路 + 选集）
├── SearchActivity    搜索
├── PlayerActivity    播放器（ExoPlayer + 手势）
├── FeedActivity      刷视频（上下切换）
├── Api               非凡资源接口封装
├── BiliApi           B 站接口封装（推荐流 / playurl）
└── Ui                纯系统控件 UI 工具类
```

**为什么不用 App 自带的 okhttp / media3？**

原版经过 R8 混淆，`okhttp3.*`、`androidx.media3.*` 的类名全被改成了 `a.b.c` 这种，无法通过公开 API 调用。所以新代码只用**系统类**：

- 网络：`java.net.HttpURLConnection`
- 播放：`android.widget.VideoView`（系统播放器，API 1 就有）
- UI：`LinearLayout` / `TextView` / `ImageView` / `GridView` 等纯系统控件

唯一的外部依赖是**图片加载**，用了自己写的极简 `Ui.java`（`HttpURLConnection` + `BitmapFactory` + 二级内存缓存），避免引入 Glide 带来的类名冲突。

---

## 🛠 构建

```bash
git clone https://github.com/xbfhm/bt.git
cd bt
bash tools/build_plus.sh
```

依赖：JDK 8+（`javac` / `javac` 的 `--release 8`）、Python 3、`curl`、`unzip`。

脚本会自动下载 `smali` / `baksmali` / `d8`（首次运行约 20 MB），然后：

1. 编译 `plus/src/` 下的 Java 源码
2. `d8` 转成 `classes3.dex`
3. 用 `tools/axml.py` 在 `AndroidManifest.xml` 里插入 5 个 Activity
4. 把 `classes3.dex` + 新 manifest 塞进 `src/base.apk`
5. 调用 `uber-apk-signer` 做 **zipalign + V1/V2/V3 签名**

产物在 `BT_Plus_0.9.41_API22.apk`。

### 目录结构

```
bt/
├── src/base.apk               # 原版 0.9.40，未修改
├── plus/
│   ├── src/com/yzb/btplus/    # 新功能 Java 源码（8 个类）
│   └── tools/axml.py          # AXML 解析 / 重建器
├── tools/
│   ├── build.sh               # 原版 API 22 适配构建
│   ├── build_plus.sh          # 增强版构建（含新功能）
│   ├── patch_axml.py          # minSdk 修改器
│   ├── patch_api22.py         # smali 层兼容补丁
│   ├── verify.py              # 构建后自检
│   └── verify_plus.py         # 增强版 12 项自检
├── keystore/bt-release.jks    # 固定签名（口令 bt2026）
├── jars/                      # smali / baksmali 离线缓存
├── .github/workflows/build.yml
├── CHANGELOG.md
├── 合格证.md
└── README.md
```

---

## 📄 声明

- 本项目**仅做兼容性适配 + 新增独立功能**，不修改原 App 的任何业务逻辑、不破解会员、不去广告。
- `src/base.apk` 版权归原作者 **重庆魂搜网络科技（chinasoul）** 所有。
- 新增功能使用的是公开的免费资源接口与 B 站公开 API，仅供个人学习使用。
- 请勿用于商业分发。

---

## 相关项目

- [xbfhm/tv](https://github.com/xbfhm/tv) —— AnimeTV 追番电视应用
- [xbfhm/Small-TV-Beyond-the-Clouds](https://github.com/xbfhm/Small-TV-Beyond-the-Clouds) —— 云之彼端小电视
- [xbfhm/misaka-music](https://github.com/xbfhm/misaka-music) —— MisakaMusic 音乐播放器