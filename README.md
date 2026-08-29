Neiro

Kotlin + Jetpack Compose + **Material 3 Expressive** 音乐播放器。
![Android](https://img.shields.io/badge/platform-Android%207+-green?logo=android)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?style=flat-square&logo=kotlin&logoColor=white)
![License](https://img.shields.io/github/license/Wxjxpp/Neiro)
![Stars](https://img.shields.io/github/stars/Wxjxpp/Neiro)
![Version](https://img.shields.io/github/v/release/Wxjxpp/Neiro)
本地曲库 + 在线搜索播放双轨并行：本地功能（扫描 / 歌单 / 统计）开箱即用，
在线功能（聚合搜索 / 歌词 / 自定义音源取流）无需任何配置即可搜索，接入 LX 格式音源脚本后可播放在线歌曲。

## 功能

### 本地
- MediaStore 扫描曲库，元数据（标签 / 封面 / 时长 / 码率）异步补齐
- **曲库持久化**：扫描结果落库 Room，启动直接读取；仅手动刷新（下拉 / 顶栏按钮）时重扫
- **排序自定义**：首字母 / 文件时间 / 播放次数，支持正序或倒序（顶栏排序菜单）
- 歌单（增删改 / 排序 / 多选加入）、播放队列、真随机 / 伪随机、单曲 / 列表循环
- 播放统计（收听时长流水）与 24 小时分布，年度报告数据已就绪
- **听歌热力图**（听歌日记页）：GitHub 风格年历色块，五级深浅（莫奈动态取色），
  记录每天播放歌曲数 / 应用启动次数 / 收听时长 / 高频歌曲标签

### 歌词
统一歌词模型 `Lyrics`（原文 / 翻译 / 罗马音 / 逐字音节），支持格式：

| 格式 | 说明 |
|---|---|
| LRC | 标准 `[mm:ss.xx]`，一行多时间戳、`[offset:]`、同时间戳双行视为翻译 |
| 增强型 LRC | `<mm:ss.xx>`（A2 扩展）与 `<毫秒,时长>`（LX / KRC）两种写法 |
| QRC / KRC | `[毫秒,时长]` 行时间戳 + `(毫秒,时长)` 逐字 |
| TTML | Apple Music / AMLL 风格，含 `x-translation` / `x-roman` 角色 span、嵌套 span |
| SRT / VTT | 字幕式歌词，双语字幕第二行自动作为译文 |
| 纯文本 | 无时间轴兜底 |

歌词来源优先级：**文件内嵌**（ID3v2 USLT/SYLT、FLAC VorbisComment、MP4 ©lyr、OGG/Opus tags）
→ **同名外挂文件**（`.lrc/.ttml/.srt/.qrc/.krc/.vtt/.txt`，支持 `歌名.zh.lrc` 翻译文件与 GBK 编码）
→ **在线音源**（平台公开接口或音源脚本）。

带逐字时间轴的歌词由**自研纯 Compose 渲染器**以音节级卡拉 OK 渲染：
逐字填充、翻译行跟随、非当前行渐隐、当前行自动滚动居中。
（曾接入 accompanist-lyrics-ui，但其 native 文本引擎在部分设备上渲染黑块
且 1.0.14+ 的 Maven 发布产物损坏，故移除并自研实现。）普通行时间轴歌词自动退化为整行进度填充。

在线歌词优先从 **amll-ttml-db** 逐字库（jsDelivr CDN）按平台歌曲 ID 直取 TTML，
命中即为完整逐字 + 翻译，无需登录；未命中时回退平台官方 LRC / 翻译。

### 在线
- **聚合搜索**：酷我 / QQ 音乐 / 网易云 / 酷狗 / 咪咕 五平台并发搜索，交错展示；也可切单平台
- **在线歌词**：搜索结果自动匹配平台歌词（含翻译 / 罗马音 / 逐字）
- **自定义音源**（LX 协议，QuickJS 沙箱）：
  - 兼容 LX Music 用户脚本，本地 `.js` 或 URL 导入
  - 导入前静态校验：空内容 / 网页直链 / 非 LX 协议脚本会被拦截并给出原因
  - URL 导入失败逐层提示（域名解析 / 超时 / HTTP 状态码 / Content-Type）
  - 初始化超时保护（20 秒），脚本不响应不会卡死
  - 脚本能力表（支持平台 / 音质）落库展示，URL 导入的脚本可一键更新
- **播放链路**：官方接口优先 → 音源脚本兜底：
  - 网易云：优先走**官方公开取流 API**（免登录播免费歌曲，填 Cookie 可解锁 VIP），
    失败再回退 weapi 加密接口 / 音源脚本；CDN 地址自动升级 https
  - 其它平台：直接向音源脚本请求临时播放地址 → ExoPlayer 播放
  - 失败时以明确原因提示（未导入音源 / 官方接口失败 VIP 或无版权 / 脚本不支持该平台 / 脚本报错）
  - Manifest 已开启 `usesCleartextTraffic`，兼容返回 http 明文地址的音源

> 在线搜索与歌词使用平台公开接口，**不需要**音源脚本；
> 音源脚本仅负责解析各平台的加密取流接口。

## 技术栈

| 项 | 版本 |
|---|---|
| Kotlin | 2.2.21 |
| AGP | 8.13.2 |
| Gradle | 8.14.5 |
| compileSdk / targetSdk | 36 |
| minSdk | 24 |
| compose ui/foundation/animation | 1.11.4 |
| material3 | 1.5.0-alpha18（Expressive API 所在版本） |
| media3 | 1.11.0 |
| room | 2.7.2 |
| quickjs-wrapper | 3.2.3 |
| accompanist-lyrics-ui / core | 1.0.16 / 0.4.2 |

> material3 不走 compose-bom，单独锁 `1.5.0-alphaXX`。
> 版本上限受 AGP 约束：AGP 8.13.2 最高支持 compileSdk 36，
> material3 1.5.0-alpha19+ / compose ui 1.12.0+ 等要求 compileSdk 37 + AGP 9.1.0。

## 目录结构

```
app/src/main/java/com/wxjxpp/musicplayer/
├── MainActivity.kt
├── app/                         应用壳层
│   ├── AppContainer.kt          依赖装配（唯一的实现绑定点）
│   ├── AppViewModel.kt          壳层状态（曲库 / 搜索 / 歌词 / 音源）
│   ├── MusicPlayerApp.kt        抽屉 + 共享元素动画 + 路由分发 + 全局提示
│   └── navigation/
├── core/                        与 UI 无关的能力层
│   ├── model/                   领域模型（Song / Lyrics / PlaybackState ...）
│   ├── data/                    Room + DataStore 仓库实现
│   ├── db/                      Room 实体 / DAO / 映射
│   ├── player/                  Media3 播放控制器（含在线取流回调）
│   ├── source/                  音源契约 + 本地 / 在线音源实现
│   │   └── online/              五平台适配（搜索 + 歌词，公开接口）
│   ├── lyrics/                  歌词解析器（LRC/ELRC/TTML/SRT/QRC）+ 内嵌读取 + 定位
│   ├── search/                  本地匹配 + 在线聚合搜索
│   ├── userapi/                 QuickJS 音源引擎 / 脚本存储 / 调用客户端
│   ├── net/                     轻量 HTTP 客户端（gzip / 超时 / 错误流）
│   ├── crypto/                  脚本可用的 AES / RSA 工具
│   ├── scanner/                 MediaStore 扫描与元数据读取
│   └── together/                一起听传输层抽象
├── feature/                     页面
│   ├── home/ player/ playlist/  本地功能页面
│   ├── search/                  搜索页（本地 + 在线聚合）
│   ├── userapi/                 自定义音源管理页
│   └── settings/
└── ui/                          主题 token 与可复用组件
```

## 架构约定

1. **音源即插件**：`MusicSource` 接口 + `SourceCapability` 能力声明，
   UI 按能力显示入口，不写 `if (source == "xxx")`。新增平台 = 新增一个
   `OnlinePlatform` 实现加进 `defaultOnlinePlatforms`。
2. **歌词格式即插件**：`LyricsParser` + 注册表按特征嗅探自动选择，
   新增格式不影响渲染层。
3. **脚本协议兼容 LX**：`assets/script/user-api-preload.js` 与 LX Music 一致，
   现有用户脚本可直接使用；脚本只提供 `musicUrl` / `lyric` / `pic`，
   搜索由宿主原生实现（这也是 LX 协议的设计）。
4. **UI token 化**：尺寸走 `AppTheme.dimens`，颜色走 `colorScheme`，
   动画走 `MaterialTheme.motionScheme`。

## 构建

GitHub Actions（`.github/workflows/`）：
- push 到 `dev` / `main` 触发 Debug 构建与 Release 构建
- Release 构建自动发布到 GitHub Releases（非 main 分支为预发行）

本地：

```bash
./gradlew assembleDebug
```

## License

见 [LICENSE](LICENSE)。
