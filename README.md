# 应用管控 Demo

独立 Android 原生应用，项目与“幻境”完全分开。通过 Root 暂停指定第三方应用，实现手动和倒计时恢复。无网络权限，不上传应用清单，不卸载或清除目标数据。

## 使用

1. 安装 APK，打开“应用管控 Demo”。需要 Android 8.0+ 和已正常工作的 Magisk / KernelSU / APatch 等 Root 环境。
2. 点“检测 Root”，在 Root 管理器中永久允许 **应用管控 Demo / com.local.appcontrol.demo**。ADB 能取得 Root 不代表这个新 App 已获授权；若之前被拒绝，到 Root 管理器“超级用户”页单独开启本 Demo。
3. Android12+ 检查“倒计时权限”，按提示允许系统“闹钟和提醒”。
4. 点“选择应用”，搜索并勾选抖音或其他应用。此处按需求把勾选项视为**管控名单**；勾选本身不会停用应用。
5. 回到“管控名单”，点某应用的“停用…”。选择倒计时（秒 / 分钟 / 小时，1秒～30天），或选择“手动恢复”。
6. 停用后，系统会阻止该应用打开；列表显示实际系统状态与倒计时。“恢复使用”只解除暂停，“打开”按钮可随后启动应用。
7. “恢复全部受控应用”可一键解除本 Demo 接管的暂停；取消勾选时也会先恢复，再移出名单。其他管理来源已经暂停的应用不会被本 Demo接管或解除。

Demo自身、当前桌面、系统应用和已知Root管理器不会被列为可选目标。只操作本次勾选的应用，按当前Android用户执行。

## 倒计时保证与限制

- 到期任务由 Android AlarmManager 的 AlarmClock 精确闹钟触发，不依赖 Activity 常驻或悬浮窗。普通退出或进程被系统回收后仍可触发。
- 记录保存在私有 AtomicFile；先保存恢复意图并安排闹钟，再执行暂停。后台恢复使用独立的续接闹钟和最多8秒广播租期，避免被长Root任务阻塞后丢失恢复机会。
- 一次开机内用单调时钟计时，调整系统时间不会延长原倒计时；开机、更新、时间变化后重新安排。重启后按保存的到期墙钟时间恢复；手机关机时不能执行，开机解锁后补处理。
- Root拒绝或执行失败不会显示成恢复成功；保留恢复记录并显示错误，过期任务会重试。
- **强行停止、清除数据、卸载 Demo 或撤销精确闹钟权限，可能取消系统任务。操作这些动作前先点“恢复全部”。Root授权应为持久允许，以便无人点击时后台执行。**
- Root管理器、安全模式、其他有特权工具仍可绕过或改变状态；此Demo是本机管理工具，不是不可绕过的安全边界。
- 普通第三方App兼容性取决于ROM对`pm suspend`的支持。部分系统组件不能被暂停；本Demo不提供系统应用管控。

## 异常恢复

优先打开 Demo →“检测 Root”→“恢复全部受控应用”。若 Demo 已被卸载或数据已清除，在授权Root的 ADB 终端恢复指定目标，例如抖音：

```sh
adb shell su -c 'pm unsuspend --user 0 com.ss.android.ugc.aweme'
```

以上`0`为主用户。工作资料/其他Android用户应替换为对应用户ID。不要向命令拼接陌生输入。恢复不会清除抖音数据；解除后从桌面重新打开即可。

## 构建与结构

Java11字节码、原生View、minSdk26、target/compileSdk31；零运行时依赖，测试使用JUnit4。需要JDK17和AndroidSDK31。将本机SDK路径放在未提交的`local.properties`，不把JDK绝对路径写入项目。

```sh
./gradlew testDebugUnitTest assembleDebug assembleRelease
```

Release默认输出未签名包，须使用自己的证书签名；本次交付的Release APK使用本机开发证书签名，便于MuMu覆盖安装，不包含任何私钥。

- `ControlManager`：选择、暂停所有权、事务顺序、恢复/倒计时/故障状态。
- `Rule` / `TimePoint`：不可变状态与跨重启、改系统时间的计时。
- `AndroidBackend` / `RootCommander`：参数校验、系统状态复核、有限时Root执行。
- `RuleStore` / `RecoveryAlarm`：私有原子文件和单个最早到期系统闹钟。
- `RecoveryLease` / `ReceiverWork`：预先续接闹钟、排队工作与广播完成期限。
- `AppEngine`：单线程串行控制、非UI包管理器读取。
- `MainActivity`：应用选择、状态、操作和倒计时界面；ListView复用行，界面每秒只更新有倒计时的列表。

设计与实施记录见`docs/superpowers/`；测试验收见`docs/acceptance.md`。

官方机制参考：[应用暂停](https://developer.android.com/reference/android/app/admin/DevicePolicyManager#setPackagesSuspended(android.content.ComponentName,%20java.lang.String[],%20boolean))、[系统精确闹钟](https://developer.android.com/develop/background-work/services/alarms)。本Demo通过获得授权的Root调用系统包管理命令，不要求将自身配置为Device Owner。
