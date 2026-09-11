# Performance Round 2：独立短名单与实验预注册

> 冻结日期：2026-09-11。输入是 `candidates.md` 的 30 项公开候选，以及 Round 2 单一控制运行（实际执行身份为 commit `5355f563`、JDK 25 fresh jar SHA-256 `23D296C4…C5046`）。`leaf-api` / `leaf-server` 对 `upstream/ver/26.2` 的源码 diff 为空只支持“控制源码等价推断”，不代表已经运行第二套独立 upstream 测试或 benchmark。可提交的复现合同见 `protocol.md`。本文件只冻结准入实验与淘汰规则，不宣称任何候选已经带来收益。

## 1. 筛选结论

现有单控制 profiling 的最终窗口只有 15 个真实 tick runnable samples：15/15 穿过 villager AI、14/15 穿过 `Brain.tick`、12/15 穿过 `AcquirePoi -> PoiAccess`。这些重叠、非加和的稀疏 samples 只能说明 Brain/POI 值得继续调查，不能给出稳定 inclusive 排名、CPU 占比或收益归因。本次记录到 0 条 real-tick Server-thread park/monitor event 也只表示“本次未观测”，不证明没有等待。候选池没有外部补丁可由这 15 个 samples 直接准入。因此以下 **全部 5 项（包括 H1）都是待 benchmark 的准入研究**；每项先补足专项 profile、真实 tick 计时边界、工作量/完成量证明，准入后才允许实施。

有序顺序如下。工程阶段无论前项通过、失败或无法准入，都必须记录结论并继续后项；不得在第一个失败后结束。

| 顺序 | 实验 | 与证据的关系 | 准入结论 |
|---:|---|---|---|
| 1 | H1 `AcquirePoi` 冗余中间集合/分配 | 15 samples 给出 Brain/POI 方向线索，尚非稳定热点排名 | 先补专项 profile、真实 tick 边界、POI 耗尽与任务完成量；未准入不得改代码 |
| 2 | #6 explosion entity-raycast hit factory | 基线未覆盖爆炸 | 先以真实 TNT workload 证明该子树是 inclusive CPU/allocation 热点 |
| 3 | #7 explosion 16³ shell rays | 与 #6 技术上可独立归因 | 与 #6 分开实现、分开 A/B；通过后才做组合增量回归 |
| 4 | #1 无 listener 时跳过 event 构造/dispatch | 基线没有 event 热点且 Leaf 已有部分 guard | 先做逐源码差集、许可核验和事件压力 profile；冷则不实现 |
| 5 | #13 FerriteCore FastMap `StateHolder` | 基线未命中 property lookup | 仅在 redstone/random-tick 专项 workload 命中后实现 |

这 5 项是“准入研究输入”，不是 5 个已证明可实施或承诺保留的补丁。专项 admission profile 未命中、样本不足、计时边界不成立、许可证未清、已有等价实现或无法建立语义 oracle，均记录为未准入并继续下一项；静态排除或未准入不计作“已实现 + 已完成 A/B”的候选数量。

## 2. 所有实验共同冻结项

每项在写代码前把以下内容写入该项原始结果目录；任一项缺失则该实验无效：

1. **提交与工件**：固定 baseline commit、candidate commit（仅包含该候选）、fresh jar 哈希；candidate 必须能用单独 commit 完整 revert。A/B 不允许夹带 README 或其他候选。
2. **同龄输入**：相同只读世界母本的独立副本、seed、server 配置、插件集合、JDK/JVM flags、CPU affinity/电源策略、bot 脚本与完成量；每次新进程。预热与测量是两个边界：每次预热 `>=420 s`，随后才进入另行预注册的实际测量窗口。
3. **运行设计**：先用源码边界证明 busy tick 的真实 tick 口径，再预注册测量秒数和精确 tick 数；不得把 `>=420 s` 预热误写成测量时长。现有 runner 的 `420 s warmup + 60 s measurement` 只产生方向性 profile，不足以替代候选 A/B。正式候选至少 3 对交错 `A-B / B-A / A-B`（若继续加对，仍保持配对和顺序平衡），并在实施前用控制重复的噪声/功效决定且冻结测量时长。保存每次原始向量，不只保存汇总。
4. **CPU 与 wall 分离**：主线程 inclusive CPU、total CPU 只回答执行成本；wall/lock/park 单列回答等待。禁止把 wall 等待变化写成 CPU 收益，或把异步搬运写成 tick CPU 优化；若 total CPU 上升或完成量下降，撤销。
5. **共同观察项**：average、P50/P95/P99 MSPT，main-thread inclusive CPU，total CPU，allocation rate/bytes，GC pause/count，contention/lock，以及该 workload 的完成量。TPS 只作饱和/健康护栏，TPS-window 降级不重访。
6. **默认通过门槛**：配对中心改善 `>=5%`，且改善量大于控制重复/配对噪声带；3 对方向一致，不得由单个 outlier 驱动。P95/P99 不回退超过噪声，GC、allocation、contention、total CPU、完成量均无实质回退。
7. **例外必须预先匹配**：只有下文明确标为“低风险尾延迟例外”或“内存例外”的候选可低于 5%；本短名单 H1/#6/#7/#1/#13 均不是低风险例外，所以仍执行 `>=5%`。事件、生命周期、线程、缓存失效或可观察顺序风险绝不降门槛。
8. **测试归因**：现有实际执行只有 Fork fresh `leaf-server:test`：XML 为 8,967 tests / 10 failures / 0 errors / 23 skipped。与 upstream 源码 diff 为空只支持控制源码等价推断，未执行独立 frozen-upstream 测试；因此这 10 项不得称“已知 upstream 复现”。任何性能改动前，须在隔离、完整 frozen-upstream checkout 核对构建、依赖、配置并运行匹配测试，保存测试集合及失败签名。候选比较按测试集合和签名，而非只比总数；新增/变化失败或 skip 集变化均阻塞。没有 CMI jar 时只写“CMI 未验证”，不得写“应该兼容”。

## 3. 逐项预注册

### 3.1 H1 — `AcquirePoi` 冗余中间集合/分配（方向线索，待准入）

- **来源/许可/重复**：来自本 Fork 已应用的 `AcquirePoi.java` 与 Moonrise `PoiAccess` 调用链的本地差集分析，不复制未知许可外部实现。当前代码在一次 acquisition 中建立 `poiPositionsRaw`、`Set<Pair<...>>`，随后 `findPathToPois` 又建立 `Set<BlockPos>`；尚未把“看见多次集合”当收益证据，必须以 allocation profile 证实。
- **局部范围**：只允许修改 `leaf-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/AcquirePoi.java`，以及确有必要且仍为专用调用的相邻 `PoiAccess` 参数形态；不得改异步 path executor、Brain tick 频率、搜索半径、POI 加载策略或任务线程归属。
- **baseline/candidate**：A=实际执行身份 `5355f563` 的 frozen control；独立 upstream 测试与补充 profile 完成后才冻结 B，且 B 只消除经 profile 证实的冗余容器/转换，保持输入 POI 的过滤、目标集合与 valid range。
- **admission workload**：以现有 AI/POI 世界为起点，但 15 samples 不足以准入。先延长独立 profile，预注册真实 tick 计时边界，并固定高/低 POI 密度、“无可达 POI/POI 耗尽”分层、实体数、bot 行为和每窗 acquisition/path/成功占用/失败重试等业务完成量；只有 `AcquirePoi`/`PoiAccess` 获得可解释、可重复的 inclusive 排名后才实施。
- **主指标/门槛**：`AcquirePoi` + `PoiAccess` inclusive main-thread CPU；通过要求 >=5% 且超过噪声。allocation bytes/op 为支持证据，不可替代 CPU 门槛。
- **安全契约/oracle**：逐 tick 比较候选 POI 位置集合、路径 target、`maxRange`、成功占用的 `GlobalPos`、失败 retry-cache key/退避时刻；保持稳定遍历对路径选择的影响、`validPoi` 调用语义、pending `AsyncPath` 的单 owner 生命周期，以及实体移除/世界卸载后的状态释放。不得减少搜索、延长 tick 间隔或把工作转移线程。
- **淘汰**：allocation 未命中、CPU <5%、路径/占用/retry 序列变化、尾延迟或完成量回退，立即 revert 并继续 #6。

### 3.2 #6 — Explosion entity-raycast 专用 hit factory

- **来源/许可/版本**：Lithium PR #623，LGPL-3.0；公开稳定源为 26.1.x，26.2 必须逐算法/映射移植并保留所复制文件/片段的 attribution/header。先 diff 当前 `ServerExplosion`、Paper explosion 优化与 Moonrise；没有 Leaf 标记不等于没有实现。
- **局部范围**：只改 entity exposure/raycast 中 hit-result/上下文构造路径；不得改 ray 数、block traversal、damage/knockback 公式、Bukkit explosion events 或保护插件回调。
- **admission workload**：固定 seed 的 16/32/64 TNT 真实组为目标 workload，4096 TNT 仅作放大诊断；先证明 hit factory 所在子树贡献可测 inclusive CPU 或 allocation，冷则不实现。
- **baseline/candidate**：A=冻结基线；B=仅 #6，明确不含 #7。
- **主指标/门槛**：目标 workload explosion entity-raycast inclusive main-thread CPU，>=5% 且超噪声；allocation bytes/explosion 为辅证。4096 TNT 的收益不能替代真实组门槛。
- **安全契约/oracle**：固定 seed/实体布局逐次比较 affected entity IDs、伤害、knockback、exposure/raycast 命中、affected blocks、event 次数/取消结果；NaN/边界盒、零距离、多世界及实体移除不得改变。P95/P99、total CPU、GC、完成爆炸数不得回退。
- **淘汰**：未命中、<5%、任何实体/事件 parity 差异或许可证/header 无法满足即 revert/不实现，继续 #7。

### 3.3 #7 — Explosion 16³ shell rays（与 #6 独立）

- **来源/许可/版本**：Lithium PR #686，LGPL-3.0，源为 1.21.8；26.2 必须核对 vanilla/Paper 当前 ray 初始点、循环边界与随机数消费。保留 attribution/header。
- **局部范围**：只用等价的 16³ 外壳枚举替换当前边界 ray 起点生成；不得包含 #6 hit factory，不得减少外壳点、改变 ray 步长/衰减/随机调用顺序或 event 流程。
- **admission workload**：与 #6 相同的固定 seed 16/32/64 TNT 真实组；4096 TNT 仍仅诊断。先单独 profile ray generation/traversal inclusive CPU。
- **baseline/candidate**：A=冻结基线；B=仅 #7。即使 #6 已通过，本项主判定仍在不含 #6 的基线上完成，防止相互掩盖。
- **主指标/门槛**：explosion ray generation+traversal inclusive main-thread CPU，>=5% 且超噪声。
- **安全契约/oracle**：逐爆炸比较 ray 起点/顺序、PRNG 消费序列、affected block set 及稳定顺序、entity damage/knockback、fire、水/流体、event cancellation。固定 seed 下必须完全一致。
- **组合规则**：只有 #6 与 #7 各自独立通过后，才测 `base / #6 / #7 / #6+#7` 的同龄组合；组合必须保留各自方向，且 `#6+#7` 相对最佳单项仍有超过组合噪声的增量或至少不回退。组合不取代两项独立证据。
- **淘汰**：单项 <5% 或 parity 失败则单独 revert；不得以“同属爆炸”强制捆绑，继续 #1。

### 3.4 #1 — 无 listener 时跳过 Bukkit event 构造/dispatch

- **来源/许可/重复**：Paper PR #14173（open，作者 flennium）；逐 hunk/文件 header 未核前禁止复制。Leaf 已有若干单点 guard，必须对当前 applied source 做逐事件差集，剔除已有等价 guard。
- **局部范围**：只允许对差集中、profile 命中的具体 event call site 加无 listener fast path；一个 event family 一个可 revert commit。不得缓存跨 reload 的 listener 状态，不得改变动态注册、优先级、取消、MONITOR 或插件 manager 生命周期。
- **admission workload**：在不伪造 CMI 的前提下建立同龄 event-heavy workload，分别运行 0 listener 与至少一个动态注册/注销 listener；记录每 tick event 构造/dispatch 数及 JFR allocation。若实际服务器可提供 CMI jar/config，再加 CMI 回归；缺 jar 标“未验证”，不声称兼容。
- **baseline/candidate**：A=冻结基线；B=只含一个已核许可且未重复的 event-family guard。
- **主指标/门槛**：0-listener 目标 workload 中对应 event call-site inclusive main-thread CPU，>=5% 且超噪声；allocation/event 为辅证。有 listener control 必须性能不实质回退。
- **安全契约/oracle**：0/1/N listener 下 event 构造数（仅 0 可省）、dispatch 次数、顺序、payload、取消和副作用一致；运行中注册/注销下一次合法观察点立即生效；plugin enable/disable/reload 不得留下陈旧 fast-path 状态。
- **淘汰**：许可未清、差集为空、profile 冷、<5%、动态 listener 或事件 parity 失败即不实现/revert，继续 #13。

### 3.5 #13 — FerriteCore FastMap `StateHolder`（后备专项）

- **来源/许可/版本**：FerriteCore `FastMapStateHolderMixin` @ `0cef1f2a`，MIT；只有 26.1 分支。26.2 必须逐源码差集 Paper/Moonrise/Leaf 的 `StateHolder`，确认没有等价表或缓存后才能移植，并保留许可说明。
- **局部范围**：只优化 immutable block/fluid state 的 property-to-value/index lookup 表示；不得改 registry identity、state 枚举顺序、codec/serialization、datapack reload publication 或可变生命周期。
- **admission workload**：固定 redstone/random-tick farm 与大 state-space/datapack 两层；先证明 `StateHolder` property lookup 是 main-thread inclusive CPU 或 allocation 热点。TPS-window、减少 random tick 数或改变 farm 完成量均禁止。
- **baseline/candidate**：A=冻结基线；B=仅 FastMap lookup/representation，不夹带 cache compression (#12)。
- **主指标/门槛**：目标 workload 的 `StateHolder` lookup inclusive main-thread CPU，>=5% 且超噪声；heap/RSS、allocation 与 GC 为护栏而非本项替代主指标。
- **安全契约/oracle**：穷举 vanilla + 测试 datapack 所有合法 state，比较 property get/set、缺失/非法值异常、state identity、枚举/serialization roundtrip、reload 前后对象不可混用；缓存必须 immutable、安全发布且不跨 registry 生命周期保留。P95/P99、startup、heap/RSS、GC、完成 tick 数不得回退。
- **淘汰**：cold profile、<5%、heap 增长、cache 生命周期/identity/parity 不明即 revert；记录结果后结束本短名单，不转而强上 #2/#8/#12。

## 4. 明确不进入本轮实现的候选

- **#2 timing wheel**：本轮真实 profile 未证明 `CraftScheduler.mainThreadHeartbeat` 热；风险/工作量/CMI 均高。只有未来真实 plugin workload 命中，并能证明稳定顺序、同 tick 重入、取消、超长 delay、async producer publication parity，才另轮预注册；高风险门槛仍 >=5%。
- **#3 `hasItemMeta`**：许可待核且 Paper PR 自认 compatibility 风险；没有真实 ItemMeta/CMI workload 热点。没有 CMI jar 只能标未验证，不据此假定兼容。
- **#4 Copper Golem**：改变 path validity 检查时序，且不是 t2 热点实体；不以行为降级换 MSPT。
- **#8 serialization**：没有 serialization/I/O profile 命中，且 NBT/DFU/anti-xray 风险高。
- **#9 equipment clear**：逐源码差集确认当前 `EntityEquipment.clear()` 在填充 `ItemStack.EMPTY` 前调用 `invalidateData()`，其 `invalidateData()` 已逐 stack `unsubscribeWithData`；即 Lithium #770 的泄漏修复语义已存在，属于重复，不再实现。若未来发现不同泄漏，可按 retained refs/heap/allocation/GC 的预注册内存例外另案评估，而非机械要求 MSPT。
- **#12 BlockState cache compression**：MIT 且内存收益原则上可用 heap/RSS/GC 例外评估，但当前为高风险/高工作量架构改造，t2 又未给出 heap/GC 压力证据；不为凑数优先。若未来 heap profile 命中，应预先冻结“稳态 retained heap/RSS 至少 10% 或 full-GC/pause 明显超噪声改善，startup 与 MSPT 不回退”的独立内存门槛。
- **#23 pathfinder sync-load**：许可映射未解决，禁止复制；与 Paper/Moonrise/Leaf async pathfinding 的源码差集也未完成。可做 sync-load 计数研究，但不能进入实现短名单。
- **其余项**：沿用 `candidates.md` 的重复、非 steady 等价 workload、版本成熟度、Moonrise 重叠或许可证阻塞结论。无 Leaf 标记从不作为“未实现”的证据，必须看 applied source 差集。

## 5. 停止、保留与组合门禁

1. 每项只有三种终态：`保留（独立通过）`、`撤销（证伪/回退）`、`未准入（cold/重复/许可或 oracle 阻塞）`；禁止“看起来更快”或零收益保留。
2. 工程阶段按 H1 → #6 → #7 → #1 → #13 顺序完成 admission；只有准入项才实施并进入 A/B。每个实施项拥有独立 commit 和原始 A/B 目录；未准入或失败不阻断后项。
3. 只组合所有独立通过项，再用同一真实 AI/POI workload 加每个专项 workload 做至少 3 对交错组合回归；组合中任一专项收益消失、P99/GC/total CPU/完成量回退或新增测试失败，则二分定位并撤销责任项。
4. 最终 README 只能陈述原始证据实际支持的范围，不外推 CMI、TPS 或百分比；没有通过项就明确 Round 2 零保留性能补丁。
