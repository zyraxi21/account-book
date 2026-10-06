# 记账本

使用 Kotlin、Jetpack Compose 和 Microsoft Fluent UI 构建的 Android 本地记账应用。包名为 `io.github.zyraxi21.accountbook`，最低支持 Android 14（API 34），编译及目标版本为 Android 17（API 37）。

## 功能与计算口径

- **资产：**每个自然月保存一份资产表，支持补录、编辑、删除，登记日期时间、渠道余额及负债。所属月份根据登记时间确定，统一使用 `Asia/Shanghai`。
- **收入：**随时登记、编辑或删除收入，自动汇总累计收入；可粘贴工行短信解析，也可授权后自动登记新收到的工行收入短信。
- **渠道：**初始提供银行、支付宝、微信，支持添加、改名及删除。成功保存资产后，在同一事务中记住所选渠道及顺序；下一次新登记恢复选择，金额重新填写。取消或保存失败不会改变记忆。
- **隐私：**顶栏“隐私”切换账务显示；每次启动和进入后台自动隐藏。金额、项目、渠道名称及登记时间同时从画面和无障碍语义中遮挡。隐藏后关闭编辑面板和键盘，显示后继续内存草稿。
- **导入导出：**设置页可把整本账导出为 JSON 或 CSV，也可从这两种格式导入。导出与导入都通过系统文件选择器（SAF）完成，用户自行选择保存位置和来源文件。

| 字段 | 计算方式 |
| --- | --- |
| 总资产 | 各渠道金额之和 |
| 净资产 | 总资产 − 负债 |
| 与上月差额 | 本月总资产 − 上月总资产 |
| 估算月支出 | 上月净资产 ＋ 本自然月收入 − 本月净资产 |

缺少相邻上月资产表时，差额和估算月支出显示“暂无上月记录”。差额、净资产和估算支出允许为负；估算支出也会受投资涨跌、资产转入等变化影响。修改历史资产或收入后，界面通过 `Flow` 重新计算相关汇总。

金额使用 `Money(fen: Long)` 以分存储，输入通过 `BigDecimal` 精确转换，人民币显示两位小数。收入必须大于零，渠道金额及负债不得为负，超过两位小数及溢出会被拒绝。渠道采用稳定 ID 和软删除，历史资产表保留登记时的名称。

## Compose 与 Fluent 界面

资产、收入、设置、编辑表单及确认对话框均由 Kotlin `@Composable` 构建，没有应用页面的 XML 布局文件。使用 Fluent `FluentTheme`、`AppBar`、`Button`、`TextField`、`BasicCard`、`Dialog`；日期时间选择通过 `DisposableEffect` 托管 Fluent 原生 `DateTimePickerDialog`，离开编辑界面即销毁。底部导航参考 `D:\source\android\myapplication` 的实现，用 Compose 绘制完整导航项，并沿用 Fluent 图标尺寸、排版和颜色令牌。

界面采用月度对账单布局，渠道金额右对齐，集中呈现资产、负债和净资产。默认从系统壁纸配色取得动态品牌种子，展开为 Fluent 的 16 级品牌色阶，并按官方色阶的亮度分布校准对比度；深色模式降低品牌色饱和度。按钮、输入框、汇总强调和导航选中项使用同一套品牌令牌，预览关闭动态取色时回退到 Fluent 蓝 `#0F6CBD`。[Android 动态颜色说明](https://developer.android.com/develop/ui/compose/designsystems/material3#dynamic-color-schemes)

中性背景、表面和正文使用 Fluent 对应令牌；正值绿和负值红保持固定含义。金额使用等宽字体，页面可滚动并限制宽窗口内容宽度。浅色顶栏使用动态品牌色、浅色文字，深色顶栏使用中性表面色。顶栏背景延伸到状态栏，底栏背景延伸到手势及三键导航区域，系统图标明暗按实际背景对比度选择。系统边衬由各栏位消费一次，同时处理横向挖孔、桌面标题栏和编辑面板的键盘边衬。

底部导航显式使用有界涟漪与独立交互源，点击区和反馈包含底部系统边衬，图标及文字仍位于系统导航区域之上；导航项提供 `Tab` 角色与选中语义。关闭系统额外的导航栏灰色遮罩，保持底栏背景连续；导航项高度随文字固有高度变化，适配大字体。[Android 系统栏说明](https://developer.android.com/develop/ui/views/layout/edge-to-edge)

`BookNavigation.kt` 提供浅色和深色 Compose 预览，可在 Android Studio 中查看顶栏、对账单标题和底部导航。窗口的浅色及深色背景资源与画布一致，减少冷启动背景跳变；这些 XML 资源只配置窗口，不绘制应用页面。

Fluent 发布模块的依赖声明未包含主题所需的 Compose `runtime-livedata`，本应用在 version catalog 中显式声明并引入该模块，版本由 Compose BOM 对齐，确保 `FluentTheme` 的 `observeAsState` 在运行时可用。[Compose LiveData 集成说明](https://developer.android.com/develop/ui/compose/state)

XML 文件负责 Android Manifest、统一文案 `strings.xml`、主题、矢量图标、启动器图标及备份规则。依赖采用与 `D:\source\android\fluentui-android` 本地源码对应的已发布 Fluent 模块，无需将其旧版 Gradle 工程加入本项目。

## 本地加密与数据生命周期

Room 2.8.5 通过 SQLCipher 4.19.1 的 `SupportOpenHelperFactory` 打开加密数据库。资产、渠道、收入、短信去重凭据、自动登记开关和渠道记忆都保存于该数据库，没有明文账务偏好文件。

首次使用生成 32 字节随机数据库口令，用 Android Keystore 中的 AES-256-GCM 密钥封装后通过 `AtomicFile` 保存；封装文件经过校验后才用于创建数据库。密钥不硬编码，账务和短信正文不写入日志。数据库、WAL 等旁路文件和密钥封装文件位于应用私有的 `noBackupFilesDir/ledger`。SQLCipher 日志关闭，未配置任何破坏性数据库迁移或自动清库逻辑。

首次创建时口令先封装到 `database-key.pending.v1`，数据库实际打开并完成结构校验后，再原子提交为 `database-key.v1`。若进程在首次创建中途终止，下次使用同一口令继续初始化；已完成创建的账本仍禁止在数据库缺失时自动重建。

数据库文件缺失、密钥丢失或封装校验失败时保留现有文件并显示错误，禁止自动创建空账本覆盖原数据。数据库结构 schema 位于 `app/schemas/io.github.zyraxi21.accountbook.data.local.BookDatabase/`，当前为 `2.json`，仅包含结构，不含用户数据。从 v1 升级到 v2 只为设置表增加导出时间列（`lastExportAtMillis`），不触碰账务数据，由 `MIGRATION_1_2` 显式完成，没有配置破坏性迁移。

应用关闭云备份和设备迁移，不申请联网权限。活动与编辑对话框启用 `FLAG_SECURE`，并关闭最近任务截图。草稿只保存在 ViewModel 内存中，编辑控件不接入系统保存状态；进程被系统终止后，未保存的草稿会丢失。

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
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n io.github.zyraxi21.accountbook/.MainActivity
```

安装后至少启动一次，再在应用设置中开启并授权自动登记。ADB 默认安装流程会放行受限权限；请勿添加 `--restrict-permissions`，该选项会取消放行。如果系统安装器没有放行，可在保持原签名的前提下尝试上述 ADB 覆盖安装，厂商策略仍以实际设备为准。[Android 安装命令实现](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/pm/PackageManagerShellCommand.java)

开发设备已由安装器放行时，也可显式授予运行时权限，再在应用内开启开关：

```powershell
adb shell pm grant io.github.zyraxi21.accountbook android.permission.RECEIVE_SMS
```

该命令无法绕过安装器的限制。在系统设置中强行停止应用后，需要再次启动才能继续接收短信；应用不要求成为默认短信应用。

## 导入与导出

设置页提供“导出 JSON”“导出 CSV”“导入账本”三个入口，全部通过系统文件选择器（Storage Access Framework）完成：导出时由 `CreateDocument` 让用户选择保存位置和文件名，导入时由 `OpenDocument` 选择来源文件。应用不申请存储权限，也不联网。

### 导出

一次导出会写出整本账：资产表、渠道表、收入表以及设置中的记忆渠道。JSON 与 CSV 承载相同内容，区别只在编码方式。

| 格式 | 结构 | 适用场景 |
| --- | --- | --- |
| JSON | 单个对象，含 `version`、`exportedAt`、`settings`、`channels`、`balances`、`incomes` | 完整备份与跨设备恢复，字段固定、可被程序精确解析 |
| CSV | 三段表，依次为资产表、渠道表、收入表，每段各带表头 | 用表格软件查看、整理或人工编辑 |

JSON 中金额一律写成字符串（如 `"1234.56"`），不使用 JSON 数字，避免二进制浮点在往返中产生误差；读取端遇到小数形式的数字会直接拒绝并提示“金额请用字符串”。CSV 使用 UTF-8、CRLF 换行，含逗号、引号或换行的字段按 RFC 4180 加引号并转义内部引号。

CSV 的表头固定为：

```text
月份,登记时间,渠道ID,渠道名称,余额,负债
渠道ID,渠道名称,启用,顺序
收入ID,项目,金额,日期时间,来源
```

### 导入

导入按“先看扩展名、再看首个非空白字符”的顺序判断格式：`.json` 走 JSON 解析，`.csv` 走 CSV 解析，扩展名无法判定时用文件首字符兜底（`{` 视为 JSON，其余视为 CSV）。CSV 的具体段落由表头名称识别，因此手工整理过的文件可以调整列顺序、也可以省略部分列，比按固定列号解析更宽容。

导入是单个事务，中途失败不会留下半份数据。导入前先判断账本是否为空：

- **空账本**：直接建表写入，不额外构造默认渠道，导入文件就是唯一数据来源。
- **已有数据**：弹出确认对话框，用户选择合并或替换。
  - **合并**：渠道和收入按稳定 ID 去重，已存在的月份跳过，不覆盖现有资产表。
  - **替换**：清空资产、渠道、收入后整表重建。

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
| 字段取值非法（金额格式、来源、月份格式等） | 字段取值不合法，并指明记录序号与字段名 |
| 日期时间无法解析 | 日期时间格式不正确 |
| 月份或收入项目重复 | 同一月份/同一收入项目重复 |
| 记录数超过 10000 条 | 记录数超过上限 |
| 文件超过 5 MB | 文件过大，请拆分后导入 |

错误提示会带定位信息，例如“第 2 条记录的字段 month：月份格式应为 yyyy-MM”，便于对照表格软件里的行号排查；CSV 解析时保留空行的行号，因此提示行号与实际可见行号一致。金额字段单独说明——资产差额与净资产允许为负，导入时接受带符号的定点写法，不接受科学计数法。

## 工程结构

```text
app/
  schemas/                         Room 数据库结构（v1 归档与当前 v2）
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

使用 JDK 25、Gradle Wrapper 9.6.0、AGP 9.4.1、KSP 2.3.12。Android SDK 需要 Platform 37（本机 SDK 目录为 `platforms/android-37.0`）和 Build Tools 37.0.0。配置 Android Studio 的 SDK 路径，或在未提交的 `local.properties` 中设置 `sdk.dir`。所有模块依赖及构建插件通过 version catalog 引用，AGP 内置 Kotlin，不再应用 `kotlin-android` 插件。

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
```

Android Studio 中选择 **Android App 类型的 `app` 配置**和已连接手机，再点击 Run。名称为 `main`、`unitTest`、`androidTest` 的 Android App 配置不代表测试运行器；运行设备测试需要 Android Instrumented Tests 配置，或使用下面的命令。

输出文件：

- 安装包：`app/build/outputs/apk/debug/app-debug.apk`，由本机开发密钥签名，适合自行安装验证。
- 测试包：`app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`。
- 单元测试报告：`app/build/reports/tests/testDebugUnitTest/index.html`。
- Lint 报告：`app/build/reports/lint-results-debug.html`。

2026-10-06 验证情况：22 项单元测试通过；Debug APK、设备测试 APK 编译通过；Lint 无错误，另有 6 条依赖升级提示。依赖版本保持已验证的模板工具链及实施计划所指定的版本。

已在小米 25113PN0EC、Android 16（API 36）手机上覆盖安装并验证冷启动：加密账本成功打开，主界面正常显示，隐私默认隐藏。13 项加密数据库设备测试通过，包含首次创建中断后使用原口令恢复、损坏文件保留、渠道记忆和短信去重。修复过程中保留了原密钥封装，没有清除应用数据。

**3 项界面自动测试的本轮回归尚未完成。** 测试框架在 `ActivityScenario` 同步启动活动时等待，随后设备连接断开；正常启动应用已单独验证。设备测试记录位于 `app/build/reports/device/`，未计入完成的界面回归。Android 17（API 37）的实际运行兼容性仍需在对应真机或模拟器上验证。

2026-10-07 界面调整验证：26 项单元测试通过，其中 4 项主题测试覆盖动态种子、明暗配色、色阶亮度和系统栏图标对比度；Debug APK、设备测试 APK 构建及 Lint 通过，Lint 仅有此前的 6 条依赖升级提示。本轮没有连接设备或可用模拟器，新的状态栏、底部导航及涟漪尚未进行真机视觉验收，未将此前的真机结果计作本轮界面验证。

2026-10-08 导入导出验证：41 项单元测试通过，其中 14 项为新增的 JSON/CSV 编解码测试，覆盖两格式往返、金额符号与两位小数、字段转义、表头重排列、空行行号保持、渠道表省略可选列以及各错误分支；Debug APK、设备测试 APK 构建通过，Lint 无新增问题（仍为同样的 6 条依赖升级提示）。数据库升至 v2，`MIGRATION_1_2` 只增加 `lastExportAtMillis` 列。**SAF 文件选择器与整本导入的真实交互尚未在设备上验收**，需要手选保存位置、覆盖已有文件、取消选择、选择损坏文件等场景的人工确认。

连接 Android 17（API 37）真机或模拟器后执行：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest --console=plain
```

设备测试覆盖加密重启读写、首次创建中断恢复、明文 SQLite 拒读、数据库及旁路文件明文扫描、数据库和密钥损坏后的文件保留、渠道记忆及历史名称、月份唯一约束与编辑、重复入账与删除后重投、并发导入、隐私语义、后台隐藏及编辑草稿恢复、v1→v2 迁移。

人工验收还需检查：短信权限拒绝及安装器限制；真实分段工行短信到达后的自动登记；导入导出的文件选择器交互（选择保存位置、覆盖已有文件、取消选择、选择损坏或超大文件后的提示）；大字体、横屏、宽窗口和深色模式；日期时间选择器；键盘弹出后的表单滚动和按钮可达性；截图和最近任务预览保护。

## 设计及集成参考

- [Fluent 设计原则](https://fluent2.microsoft.design/design-principles)、[颜色](https://fluent2.microsoft.design/color)、[布局](https://fluent2.microsoft.design/layout)、[字体](https://fluent2.microsoft.design/typography)。
- [Android Compose 边衬区](https://developer.android.google.cn/develop/ui/compose/layouts/insets?hl=zh-cn)。
- [SQLCipher 与 Room 集成](https://github.com/sqlcipher/sqlcipher-android#sqlcipher-for-android-room-integration)。
- [Android 内置 Kotlin 源码目录配置](https://developer.android.com/build/migrate-to-built-in-kotlin)。
