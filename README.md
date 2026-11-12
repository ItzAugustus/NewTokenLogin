<p align="center">
  <img src="docs/icon.png" width="160" alt="TokenLogin">
</p>

<h1 align="center">TokenLogin</h1>

<p align="center">
  <a href="https://fabricmc.net/"><img alt="Minecraft" src="https://img.shields.io/badge/Minecraft-1.21.11-3C8527"></a>
  <a href="https://fabricmc.net/"><img alt="Fabric" src="https://img.shields.io/badge/Loader-Fabric-DBD84B"></a>
  <a href="https://adoptium.net/"><img alt="Java" src="https://img.shields.io/badge/Java-21-ED8B00"></a>
  <a href="LICENSE"><img alt="License" src="https://img.shields.io/badge/License-CC0--1.0-lightgrey"></a>
</p>

<p align="center">
  用 Refresh Token 登录 Minecraft 的 Fabric 客户端模组。<br>
  把 Refresh Token 换成 Access Token 并直接切换游戏会话，支持多账号、头像、剩余时间和自动续期。
</p>

---

## 功能

| 功能 | 说明 |
| --- | --- |
| Refresh Token 登录 | 粘贴 Refresh Token，自动走完整的微软认证链换成 Access Token，并替换当前游戏会话 |
| 容错的导入 | 单独一行 Token、启动器账号 JSON、OAuth 返回的 JSON、带引号的 JSON 片段都能识别；Token 里的 `!` `*` `%` `#` 等符号不会被截断 |
| 多账号管理 | 账号列表、搜索、当前账号标记，每个账号可以单独登录 / 刷新 / 删除 |
| 头像 | 使用 Mojang 官方会话服务器拉取 64×64 皮肤，按 `PlayerSkinDrawer` 绘制（脸和帽子层、粗细手臂比例都正确），失败时回退到 mc-heads.net |
| 时间信息 | 显示 Access Token 还有多久过期、Refresh Token 是多久以前导入的、当前是否开启自动刷新 |
| 自动续期 | 剩余不到 10 分钟时自动用 Refresh Token 续期；连续失败 3 次后把该账号标记为「Refresh Token 失效」，不再反复请求 |
| 手动刷新 | 账号右侧的「刷新」按钮随时可以主动续期，不需要等自动刷新 |
| 界面按钮 | 多人游戏界面加一个入口按钮，可放在四个角，也可以完全隐藏；隐藏后还能用快捷键或 Mod Menu 进入 |
| 与其他模组共存 | 按钮会避开屏幕上已有的控件（例如 ViaFabricPlus 的按钮），窗口缩放后重新排版；每个界面实例只保留一个按钮 |
| 快捷键 | 默认未绑定，在「选项 → 控制 → 按键绑定 → 多人游戏」里给「打开 Token 账号管理」设置按键 |
| 设置 | 按钮位置、自动刷新开关，保存在 `config/tokenlogin.json` |
| 本地化 | 简体中文与英文 |
| 可选联动 | 安装 Mod Menu 后，模组列表里会出现设置入口 |

## 环境要求

| 项目 | 版本 |
| --- | --- |
| Minecraft | 1.21.11 |
| Fabric Loader | 0.19.5 或更高（最低 0.17.3） |
| Fabric API | 0.141.6+1.21.11 |
| Java | 21 |
| Mod Menu | 17.0.1（可选，仅用于设置入口） |

本模组是**纯客户端**模组：只需要装在自己的客户端，服务器不需要安装。

## 安装

1. 安装 [Fabric Loader](https://fabricmc.net/use/)（选择 Minecraft 1.21.11）。
2. 把 [Fabric API](https://modrinth.com/mod/fabric-api) 和本模组的 jar 一起放进 `.minecraft/mods/`。
3. 启动游戏，进入「多人游戏」界面，点击右上角的「Token 登录」按钮。

构建好的 jar 在 [Releases](../../releases) 页面下载；也可以自己按下面的步骤从源码构建。

## 使用

1. 打开「Token 登录」→「新增账号」，把 Refresh Token 粘贴进输入框（也可以直接粘贴整段启动器账号 JSON），点「导入」。
2. 导入成功后账号会出现在列表里，头像、Access Token 剩余时间、Refresh Token 导入时间都会显示出来。
3. 点账号右边的「登录」切换会话，然后**重新连接一次服务器**生效。
4. 需要时点「刷新」手动续期；自动刷新开着时，剩余不到 10 分钟的账号会在后台自动续期。

## 从源码构建

需要 JDK 21 和网络连接（首次构建要下载 Gradle、Minecraft 与 Fabric 依赖）。

```bash
git clone https://github.com/<your-name>/TokenLogin.git
cd TokenLogin
./gradlew build
```

产物：

- `build/libs/tokenlogin-0.1.0-r11.jar` —— 模组本体
- `build/libs/tokenlogin-0.1.0-r11-sources.jar` —— 源码包

开发时可以直接启动带模组的客户端：

```bash
./gradlew runClient
```

> Windows 用 `gradlew.bat`；如果 `./gradlew` 提示找不到 Gradle，请确认已经安装 JDK 21 并设置好 `JAVA_HOME`。

## 项目结构

```text
TokenLogin/
├── src/main/java/com/kcl/tokenlogin/
│   ├── TokenLogin.java                     模组常量（id、名称、版本、日志）
│   └── client/
│       ├── TokenLoginClient.java           客户端入口：读配置、注册界面与按键
│       ├── TokenLoginKeys.java             可选快捷键
│       ├── auth/
│       │   ├── MsAuth.java                 Refresh Token → XBL → XSTS → Access Token
│       │   ├── AccountService.java         导入 / 刷新 / 登录的调度与重试
│       │   ├── AccountStore.java           config/tokenlogin-accounts.json 的读写
│       │   ├── Account.java                单个账号的数据结构
│       │   ├── TokenParser.java            从任意粘贴文本里提取 Refresh Token
│       │   └── UserManager.java            替换运行中的游戏会话
│       ├── avatar/AvatarCache.java         皮肤下载、缓存与界面绘制
│       ├── config/TokenLoginConfig.java    config/tokenlogin.json 的读写
│       ├── mixin/                          访问并修改 MinecraftClient 的 Session
│       ├── ui/                             账号管理 / 导入 / 设置 / 多人界面按钮
│       └── compat/TokenLoginModMenu.java   Mod Menu 集成
├── src/main/resources/                     fabric.mod.json、语言文件、图标
├── gradle/wrapper/                         Gradle Wrapper（9.5.1）
├── docs/icon.png                           仓库图标
└── .github/workflows/build.yml             CI：每次推送自动构建 jar
```

## 工作原理

1. 用 Refresh Token 调用 `login.live.com/oauth20_token.srf` 换出微软 access token。
2. 依次换取 Xbox Live token（`user.auth.xboxlive.com`）和 XSTS token（`xsts.auth.xboxlive.com`）。
3. 用 `identityToken` 调用 `api.minecraftservices.com/authentication/login_with_xbox`，拿到 Minecraft Access Token。
4. 调用 `api.minecraftservices.com/minecraft/profile` 取到 UUID 和玩家名。
5. 通过 Mixin 的 accessor 把 `MinecraftClient` 里的 `Session` 换掉，游戏之后的连接就用这个账号。

自动刷新在客户端每 tick 检查一次（最多每 20 秒真正扫描一次）；刷新失败后该账号 3 分钟内不再重试，连续失败 3 次才判定 Refresh Token 失效。

## 数据与安全

- 配置：`config/tokenlogin.json`（按钮位置、自动刷新开关）
- 账号：`config/tokenlogin-accounts.json`
- 皮肤缓存：`config/tokenlogin/skins/`

> **`config/tokenlogin-accounts.json` 里保存着明文的 Refresh Token，等同于账号凭据。**
> 不要把该文件、包含它的整合包或截图发给别人，也不要上传到公开网盘或论坛。
> 删除模组里的账号只会删掉本地记录，不会吊销授权；要真正撤销授权，请到微软账号的「应用和权限」页面移除授权。

本模组不会向除 Mojang / 微软官方认证服务器和你选用的皮肤回退服务（mc-heads.net）之外的任何地址发送数据。

## 常见问题

**导入后账号没出现 / Refresh Token 消失了？**
请使用最新版本。旧版本在部分剪贴板内容上会截断 Token，现在已修复；如果仍然失败，界面会显示具体错误原因。

**登录成功但进服务器还是原来的账号？**
登录只替换本地的会话，需要重新连接一次服务器。

**服务器里显示的还是旧皮肤？**
皮肤来自 Mojang 会话服务器，切换后重新进服即可；头像缓存 2 分钟内的失败请求不会重复发起。

**提示「该账号没有 Minecraft: Java Edition」**
这个微软账号没有购买 Java 版，或者还没创建 Xbox 档案。

**提示儿童账号 / 地区不支持**
需要在微软家庭里完成家长同意，或确认账号所在地区支持 Xbox Live。

## 参与贡献

欢迎提 Issue 和 Pull Request。提交 PR 前请确认 `./gradlew build` 能通过，并尽量保持代码风格和现有注释一致。

## 许可证

本项目以 [CC0 1.0 Universal](LICENSE) 发布，可自由使用、修改和分发。Minecraft 与 Fabric 相关组件遵循其各自的许可证。

---

## English

**TokenLogin** is a client-side Fabric mod for Minecraft 1.21.11 that logs you in with a refresh token. It converts the token into a Minecraft access token, swaps the running game session, and manages multiple accounts with avatars, expiry countdowns, manual refresh and automatic renewal shortly before the access token expires.

- Requires Minecraft 1.21.11, Fabric Loader 0.17.3+, Fabric API, Java 21. Mod Menu is optional.
- Client side only. Build with `./gradlew build` on JDK 21; the jar appears in `build/libs/`.
- Refresh tokens are stored in plain text in `config/tokenlogin-accounts.json` — never share that file.
- Licensed under CC0 1.0.
