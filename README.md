# 记账本

使用 Kotlin、Jetpack Compose 和 Microsoft Fluent UI 构建的 Android 本地记账应用。包名为 `io.github.zyraxi21.accountbook`，最低支持 Android 14（API 34），编译及目标版本为 Android 17（API 37）。

## 功能与计算口径

- **资产：**每个自然月保存一份资产表，支持补录、编辑、删除，登记日期时间、渠道余额及负债。所属月份根据登记时间确定，统一使用 `Asia/Shanghai`。
- **收入：**随时登记、编辑或删除收入，自动汇总累计收入；可粘贴工行短信解析，也可授权后自动登记新收到的工行收入短信。
- **渠道：**初始提供银行、支付宝、微信，支持添加、改名及删除。成功保存资产后，在同一事务中记住所选渠道及顺序；下一次新登记恢复选择，金额重新填写。取消或保存失败不会改变记忆。
- **隐私：**顶栏眼睛图标切换账务显示；默认在启动时隐藏，可在设置中关闭。进入后台始终隐藏。金额、项目、渠道名称及登记时间同时从画面和无障碍语义中遮挡。隐藏后关闭编辑面板和键盘，显示后继续内存草稿。
- **设置与关于：**自动登记、启动隐藏和前台截屏均用开关实时保存，不弹成功提示。数据仅在本机保存的说明位于“关于”页面。
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

顶栏为标题保留 20dp 左侧边距，隐私按钮仅显示眼睛图标并保留无障碍描述。月度页面使用 Compose `HorizontalPager`：页面跟随手指移动，松手后吸附或回弹，按钮切换使用 250ms 水平动画。翻页结束后才同步登记目标月份，滑动途中暂停登记；本月为末页，不创建未来页面。“下个月”按钮及滑动最多到本月；查看历史月份时，右下角显示返回“本月”的 Fluent 悬浮按钮。[Compose Pager 说明](https://developer.android.com/develop/ui/compose/layouts/pager)

系统操作提示使用 Snackbar，在编辑弹窗中也可见。开关视觉轨道为 52×32dp，整行提供至少 48dp 点击区。深色填充按钮改用较暗品牌色阶和白色前景，文字强调色仍保持可读。

底部导航显式使用有界涟漪与独立交互源，点击区和反馈包含底部系统边衬，图标及文字仍位于系统导航区域之上；导航项提供 `Tab` 角色与选中语义。关闭系统额外的导航栏灰色遮罩，保持底栏背景连续；导航项高度随文字固有高度变化，适配大字体。[Android 系统栏说明](https://developer.android.com/develop/ui/views/layout/edge-to-edge)

`BookNavigation.kt` 提供浅色和深色 Compose 预览，可在 Android Studio 中查看顶栏、对账单标题和底部导航。窗口的浅色及深色背景资源与画布一致，减少冷启动背景跳变；这些 XML 资源只配置窗口，不绘制应用页面。

Fluent 发布模块的依赖声明未包含主题所需的 Compose `runtime-livedata`，本应用在 version catalog 中显式声明并引入该模块，版本由 Compose BOM 对齐，确保 `FluentTheme` 的 `observeAsState` 在运行时可用。[Compose LiveData 集成说明](https://developer.android.com/develop/ui/compose/state)

Fluent 复选框调用 `androidx.compose.material.icons.Icons.Filled`，显式引入 `material-icons-core:1.7.8`，修复打开资产登记时的 `NoClassDefFoundError`。手机的“加固技术不适配”提示在本次异常中对应运行时类缺失，项目没有接入应用加固 SDK。

XML 文件负责 Android Manifest、统一文案 `strings.xml`、主题、矢量图标、启动器图标及备份规则。依赖采用与 `D:\source\android\fluentui-android` 本地源码对应的已发布 Fluent 模块，无需将其旧版 Gradle 工程加入本项目。月份导航与关于页返回按钮使用 `fluentui_icons` 的官方箭头，已删除被替换的自绘箭头资源；库中没有对应的眼睛、资产、收入、设置及日历图标，这些保留矢量资源。未使用的 `fluentui_tablayout` 已从依赖及版本目录移除，运行时依赖中也不包含该模块。

## 本地加密与数据生命周期

Room 2.8.5 通过 SQLCipher 4.19.1 的 `SupportOpenHelperFactory` 打开加密数据库。资产、渠道、收入、短信去重凭据、自动登记开关和渠道记忆都保存于该数据库，没有明文账务偏好文件。

首次使用生成 32 字节随机数据库口令，用 Android Keystore 中的 AES-256-GCM 密钥封装后通过 `AtomicFile` 保存；封装文件经过校验后才用于创建数据库。密钥不硬编码，账务和短信正文不写入日志。数据库、WAL 等旁路文件和密钥封装文件位于应用私有的 `noBackupFilesDir/ledger`。SQLCipher 日志关闭，未配置任何破坏性数据库迁移或自动清库逻辑。

首次创建时口令先封装到 `database-key.pending.v1`，数据库实际打开并完成结构校验后，再原子提交为 `database-key.v1`。若进程在首次创建中途终止，下次使用同一口令继续初始化；已完成创建的账本仍禁止在数据库缺失时自动重建。

数据库文件缺失、密钥丢失或封装校验失败时保留现有文件并显示错误，禁止自动创建空账本覆盖原数据。数据库结构 schema 位于 `app/schemas/io.github.zyraxi21.accountbook.data.local.BookDatabase/`，当前为 `3.json`，仅包含结构，不含用户数据。`MIGRATION_1_2` 增加导出时间，`MIGRATION_2_3` 增加启动隐藏和前台截屏偏好；升级保留原账务及渠道记忆，没有配置破坏性迁移。

应用关闭云备份和设备迁移，不申请联网权限。默认启用 `FLAG_SECURE`；用户可在设置中允许前台截屏，编辑弹窗和日期选择器遵循这一选择。离开前台前恢复截图保护，最近任务截图保持关闭。草稿只保存在 ViewModel 内存中，编辑控件不接入系统保存状态；进程被系统终止后，未保存的草稿会丢失。

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
  schemas/                         Room 数据库结构（v1/v2 归档与当前 v3）
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

2026-10-07 本轮验证：

| 检查 | 结果 |
| --- | --- |
| 单元测试 | 53 项通过，覆盖金额、统计、短信、编解码、合并与覆盖、隐私草稿和主题对比度 |
| Lint | 0 错误、6 条已有依赖升级提示 |
| Debug APK 与测试 APK | 均构建成功，API 37 编译及目标设置保持不变 |
| 加密数据库设备测试 | 小米 25113PN0EC、Android 16（API 36）上 19 项通过 |
| Compose 界面与活动生命周期测试 | 同一手机上 10 项通过，其中 8 项界面测试、2 项前后台隐私与截屏测试 |
| 依赖检查 | `debugRuntimeClasspath` 不包含 `fluentui_tablayout` |

数据库设备测试覆盖加密重启读写、首次创建中断恢复、明文 SQLite 拒读、数据库及旁路文件明文扫描、损坏文件保留、渠道记忆与历史名称、月份唯一约束、短信去重与删除后重投、并发登记、JSON/CSV 自身备份重复合并、新渠道余额外键顺序、覆盖保留偏好和短信凭据、非法覆盖回滚、实际文件读写及 v1/v2→v3 迁移。

界面回归验证了资产登记的 Fluent 复选框、日期选择器、隐私语义和草稿恢复、Snackbar、月份跟手移动与短拖回弹、本月边界和悬浮按钮、设置静默保存、关于页、导入模式选择及覆盖确认。使用捕获文件选择器合约的测试替身验证 JSON→CSV→JSON 的 MIME 和默认后缀，真实文件读写通过隔离文件完成；跨文档提供方的手选目录、覆盖文件及取消交互仍需人工验收。

新版已覆盖安装到手机，保留原账本与密钥，没有清除应用数据。数据库测试使用独立临时账本，界面预览使用测试替身，活动测试结束后恢复原有偏好。设备输出位于 `app/build/reports/device/`，测试数据的界面预览位于设备应用外部私有目录 `files/ui-verification/`。Android 17（API 37）的实际运行兼容性仍需对应真机或模拟器验证。

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
