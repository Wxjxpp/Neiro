# music-player

Kotlin + Jetpack Compose + **Material 3 Expressive** 音乐播放器模板。

当前是一个**可编译、可运行的骨架**：UI 壳层、导航、播放栏动画、下拉刷新全部跑通，
业务能力以接口 + 内存实现的形式预留，后续按需替换。

## 技术栈

| 项 | 版本 |
|---|---|
| Kotlin | 2.2.21 |
| AGP | 8.13.2 |
| Gradle | 8.14.5 |
| compileSdk / targetSdk | 36 |
| minSdk | 24 |
| compose-bom | 2026.08.00 |
| material3 | 1.5.0-alpha26（Expressive API 所在版本） |

> material3 不走 BOM，单独锁 `1.5.0-alphaXX`。
> `MaterialExpressiveTheme` / `MotionScheme` / `LoadingIndicator` /
> `WavyProgressIndicator` / `MaterialShapes` / `ButtonGroup` 只在 1.5.0-alpha 提供，
> 稳定版 1.4.x 用不了。

## 目录结构

```
app/src/main/java/com/wxjxpp/musicplayer/
├── MainActivity.kt
├── app/                         应用壳层
│   ├── AppContainer.kt          依赖装配（唯一的实现绑定点）
│   ├── AppViewModel.kt          壳层状态
│   ├── MusicPlayerApp.kt        抽屉 + 共享元素动画 + 路由分发
│   └── navigation/
│       ├── Destination.kt       路由表
│       └── AppDrawer.kt         侧滑菜单（数据驱动）
├── core/                        与 UI 无关的能力层
│   ├── model/                   领域模型
│   │   ├── Media.kt             Song / Album / Artist / MediaLocation / ReplayGain
│   │   ├── Playback.kt          PlaybackState / RepeatMode
│   │   ├── Lyrics.kt            统一歌词模型（含逐字音节）
│   │   ├── Stats.kt             听歌记录 / 日记 / 年度报告
│   │   └── Together.kt          一起听房间与同步事件
│   ├── data/                    仓库契约 + 内存实现
│   ├── player/                  PlayerController 契约 + 内存实现
│   ├── source/                  MusicSource 插件契约 + 注册表
│   ├── lyrics/                  歌词解析器契约 + LRC 实现
│   └── scanner/                 扫描与元数据读取契约
├── feature/                     页面
│   ├── home/                    首页（M3E 下拉刷新）
│   ├── player/                  播放栏 + 播放详情页
│   └── placeholder/             未实现功能占位页
└── ui/
    ├── theme/                   MaterialExpressiveTheme + 设计 token
    └── components/              可复用组件
```

## 已规划的扩展点

下面每一项都已有接口就位，实现时**只碰一个文件 + 一处装配**，不动 UI。

### 扫描本地歌曲 / 读取元数据

- `core/scanner/MediaScanner.kt` — `MediaScanner`、`MetadataReader`
- 元数据模型已含封面、时长、艺术家、`ReplayGain`、`AudioFormat`（采样率 / 位深 / 码率）
- 实现建议：MediaStore 建索引 → `MetadataReader` 异步补齐标签
  （需要更全的标签支持时把底层从 `MediaMetadataRetriever` 换成 jaudiotagger / taglib）

### 多种歌词格式

- `core/lyrics/LyricsParser.kt` — 契约 + `LyricsParserRegistry`
- `LrcParser` 已实现：标准 LRC、一行多时间戳、增强型逐字 `<mm:ss.xx>`、`[offset:]`、同时间戳双行视为翻译
- `TtmlParser` / `SrtParser` 为骨架，`canParse` 已就绪，填 `parse` 即可
- 统一模型 `Lyrics` 同时承载原文 / 翻译 / 罗马音 / 逐字音节，渲染层不需要区分格式

### 在线播放（WebDAV / 云盘 / API 源）

- `core/source/MusicSource.kt` — 每个音源一个实现类
- `MediaLocation` 已区分 `Local` / `WebDav` / `Remote`
- `SourceCapability` 声明能力，UI 据此显示入口，不写 `if (source == "xxx")`
- 新增音源：实现接口 → 在 `AppContainer` 的 `DefaultMusicSourceRegistry` 注册

### 歌单 / 听歌日记 / 年度报告

- `core/data/Repositories.kt` — `PlaylistRepository`、`DiaryRepository`、`StatsRepository`
- 年度报告基于 `PlayEvent` 流水表统计，`ListeningReport` 已含 topSongs / topArtists / 小时分布
- 抽屉入口和路由已接好，指向占位页

### 一起听

- `core/together/TogetherTransport.kt` — 传输层抽象
- `TogetherEvent` 定义 Play / Pause / Seek / QueueChanged / Chat
- `serverTimeOffsetMs` 放在传输层，因为不同方案（内网穿透 / 自建后端 / 局域网）对时方式不同
- 远端事件最终翻译成 `PlayerController` 调用，不绕过播放层

### 侧滑功能菜单

- `app/navigation/AppDrawer.kt` — 往 `drawerItems` 加一条就出现新入口
- 路由字符串集中在 `Destination.kt`，不散落

### 接入 Media3

- `core/player/PlayerController.kt` 是唯一契约，UI 只依赖它
- 新增 `Media3PlayerController : PlayerController`，在 `AppContainer` 替换 `InMemoryPlayerController`
- `AndroidManifest.xml` 已预留 `MediaSessionService` 声明与前台服务权限（注释状态）

## UI 可维护性约定

这几条是为了避免"改一个地方波及一片"：

1. **不在页面里写死 dp 和颜色**。尺寸走 `AppTheme.dimens`（`ui/theme/Tokens.kt`），
   颜色走 `MaterialTheme.colorScheme`。改视觉只改 token 文件。
2. **不在组件里写死动画时长**。统一用 `MaterialTheme.motionScheme`
   （由 `MaterialExpressiveTheme` 下发），需要更克制的动效就在
   `MusicPlayerTheme` 传 `MotionScheme.standard()`。
3. **播放栏只有一份实现**。常规态与悬浮态是同一个 `PlayerBar`，
   由 `floating: Boolean` 驱动 token 插值，不存在两套代码导致行为不一致。
4. **展开与收起共用同一条动画路径**。封面在播放栏和详情页使用同一个
   `sharedElement` key（`PlayerSharedKeys.Cover`），返回时自然缩回，
   不需要单独写反向动画。
5. **播放栏挂在壳层**，不在各页面内部，保证跨页时是同一实例。

## 构建

GitHub Actions（`.github/workflows/android-debug.yml`）：
push 到 `main` / `dev` 或手动 `workflow_dispatch` 触发，
产物在 Actions 页面的 `music-player-debug`。

本地：

```bash
./gradlew assembleDebug
```

## 当前状态

- 播放是 `InMemoryPlayerController`：按真实时间推进进度、支持队列 / 随机 / 循环，但不解码音频
- 曲库是内存示例数据（`InMemoryRepositories.kt` 里的 `SampleLibrary`）
- 抽屉中除首页外均为占位页
- 封面是渐变色块，接入 Coil 时只改 `ui/components/SongCover.kt`