# 界面参考与本地素材

2026-10-04，1.4.1 优化原生 Compose 页面。保留备忘录默认主页与两套独立导航；不新增服务或依赖，数据库和备份仍为版本 4。

## 参考界面

| 官方来源 | 本次采用的设计思路 |
|---|---|
| [Notesnook](https://notesnook.com/) / [自定义界面](https://notesnook.com/help/customizing-notesnook) | 标题、内容摘要和更新时间分层；压缩首页介绍区，突出实际备忘录 |
| [Todoist 功能界面](https://www.todoist.com/features) | 完成作为主要操作，管理操作降低视觉权重，状态易于辨认 |
| [Cashew](https://cashewapp.web.app/) / [功能说明](https://cashewapp.web.app/faq.html) | 目标剩余金额与进度突出显示，预计价格和已攒金额作为辅助信息 |
| [Actual Budget 报表](https://actualbudget.org/docs/reports/) | 收支概要和图表分别放在清晰的卡片容器中 |
| [Memos](https://usememos.com/) / [记录功能](https://usememos.com/docs/usage/memos) | 快速新增、搜索、置顶与内容浏览 |

这些网页用于参考布局与交互，没有复制项目实现代码或截图作为应用素材。

## 图标来源

采用 [Lucide 官方图标](https://lucide.dev/icons/)，源文件固定至 [500620a2e8123f8d1db191538886dc0c223f69a9](https://github.com/lucide-icons/lucide/tree/500620a2e8123f8d1db191538886dc0c223f69a9/icons)。SVG 转为 Android VectorDrawable，保留 24 × 24 视口、圆角端点与连接，笔画宽度调整为 1.8；全部打包在本地，离线显示。

| 本地资源 `ic_ui_*.xml` | Lucide 文件名 |
|---|---|
| notebook | notebook-pen |
| wallet | wallet-cards |
| report | chart-no-axes-combined |
| settings | settings-2 |
| more | ellipsis |
| back | arrow-left |
| chevron_right | chevron-right |
| arrow_up_right / arrow_down_right | arrow-up-right / arrow-down-right |
| heart / package / search / pin / check / trash / plus / copy / goal | 同名 |

遵循 [Lucide 授权](https://lucide.dev/license)，保留该固定版本的完整 ISC 与继承自 Feather 的 MIT 授权文件，随 APK 打包在 `app/src/main/assets/licenses/lucide.txt`。各向量资源注释标明源文件和版本。

## 后续修改注意

- 金额可以换行，不能以省略号隐藏末位；名称和内容摘要可以省略。
- 使用主题的语义颜色，以同时适配澄蓝、青绿与浅色、深色；主要页面统一采用 20 dp 外边距。
- 主要操作保持至少 48 dp 点击范围，列表内操作与卡片编辑独立处理。
- 图标更新需同时保留来源、固定版本及授权文件。不要依赖运行时远程图标地址。

## 1.5.0 PDF 与链接参考

[PalmNote](https://github.com/PickGear/PalmNote) 的奶白背景、白色圆角卡片、橙色新增与生活分组用于生活、物品、账户概览；PDF 的笔记/待办截图用于彩色卡片、分组、勾选、搜索和排序。[Cashew 分支](https://github.com/wenpengcheng0413/cashew-budget-mobile-app) 的日历、热力与预算组织用于账单浏览。

新增奶白主题，不重置既有用户的配色选择。浅深色保留；彩色笔记使用固定深色正文确保可读。新增日历图标为本项目绘制的基本几何向量，其他 Lucide 来源与授权保持原样。用户 PDF 和截图只用于本地参考，不作为 APK 或公开仓库素材。
