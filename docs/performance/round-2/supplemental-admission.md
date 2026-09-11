# Performance Round 2：S1/S2 独立准入审查

> 日期：2026-09-11。本文件独立复核 `supplemental-candidates.md` 与 26.2 applied source，只批准可证伪的研究步骤；未运行服务器、构建或重负载，也不批准任何实现已经正确或有收益。共同身份、测试前置与计时协议以 `protocol.md` 为准。

## 1. 结论摘要

| 候选 | 独立结论 | 可进入的下一步 |
|---|---|---|
| S1 nitwit job-site `AcquirePoi` | **性能实现暂不准入**：predicate 恒 false 不等于无副作用；当前 behavior 仍消费共享 RNG、维护调度/trigger，并以 `LOAD_FOR_SEARCHING` 遍历/加载 POI sections。literal removal 已拒绝，行为壳 fast path 也只是条件研究。 | 只先做 instrumentation/admission；只有能保持 Brain 生命周期、memory、调度/RNG/trigger以及POI加载副作用，才允许冻结单独 candidate。 |
| S2 map/player/tick 公共追踪去重 | **性能实现暂不准入**：当前公共扫描依赖 frame/inventory 模式和同 tick 可变状态，源码没有足够的局部 mutation epoch；简单 `(map, player, gameTime)` stamp 会吞更新。 | 只批准 no-code instrumentation/admission。只有先证明局部、不漏失效的 immutable-input token 或更窄等价区域，才允许冻结 candidate；否则以 oracle 阻塞终止。 |

两项均不是已证明热点，也不计“实现 + A/B”。它们只能排在 `t4` 已补齐独立 frozen-upstream 测试、完整专项 profile、真实 tick 边界和完成量之后；engineer 不得根据本文静态分析提前改源码。

## 2. 共用实验门禁

1. **前置身份**：先完成 `protocol.md` 所列隔离完整 frozen-upstream 匹配测试与失败签名分类，并补足可解释的独立 profile。现有 15 tick samples 只给 Brain/POI 方向，不能给 S1 排名；对 S2 没有任何热点证据。
2. **先 admission、后 candidate**：admission instrumentation 与 A/B candidate 分开。静态恒 false、调用次数多或 allocation 看起来明显，都不算真实热点。
3. **预热与测量分离**：每 run 新进程、同一停止模板副本，`warmupSeconds >= 420`；随后进入在实现前按控制噪声/功效冻结的 `measureSeconds` 和精确 `measuredTickCount`。不得把 420 s 写成测量时长。
4. **真实 tick 边界**：主 A/B 使用 `ServerTickStartEvent(N)` 读取已发布 prior ring entry 的 busy-tick vector；full-loop duration/lost tick、wall/park/monitor 单列。JFR/async-profiler inclusive samples 只作热点/归因支持，不替代真实 tick 主指标。
5. **独立设计**：每项 A=同一个冻结无性能补丁 control，B=只含该候选；至少 3 对平衡交错 `A-B / B-A / A-B`，同龄、同 workload、同顺序平衡。默认保留要求预注册主指标改善 `>=5%`、每对同方向且超过 noise band；不得事后换主指标。
6. **共同护栏**：average/P50/P95/P99 MSPT、main-thread inclusive CPU、total CPU、allocation、GC、contention、RSS、业务完成量和精确测试签名。任何行为/线程/生命周期差异、P95/P99 或完成量实质回退、total CPU 上升、收益不足均 revert/不实现。
7. **组合**：S1-safe、S2 只有分别相对同一冻结 A 独立通过后，才允许测 `A / S1 / S2 / S1+S2`。组合仍至少 3 对交错，并且不得掩盖单项回退；组合结果不能取代单项证据。
8. **许可/兼容**：CMI 无 jar 只标“未验证”。S1 外部灵感为 LGPL-3.0；若复制任何表达性代码须保留 attribution/license。本审查优先要求从 applied source 不变量独立写最小差异。S2 的 Paper issue 只作问题/复现来源，不当作可复制代码。

## 3. S1 — nitwit job-site `AcquirePoi`

### 3.1 版本、来源与 applied-source 差集

- 外部来源：[Lithium PR #718](https://github.com/CaffeineMC/lithium/pull/718)，head `14bdadfda9a150ea540e6a7e99377e3253898e78`，closed/unmerged，8 files、151 additions；fork/repository 标示 LGPL-3.0。它不是经合并验证的 Leaf 26.2 patch，不移植其 generic Brain-removal framework。
- 26.2 applied source 中，`VillagerProfession` 用 `PoiType.NONE` 注册 `NITWIT`，`PoiType.NONE` 为恒 false predicate；`VillagerGoalPackages#getCorePackage` 仍对传入的所有 profession 建 priority 6 job-site `AcquirePoi`。没有按 nitwit 省略的现有分支，故静态差集存在。
- 但 `AcquirePoiBehavior#trigger` 在 predicate 查询前后仍有可观察/可传导工作：检查 `JOB_SITE`/`POTENTIAL_JOB_SITE` memory；处理 pending path；从共享 `level.getRandom()` 消费初始化/周期 jitter；维护 `nextScheduledStart`；清理 retry map；最终返回 `true`。而 `PoiAccess.findNearestPoiPositions(..., LOAD_FOR_SEARCHING, ...)` 会在按 type predicate 过滤前遍历并 `getOrLoad` POI sections；恒 false 仍可能改变 POI/chunk加载状态与后续时序。所以“没有成功候选”不证明 behavior 或查询无副作用。
- Brain 构造时捕获 profession：`Villager.BRAIN_PROVIDER` 调 `getCorePackage(profession)`；save/load、age boundary 和显式 `refreshBrain` 会重建。`Villager#setVillagerData` 与 Bukkit `CraftVillager#setProfession` 本身不自动 refresh。优化若每 tick看当前 profession，会改变现有“直到 Brain 重建才换 predicate”的语义。

### 3.2 允许与禁止的局部边界

**不允许**：从 CORE behavior 列表省略 priority 6 条目；减少触发/调度次数；跳过当前会发生的 RNG 消费；把判定改成每 tick 动态 profession；修改 HOME/MEETING acquisition、Brain 通用 mutation API、PoiAccess、async path executor 或 tick 频率。

**仅条件允许研究的 S1-safe**：在 Brain 构造时随现有 captured profession 冻结“job-site predicate 恒 false”标志，保留同一个 behavior 壳和 required memories。任何 fast path 除保持与 A 相同的 memory/pending/schedule/RNG/retry、`trigger` 返回与 activity scheduling外，还必须证明或重现 `LOAD_FOR_SEARCHING` 的 section访问/加载副作用、顺序与异常。若要保留这些加载就无法局部省掉主要扫描，或无法构造完整 oracle，则无 candidate。

### 3.3 Admission 与 A/B 预注册

- **admission 场景**：0/100/500/1000 active nitwits，配同数 NONE/unemployed/farmer controls；固定 chunk/POI age、活动范围、20 TPS、job blocks。增加 normal POI、无可达 POI、POI 耗尽三层。
- **admission 证据**：补足独立 runnable profile，证明 nitwit 的 `Brain.tick -> AcquirePoi -> PoiAccess` 在真实 tick 内形成稳定、可解释 inclusive 排名；记录每窗 CORE behavior触发、memory-gate通过、scheduled attempt、PoiAccess调用、path创建、成功占用、失败重试和完成 ticks。冷则停止。
- **条件 A/B**：只有上述副作用 oracle 先通过才冻结 B；A=冻结 control，B=仅 S1-safe且不是 literal removal。`>=420 s` 预热后使用事前冻结的实际测量时长；至少3对交错。
- **主指标**：预注册为 exact busy-tick mean MSPT，要求 `>=5%` 且超噪声；nitwit `AcquirePoi/PoiAccess` inclusive CPU 与 allocation 为归因支持，不能事后替换主指标。
- **完成量/护栏**：相同 measured ticks、behavior attempts、普通村民 acquisition/path/occupation；process/total CPU、P95/P99、GC、contention不得回退。

### 3.4 必须逐步一致的 oracle

1. 每 villager/tick 的 required-memory gate、behavior trigger boolean、active CORE task/priority与 debug-visible task list。
2. `nextScheduledStart`、jitter draw 数/值及每次调用前后的共享 `level.random` 后续序列；retry map key/mark/expiry。
3. `JOB_SITE`、`POTENTIAL_JOB_SITE`、walk target、activity、path target、POI tickets、访问/加载的POI sections、同步加载计数、已加载chunk/section集合、broadcast/debug event 序列；覆盖已加载中心与未加载边界。
4. 初生/初载 nitwit；save→reload；age boundary；zombie cure；nitwit→farmer、farmer→nitwit、NONE→nitwit。分别覆盖 Bukkit/NMS 仅 `setVillagerData`（不 refresh）与随后显式/vanilla `refreshBrain`，候选必须匹配 A 的 Brain-lifetime捕获行为。
5. 插件事件改变 profession（Assign/Reset）、取消或改写职业时，Brain重建前后分别比较；没有真实插件包则相关插件语义标未验证，不能声称兼容。

任一 RNG、trigger、memory、profession/Brain 重建或完成量差异即拒绝；不能以“最终都找不到 job site”豁免。

## 4. S2 — `MapItemSavedData#tickCarriedBy` 公共追踪去重

### 4.1 版本、来源与 applied-source 差集

- 来源：[Paper issue #9597](https://github.com/PaperMC/Paper/issues/9597)，open、`status: accepted`、`scope: performance`。它描述重复调用和 add/remove churn，不提供实现；issue 文本不是可复制 patch。候选若独立实现，许可遵循本仓库实际文件/header 与 Leaf `LICENSE.md`。
- 当前 26.2 `MapItemSavedData#tickCarriedBy` 每调用创建 `mapMatcher`，检查 ticking player inventory，遍历并可能修改整个 `carriedBy`，处理 frame marker，最后合并该 `ItemStack` 的 `MAP_DECORATIONS`。调用来自 `MapItem#inventoryTick`、`ServerEntity` item-frame viewer loop、`ServerPlayer` dropped-map path；没有 `(map,player,tick)` stamp，静态差集存在。
- 公共扫描并非只由 `(MapItemSavedData, Player, gameTime)` 决定：`placedInFrame != null` 会绕过 other-player inventory presence；matcher 取当前 stack item/MAP_ID；player removed/inventory/dimension/position/rotation与隐身装备可同 tick 变化；`carriedBy`和decorations在每次调用中变化。frame调用后立即 `getUpdatePacket`，所以只比较 tick 末状态不够。

### 4.2 当前不批准实现的原因与条件边界

简单“本 tick 已处理 player”会吞掉合法更新，包括：同 tick map拿起/丢弃/换手、inventory或MAP_ID/MAP_DECORATIONS变化、不同复制 stack 的static decorations、frame/手持/掉落调用交错、frame替换/移除、player removed或换维度/移动/旋转、隐身装备变化，以及调用后立刻发包/renderer观察。

源码当前没有覆盖这些输入的单一 mutation/version token。为它们跨 `Inventory`、`ItemStack` components、Entity/player/frame、map renderer与tracking生命周期增加失效钩子会成为高风险跨文件缓存系统，不符合“局部替补”。因此本轮只批准 instrumentation；除非 engineer 在改代码前提交一个局部、可证明完整的 immutable-input token 或找到更窄、完全不依赖上述可变输入的重复区域，否则以 oracle 阻塞终止 S2，不为凑数重构。

若后来满足条件，允许的 B 只能跳过 **输入 token 完全相同且其间无任何观察点/突变** 的公共计算；每 stack static decorations、每 frame marker、每次应有的 dirty传播、packet/render cadence仍逐调用。不得缓存跨 tick，不得仅凭 gameTime。

### 4.3 Admission 与条件 A/B 预注册

- **admission 场景**：单 map/单 player control；同 map ID 36-slot duplicates × 20/50 players；100/500 frames × 多 viewers；手持+frame+掉落交错；不同 `MAP_DECORATIONS` duplicates。加入脚本化同 tick inventory换手/丢弃/拾取、MAP_ID/component变化、frame add/remove/replace、player disconnect/remove/维度切换/移动旋转、隐身装备切换。
- **插件/renderer 场景**：vanilla `CraftMapRenderer` control与自定义 Bukkit `MapRenderer`，记录 renderer调用、每 viewer packet cadence/payload。未提供插件包时标相应路径未验证，不能外推兼容。
- **admission 证据**：证明 real-tick `tickCarriedBy`、inventory contains与`carriedBy`扫描为可重复 inclusive CPU/allocation热点；记录调用数、唯一 `(mapId,player)`、frame/inventory/drop模式数、扫描条目数、state mutations、renderer调用、实际packets和完成ticks。
- **条件 A/B**：只有局部失效证明通过才冻结 B。A=同一冻结control；B=仅S2窄去重。每run `>=420 s` 预热，测量时长/精确ticks事前另冻，至少3对交错。
- **主指标**：exact busy-tick mean MSPT，`>=5%` 且超噪声；`tickCarriedBy` inclusive CPU和allocation/call仅作归因。不得把少发包、少render或少业务更新当收益。
- **组合**：S2单独通过前不得与S1或其他patch组合测量。

### 4.4 每次调用边界 oracle

在 A/B 中对每次 `tickCarriedBy` 返回点、以及紧随其后的 `getUpdatePacket`/renderer观察点比较：

1. `carriedByPlayers` key→HoldingPlayer identity、`carriedBy`顺序/成员及删除清理。
2. player、frame、static decorations完整map、顺序、tracked count、dirty状态与每个 HoldingPlayer dirty flags/counters。
3. frameMarkers identity/entity id/position/direction；itemFrame cursor limit行为。
4. 每 viewer packet是否发送、类型、payload、cadence；Bukkit renderer调用次数/顺序/输出。
5. 同 tick每次 inventory/map component/frame/player变化前后的状态，尤其 frame-mode `placedInFrame != null` 与 inventory-mode的不同清理分支。
6. player offline/remove、dimension变化、map丢弃/拾取、frame tracking进入/退出和`removedFromFrame`后的释放；不得跨world/map生命周期保留stamp。

任何一次中间状态、packet/render、decorations、cleanup或完成量差异都拒绝。只在“普通稳定tick末看起来相同”不足以通过。

## 5. 对 t4 的交付顺序

1. 先完成 `protocol.md` 尚缺的独立 frozen-upstream 测试、失败签名核对与补足 profile/runner门禁。
2. 再做 S1/S2 no-code admission instrumentation；不要同时实现。
3. S1 literal behavior removal 已拒绝；S1-safe 也只有全部 RNG/Brain/POI-section加载 oracle 可构造才可进入单独 A/B，否则记录 oracle 阻塞。
4. S2 只有先证明局部 immutable-input token/完整失效才可进入单独 A/B；否则记录 oracle 阻塞，不改代码。
5. 未准入/拒绝不计实现+A/B数量；失败继续下一项，不转向 timing wheel、线程搬运或高风险缓存重构凑数。
