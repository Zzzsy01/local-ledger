# UI 视觉升级验证

日期：2026-10-07 至 2026-10-08。发行版本为 1.7.0 / 12，沿用原 GitHub 更新渠道。

## 构建与素材

- `:app:assembleDebug :app:lintDebug --offline --no-daemon` 成功。Lint 0 个错误、16 个警告：13 个 UseKtx、2 个 ConfigurationScreenWidthHeight、1 个 Gradle 版本建议。
- 递增至 1.7.0 / 12 后重新构建和 Lint 通过；现有 `UpdateManifestTest` 的 1 项解析与拒绝无效清单检查通过。
- 三张 ImageGen 透明插画已实际打包进 APK；512 px 无损 WebP 共 457,850 字节。主题参考、素材提示词见 [设计说明](../DESIGN.md) 与 [素材记录](../design-assets.md)。
- 修改集中于 Compose 页面、主题、本地素材和检查脚本；数据库、金额计算、网络服务及依赖未变。

## 运行检查

Android 11 / API 30 专用模拟器，同签名 `adb install -r` 覆盖安装，未卸载或清除应用数据。最终采用明亮奶白、薄荷渐变和杏橙按钮。

- 明亮配色在递增发行版本号前完成奶白浅色的 10 个检查：笔记、待办、生活、藏品列表、藏品网格、心愿、账单、报表、工具、设置。证据：`build/ui-refresh/bright-final/`。后续发行构建只递增版本号，未再修改 UI 或数据逻辑。
- 布局版本在提亮前已完成常规浅色、深色及 320 dp / 1.3 倍字体下各 10 个检查；提亮只调整颜色，没有改动尺寸或导航。对应证据为 `light-final/`、`dark-final/`、`narrow-final/`，不作为最后一次配色构建的截图。
- 笔记状态切换后显示已完成筛选，再恢复全部；藏品展开位置筛选、收起筛选及列表／网格切换可用。窄屏概要纵排后，向上滑动使这些控件位于悬浮按钮上方再操作。
- 隐藏金额时，账单、心愿与报表可见 UI 文本和朗读描述未出现货币数值，金额显示为掩码；截图确认预算进度和心愿百分比收起，报表显示图表隐藏说明。
- 最终杏橙色 `#FFB86C` 与深棕按钮文字 `#4A2B15` 的计算对比度约 7.48:1，奶白主题选中按钮的绿色与白字约 5.18:1。概要金额深色文字与渐变背景端点约 8.2–8.5:1。
- 最终构建另检查深色账单、报表、心愿和生活，以及澄蓝账单；金额隐藏时账单金额和预算进度均收起，文本／朗读描述未出现货币数值。证据：`build/ui-refresh/bright-variants/`。结束时恢复奶白浅色、显示金额，本次 AndroidRuntime 日志未发现崩溃。

最终明亮版截图拼图为项目根目录 `output/android/ui-refresh-preview.png`。未验证实体手机或完整 TalkBack 流程。

检查脚本位于 [check-ui.py](../scripts/check-ui.py)，使用已安装测试应用的专用模拟器，在 `android` 目录执行：

```powershell
python scripts/check-ui.py "$env:ANDROID_HOME\platform-tools\adb.exe" emulator-5554 build/ui-refresh/bright-final
# 模拟器已设为 320 dp、1.3 倍字体时：
python scripts/check-ui.py "$env:ANDROID_HOME\platform-tools\adb.exe" emulator-5554 build/ui-refresh/narrow-final --narrow
```

## 发行安装包

- 路径：`app/build/outputs/apk/debug/app-debug.apk`，57,938,754 字节。
- SHA-256：`03ceddffd9dc4e863b69f006c91cfb86c46c8917860cb0c08899f501da7d07de`。
- 签名证书 SHA-256：`447144514e8fb7d24c34a515f8aa70d014cebb4599629947f88302dc839956c9`，与原安装包一致。
- 1.6.0 原发行包与验证记录继续保留；1.7.0 使用新标签和新安装包文件名，原固定更新地址继续沿用。

## 公开发行核对

- 2026-10-07 已使用原 `scripts/publish-update.ps1` 正式发布 [v1.7.0](https://github.com/Zzzsy01/local-ledger/releases/tag/v1.7.0)，先上传草稿附件，再公开为最新版。标签对应源码提交 `7e28bc98124fee4c793762eeb16ebe7b9ac6a3fb`。
- 原固定 [更新地址](https://github.com/Zzzsy01/local-ledger/releases/latest/download/update.json) 与清单指定的 [APK](https://github.com/Zzzsy01/local-ledger/releases/download/v1.7.0/ledger-1.7.0.apk) 均经匿名 HTTPS 下载返回 200。清单为 1.7.0 / 12；公开 APK 字节数与 SHA-256 均与上述发行包一致。
- 专用模拟器的 1.6.0 / 11 客户端通过原 JobScheduler 检查任务收到「随手账 1.7.0 可更新」通知；应用内「检查更新」显示「发现新版本 1.7.0」。测试主动触发既有任务，不代表实体手机已收到通知。
- 旧客户端从原渠道完成下载，显示「校验通过，可以覆盖安装并保留数据」。缓存 APK 的 SHA-256 与公开发行包一致；客户端已有包名、版本和签名检查保持启用。
- 2026-10-08 从旧客户端「安装更新」进入 Android 系统安装器并完成覆盖升级；安装器显示 `App installed.`，安装后实际版本为 1.7.0 / 12。未卸载、未清除数据。
- 升级后仍可见原 3 条笔记（含「周末的小计划」）、2 件藏品（含 `MuseumCamera`）、2 个待买心愿（含「微单相机」）和本月支出 ¥96.00、预算 ¥5000.00。本次核对可见记录，未执行全部数据库字段快照比较。
- 升级后原自动检查开关仍开启，更新地址未变；再次检查提示「当前已是最新版本」。AndroidRuntime 日志未发现崩溃，结束时恢复笔记页、440 dpi 与 1.0 倍字体。

客户端截图与 UI 层次证据：`build/releases/v1.7.0/client-check/`，包含旧版发现更新、下载校验、系统安装完成、升级后的主要页面与最新版检查。公开下载核对文件位于 `build/releases/v1.7.0/`。未验证实体手机；日常自动检查时间仍由 Android 系统调度。
