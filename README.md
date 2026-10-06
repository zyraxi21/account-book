# 记账本

使用 Kotlin、Jetpack Compose 和 Microsoft Fluent UI 构建的 Android 本地记账应用。包名为 `io.github.zyraxi21.accountbook`，最低支持 Android 14（API 34），编译及目标版本为 Android 17（API 37）。

## 功能与计算口径

- **资产：**每个自然月保存一份资产表，支持补录、编辑、删除，登记日期时间、渠道余额及负债。所属月份根据登记时间确定，统一使用 `Asia/Shanghai`。
- **收入：**随时登记、编辑或删除收入，自动汇总累计收入；可粘贴工行短信解析，也可授权后自动登记新收到的工行收入短信。
- **渠道：**初始提供银行、支付宝、微信，支持添加、改名及删除。成功保存资产后，在同一事务中记住所选渠道及顺序；下一次新登记恢复选择，金额重新填写。取消或保存失败不会改变记忆。
- **隐私：**顶栏“隐私”切换账务显示；每次启动和进入后台自动隐藏。金额、项目、渠道名称及登记时间同时从画面和无障碍语义中遮挡。隐藏后关闭编辑面板和键盘，显示后继续内存草稿。

| 字段 | 计算方式 |
| --- | --- |
| 总资产 | 各渠道金额之和 |
| 净资产 | 总资产 − 负债 |
| 与上月差额 | 本月总资产 − 上月总资产 |
| 估算月支出 | 上月净资产 ＋ 本自然月收入 − 本月净资产 |

缺少相邻上月资产表时，差额和估算月支出显示“暂无上月记录”。差额、净资产和估算支出允许为负；估算支出也会受投资涨跌、资产转入等变化影响。修改历史资产或收入后，界面通过 `Flow` 重新计算相关汇总。

金额使用 `Money(fen: Long)` 以分存储，输入通过 `BigDecimal` 精确转换，人民币显示两位小数。收入必须大于零，渠道金额及负债不得为负，超过两位小数及溢出会被拒绝。渠道采用稳定 ID 和软删除，历史资产表保留登记时的名称。

## Compose 与 Fluent 界面

资产、收入、设置、编辑表单及确认对话框均由 Kotlin `@Composable` 构建，没有应用页面的 XML 布局文件。使用 Fluent `FluentTheme`、`AppBar`、`TabBar`、`Button`、`TextField`、`BasicCard`、`Dialog`；日期时间选择通过 `DisposableEffect` 托管 Fluent 原生 `DateTimePickerDialog`，离开编辑界面即销毁。

界面采用月度对账单布局，渠道金额右对齐，集中呈现资产、负债和净资产。浅色模式使用品牌蓝 `#0F6CBD`、背景 `#F5F5F5`、表面白、正文 `#242424`、正值绿 `#107C10`、负值红 `#C50F1F`；深色模式使用 Fluent 深色主题与对应颜色。金额使用等宽字体，页面可滚动并限制宽窗口内容宽度，启用 edge-to-edge，处理系统安全区域及编辑面板的键盘边衬。

Fluent 发布模块的依赖声明未包含主题所需的 Compose `runtime-livedata`，本应用在 version catalog 中显式声明并引入该模块，版本由 Compose BOM 对齐，确保 `FluentTheme` 的 `observeAsState` 在运行时可用。[Compose LiveData 集成说明](https://developer.android.com/develop/ui/compose/state)

XML 文件负责 Android Manifest、统一文案 `strings.xml`、主题、矢量图标、启动器图标及备份规则。依赖采用与 `D:\source\android\fluentui-android` 本地源码对应的已发布 Fluent 模块，无需将其旧版 Gradle 工程加入本项目。

## 本地加密与数据生命周期

Room 2.8.5 通过 SQLCipher 4.19.1 的 `SupportOpenHelperFactory` 打开加密数据库。资产、渠道、收入、短信去重凭据、自动登记开关和渠道记忆都保存于该数据库，没有明文账务偏好文件。

首次使用生成 32 字节随机数据库口令，用 Android Keystore 中的 AES-256-GCM 密钥封装后通过 `AtomicFile` 保存；封装文件经过校验后才用于创建数据库。密钥不硬编码，账务和短信正文不写入日志。数据库、WAL 等旁路文件和密钥封装文件位于应用私有的 `noBackupFilesDir/ledger`。SQLCipher 日志关闭，未配置任何破坏性数据库迁移或自动清库逻辑。

首次创建时口令先封装到 `database-key.pending.v1`，数据库实际打开并完成结构校验后，再原子提交为 `database-key.v1`。若进程在首次创建中途终止，下次使用同一口令继续初始化；已完成创建的账本仍禁止在数据库缺失时自动重建。

数据库文件缺失、密钥丢失或封装校验失败时保留现有文件并显示错误，禁止自动创建空账本覆盖原数据。数据库结构初始 schema 位于 `app/schemas/io.github.zyraxi21.accountbook.data.local.BookDatabase/1.json`，仅包含结构，不含用户数据。

应用关闭云备份和设备迁移，不申请联网权限。活动与编辑对话框启用 `FLAG_SECURE`，并关闭最近任务截图。草稿只保存在 ViewModel 内存中，编辑控件不接入系统保存状态；进程被系统终止后，未保存的草稿会丢失。

**卸载应用、清除应用数据会删除账本及密钥。本版本没有云同步、导出或跨设备恢复功能；已有密文文件也不能在另一台设备上直接解密。** 更新 APK 时请保持包名与签名一致并使用覆盖安装。

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

## 工程结构

```text
app/
  schemas/                         Room 初始数据库结构
  src/main/java/io/github/zyraxi21/accountbook/
    AccountBookApplication.kt       应用级容器
    MainActivity.kt                Compose 入口与后台隐私保护
    domain/                        金额、模型、统计、仓库接口
    data/crypto/                   Keystore 口令封装
    data/local/                    Room 表、DAO、SQLCipher 工厂
    data/repository/               事务、渠道记忆、短信去重
    sms/                           纯 Kotlin 解析器、分段拼接、系统接收器
    ui/                            ViewModel、Fluent 页面、表单、主题
  src/main/res/values/strings.xml   应用文案及无障碍描述
  src/test/                        22 项金额、统计、短信、隐私草稿单元测试
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

连接 Android 17（API 37）真机或模拟器后执行：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest --console=plain
```

设备测试覆盖加密重启读写、首次创建中断恢复、明文 SQLite 拒读、数据库及旁路文件明文扫描、数据库和密钥损坏后的文件保留、渠道记忆及历史名称、月份唯一约束与编辑、重复入账与删除后重投、并发导入、隐私语义、后台隐藏及编辑草稿恢复。

人工验收还需检查：短信权限拒绝及安装器限制；真实分段工行短信到达后的自动登记；大字体、横屏、宽窗口和深色模式；日期时间选择器；键盘弹出后的表单滚动和按钮可达性；截图和最近任务预览保护。

## 设计及集成参考

- [Fluent 设计原则](https://fluent2.microsoft.design/design-principles)、[颜色](https://fluent2.microsoft.design/color)、[布局](https://fluent2.microsoft.design/layout)、[字体](https://fluent2.microsoft.design/typography)。
- [Android Compose 边衬区](https://developer.android.google.cn/develop/ui/compose/layouts/insets?hl=zh-cn)。
- [SQLCipher 与 Room 集成](https://github.com/sqlcipher/sqlcipher-android#sqlcipher-for-android-room-integration)。
- [Android 内置 Kotlin 源码目录配置](https://developer.android.com/build/migrate-to-built-in-kotlin)。
