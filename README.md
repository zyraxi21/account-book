# 记账本

使用 Kotlin、Jetpack Compose 和 Microsoft Fluent UI 构建的 Android 本地记账应用。包名为 `io.github.zyraxi21.accountbook`，最低支持 Android 14（API 34），编译及目标版本为 Android 17（API 37）。

当前版本为 **2026.10.08.1**（`versionCode = 202610081`）。[发布说明](release-notes/2026.10.08.1.md)

## 功能与计算口径

- **资产**：每个自然月保存一份资产表，支持补录、编辑、删除，登记日期、渠道余额及负债。界面只选择日期，落库时与保存时的上海时间组合；所属月份仍按 `Asia/Shanghai` 确定。
- **收入**：按月浏览、登记、编辑或删除收入，汇总所选月份的收入，以及截至该月月底的历史累计收入；后续月份的记录不计入。与资产页共享所选月份，可粘贴工行短信解析，也可授权后自动登记新收到的工行收入短信。
- **渠道**：初始提供银行、支付宝、微信，在登记资产对话框内集中添加、改名、删除及拖动排序；设置页不再重复提供渠道管理。点渠道标题右侧的“＋”打开新增弹窗，原有金额草稿保留；编辑和删除使用图标按钮，所有启用渠道以卡片呈现，金额留空按 0 元保存。新增、改名和删除即时生效；金额与卡片顺序在保存资产时用同一事务提交，取消登记不保存排序。再次登记使用当前渠道排序，金额重新填写；编辑历史月份时保留原余额、历史名称及已删除渠道的历史明细。
- **隐私**：顶栏隐私按钮切换账务显示。自动隐藏开启时，冷启动及从后台返回均隐藏；关闭时，冷启动显示，返回时恢复离开前的显示／隐藏选择。后台临时遮挡不改变手动选择；金额、项目、渠道名称及登记时间同时从画面和无障碍语义中移除，编辑面板及键盘关闭，草稿保留在内存。配置变化保留当前选择及草稿，设置读取完成前保持遮挡。
- **设置与关于**：自动登记、启动隐藏、前台截屏和万位分隔均用开关实时保存，不弹成功提示。金额默认千位分隔（`12,345,678.90`）；启用万位分隔后每四位分组（`1234,5678.90`），仅影响显示，输入及 JSON／CSV 金额保持无分隔符的精确值。显示偏好加密保存，重启和覆盖导入保留设备选择。数据仅在本机保存的说明位于“关于”页面，“关于”页面底部提供“检查更新”。
- **导入导出**：设置页可把整本账导出为 JSON 或 CSV，也可从这两种格式导入。导出与导入都通过系统文件选择器（SAF）完成，用户自行选择保存位置和来源文件。

## 检查更新

应用启动时自动在后台检查更新，“关于”页面底部也保留手动“检查更新”。两个入口共用 GitHub Releases 查询，向 `https://api.github.com/repos/zyraxi21/account-book/releases` 发 GET 请求，取最新正式版本（排除草稿、预发布，以及标签带 `-rc`、`-beta` 等后缀的版本），与当前版本比较：

- 当前版本号从 `versionName` 读取，来源是构建配置，代码中不硬编码。
- 版本号按 `YYYY.M.D.N` 各段数值逐段比较，省略的末尾段补 0，因此 `2026.10.9.1` 小于 `2026.10.10.1`。
- 每次新启动自动检查一次，返回前台及旋转屏幕不重复请求；无更新、没有正式发布或查询失败时保持静默。
- 手动检查时，无更高版本提示“当前已是最新版本”；仓库没有发布记录或只有预发布时给出各自的说明。
- 有新版本时在当前页面弹窗展示版本号与更新说明，无需先打开关于页；可选择“稍后”或“立即下载”，确认前不会下载安装包。应用处于后台时保留结果，回到前台后再显示弹窗。没有 `.apk` 资源时禁用下载并说明原因。
- 下载交由系统 `DownloadManager` 完成，每次使用独立文件名，文件落在应用外部私有目录，界面轮询显示进度；下载完成后按该任务 ID 获取 content URI 并调起系统安装器，避免误用残留旧包。Android 8.0 起先检查未知来源安装授权，未授权时引导到系统设置页。
- 手动检查失败时按原因分类提示：无网络、超时、GitHub 限流、仓库不存在、服务异常、响应无法解析；确认下载后另有空间不足与传输失败提示，下载过程中可取消。

这是应用唯一的出网路径，只读取公开发布信息，不上传任何账务数据。原“应用不申请联网权限”的表述仅适用于账务功能，现补充 `INTERNET` 权限专供检查更新使用。

| 字段 | 计算方式 |
| --- | --- |
| 总资产 | 各渠道金额之和 |
| 净资产 | 总资产 − 负债 |
| 与上月差额 | 本月总资产 − 上月总资产 |
| 估算月支出 | 上月净资产 ＋ 本自然月收入 − 本月净资产 |

缺少相邻上月资产表时，差额和估算月支出显示“暂无上月记录”。差额、净资产和估算支出允许为负；估算支出也会受投资涨跌、资产转入等变化影响。修改历史资产或收入后，界面通过 `Flow` 重新计算相关汇总。

金额使用 `Money(fen: Long)` 以分存储，输入通过 `BigDecimal` 精确转换，人民币显示两位小数。收入必须大于零，渠道金额及负债不得为负，超过两位小数及溢出会被拒绝。渠道采用稳定 ID 和软删除，历史资产表保留登记时的名称。

## Compose 与 Fluent 界面

资产、收入、设置、编辑表单及确认对话框均由 Kotlin `@Composable` 构建，没有应用页面的 XML 布局文件。使用 Fluent `FluentTheme`、`AppBar`、`Button`、`TextField`、`BasicCard`、`Dialog`；日期选择通过 `DisposableEffect` 托管 Fluent 原生 `DateTimePickerDialog` 的 `Mode.DATE`，离开编辑界面即销毁。界面不提供时间页及日期／时间切换栏，手动资产和收入以所选日期加保存时的时间落库，工行短信保留银行提供的时间。

入口使用 `AppCompatActivity`，满足 Fluent 日历查找宿主并创建七个星期标题的要求，消除空白星期栏；页面仍通过 Compose 构建。日期弹窗以单独的 XML 主题桥接原生控件配色，按当前 Compose 模式选择浅色或深色角色，标题、关闭图标、星期及日历数字均保持清晰，选中态使用系统动态强调色。底部导航参考 `D:\source\android\myapplication` 的实现，用 Compose 绘制完整导航项，并沿用 Fluent 图标尺寸、排版和颜色令牌。

关于页使用 `fluentui_drawer:0.3.10` 的 `com.microsoft.fluentui.tokenized.bottomsheet.BottomSheet`，把手、遮罩和拖动停靠均由 Fluent 实现；支持打开、下滑、点击遮罩、返回键及关闭按钮。正文的嵌套滚动不一定触发 SDK 的关闭回调，因此统一观察停靠隐藏状态，在关闭动画完成后移除弹层；下滑后可再次打开。使用官方固定停靠模式，高度为窗口的 88%，最多 560dp；正文扣除 Fluent 把手的 20dp，避免内容高度超过可见停靠范围。布局参考 `D:\source\android\myapplication` 的关于面板：应用图标、名称、简介和版本居中，作者、许可证、项目链接与本地数据说明位于可滚动正文，底部固定保留检查更新按钮，不附加标题或卡片。信息链接使用小号 Fluent 文本按钮，行高为 28dp，正文分组间距为 12dp。Logo 复用启动图标路径，线条与背景共同使用当前动态主题色。作者 [zyraxi](https://zyraxi21.github.io/)、[GPL v3 许可证](https://github.com/zyraxi21/account-book/blob/main/LICENSE)及 [GitHub 项目](https://github.com/zyraxi21/account-book)均可点击打开。保留系统底部及横向边衬，与主界面共用窗口的截屏保护。所有应用文案统一放在 `strings.xml`。[Fluent BottomSheet 接口](https://github.com/microsoft/fluentui-android/wiki/Controls#bottom-sheet)

界面采用月度对账单布局，渠道金额右对齐，资产、负债、净资产及月度差额合并在同一卡片；卡片右上角提供编辑和红色删除图标，收入分项使用同一组操作图标，并保留无障碍描述及确认弹窗。默认从系统壁纸配色取得动态品牌种子，展开为 Fluent 的 16 级品牌色阶，并按官方色阶的亮度分布校准对比度；深色模式降低品牌色饱和度。按钮、输入框、汇总强调和导航选中项使用同一套品牌令牌，预览关闭动态取色时回退到 Fluent 蓝 `#0F6CBD`。[Android 动态颜色说明](https://developer.android.com/develop/ui/compose/designsystems/material3#dynamic-color-schemes)

中性背景、表面和正文使用 Fluent 对应令牌；正值绿和负值红保持固定含义。页面可滚动并限制宽窗口内容宽度。浅深色顶栏均采用动态品牌渐变，背景从屏幕顶边延伸到系统状态栏和标题区域；标题、隐私图标及状态栏图标使用白色前景，深色模式使用较暗品牌色阶。底栏背景延伸到手势及三键导航区域，导航栏图标明暗按底栏背景对比度选择。系统边衬由各栏位消费一次，同时处理横向挖孔、桌面标题栏和编辑面板的键盘边衬。

顶栏铺满整个屏幕上方，采用直边布局；先绘制覆盖状态栏的完整渐变，再为内部 Fluent `AppBar` 应用系统边衬，使标题与隐私按钮位于状态栏下方，横向挖孔不遮挡操作。宽窗口只限制标题内容宽度，背景仍占满屏幕。顶栏仅显示标题与隐私图标按钮，并保留无障碍描述。月度页面使用 Compose `HorizontalPager`：标题、月份和月份按钮固定，只有下方账单内容跟随手指移动，松手后吸附或回弹，按钮切换使用 250ms 水平动画。各页登记和删除始终绑定该页月份；账务显示时，滑动期间也可操作。账务隐藏时，资产编辑、登记及删除按钮均禁用。删除确认保留打开时的目标月份。连续点击累计目标，反向操作及返回本月可接管动画。点月份可直接打开月份选择器；本月为末页，资产和收入页的下一月按钮均禁用。查看历史月份时，右下角的“本月”Fluent 悬浮按钮淡入并放大，回到本月时淡出并缩小。[Compose Pager 说明](https://developer.android.com/develop/ui/compose/layouts/pager)

渠道卡片使用 Fluent `BasicCard`、`TextField` 及仅显示图标的 `Button`，保留 48dp 操作点击区和无障碍名称。拖动项即时跟手并抬起，相邻卡片通过 Compose `LookaheadScope` 与 `animateBounds` 平滑让位。编辑、删除及添加复用 Microsoft Fluent UI Android 示例中的矢量资源，不增加图标依赖；许可见 `THIRD_PARTY_NOTICES.md`。

系统操作提示使用 `fluentui_notification` 的原生 `Snackbar` 和 `SnackbarState`，保留原生关闭、滑动及进出场动画；主页面与弹窗共用消息状态，同一时刻仅显示一个提示宿主。浅色模式使用淡品牌色提示面和深品牌色文字，深色模式使用低亮度品牌色提示面；中文关闭动作与提示文本均进入无障碍语义。Fluent 填充按钮和胶囊 FAB 与顶栏共用同一垂直渐变：上端为品牌填充色混入 4% 白色的略亮同色，下端保留原品牌色，按压时整体加深，禁用态沿用 Fluent 令牌。对话框、BottomSheet 与卡片统一表面色，输入框背景透明，通过底线、标签及聚焦强调色区分。开关视觉轨道为 52×32dp，整行提供至少 48dp 点击区。[Fluent Snackbar 接口](https://github.com/microsoft/fluentui-android/wiki/Controls#snackbar)

“本月”按钮以同一进度驱动透明度和缩放，使用 `CompositingStrategy.ModulateAlpha`，避免半透明离屏图层裁切 Fluent 胶囊阴影而产生矩形边界。

底部导航显式使用有界涟漪与独立交互源，点击区和反馈包含底部系统边衬，图标及文字仍位于系统导航区域之上；导航项提供 `Tab` 角色与选中语义。关闭系统额外的导航栏灰色遮罩，保持底栏背景连续；导航项高度随文字固有高度变化，适配大字体。[Android 系统栏说明](https://developer.android.com/develop/ui/views/layout/edge-to-edge)

`BookNavigation.kt` 提供浅色和深色 Compose 预览，可在 Android Studio 中查看顶栏、对账单标题和底部导航。窗口的浅色及深色背景资源与画布一致，减少冷启动背景跳变；这些 XML 资源只配置窗口，不绘制应用页面。

Fluent 发布模块的依赖声明未包含主题所需的 Compose `runtime-livedata`，本应用在 version catalog 中显式声明并引入该模块，版本由 Compose BOM 对齐，确保 `FluentTheme` 的 `observeAsState` 在运行时可用。[Compose LiveData 集成说明](https://developer.android.com/develop/ui/compose/state)

Fluent 复选框调用 `androidx.compose.material.icons.Icons.Filled`，显式引入 `material-icons-core:1.7.8`，修复打开资产登记时的 `NoClassDefFoundError`。手机的“加固技术不适配”提示在本次异常中对应运行时类缺失，项目没有接入应用加固 SDK。

XML 文件负责 Android Manifest、统一文案 `strings.xml`、主题、矢量图标、启动器图标及备份规则。依赖采用与 `D:\source\android\fluentui-android` 本地源码对应的已发布 Fluent 模块，无需将其旧版 Gradle 工程加入本项目。月份导航与关于页返回按钮使用 `fluentui_icons` 的官方箭头，已删除被替换的自绘箭头资源；库中没有对应的隐私、资产、收入、设置及日历图标，这些保留矢量资源。未使用的 `fluentui_tablayout` 已从依赖及版本目录移除，运行时依赖中也不包含该模块。

## 本地加密与数据生命周期

Room 2.8.5 通过 SQLCipher 4.19.1 的 `SupportOpenHelperFactory` 打开加密数据库。资产、渠道、收入、短信去重凭据、自动登记开关和渠道记忆都保存于该数据库，没有明文账务偏好文件。

首次使用生成 32 字节随机数据库口令，用 Android Keystore 中的 AES-256-GCM 密钥封装后通过 `AtomicFile` 保存；封装文件经过校验后才用于创建数据库。密钥不硬编码，账务和短信正文不写入日志。数据库、WAL 等旁路文件和密钥封装文件位于应用私有的 `noBackupFilesDir/ledger`。SQLCipher 日志关闭，未配置任何破坏性数据库迁移或自动清库逻辑。

首次创建时口令先封装到 `database-key.pending.v1`，数据库实际打开并完成结构校验后，再原子提交为 `database-key.v1`。若进程在首次创建中途终止，下次使用同一口令继续初始化；已完成创建的账本仍禁止在数据库缺失时自动重建。

数据库文件缺失、密钥丢失或封装校验失败时保留现有文件并显示错误，禁止自动创建空账本覆盖原数据。数据库结构 schema 位于 `app/schemas/io.github.zyraxi21.accountbook.data.local.BookDatabase/`，当前为 `4.json`，仅包含结构，不含用户数据。`MIGRATION_1_2` 增加导出时间，`MIGRATION_2_3` 增加启动隐藏和前台截屏偏好，`MIGRATION_3_4` 仅增加默认关闭的万位分隔偏好；升级保留原账务及渠道记忆，没有配置破坏性迁移。

默认启用 `FLAG_SECURE`；用户可在设置中允许前台截屏，编辑弹窗和日期选择器遵循这一选择。离开前台前恢复截图保护，最近任务截图保持关闭。草稿只保存在 ViewModel 内存中，编辑控件不接入系统保存状态；进程被系统终止后，未保存的草稿会丢失。

**卸载应用、清除应用数据会删除账本及密钥。应用内没有云同步，跨设备迁移与长期备份需要使用设置页的导出功能自行保存文件。** 更新 APK 时请保持包名与签名一致并使用覆盖安装。

## 工行短信解析与授权

支持如下正文，兼容中英文括号、冒号、逗号及常见空白：

```text
尾号1234卡10月6日12:34工商银行收入(工资)1000.00元，余额5000.00元。【工商银行】
```

提取日期时间、项目和收入金额；余额仅用于校验和去重，不修改资产表。没有年份时，使用短信时间或粘贴时的当前时间，在本年及上一年中推断最近且不晚于参考时间五分钟的合法日期。粘贴历史短信时请核对年份，解析结果可在保存前修改。

自动登记默认关闭。打开设置页中的“自动登记工行收入”，阅读用途说明并授予 `RECEIVE_SMS` 后，只处理来自 `95588`（含 `+86` 或 `0086` 前缀）的系统新短信广播，拼接分段正文并在后台事务内入账；不会扫描历史短信。短信指纹包含卡尾号、推断后的完整日期时间、规范化项目、收入和余额，数据库唯一凭据防止重复广播及重复粘贴。删除短信收入后仍保留凭据，同一条短信不会重新出现。

`RECEIVE_SMS` 属于安装器必须放行的受限权限；普通运行时授权无法替代安装器的放行。权限被拒绝或安装器未放行时，设置页显示不可用状态，粘贴解析仍可使用。[Android 接收短信权限说明](https://developer.android.com/reference/android/Manifest.permission#RECEIVE_SMS)

自行安装时可用以下方式，先在设备开启 USB 调试，并将 SDK `platform-tools` 加入 PATH：

```powershell
adb devices -l
adb install -r .\app\build\outputs\apk\release\app-release.apk
adb shell am start -n io.github.zyraxi21.accountbook/.MainActivity
```

安装后至少启动一次，再在应用设置中开启并授权自动登记。ADB 默认安装流程会放行受限权限；请勿添加 `--restrict-permissions`，该选项会取消放行。如果系统安装器没有放行，可在保持原签名的前提下尝试上述 ADB 覆盖安装，厂商策略仍以实际设备为准。[Android 安装命令实现](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/pm/PackageManagerShellCommand.java)

开发设备已由安装器放行时，也可显式授予运行时权限，再在应用内开启开关：

```powershell
adb shell pm grant io.github.zyraxi21.accountbook android.permission.RECEIVE_SMS
```

该命令无法绕过安装器的限制。在系统设置中强行停止应用后，需要再次启动才能继续接收短信；应用不要求成为默认短信应用。

## 导入与导出

设置页提供“导出 JSON”“导出 CSV”“导入账本”三个入口，全部通过系统文件选择器（Storage Access Framework）完成：导出时由 `CreateDocument` 让用户选择保存位置和文件名，导入时由 `OpenDocument` 选择来源文件。应用不申请存储权限；导入导出不联网。

### 导出

一次导出写出资产表、渠道表和收入表。设备上的短信开关、启动隐私与截屏偏好不随文件导入覆盖。JSON 与 CSV 分别使用固定 MIME 的独立 `CreateDocument` 合约，连续切换导出格式不会沿用上一种文件后缀。两种格式成功写入后均记录导出时间。

| 格式 | 结构 | 适用场景 |
| --- | --- | --- |
| JSON | 单个对象，含 `version`、`exportedAt`、`channels`、`snapshots`、`incomes`，余额嵌入资产表 | 账务备份与跨设备恢复，字段固定、可被程序精确解析 |
| CSV | 三段表，依次为资产表、渠道表、收入表，每段各带表头 | 用表格软件查看、整理或人工编辑 |

JSON 中金额一律写成字符串（如 `"1234.56"`），不使用 JSON 数字，避免二进制浮点在往返中产生误差；读取端遇到小数形式的数字会直接拒绝并提示“金额请用字符串”。CSV 使用 UTF-8、CRLF 换行，含逗号、引号或换行的字段按 RFC 4180 加引号并转义内部引号。

CSV 的表头固定为：

```text
月份,登记时间,渠道ID,渠道名称,余额,负债
渠道ID,渠道名称,启用,顺序
收入ID,项目,金额,日期时间,来源
```

### 导入

导入优先按实际内容判定格式：首个非空白字符为 `{` 时按 JSON 解析，否则按 CSV 表头识别。兼容历史错误后缀，例如内容为 CSV 的 `.csv.json` 文件。CSV 按各段表头分别还原资产、渠道和收入，同时支持单张表；导出的三段表格、空表及含引号和换行的字段均可往返。

点击导入后明确选择模式，再选择文件。解析和引用校验先完成，写入只使用一个事务；失败时原数据不变：

- **合并导入：**渠道和收入按稳定 ID 去重，资产表按月份去重，本机已有记录优先。只插入新增记录，重复导入自身备份不改动账本；新渠道名称冲突时增加编号，历史名称仍保留。
- **覆盖导入：**需再次确认删除原账务，再选择文件。用文件中的渠道、资产和收入替换本机账务，保留设备偏好及短信去重凭据。

渠道会先于资产表插入，保证外键有效。导入完成后隐私状态会被重新断言为隐藏，金额、项目、渠道名称不会因导入而意外露出。

### 异常处理

所有格式与解析问题都会中断本次导入并显示对应文案，不做部分写入：

| 情况 | 提示 |
| --- | --- |
| 文件为空或没有表头 | 文件内容为空，无法导入 |
| 既不是合法 JSON 也不是可识别的 CSV | 无法识别文件格式，请选择本应用导出的 JSON 或 CSV |
| JSON 语法错误、引号未闭合 | 文件格式错误，无法解析 |
| JSON 版本号高于当前支持 | 文件版本过高，请升级应用后再导入 |
| 缺少必需字段 | 缺少必需字段 |
| 字段取值非法（金额格式、来源、月份格式等） | 文件中的字段取值不合法，请核对后重新导出 |
| 日期时间无法解析 | 日期时间格式不正确 |
| 月份或收入项目重复 | 同一月份/同一收入项目重复 |
| 记录数超过 10000 条 | 记录数超过上限 |
| 文件超过 5 MB | 文件过大，请拆分后导入 |

解码异常内部保留字段和行号，界面通过 Snackbar 显示统一错误类别，不把账务数值写入提示或日志。导入后资产差额与净资产仍按原口径计算，金额不接受科学计数法。

## 工程结构

```text
app/
  schemas/                         Room 数据库结构（v1/v2/v3 归档与当前 v4）
  src/main/java/io/github/zyraxi21/accountbook/
    AccountBookApplication.kt       应用级容器
    MainActivity.kt                Compose 入口与后台隐私保护
    domain/                        金额、模型、统计、仓库接口、JSON/CSV 编解码
    data/crypto/                   Keystore 口令封装
    data/local/                    Room 表、DAO、SQLCipher 工厂、迁移
    data/repository/               事务、渠道记忆、短信去重、整本导入导出
    data/transfer/                 SAF 文件读写的格式判定与字节上限
    sms/                           纯 Kotlin 解析器、分段拼接、系统接收器
    ui/                            ViewModel、Fluent 页面、表单、主题
  src/main/res/values/strings.xml   应用文案及无障碍描述
  src/test/                        金额、统计、短信、隐私草稿、主题对比度、导入导出编解码单元测试
  src/androidTest/                 加密数据库和 Compose 设备测试
  src/sharedTest/                  两类测试共用的只读仓库替身
gradle/libs.versions.toml           全部构建插件及依赖版本目录
```

## 构建与验证

使用 JDK 25、Gradle Wrapper 9.8.0、AGP 9.4.1、KSP 2.3.12。Android SDK 需要 Platform 37（本机 SDK 目录为 `platforms/android-37.0`）和 Build Tools 37.0.0。配置 Android Studio 的 SDK 路径，或在未提交的 `local.properties` 中设置 `sdk.dir`。所有模块依赖及构建插件通过 version catalog 引用，AGP 内置 Kotlin，不再应用 `kotlin-android` 插件。

```powershell
.\gradlew.bat :app:assembleRelease --console=plain
```

日常安装选择 **Release**：使用 AGP 的 `release { optimization { enable = true } }` 开启 R8 代码压缩、优化、混淆及资源压缩，保持默认完整模式。Debug 和 Release 均只打包 `arm64-v8a`。Release 不可调试，Compose 调试工具及测试入口仅在 Debug 中引入；需要断点或布局调试时，在 Android Studio 的 Build Variants 中临时选择 Debug。[Android 官方优化配置](https://developer.android.google.cn/topic/performance/app-optimization/enable-app-optimization)

Release 签名从项目根目录的 `keystore.properties` 读取，该文件含口令、已被 `.gitignore` 排除，不会进版本库。首次配置时复制 `keystore.properties.example` 并填写本机 `storeFile` 路径与三项口令；缺任一项时构建直接报错，不会退回 debug 签名。**不要用 `~/.android/debug.keystore` 发布**：其口令是 Android SDK 的公开默认值，任何人都能伪造出同签名的 APK。更换电脑时需确认使用同一证书，否则无法覆盖安装、必须卸载重装（卸载会删除加密账本与 Keystore 密钥）。不要卸载或清除应用数据来更新版本。

Android Studio 中选择 **Android App 类型的 `app` 配置**和已连接手机。名称为 `main`、`unitTest`、`androidTest` 的 Android App 配置不代表测试运行器；运行设备测试需要 Android Instrumented Tests 配置。

本地检查与按需构建测试包：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintRelease :app:assembleRelease :app:assembleDebugAndroidTest --console=plain
```

输出文件：

- 日常安装包：`app/build/outputs/apk/release/app-release.apk`。
- 按需调试包：`app/build/outputs/apk/debug/app-debug.apk`，使用 `:app:assembleDebug` 构建。
- 混淆映射及合并规则：`app/build/outputs/mapping/release/mapping.txt`、`configuration.txt`，与对应 APK 一同留存，用于定位异常。
- R8 分析：`:app:analyzeReleaseR8Config` 生成 `app/build/reports/r8/r8-config-analyzer-release.html`。
- 测试包：`app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`。
- 单元测试报告：`app/build/reports/tests/testDebugUnitTest/index.html`。
- Lint 报告：`app/build/reports/lint-results-release.html`。

SQLCipher 的 JNI 和 Room 数据库构造入口沿用依赖的保留规则。应用 `.keep` 仅忽略 Fluent 发布包中两个编译期 Parcelize 注解的缺失提示，未加入应用级整包保留。原生库保留必要的动态符号；当前依赖已去除调试节及静态符号表，无额外原生调试信息随 APK 安装。

### 安装包体积

当前 Release 版本号为 `2026.10.08.1`，下表同时保留此前优化的基准。数据按 MiB（1 MiB = 1048576 字节）记录：

| 产物 | APK 大小 | DEX 未压缩大小 |
| --- | --- | --- |
| 实施计划中的原全架构 Debug 基准 | 48.62 MiB | 38.84 MiB |
| 手机原安装的 arm64 Debug 实测 | 43.27 MiB | 38.84 MiB |
| 2026-10-07 按需生成的 arm64 Debug | 43.97 MiB | 39.49 MiB |
| 2026-10-07 arm64 Release | 6.06 MiB（6353002 字节） | 2.90 MiB |
| 当前 arm64 Release | **6.15 MiB**（6447506 字节） | **2.96 MiB** |

当前 APK 相对计划基准减少约 **87.3%**，相对手机原安装包减少约 **85.8%**。两种构建均只含 `arm64-v8a` 的 SQLCipher 和 Compose 路径原生库；Release 不可调试，Manifest 没有测试活动及测试运行器。AppCompat 测试宿主只在 Debug Manifest 中声明。

此前 6.03 MiB 版本覆盖安装后，Android `StorageStats.appBytes` 实测手机应用代码占用为 **6.15 MiB**（6448640 字节）。该值不包含账本数据和缓存，后续 ART 编译也可能改变它，不能与 APK 文件大小混为同一口径。原账本及 Keystore 密钥保留，未卸载或清除应用数据。当前版本使用虚拟设备验证，未重复测量手机安装占用。

2026-10-08 隐私操作与关于页简化已执行的验证：

API 37 虚拟设备上一次复查 3 个既有用例，确认隐藏状态下资产编辑及登记入口禁用，点击不会打开编辑界面或触发 Snackbar；显示账务后编辑按钮恢复可用，其他系统提示仍使用 Snackbar。关于页仅显示检查更新按钮，移除更新标题及卡片，浅深色布局完整，下滑关闭后可以重新打开。报告和预览位于 `app/build/reports/privacy-about-cleanup/`。Debug、Release 与测试 APK 构建成功，Release Lint 为 0 错误、13 条既有警告。

2026-10-08 启动静默检查更新已执行的验证：

`BookUpdateTest` 的 4 项单元测试通过，覆盖启动只查一次、无更新及查询失败时保持静默、有新版本时等待用户确认，以及手动检查继续提示结果。API 37 虚拟设备上一次运行 `UpdateUiTest` 的 7 项用例，验证无需打开关于页即可提示新版本、“稍后”关闭、无更新和断网时无提示，以及原有手动检查与关于弹层内的 Snackbar。更新仓库使用替身，未触发真实下载或安装；设备报告位于 `app/build/reports/startup-update/ui-verification.txt`。Debug、Release 与测试 APK 构建成功，Release Lint 为 0 错误、13 条既有警告。

2026-10-08 关于页信息及 Logo 改动已执行的验证：

API 37 虚拟设备上复查 1 个既有关于页用例，通过点击界面中的作者、许可证和项目链接验证对应 URL，并确认下滑关闭后可重新打开。收紧行距及面板高度后，仅复查该用例。浅深色截图确认 Logo 线条与背景使用一致的动态主题色，信息居中且更新按钮完整显示。报告及预览位于 `app/build/reports/about-identity/`。Debug、Release 与测试 APK 构建成功，Release Lint 为 0 错误、13 条警告，其中 1 条为精简关于页后未使用的导出说明文案，其余 12 条延续既有提示；设备检查使用 Debug。

2026-10-08 余额输入光标修复已执行的验证：

API 37 虚拟设备上 4 项定向检查全部通过：登记及编辑资产时逐位输入 `1234.56`，每次检查文字与光标位置；移动光标后插入和退格仍在正确位置，草稿同步；渠道拖动取消保留已接受顺序，相邻卡片继续平滑让位。渠道卡片仅缓存 ID 排序，内容同步读取最新草稿，继续使用 Fluent 输入框。复现与修复后的报告位于 `app/build/reports/balance-caret/`。

Debug、Release 和测试 APK 均构建成功，Release Lint 为 0 错误、16 条警告，其中 4 条为当前关于页改动后未使用的文案，其余 12 条延续既有提示。设备检查使用 Debug 构建。

2026-10-08 此前深色界面及金额显示改动已执行的验证：

| 检查 | 结果 |
| --- | --- |
| 金额、统计、ViewModel 和主题对比度单元测试 | 39 项通过；验证三位／四位分隔的精确金额、独立新增渠道对话框及草稿恢复、Snackbar 对比度 |
| Release Lint | 0 错误、12 条既有警告；依赖升级、仅 arm64 的 ChromeOS 兼容性及代码简化建议 |
| Release、Debug 与界面测试 APK | 均构建成功；Release 仅包含 arm64 原生库 |
| Android Studio 虚拟设备定向检查 | API 37、16 KB 页大小上 7 项界面检查及 2 项加密数据库检查全部通过；顶栏配色调整后仅复查受影响的图标操作用例 |

本次界面检查覆盖：金额分隔开关即时更新资产与收入且静默保存、资产及收入图标编辑与删除确认、独立新增渠道及取消后的草稿恢复、渠道图标改名与删除、关于页布局及下滑重开、本月按钮的中间动画帧和连续翻页。浅深色截图确认输入框与卡片背景一致、深色 Snackbar 可读、顶栏内外统一，本月按钮的阴影无矩形边界。数据库检查覆盖偏好重启恢复、覆盖导入保留设备偏好，以及 v1／v2／v3→v4 迁移保留旧账务和隐私设置。

定向报告及预览保存在 `app/build/reports/theme-refinement/`。此次使用虚拟设备的 arm64 原生桥运行 Debug，未进行真机测试或正式签名 Release 的设备回归；最终 Release 已完成构建及 Lint。

2026-10-08 此前渠道卡片及按月收入改动已执行的验证：

| 检查 | 结果 |
| --- | --- |
| 金额、统计及 ViewModel 单元测试 | 33 项通过；覆盖上海时区的跨年月末累计、修改与删除收入后的更新，以及卡片顺序、历史名称和金额草稿 |
| Release Lint | 0 错误、12 条警告；依赖升级、仅 arm64 的 ChromeOS 兼容性及既有代码简化建议 |
| Release、Debug 与界面测试 APK | 均构建成功；Release 仅包含 arm64 原生库，没有可调试标记及测试入口 |
| Android Studio 虚拟设备定向检查 | Android 17、API 37、16 KB 页大小上 9 项界面测试及 2 项加密数据库测试全部通过；截图定位修正后仅复测对应的图标操作用例 |

本轮界面检查覆盖：资产卡片登记与日期选择、隐私恢复金额和排序、图标改名及删除、拖动取消保持已接受顺序、相邻卡片动画的中间位置、累计收入截至显示月份、当月下一页禁用、月份栏固定与连续翻页、本月按钮退场，以及关于弹层的更新按钮和下滑后重开。加密数据库检查覆盖当前渠道顺序与历史明细独立、登记后记忆顺序、删除渠道后重启恢复。报告和浅深色预览保存在 `app/build/reports/interface-refinement/`。

本轮只在虚拟设备安装 Debug 和界面测试 APK，未运行真机测试，未重复此前整套回归，也未覆盖安装正式签名 Release；最终 Release 已完成构建、Lint 和 APK 内容检查。

2026-10-07 已执行的验证（包含此前 API 36 回归及本次 API 37 定向检查）：

| 检查 | 结果 |
| --- | --- |
| 单元测试 | 66 项通过，新增验证所选上海日期、保存时间及短信原始时间与指纹保留 |
| Release Lint | 0 错误、8 条警告：7 条依赖升级提示及 1 条仅 arm64 的 ChromeOS 兼容性提示 |
| Release、Debug 与测试 APK | 均构建成功，API 37 编译及目标设置保持不变 |
| 加密数据库设备测试 | 小米 25113PN0EC、Android 16（API 36）上 23 项通过 |
| Compose 界面与活动生命周期测试 | 同一手机上 20 项通过，其中 17 项界面测试、3 项隐私与截屏生命周期测试；一次活动启动中断的用例单独重跑通过 |
| 日期与弹层专项检查 | Android Studio 的 Resizable 虚拟设备、Android 17（API 37.2）、16 KB 页大小上两个既有用例通过；修正宿主后单独复测日历通过。验证七个星期标题、单页日历高度、浅深色对比度，以及正文和把手下滑后重新打开 |
| Release 启动与账本加载 | 最终版本在上述虚拟设备覆盖安装并正常加载加密账本；此前手机同证书覆盖升级已保留原账本，未清除数据 |
| 依赖检查 | `debugRuntimeClasspath` 不包含 `fluentui_tablayout` |

数据库设备测试覆盖加密重启读写、首次创建中断恢复、明文 SQLite 拒读、数据库及旁路文件明文扫描、损坏文件保留、渠道记忆与历史名称、月份唯一约束、短信去重与删除后重投、并发登记、JSON/CSV 自身备份重复合并、新渠道余额外键顺序、覆盖保留偏好和短信凭据、非法覆盖回滚、实际文件读写及 v1/v2→v3 迁移。

此前界面回归验证了旧版资产登记的 Fluent 复选框、日期选择器、隐私语义和草稿恢复、Snackbar、固定月份栏与正文跟手移动、短拖回弹、滑动中各页按钮的月份归属、连续翻页和反向接管、本月边界和悬浮按钮、设置静默保存、关于页、月份选择器、按月收入及渠道拖动排序。当前登记已改为渠道卡片，相关检查见本轮记录。使用捕获文件选择器合约的测试替身验证 JSON→CSV→JSON 的 MIME 和默认后缀，真实文件读写通过隔离文件完成；跨文档提供方的手选目录、覆盖文件及取消交互仍需人工验收。

此前加密事务、短信去重和 JSON／CSV 往返的完整自动化检查在 Debug 上执行，2026-10-07 的压缩 Release 已确认构建、架构及虚拟设备账本加载；完整的 Release 导入导出界面流程、真实短信广播和 API 37 的整套回归尚未验证。

此前版本已覆盖安装到手机，保留原账本与密钥，没有清除应用数据；本次版本安装在虚拟设备中。数据库测试使用独立临时账本，界面预览使用测试替身，活动测试结束后恢复原有偏好。此前设备输出位于 `app/build/reports/device/`，本次虚拟设备输出及浅深色日历截图位于 `app/build/reports/emulator/`。虚拟设备通过原生桥运行 arm64 库，API 37 的真实 arm64 设备及完整兼容性回归仍未执行。

连接 Android 17（API 37）真机或模拟器后执行：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest --console=plain
```

人工验收还需检查：短信权限拒绝及安装器限制；真实分段工行短信到达后的自动登记；跨文档提供方的文件选择器交互；大字体、横屏和宽窗口；键盘弹出后的表单滚动和按钮可达性；厂商最近任务卡片的实际预览效果。

## 设计及集成参考

- [Fluent 设计原则](https://fluent2.microsoft.design/design-principles)、[颜色](https://fluent2.microsoft.design/color)、[布局](https://fluent2.microsoft.design/layout)、[字体](https://fluent2.microsoft.design/typography)。
- [Android Compose 边衬区](https://developer.android.google.cn/develop/ui/compose/layouts/insets?hl=zh-cn)。
- [SQLCipher 与 Room 集成](https://github.com/sqlcipher/sqlcipher-android#sqlcipher-for-android-room-integration)。
- [Android 内置 Kotlin 源码目录配置](https://developer.android.com/build/migrate-to-built-in-kotlin)。
