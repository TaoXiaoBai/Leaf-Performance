# Performance Round 2：重复项复核与局部替补候选

> 日期：2026-09-11。本文只做 applied-source / 公开来源的静态复核，是给 captain/reviewer 的补充研究输入；没有运行服务器、Gradle 或 A/B，也不修改已经冻结的 `candidates.md` / `shortlist.md`。TPS-window 不重访。

## 1. engineer 指出的三项重复：精确复核

### #6 Lithium explosion entity-raycast hit factory — **功能已被更强的 Paper/Moonrise 路径覆盖**

外部原候选为 [Lithium PR #623](https://github.com/CaffeineMC/lithium/pull/623)（Lithium [LGPL-3.0](https://github.com/CaffeineMC/lithium/blob/develop/LICENSE.md)），重点是避免 entity exposure raycast 的通用 hit-result/clip 开销。

当前 applied source 的精确差集为空或更强：

- `leaf-server/src/minecraft/java/net/minecraft/world/level/ServerExplosion.java:141-249` 已实现专用 `clipsAnything(...)`。它以 DDA 直接遍历 block coordinates，并返回 `boolean`；不是为每条 exposure ray 调 `Level#clip` 后构造/返回 `BlockHitResult`。
- `ServerExplosion.java:252-297` 的 `getSeenFraction(...)` 直接复用 `LazyEntityCollisionContext`、direct-mapped `ExplosionBlockCache[]` 和一个 `MutableBlockPos`，每条采样 ray 只问 `clipsAnything`。
- `ServerExplosion.java:96-138, 191-220` 还缓存 chunk/block state、resistance 与可缓存 collision shape；这比“只换 hit factory”覆盖面更大。

结论：不存在可隔离的 #623 hit-result 构造差集；强行移植只会在已专用的 boolean traversal 上重复包一层。**不准入、不计实现/A-B。**

### #7 Lithium 16³ shell rays — **逐循环等价已存在**

外部原候选为 [Lithium PR #686](https://github.com/CaffeineMC/lithium/pull/686)（LGPL-3.0）。

当前 applied source 精确覆盖：

- `ServerExplosion.java:58-83` 静态构建 `CACHED_RAYS`；三重循环虽枚举 0..15，但仅在 `x/y/z` 任一坐标为 `0` 或 `15` 时写入，恰为 16³ 外壳而非内部 4096 点。
- `ServerExplosion.java:393-410` 注释明确写出 vanilla 只有约 1/3 perimeter rays 有效，并直接迭代 `CACHED_RAYS`，不在每次爆炸重做内部点判断或向量归一化。
- `ServerExplosion.java:412` 的 PRNG 消费发生在缓存 ray 迭代内，外壳顺序保持 x/y/z 原循环边界顺序。

结论：#686 不只是“同类”，而是 ray 集、缓存和消费位置均已实现。**不准入、不计实现/A-B。**

### #13 FerriteCore FastMap StateHolder — **Moonrise zero-collision table 已覆盖同一 lookup 目标**

外部原候选为 FerriteCore [`FastMapStateHolderMixin`](https://github.com/malte0811/FerriteCore/blob/0cef1f2add1f1329aa6e690e8e292acd625c5c6d/Common/src/main/java/malte0811/ferritecore/mixin/fastmap/FastMapStateHolderMixin.java)（[MIT](https://github.com/malte0811/FerriteCore/blob/26.1/LICENSE)）。

当前 applied source 不是 vanilla map：

- `StateHolder.java:16,25-57` 已实现 Moonrise `PropertyAccessStateHolder`，为 state 建 `ZeroCollidingReferenceStateTable` 和预计算 `tableIndex`；初始化后把每个 state 的 `propertyKeys/propertyValues/neighbors` 置空去重。
- `StateHolder.java:92-152` 的 `getProperties/hasProperty/getValue/setValue/trySetValue` 全部走 `optimisedTable`，不再走原始 property map/linear neighbor representation。
- `ZeroCollidingReferenceStateTable.java:20-51` 以 property ID 建无冲突 indexer；`:97-152` 用预计算乘法/除法 magic 直接取值和算目标 state index，`:81-95` 用连续 `S[] lookup`。
- `StateDefinition.java:70-72` 在 state 集完成后统一 `moonrise$init(states)`，生命周期也已经接入。

Ferrite FastMap 的核心目标（property→value/state 的紧凑索引和直接 lookup）因此没有独立差集；替换 Moonrise 表会是架构替换而非局部补丁，且基线未命中 StateHolder。**不准入、不计实现/A-B。**

## 2. 替补研究项（只找到 2 项满足“局部 + 可做真实 tick 专项”）

没有为了凑到 3 项加入 scheduler rewrite、异步搬运、纯启动/保存路径或 workload 降级。以下两项仍然只是 **待 admission profile**；captain/reviewer 未准入前不得实现。

### S1 — 对 NITWIT 省略永不可能成功的 job-site `AcquirePoi`

- **公开来源/许可**：[Lithium PR #718](https://github.com/CaffeineMC/lithium/pull/718)，固定 head [`14bdadfda9a150ea540e6a7e99377e3253898e78`](https://github.com/jcw780/lithium/commit/14bdadfda9a150ea540e6a7e99377e3253898e78)；PR 未合并、已关闭，来源代码为 **LGPL-3.0**，若复制任何片段必须保留 attribution/license。本建议优先根据本地不变量做最小独立实现，而不是移植其 8-file generic Brain-removal framework。
- **26.2 与现状**：外部 PR 基于当时 Lithium `develop`，不能宣称原生 26.2；但当前 Leaf 26.2 的不变量可直接核验：`VillagerProfession.java:259` 以 `PoiType.NONE` 注册 `NITWIT`，而 `PoiType.java:9` 定义 `NONE = poiType -> false`；与此同时 `VillagerGoalPackages.java:49-59` 对所有 profession 无条件加入 job-site `AcquirePoi`。本地未发现按 `NITWIT`/恒 false predicate 省略这一个 behavior 的分支。
- **局部实现边界**：只允许在 `VillagerGoalPackages#getCorePackage` 构造列表时，对 `profession.is(VillagerProfession.NITWIT)` 省略 priority 6 的 job-site `AcquirePoi`；不得引入通用 Brain mutation API，不得触碰 HOME/MEETING acquisition、行为 tick 频率、搜索半径、PoiAccess 或异步 pathfinding。更保守的 reviewer 也可先只做 instrumentation，不写 patch。
- **风险/工作量/CMI**：风险 **低到中**、工作量低。vanilla built-in nitwit 的 predicate 恒 false，理论上无成功路径；但 datapack/registry reload、profession 切换后的 brain refresh、插件/NMS 对 behavior 列表的观察仍须验证。CMI jar 未提供，必须标“CMI 未验证”，不可推断兼容。
- **真实 tick admission workload**：从当前 active-villager 母本复制，分别放置固定数量（例如 0/100/500/1000）的 active nitwits 与同数量普通 unemployed/farmer control；确保 chunk/POI age、活动范围和 20 TPS 一致。先以 JFR/async-profiler 证明 nitwit 的 `Brain.tick -> AcquirePoi -> PoiAccess` 确实出现且可重复，记录每窗 nitwit behavior checks、job-site acquisition attempts、普通村民成功占用/路径完成量。
- **作用计时边界**：主指标是 **nitwit 子组** `AcquirePoi/PoiAccess` inclusive main-thread CPU；整窗 busy MSPT（`ServerTickStartEvent(N)` 读取 prior ring entry）为最终门槛。不得用构造期或启动期时间。仍执行冻结的 3 对交错、>=420s 预热和默认 `>=5%` 超噪声门槛。
- **oracle/淘汰**：nitwit 不得获得/丢失任何原本可观察 memory、activity、walk target；普通 profession 的 behavior 列表/POI/path/occupation 序列必须 bit-for-bit/事件级等价。profile 冷、收益 <5%、profession 切换/registry lifecycle 不清或任何行为差异即不实现/revert。

### S2 — `MapItemSavedData#tickCarriedBy` 的“每 map/player/tick 公共追踪”去重

- **公开来源/许可**：[Paper accepted performance issue #9597](https://github.com/PaperMC/Paper/issues/9597) 精确指出同一 map 的复制品、玩家库存和 item frame 会让 `tickCarriedBy` 在同 tick 重复扫描 `carriedByPlayers` 并重复读取 decorations。该 issue 是问题/复现来源，不提供可复制实现；候选应从当前 applied source 独立实现。Paper repo license API 为 `NOASSERTION`，Leaf patch 许可按 `LICENSE.md` 与实际 header 决定，不擅自赋予 issue 文本代码许可证。
- **26.2 与现状**：issue 很旧但当前 Leaf 26.2 仍有精确形态：`MapItemSavedData.java:203-269` 每次调用都创建 capturing `mapMatcher`、扫描 `tickingPlayer` inventory、遍历整个 `carriedBy`，之后再合并当前 `ItemStack` 的 static decorations；没有 game-time/player 去重。调用点有 `MapItem.java:283`（inventory）、`ServerEntity.java:129`（frame 对每 viewer）及 `ServerPlayer.java:2912`（dropped map）。因此不是靠 Leaf 标记判定缺失。
- **局部实现边界**：只研究把与具体 `ItemStack` decorations 无关的公共部分（holder membership、当前 player inventory/map presence、`carriedBy` player decoration refresh）限制为同一 `MapItemSavedData + Player + gameTime` 一次；每个实际 stack/frame 的 static decoration 与 frame-marker 语义仍逐调用执行。不得降低 map 更新频率、减少 viewer、缓存跨 tick inventory 结果或改变 packet/render cadence。若无法清楚拆分公共与 per-stack 语义，则直接判“oracle 阻塞”，不实现。
- **风险/工作量/CMI**：风险 **中**、工作量中。风险来自复制 map 可有不同 `MAP_DECORATIONS`、同 tick inventory 变更、frame 与手持调用交错，以及 Bukkit custom renderer。CMI 直接影响预计低，但 map/image/packet 插件影响高；没有真实插件包时只标未验证。
- **真实 tick admission workload**：固定 map IDs，建立三层：单 map/单 viewer control；同一 map ID 的 36-slot duplicates × 20/50 players；100/500 frames × 多 viewer，并加入不同 static decorations 的 parity 组。保持玩家移动轨迹、render completion、packet count、map marker count一致。先证明 `tickCarriedBy`/inventory contains/`carriedBy` scan 是 real-tick inclusive CPU 或 allocation 热点。
- **作用计时边界**：`MapItemSavedData#tickCarriedBy` inclusive main-thread CPU 和 allocation bytes/call 为 admission；最终仍以相同 busy-tick MSPT + CPU 的 3 对交错 `>=5%` 判定。另记录每 tick 调用数、唯一 `(mapId,player)` 数、实际更新/packet 数作为完成量，禁止用“少发包/少刷新”伪造收益。
- **oracle/淘汰**：逐 tick 比较 `carriedByPlayers/carriedBy` membership、player/frame/static decorations（含顺序与 dirty 标记）、HoldingPlayer packet cadence、Bukkit MapRenderer 输出和离线/移除 cleanup。同 tick 换手、丢弃、拿起、进入/离开 frame tracking 和不同 decorations 必须一致。profile 冷、无法分割 per-stack 语义、<5%、packet/render/marker 差异即不实现/revert。

## 3. 为什么没有第三项

只读扫描还看到两类表面相关项，但均不满足本任务门槛：

- Paper #14144 SecondaryPoiSensor 已在当前 `SecondaryPoiSensor.java:28-45` 以 `secondaryPoi.isEmpty()` / loop guard 覆盖；Paper #11871 PoiCompetitorScan 也已在 `PoiCompetitorScan.java:25-50` unroll，不能再列。
- Lithium #712 POI memory 是 22-file/681-line、公开 PR 自称需 extensive testing 的生命周期改造；#682 portal search 是未合并的大型排序重写且承认 worst-case 变差。二者都不是低中风险局部替补。

因此如实只提交 S1/S2 两项。它们都必须先被 captain/reviewer 纳入新的预注册顺序并完成 admission profile，静态复核本身不算“实现 + A/B”。
