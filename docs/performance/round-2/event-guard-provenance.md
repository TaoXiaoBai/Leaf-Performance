# Paper #14173：`BlockFromToEvent` guard 来源、许可与最小差集

> 核验日期：2026-09-11；冻结 Leaf 控制身份：`5355f563`（t2 已记录 applied source 与 upstream Leaf `7a5d08d` 等价）。本报告只读核查公开来源和 applied source；未改性能代码，未运行 Gradle、服务器或压测。AI/POI 控制场景没命中事件路径，只说明那个场景冷，不能推出流体等其他真实 tick 场景也冷。

## 1. PR 身份：不要把 34 个文件当成一个可直接搬运的“优化包”

- PR：[PaperMC/Paper #14173 — Optimize event dispatch when no listeners are registered](https://github.com/PaperMC/Paper/pull/14173)，核验时 **open / unmerged**。
- 唯一提交 / head：[`947bb91086bb2c521ff69498a10c0e1d017fb877`](https://github.com/PaperMC/Paper/commit/947bb91086bb2c521ff69498a10c0e1d017fb877)，作者/提交者 `flennium <abderahmanmk05@gmail.com>`，提交信息与 PR 标题相同。
- base：[`11502e7a8f8d0145347e09615be26ffebf682e7f`](https://github.com/PaperMC/Paper/commit/11502e7a8f8d0145347e09615be26ffebf682e7f)（PR API 的 base SHA；也是 head 的唯一 parent）。
- PR 总体为 1 commit、34 patch files、153 additions / 115 deletions。多数 diff 是 Paper source-patch hunk/offset 联动；本任务**只选一个 event family：流体 `BlockFromToEvent`**，不把 vehicle、block physics、`EntityInsideBlockEvent` 混入同一候选。
- 该 family 的固定源文件：[head 的 `FlowingFluid.java.patch`](https://github.com/PaperMC/Paper/blob/947bb91086bb2c521ff69498a10c0e1d017fb877/paper-server/patches/sources/net/minecraft/world/level/material/FlowingFluid.java.patch)，blob `2b7f117d394db412b33fe11a450fcfee1c150a30`。对照：[base 文件](https://github.com/PaperMC/Paper/blob/11502e7a8f8d0145347e09615be26ffebf682e7f/paper-server/patches/sources/net/minecraft/world/level/material/FlowingFluid.java.patch)。

## 2. 逐文件许可证与 provenance

### 可确认的许可

Paper 的 [base/head `LICENSE.md`](https://github.com/PaperMC/Paper/blob/947bb91086bb2c521ff69498a10c0e1d017fb877/LICENSE.md) 是自定义汇总，所以 GitHub API 显示 `NOASSERTION`，但正文并非无许可：

1. Paper 从 Bukkit/CraftBukkit/Spigot 继承 **GPL version 3**；
2. 未列入 MIT opt-in 名单的作者，应推定其工作按原 GPL 发布；
3. 名单中的作者才明确选择 MIT。

`flennium` 或提交邮箱均不在 base/head 的 MIT 名单中。因此，对 `947bb910...` 新增的 guard 代码，当前可适用且保守的结论是 **GPL-3.0（Paper 文本写作 GPL version 3；未声明 “or later”）**。不能把无逐文件 SPDX/header 误写成“无许可”，也不能把它重写后标 MIT 来规避来源许可。

若未来复制 PR 具体表达：

- 在 Leaf 的 applied source / 最终 patch header 中保留 `flennium`、Paper PR #14173、commit `947bb910...` 和 GPL-3.0 说明；
- Leaf binary 本来即 GPL-3.0-only，但 `Leaf LICENSE.md` 的“patch 默认 MIT”不能覆盖该外来 GPL contribution，应使用明确 GPL header/attribution；
- `FlowingFluid.java.patch` 自身没有额外 SPDX/license header，因此适用 root presumption，而不是“未知”。

### 原有代码与第三方来源边界

- base 中 `BlockFromToEvent` 构造/dispatch 是 `CraftBukkit` 标注代码，属于 Paper 所述的 Bukkit/CraftBukkit/Spigot GPL 继承链。
- API 类 `paper-api/.../BlockFromToEvent.java` 未被 PR 修改；本候选无需改 API。其 Javadoc 明确事件用于 liquid/dragon egg，取消即阻止 flow。
- PR/commit 没有声明从另一性能 fork 复制该 guard。PR 披露 AI assistance，但没有第三方代码来源声明。准确结论只能是“公开提交中未声明其他代码来源”，不能证明不存在任何未披露来源。

**许可门禁结论：不是阻塞，但必须按 GPL-3.0 + 明确 attribution 处理；若 reviewer 要求 MIT-only patch，则此候选阻塞，除非作者另行明确授权。**

## 3. 冻结 Leaf 26.2 的真实最小差集

当前 applied file：`leaf-server/src/minecraft/java/net/minecraft/world/level/material/FlowingFluid.java`。

### 未覆盖的两个同 family call sites

- downward flow，当前 `:165-173`：先取 `CraftBlock source`，无条件构造 `BlockFromToEvent(DOWN)`，无条件 `PluginManager.callEvent`，取消时 `return`。
- horizontal flow，当前 `:204-212`：每个候选方向取 `CraftBlock source`，无条件构造 event、dispatch，取消时 `continue`。
- 仓库搜索没有找到 `BlockFromToEvent.getHandlerList().getRegisteredListeners()` 或等价 `hasBlockFromTo` flag。这里是**真实未覆盖差集**，不是靠缺少 Leaf 注释推断。

PR 对这两个 site 的最小变化都是把 event 构造、dispatch 和 cancellation branch 放入：

```java
if (BlockFromToEvent.getHandlerList().getRegisteredListeners().length != 0) {
    // existing event construction, dispatch, cancellation branch
}
```

PR 仍把 `CraftBlock source = CraftBlock.at(level, pos)` 留在 guard 外；因此它只消除 event allocation/manager dispatch，不宣称消除 source wrapper。为了逐源码归因，未来若准入应先按这两个 site 的同一 family/同一 commit 做一个可 revert candidate，**不要顺手移动 source、缓存 HandlerList、加全局 flag 或混入其他 event family**。

### 已有全局 fast path 不等于 call-site 重复

`paper-server/.../PaperEventManager.java:37-45` 已在取得 event 对象后读取 listeners，长度 0 就 return。因此当前 Leaf 已避免“0 listener 时遍历 manager/listener”，但仍已构造 event；#14173 的这个局部差集只是在构造前检查。这也压低了潜在收益：若专项 profile 只显示 event allocation 很小，应直接不准入。

## 4. 语义、副作用与异常路径

### event 构造和 payload

- `BlockFromToEvent` constructor (`paper-api/.../BlockFromToEvent.java:27-35`) 只保存 source block/face 或 target block；没有 dispatch、世界写入或可见 callback。
- `getToBlock()` 在第一次 listener 调用时才以 `source.getRelative(face)` 懒构造 target；0 listener 基线中 manager 已立即 return，因此该 lazy side effect 原本也不会发生。
- PR guard 外仍执行 `CraftBlock.at(level, pos)`，所以 source wrapper 路径/异常次序不变。

### 有 listener 与 cancellation

- listener 长度非 0 时，downward/horizontal 的 event 类型、source、face、dispatch 顺序和 cancellation branch 与 base 相同。
- non-cancelling listener 后继续同一 `spreadTo`；downward cancellation 仍退出本次 `spread`，horizontal cancellation 仍只跳过该方向。
- listener 抛出的异常仍由 `PaperEventManager:54-80` 捕获、记录，并可能发出 `ServerExceptionEvent`；guard 不得吞异常或把 listener 调用包进新 try/catch。

### 动态注册/注销与 TOCTOU

`HandlerList.handlers` 是 `volatile RegisteredListener[]`；`register/unregister` 同步并把它置 null，`getRegisteredListeners()` 在 null 时同步 bake。这使在主线程 tick 之间注册/注销后的下一次检查看到新快照，不需要长期 cache。

但 call-site 有“检查长度 → 构造 → manager 再取 listeners”的两个读取。若另一个线程恰在两者之间注册第一个 listener，guard 可能跳过一个 baseline 原本会 dispatch 的事件；若恰好注销最后一个，最多多构造一次再被 manager 0-listener return。Bukkit 对这种跨线程 listener mutation 的线性化语义在本次核验中**未找到明确保证**，所以：

- 不得把 listener count 缓存在 static boolean 或跨 tick；
- correctness workload 必须在主线程 scheduler 的已知 tick 边界注册/注销，验证下一合法 flow event 即生效；
- 对真正并发 register/unregister 只能标“语义未定义/未验证”，不能宣称安全；如 reviewer 要求对任意跨线程注册与 baseline 强线性等价，则该 guard 应排除。

### 线程/异常检查

当前 `PaperEventManager` 在 listener array 为空时先 return，随后才做 synchronous/asynchronous thread check。因此 0-listener call-site guard 不会额外消除一个原本必抛的 wrong-thread exception；正常 `FlowingFluid#spread(ServerLevel,...)` 仍应在 tick thread。未来不得借此把 fluid spread 移到异步线程。

## 5. 可复现专项 admission（不是已证明热点）

### 稳态真实 tick workload

使用同一个最小 workload 插件/数据包只负责在**固定主线程 tick**补充水/熔岩 source，使流体在封闭、预加载网格中持续向下和水平传播；A/B 使用相同 world copy、补充序列、seed、plugin set。不要通过减少 fluid scheduled ticks、降低 random/simulation distance 或扩大 tick interval 制造收益。

建议至少三层规模，使 20 TPS 下存在足够但不过载的真实 scheduled-fluid ticks：

1. control：少量独立水流，校验环境噪声；
2. target-water：多条隔离的水通道，同时触发 DOWN 和 2–4 个 horizontal candidates；
3. target-lava：规模按 tick delay 调整，单独观察 lava 路径，不把水/熔岩结果混成一个数字。

每次仍遵守冻结协议：新进程、同龄停止态母本、`>=420s` warmup、预注册 measurement tick count、至少 3 对平衡交错；busy MSPT 在 `ServerTickStartEvent(N)` 读取 prior ring entry。先做 admission profile：`FlowingFluid#spread/spreadToSides` 与 `BlockFromToEvent` constructor/`PaperEventManager#callEvent` 必须在 real-tick main-thread inclusive CPU 或 allocation 排名中稳定可解释，否则不实现。

### 四个 listener 场景

1. **L0：0 listener** — workload 插件不注册 `BlockFromToEvent`；这是性能主场景。
2. **L1：1 non-cancelling listener** — 记录 source/face/toBlock，绝不修改世界；要求 candidate 性能不实质回退，payload/order 完全一致。
3. **LN：N listeners** — 覆盖优先级、`ignoreCancelled`、一个按确定坐标取消、一个 MONITOR 观察；验证调用顺序/取消传播/异常隔离。
4. **LD：dynamic** — 主线程 scheduled task 在预定 tick 注册、注销、再注册；下一次合法 fluid event 的 dispatch 状态必须与 baseline 一致。另设一个 listener 在 handler 内注册/注销其他 listener，按 HandlerList snapshot 语义比较本次/下次事件。

不要求 CMI 付费 jar；报告只写“CMI 未验证”。若可获得任何公开流体保护插件，可额外作兼容回归，但不能据此外推 CMI。

### 完成量与 oracle

每 measured tick 保存或汇总：

- attempted downward/horizontal flow candidates；
- constructed/dispatched `BlockFromToEvent` 数（仅 L0 candidate 可降 constructed，L1/LN/LD 应与 baseline 一致）；
- listener ID/priority 调用序列、source pos、face、lazy `toBlock`、cancel state、listener exception/`ServerExceptionEvent` 数；
- successful `spreadTo` 数、scheduled fluid tick backlog、每窗水/熔岩推进数；
- measurement 结束时预定区域 block/fluid state hash。

安全 oracle：L0 最终世界/完成量与 baseline 一致；L1/LN/LD 的 event payload、顺序、取消、副作用、异常日志和世界状态一致。P95/P99、total CPU、allocation、GC、contention 均不得实质回退。

### 通过/淘汰

- 主判定仍是 L0 目标 workload 的 same-condition busy MSPT 与对应 main-thread inclusive CPU：配对中心改善 `>=5%`、超过控制噪声、3 对方向一致；allocation 只能辅证。
- L1/LN/LD 任一语义差异、动态注册边界不一致、event/flow 完成量下降、尾延迟或 total CPU 回退即淘汰。
- 若仅 constructor allocation 降低而整窗/方法 CPU 未过门槛，零收益不得保留。

## 6. 结论

`BlockFromToEvent` 是 #14173 中一个许可可确定、当前 Leaf 26.2 确有两个未 guard call sites、局部范围可限制的单一 event family。它可以作为**待专项 profile 的研究建议**，而不是已准入实现：

- 来源固定到 base `11502e7a...` / head `947bb910...` / `FlowingFluid.java.patch` blob；
- 许可证按 Paper root presumption 为 GPL-3.0，作者不在 MIT opt-in 名单；
- 差集仅为 event 构造前 listener count check，manager 已有 post-construction 0-listener early return，收益很可能小；
- 正确性主要风险是动态 listener TOCTOU，必须按上述 L0/L1/LN/LD oracle 验证。

未经 captain/reviewer 接受新的预注册并完成 admission profile，不应修改性能代码。
