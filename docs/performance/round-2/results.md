# Performance Round 2：工程实验结果

> 日期：2026-09-11。控制源 `5355f563`（可执行源码等同 upstream `7a5d08d`），JDK 25。这里记录工程阶段的候选裁决；“未准入”与“撤销”都不是性能收益声明。原始世界、JFR、日志、jar 与逐 run JSON 留在 ignored `run/perf/round2/`，不提交二进制或世界。

## 结论

本轮 **零保留性能补丁**。H1是在t9正式准入合同冻结前发生的探索性实现/对照，不能计作合规候选或正式A/B；数据只支持不保留并保守撤销。#6/#7/#13由源码重复排除；#1后来完成来源审计和初步流体profile但仍未正式准入。S1/S2补充候选也未获实现许可。

## 有序逐项裁决

| 顺序 | 候选 | 终态 | 工程证据 |
|---:|---|---|---|
| 1 | H1 `AcquirePoi` 冗余结果集合 | 准入前探索；已保守撤销 | 历史实现 commit `46189bf2`、revert `202fe387`。它发生在独立 upstream test、足量 admission profile、exact busy-tick raw、完成量与低开销 benefit runner 之前，不能追认为预注册 A/B；现有方向性数据只支持不保留。 |
| 2 | #6 explosion entity-raycast hit factory | 未准入（重复） | 当前 `ServerExplosion#getSeenFraction` 已使用 `clipsAnything`、`LazyEntityCollisionContext` 与直接 boolean 命中，且复用 block/collision-shape cache；已消除候选针对的逐 ray `ClipContext`/`HitResult` 路径。 |
| 3 | #7 explosion 16³ shell rays | 未准入（重复） | 当前 `ServerExplosion.CACHED_RAYS` 静态初始化只枚举 16³ 外壳，`calculateExplodedPositions` 直接迭代 1352 条缓存 ray；与 Lithium PR #686 的目标等价。 |
| 4 | #1 `BlockFromToEvent` guard | 待流体专项 admission | t16 将来源固定到 flennium / Paper PR #14173 / commit `947bb910...`，按 Paper root presumption 为 GPL-3.0；真实最小差集仅 `FlowingFluid` downward/horizontal 两个构造前 guard。manager 已有构造后0-listener早退，潜在收益很小；L0/L1/LN/LD、动态listener与流体完成量未测，未批准实现。 |
| 5 | #13 FerriteCore FastMap `StateHolder` | 未准入（重复） | 当前 `StateHolder` 已实现 Moonrise `PropertyAccessStateHolder`；`StateDefinition` 初始化 `ZeroCollidingReferenceStateTable`，get/set 经 table index / `optimisedTable.trySet`，不是待替换的 map lookup。 |

## H1 准入前探索性实验（非正式 A/B）

### Patch 与语义边界

历史 H1 尝试让 `PoiAccess` 在完成“最近 5 个”选择之后应用现有 `validPoi`，并直接写入原来的 `HashSet<Pair<...>>`。该想法先在 applied source 检查后转换为 canonical patch；用户对必要 patch 操作的明确授权使“存在 patch commit”本身不是违规。真正的缺口是它在 t9/t11 的 admission、测试身份与测量门禁完成前实施，且未完成逐路径语义 oracle，因此不获等价性或性能批准。实现 `46189bf2` 已由 `202fe387` 正常 revert，没有 reset/amend。

### 冻结工件与运行设计

- A：`23D296C4C2D37F42909EB3F309E7CF3AB4F8DF768AB7611D8B0A000C236C5046`
- B：`68C1ED76A48C0FFA011B53BD25ADA666B7DE1858AF2FA6AD209228BB0C812EA0`
- 每次 fresh process、同一只读 template 的独立副本、`-Xms2G -Xmx2G -XX:+AlwaysPreTouch`、420 s warmup、420 s measurement、20 TPS；所有收益运行同时开启了 JFR，因此没有独立的低开销/无 JFR 收益重复。
- 顺序：`A1-B1 / B2-A2 / A3-B3`。每 run 有 84 个约 5 s 间隔的 rolling-100 `tick query` 摘要；没有逐 tick raw vector，所以表中的 average 是 84 个窗口均值的均值，P50/P95/P99 是窗口分位数的中位数，均不得称为整段逐 tick 分布。JFR、process CPU、working set、sprint 与日志全部保存。
- 每次窗口开始只检查了一次实体状态：villager 均为 256；A1 farmer 为 **71**，其余五次为 72，并各有一个 farmer `job_site`。窗口结束未复查，也未记录 acquisition/path/成功占用/失败重试完成量；退出码均为 0。CMI 未验证。

### 结果

| Run | avg MSPT | 窗口 P50/P95/P99 中位数 (ms) | process CPU 单核% | sprint MSPT |
|---|---:|---:|---:|---:|
| A1 | 0.3036 | 0.1 / 2.4 / 3.6 | 6.96 | 0.25 |
| B1 | 0.3060 | 0.2 / 2.3 / 2.9 | 7.56 | 0.22 |
| B2 | 0.3024 | 0.1 / 2.5 / 3.2 | 7.12 | 0.23 |
| A2 | 0.3131 | 0.2 / 2.6 / 3.4 | 8.18 | 0.32 |
| A3 | 0.3024 | 0.1 / 2.6 / 3.5 | 6.20 | 0.25 |
| B3 | 0.2976 | 0.1 / 2.5 / 3.2 | 9.14 | 0.24 |

配对 average MSPT 改善依次为 **-0.78%、3.42%、1.57%**；三对没有达到 5%。process CPU 改善依次为 **-8.60%、12.91%、-47.37%**，方向不一致且两对回退。虽然 sprint 与部分窗口尾部数字偏向 B，但它们不能覆盖主 CPU/MSPT 门槛失败，故不加样硬凑结论，直接撤销。

独立复算与原始 `result.json` 一致，但上述 rolling 摘要、JFR 同开和完成量缺口使这组数据不足以支持任何正收益或精确尾延迟声明。它仍足以作保守淘汰：三对探索指标没有达到5%筛选线，total process CPU 两对回退，且候选窗口各发生 1 次 GC（14.50–16.07 ms pause），控制窗口为 0 次。allocation sampling 对 H1 目标栈显示方向性下降但每窗仅 20–126 个目标样本，不能覆盖 CPU/完成量门禁。


## Repair运行补证与分类

| 类别 | 数量 | 项目 | 可支持结论 |
|---|---:|---|---|
| 静态重复排除 | 3 | #6、#7、#13 | 当前已有等价/更强实现；不计实现或A/B。 |
| 初步专项profile | S1两次、流体L0一次 | S1与`BlockFromToEvent` | 均未满足完整正式admission；只支持不实施。 |
| 有效S2 workload | 0 | S2 | workload+immutable-token oracle阻塞。 |
| 合规实现+A/B | 0 | 无 | H1是准入前探索，不计入。 |
| 保留runtime patch | 0 | 无 | 不宣称收益。 |

- **S1**：500-nitwit/422.4s warmup profile仅2/1,079全Server-thread runnable samples命中目标；t18的256 stored-nitwit profile仅60s warmup、2个real-tick samples、0个AcquirePoi/PoiAccess命中。两者都缺完整矩阵、Brain refresh/RNG/section-load与业务计数，未准入、未实现。
- **S2**：没有connected player/bot、maps/frames、renderer/packet与同tick mutation harness，且缺完整局部token；不伪造无玩家run，不实现stamp/cache。
- **流体guard**：t16已解决GPL-3.0来源与两个`FlowingFluid` callsite差集。t18 L0网格的flow counters持续推进，但180s JFR仅5个real-tick samples，目标CPU命中0、event allocation仅1 sample，且只warmup60s；限定为cold/insufficient，未做L0/L1/LN/LD candidate A/B。

## Collector实机边界

最终collector `C05D0DCA...DE35`通过5-tick连续索引与正常timeout ABORT；外部watchdog no-capture模拟只清理自有PID。长无JFR capture为2,400连续ticks、诊断全0，absolute busy 922,085,800ns，mean/P50/P95/P99=0.384202/0.2155/2.4215/3.9956ms。但on/off短对CPU为+3.87%/+50.40%/+6.54%，长单对为-22.54%，方向矛盾，所以collector overhead仍未量定，不能称零开销；未来A/B必须同jar同capture状态。业务边界只有256 villagers/72 farmers stock，不是POI throughput。

## 构建、测试与已知失败归因

候选和最终撤销态jar均使用JDK25构建，最终runtime source相对控制为零diff。t18在detached `run/perf/round2/upstream-test`固定Winds-Studio/Leaf `7a5d08d`及其Paper `50018799f`，以process-local proxy/`core.longpaths=true`和`run/perf-tools/git.exe` wrapper运行Leaf `applyAllPatches`成功，再实际运行`:leaf-server:test --rerun-tasks`。独立Leaf和Fork各114 XML，均为8,967/10fail/0error/22skip，skip identity与归一化failure identity相同。Gradle console8,951/10/7是不同计数层，不能混用；旧23无raw XML，保留为不可追历史缺口。此前Paper-only9,245/0/22只作依赖层诊断。精确命令、wrapper hash、XML比较路径与证据manifest `BB6545A8...6F4BB`见`runtime-followup.md`。


## 外部来源

- #6：Lithium PR #623，LGPL-3.0；只用于差集核验，未复制。
- #7：Lithium PR #686，LGPL-3.0；只用于差集核验，未复制。
- #1：Paper PR #14173 / commit `947bb910...`，flennium；按Paper root presumption为GPL-3.0。仅审计两个`FlowingFluid` callsite，尚未复制或实施。
- #13：FerriteCore `FastMapStateHolderMixin` @ `0cef1f2a`，MIT；只用于差集核验，未复制。
- H1：本地 profile 驱动实现，无外部代码复制；已撤销。
