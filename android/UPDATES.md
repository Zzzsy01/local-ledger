# 应用更新发布

源码与安装包统一放在 [Zzzsy01/local-ledger](https://github.com/Zzzsy01/local-ledger)。当前版本 1.4.0（versionCode 8），1.3.1 起预置更新地址：

```text
https://github.com/Zzzsy01/local-ledger/releases/latest/download/update.json
```

## 手机使用

1. 1.4.0 首次打开会启用「自动检查并通知」，Android 13 及以上请允许通知。可在「设置 → 应用自动更新」调整开关、保存更新设置，或随时点「检查更新」。此后保留手动关闭的选择。
2. 发现新版本后查看说明、下载；客户端校验完成后点「安装更新」，按系统提示确认。首次安装可能需要允许随手账安装应用。
3. 已安装 1.3.0 或更早版本的手机，可以填写上述地址并保存，也可以从 [最新版本](https://github.com/Zzzsy01/local-ledger/releases/latest) 下载 APK 手动覆盖安装一次。旧版未开启自动检查时，首次进入 1.4.0 也会启用一次；后续沿用已保存的更新设置。

自动检查通过 Android JobScheduler 每日执行，并在应用打开时补查。系统省电策略可能推迟检查；不能静默安装。Android 13 及以上需要通知权限。关闭通知仍可手动检查。

1.3.3 起，系统强行停止应用并取消后台任务后，下次打开应用会按已保存的自动检查开关恢复任务。

账本保存在本机，更新请求不携带账本。覆盖安装要求同包名、同签名与递增版本号；不要卸载旧版。当前交付仍使用原 Debug 签名，换正式签名前应先明确升级路径。

## 发布者操作

1. 修改 `app/build.gradle.kts` 的 `versionCode` 与 `versionName`，构建 APK，并完成本次改动需要的检查。保留原签名密钥；不要因移动 `ANDROID_USER_HOME` 生成不同的 Debug 密钥。
2. 提交并推送本次源码到主仓库。准备 UTF-8 更新说明文件，例如 `build/release-notes.md`。
3. 在 `android` 目录调用脚本；`BuildToolsPath` 为本机 Android SDK 的 Build Tools 35.0.0 目录。

```powershell
# 先生成本地附件，核对版本、下载地址、哈希和说明。
.\scripts\publish-update.ps1 -NotesFile .\build\release-notes.md -BuildToolsPath "$env:ANDROID_HOME\build-tools\35.0.0" -PrepareOnly

# 使用已登录的 GitHub CLI 创建草稿，上传两个附件，然后发布为最新版本。
.\scripts\publish-update.ps1 -NotesFile .\build\release-notes.md -BuildToolsPath "$env:ANDROID_HOME\build-tools\35.0.0"
```

脚本从实际 APK 读取包名、版本与最低 Android 版本，验证签名与已交付包一致，并计算 SHA-256。附件生成在 `build/releases/v版本/`，不提交到源码；版本标签指向当前已推送的源码提交。自定义 APK 可通过 `-ApkPath` 指定。

每个 Release 包含 `ledger-版本.apk` 和 `update.json`。固定更新地址总是读取最新版的 JSON；JSON 内的 APK 地址使用具体版本标签，避免手机在跨版本发布时下载到不同版本的文件。仅推送源码不会更新手机，需要完成 Release 发布。

客户端限制版本信息为 64 Ki 字符、安装包为 128 MiB；下载后核对 SHA-256、包名、版本与本机签名，失败时不调用安装器。发布者的原始签名密钥必须自行保留，不提交 GitHub。

首次授权安装来源可能使系统重启应用；1.3.2 起再次检查更新时会重新校验本机已下载 APK，与当前清单一致则直接继续安装。缓存不匹配当前版本时重新下载。

平台依据：[GitHub 固定版本附件地址](https://docs.github.com/en/repositories/releasing-projects-on-github/linking-to-releases)、[Android PackageInstaller](https://developer.android.com/reference/android/content/pm/PackageInstaller)、[JobScheduler](https://developer.android.com/reference/android/app/job/JobScheduler)。
