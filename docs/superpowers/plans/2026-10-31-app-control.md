# App Control Demo Implementation Plan

> For agentic workers: use superpowers:executing-plans inline; request a fresh final review.

**Goal:** 独立Demo按勾选名单管控应用，手动/倒计时解除，实际Root验证。
**Architecture:** 纯Java控制状态机包裹持久化、Root后端与闹钟接口；Android适配层负责私有文件、真实系统状态、单后台线程与原生UI。
**Tech:** Java11 / Android Views / min26 target31 compile31 / JUnit4测试。
**Spec:** docs/superpowers/specs/2026-10-31-app-control-design.md

## Constraints / review focus
- 不修改幻境repo或Root配置；只操控用户选择和测试授权的App。
- 先写恢复意图、先安排精确闹钟再暂停，失败不得丢失恢复信息。
- 关闭Demo普通进程后依然到期恢复；强行停止/卸载单独限制并提供恢复操作。
- 反选恢复、重复计时、过期任务、外部暂停、Root拒绝、进程超时、篡改包名均有明确处理。

- [x] 1. 建立独立构建，先写Deadline/ControlManager失败测试，再实现纯状态机。
- [x] 2. 实现AtomicFile存储、su执行/真实状态核查、AlarmClock/启动恢复。
- [x] 3. 实现原生应用选择、Root/权限状态、每应用操作/倒计时/恢复全部。
- [x] 4. 构建并安装MuMu，验证真实应用停用/恢复、到期/后台恢复及数据保留。
- [x] 5. 独立审查、修复、完整验证、导出APK/源码/使用与恢复说明。

## Execution ledger

- 初始缺少ControlManager/Rule等的测试编译失败已观察；实现后13项核心测试通过。
- 审查P2排队广播期限：新增2项RecoveryLease测试先红后绿；安排续接闹钟后再排队工作，8秒内完成广播，后台事务继续。
- 运行时发现空名单提示覆盖Root拒绝错误，改为独立空列表说明；无倒计时时不再持续刷新列表。
- 完整Lint发现API29调用未兼容min26，增加旧版本ApplicationInfo状态读取；全量包查询属于明确应用管控用途，保留权限并定向说明，其他Lint照常执行。
- 最终Debug/Release各15项测试、双构建、Release Lint零错误。MuMu验证详见docs/acceptance.md。
- 使用根目录独立git仓库，保留分支codex/app-control-demo；不推送、合并或修改幻境。
