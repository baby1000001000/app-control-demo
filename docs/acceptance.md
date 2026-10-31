# 应用管控 Demo 验收（2026-10-31）

## 交付

独立项目 `/Users/huangweizhu/mobile_workspace/app-control-demo`，包名`com.local.appcontrol.demo`，V1.0.0/code1。未修改幻境仓库或覆盖幻境APK。当前MuMu运行端口16384；抖音com.ss.android.ugc.aweme版本36.4.0。

## 构建与自动化

JDK17执行：

```sh
./gradlew testDebugUnitTest testReleaseUnitTest assembleDebug assembleRelease lintRelease
```

Debug/Release各15项测试均通过；Release Lint零错误、2项兼容属性提示（Android12数据备份规则在旧版本被忽略、旧版使用allowBackup=false）。QUERY_ALL_PACKAGES是本工具读取完整第三方应用清单和管理包状态的核心用途，在Manifest中只对该政策提示做定向说明，未整体跳过Lint。

状态测试覆盖：勾选不执行停用、禁止操作未勾选包、Root失败、写盘/闹钟失败、外部暂停不接管、取消勾选先恢复、重新计时、超时重试、进程重建、跨开机/系统改时钟、命令参数与关键包保护。

独立审查发现一项P2：恢复广播可能排在长时间Root工作后，消耗一次性闹钟却未及时安排下一次。新增RecoveryLeaseTest先因缺少该恢复协议而失败；实现预先续接闹钟、8秒广播完成期限和幂等完成后通过。实际后台恢复也通过。

Release APK已zipalign、apksigner v1/v2/v3验证；debuggable=false，无INTERNET权限，沿用本机开发证书。私钥不在源码/输出中。

## MuMu真实抖音验证

- Root最初被Magisk按UID10048拒绝；通过超级用户界面只允许本Demo，未改动幻境的授权或框架设置。
- 从第三方应用清单勾选抖音，名单持久化；其他应用不被选中。
- 手动停用：PackageManager用户0状态`suspended=true, stopped=true`，suspendingPackage=root。
- 尝试打开实际抖音启动Activity，系统转入`android/com.android.internal.app.SuspendedAppActivity`，未进入抖音。
- 手动恢复：`suspended=false`。
- 20秒倒计时正常到期恢复。第一次在很接近到期时终止进程的测试未作为充分的进程死亡证据，因此重新执行60秒测试。
- 60秒测试：尚余58,907ms时Demo进程被结束；第一次检查抖音仍处于暂停，随后系统闹钟启动恢复进程。终止进程后59.103秒观察到`suspended=false`，私有记录owned=false、deadline=0。日志见countdown-process-death-result.json。
- 再次打开抖音，`am start -W`确认实际Activity为`com.ss.android.ugc.aweme/.main.MainActivity`。
- 最终Release APK再测手动停用→取消勾选：立即恢复目标并移出名单。测试后重新将抖音加入名单，保持允许使用，无剩余倒计时。
- 抖音数据身份`ceDataInode=7078767`始终相同，未清除/卸载；未勾选的幻境保持未暂停。

## 限制

实体手机与实际重启未测试；开机和改时钟逻辑由纯状态测试覆盖。Android8/9采用ApplicationInfo.FLAG_SUSPENDED读取状态，Android10+使用公开包暂停查询接口，API检查通过。强行停止/卸载/清除Demo数据、撤销闹钟或Root权限会影响恢复，README含手动恢复步骤。

本机证据：`/Users/huangweizhu/Downloads/app-control-demo-20261031/`，含构建/签名/计时日志及系统拦截截图。
