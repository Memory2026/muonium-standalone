# Muonium Player · 独立版（不启动 Minecraft）

从 `MuoniumPlayer-1.21.11-fabric-2.2.0.jar` 剥离出的本地音乐播放器：后端纯 Java，前端用系统浏览器打开。不启动 MC，不联网下载额外依赖。

## 运行前提

- 已安装 **JDK 21**。启动脚本会自动定位 Java：优先 PATH → `JAVA_HOME` → 常见安装目录（Windows：Program Files 等；macOS：`/usr/libexec/java_home` 与 `/Library/Java/JavaVirtualMachines`），**无需手动配置 PATH**。
- Windows：自带 Edge；macOS：自带 Safari 或安装 Chrome。
- 首次使用为**游客未登录**状态，网易云可搜索、播放试听曲目；VIP/高音质曲目可能受限。

## 启动

### Windows
双击 `run-win.bat`。脚本会在后台起本地后端，并自动用 Edge 以独立窗口打开界面。

### macOS
终端进入本目录：

```sh
chmod +x run-mac.sh
./run-mac.sh
```

后端起后自动用默认浏览器打开 `http://127.0.0.1:18391`。

> 若脚本提示找不到 java，请确认已安装 JDK 21；可在终端执行 `/usr/libexec/java_home -v 21` 查看安装路径。

## 目录结构

```
muonium-standalone/
├─ classes/        编译后的后端类（app.Server 入口）
├─ web/            前端页面 index.html
├─ libs/           运行所需 jar（主 mod + 内嵌库 + Kotlin 运行时 + commons-lang3）
├─ src/app/        后端源码备查
├─ run-win.bat     Windows 启动脚本
└─ run-mac.sh      macOS 启动脚本
```

## 当前版本能力

- 网易云关键词搜索，点选播放，上一首/下一首、暂停、进度拖动、音量。
- 热搜词展示，点击即搜；**排行榜**内置 65 个网易云榜单，点击即加载曲目。
- 音质档位可选：标准 / 较高 / 极高（未登录时无损、HiRes 不可用，会自动回退到较高档）。
- 背景图可在界面左侧「更换背景图」本地导入；图片保存在后端本地 `data/` 目录，重开界面、换浏览器都保留。
- 播放进度条上方叠加**缓冲/下载进度**条，显示已缓存范围。
- **歌词同步显示**：按时间轴逐行高亮、平滑滚动，含制作人员（作词/作曲等）行与翻译；点击任意一句可跳转到该句时间。
- **收藏歌单**：点歌曲右侧或播放栏的心形即可收藏/取消，收藏列表保存在后端本地 `data/favorites.json`；左侧「查看收藏」打开收藏队列，按顺序连播。
- **专辑封面**：播放栏左侧显示当前歌曲封面，封面图经后端代理转发（带浏览器标识，避免防盗链）。
- 音频经本地后端代理出流，支持拖动进度（Range）；整曲首次播放后落盘到 `data/cache/`，重复播放直接读本地，缓存上限 300MB 自动清理最旧。

## 已知限制（后续可补）

- 未登录，无法拉取个人歌单/收藏；部分 VIP 曲目无播放链接；无损/HiRes 需登录会员。

## 技术说明

- 后端入口：`app.Server`，本地 HTTP 服务 `127.0.0.1:18391`：
  - `GET /api/search?kw=` 搜索歌曲（JSON）
  - `GET /api/hot` 热搜词
  - `GET /api/charts` 排行榜列表
  - `GET /api/chart?pid=` 某榜单曲目
  - `GET /api/lyric?id=` 歌词（原文 + 翻译 LRC）
  - `GET/POST /api/favorites` 收藏列表（POST 切换收藏，body 含 id/name/artist/duration/source/cover/fav）
  - `GET /api/cover?url=` 封面图代理转发
  - `GET /api/stream?id=&quality=` 音频代理流（quality: STANDARD/HIGHER/EXHIGH）
  - `GET/POST /api/bg` 读取/保存背景图（存 `data/` 目录）
- 播放链路走纯 JavaSound，不依赖 LWJGL/OpenAL 原生库，便于跨平台。
