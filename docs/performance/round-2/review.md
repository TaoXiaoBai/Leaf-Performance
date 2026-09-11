# Performance Round 2：独立 Review（Round 1）

> 审查日期：2026-09-11。审查对象为 t4 候选历史、t5 原始数据复算、最终 runtime source 状态，以及 t8 中英 README 工作树。结论：**NEEDS REVISION / 不准进入 t7 集成与推送**。

## 1. 总结判定

- **最终 runtime 状态安全但不足以洗白实验过程**：`46189bf2` 已被独立 revert `202fe387` 删除；`git diff --exit-code 5355f563..202fe387 -- leaf-api leaf-server` 为 0，当前相对 upstream 的 applied `leaf-api/leaf-server` 也无 runtime diff。因此最终确实是零保留性能补丁，不宣称收益是正确方向。
- **H1 只足以保守淘汰**：六个 raw summary 证实 420 s warmup 后约 420.29–420.31 s measurement，三对 rolling-window mean 改善为 -0.784% / 3.422% / 1.575%，process CPU 为 -8.597% / 12.907% / -47.366%。未过 >=5% 且方向不一致，revert 正确。但它不满足后来冻结的 admission/真实 tick/完成量/低开销 benefit 协议，不能称为合规完成的候选 A/B。
- **静态重复结论可信**：独立源码核验确认 #6 已有 boolean `clipsAnything`/缓存 exposure 路径，#7 已有 16³ perimeter `CACHED_RAYS`，#13 已有 Moonrise `ZeroCollidingReferenceStateTable`；这些不应强行重做。#1 未清许可且无专项热点，不复制是正确的。
- **README 门禁通过**：两版均明确 Leaf-Performance 是基于 Leaf 的非官方私人实验 Fork、不代表上游认可、保持 Paper 而非 Folia、兼容非第一目标、优化需 benchmark 且无收益删除；语言链接一致。Special Thanks/特别感谢及云商/YourKit展示已删除，同时保留中性 upstream provenance、`LICENSE.md` 链接和 source/header attribution 义务。没有虚构 TPS、百分比、世界最快或官方背书。
- **整体仍不通过**：下面四项 finding 未解决。零 runtime diff 只证明当前没有残留性能代码，不能代替候选准入、测量合同、多个候选继续规则或独立测试证据。

## 2. 正式 Findings

### R6-001 — blocker — H1 在正式准入之前实施，不能称“完整/预注册 A/B”

**证据**：`results.md:7,13` 把 H1 写成“唯一进入完整 A/B”并称满足预注册框架；但 t9/t11 已确认实现发生在独立 frozen-upstream 测试、补足 hotspot profile、真实 busy-tick runner与完成量准入之前。`shortlist.md:7,19,27,32,41` 和 `protocol.md:50-62` 明确所有五项含 H1 都须先 admission。历史实现 commit `46189bf2` 还只创建 canonical patch 文件而非按仓库 `AGENTS.md` 工作在 applied source，随后虽已 revert，也不能计作合规实现。

**影响**：时序门禁不可事后补写；当前报告会让读者误以为 H1 是按照已冻结合同完成的正式候选验证。

**Required fix**：更正 `results.md`/最终报告，将 H1 明确标为“准入前探索性实验，数据仅支持保守撤销”，不得称“完整/预注册 A/B”或计入合规候选数。若 repair 重新实施任何候选，必须先完成 t9/t11 前置并只改 applied source；patch 转换留给仓库 owner。

### R6-002 — high — H1 数据缺少正式主指标、低开销重复和完成量等价

**证据**：`results.md:29-31,44-46` 与 `verification.md:27-34` 共同确认：所有收益 run 挂 JFR；没有独立低开销/无 JFR repeat；没有逐 tick raw busy vector，只有 84 个重叠 rolling-100 摘要；P50/P95/P99 不是整窗分布；A1 只有 71 farmer、其余为72；只在窗口开始检查一次且没有 acquisition/path/成功占用/失败重试完成量；allocation/GC/contention样本不足。六个 summary 的 `commit` 字段还全部是 `46189bf2`，A/B 身份实际依赖 artifact SHA，而非 runner记录的 source commit。

**影响**：不能证明同工作量，也不能按冻结主指标和噪声合同评价正收益或精确尾延迟；JFR/编译成本可能污染低负载CPU。当前三对仍足以拒绝 B，但不构成一个成功完成的正式 A/B 候选。

**Required fix**：报告必须把这些字段列为未测，并只保留“未达门槛、撤销”的负面裁决。若 H1 或其他 candidate 在 repair 中重跑，runner须记录真实 artifact-source commit、精确 busy-tick raw vector、事前冻结测量长度、窗口前后 workload/业务完成量，并将 hotspot JFR 与低开销 benefit A/B 分离。

### R6-003 — high — 仅一项实现，后续替补没有完成实际 admission，继续规则证据不足

**证据**：`results.md:7-17` 只有 H1 有代码和 A/B；#6/#7/#13 是正确的源码重复排除，#1 是许可/profile 阻塞。t10/t11 的 S1/S2 又只完成静态审查：S1 literal removal 会改变 RNG/调度/POI `getOrLoad` 副作用，S2 tick stamp 缺完整 mutation token；两项均只获 instrumentation/admission 许可，尚无真实专项数据。

**影响**：团队不能把五个静态表格行等同于多个实际候选验证。当前证据未展示在 H1 失败后完成了下一项真实 workload admission，也未满足“无收益允许零 patch，但不提前终止”的多候选门禁。

**Required fix**：完成前置基线后，至少执行 S1/S2 的 no-code admission instrumentation，记录真实 tick inclusive 排名、计时边界与完成量。若 S1/S2 因 cold profile 或 oracle 阻塞而不实施，应以实际 admission 证据记录并停止，禁止为凑数扩大成 Brain rewrite或跨 Inventory/ItemStack/player/frame 缓存；若出现局部安全热点，再单独实现/A-B/revert并继续。最终报告分别统计“静态排除”“实际 admission”“实际实现+A/B”。

### R6-004 — high — 独立 upstream 测试未完成，Fork skip 集也尚未对齐

**证据**：`verification.md:45-49` 记录 frozen-upstream worktree 的 `applyAllPatches` 因 Git-for-Windows submodule helper exit 254/挂起而失败，未生成 upstream applied tree或测试结果；只有 control source-zero-diff 推断。当前 XML 现场按 114 个 `TEST-*.xml` suite attributes 复算为 **8,967 tests / 10 failures / 0 errors / 22 skipped**；t2 可提交协议记 23 skipped，但没有保留可逐 testcase 对比的 baseline XML，故不能判断差1来自参数化/disabled计数范围、环境还是集合变化。

**影响**：10 个 Fork failure签名虽相同且最终 runtime源为零，但不能称独立 upstream复现，也不能声称 test/skip集合不变。构建/依赖/配置问题仍可能掩盖归因。

**Required fix**：修复或替换隔离 upstream checkout 的 submodule环境，记录完整 commit/ref、JDK/Gradle/依赖/配置并运行匹配测试；同时保存 Fork baseline/current 的 testcase与skip identity集合，统一参数化/disabled计数口径并解释22/23差异。若环境仍阻塞，最终交付必须明确标为未完成/阻塞，不能以源码等价推断替代独立复现。

## 3. 非阻塞核验记录

### 来源与许可

- H1 是本地思路，历史 patch header 标 GPL-3.0-only且已删除；最终无其代码可分发。
- Lithium #623/#686 仅作 LGPL-3.0 差集参考，未复制。
- FerriteCore FastMap 仅作 MIT 差集参考，未复制。
- Paper #14173 未建立逐文件许可/header映射，因此不复制，处理正确。
- README 删除商业/感谢展示没有删除 repository license summary，仍要求保留逐文件/patch header与原始 attribution。

### 生命周期、线程与行为

最终 runtime diff为零，所以没有新增线程、缓存、红石、事件、序列化或生命周期风险进入最终组合。H1 历史 diff保持同步路径且随后精确revert；由于未完成逐路径行为oracle，它只能作为失败历史，不获语义批准。S1/S2 的 RNG/Brain/POI加载和 map/inventory/frame/renderer 风险已在 `supplemental-admission.md` 正确阻塞。

### 组合

没有任何独立通过并保留的 runtime patch，故不存在可相加的组合收益；源差分可以证明最终组合为空，但不能代替本轮实验质量门禁。

## 4. Review Verdict

**NEEDS REVISION**。README 子门禁通过，静态重复排除与 H1 revert 正确；但 R6-001 至 R6-004 均为阻塞项。t7 必须等待自动 repair 和下一轮独立 review，不得基于本轮结果进行推送或性能宣传。
