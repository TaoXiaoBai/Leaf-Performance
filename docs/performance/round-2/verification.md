# Performance Round 2：独立复测与组合回归

> 日期：2026-09-11。此文件是 verifier 对工程原始数据的独立复算，不批准工程师自报，也不构成性能收益声明。

## 最终组合身份

- 最终 HEAD：`e2be6e5f`；H1 实现 `46189bf2` 已由独立 revert `202fe387` 精确删除。
- `git diff --exit-code 5355f563..202fe387 -- leaf-api leaf-server` 返回 0；当前 HEAD 相对控制的唯一已提交差异是结果文档。最终因此是 **零保留 runtime 性能补丁**，不存在可重复的暂留 patch 或可相加的组合收益。
- 最终 fresh paperclip SHA-256：`AF2FCEA54A1777BD4FF5B3F58703F4E2D9D2A348A73E1DA9E3F3D19FC3FF60FA`。它与旧控制 jar 的字节 hash 不同（构建/版本元数据会进入产物）；零 runtime source diff 才是组合等价依据，不能把 jar hash 不同说成性能差异。

## H1 原始数据复算

实际 A/B 顺序为 `A1-B1 / B2-A2 / A3-B3`，baseline/candidate jar SHA-256 分别为 `23D296...C5046` / `68C1ED...12EA0`，六个 run 的 `server.jar` 均与 summary 的 variant hash 匹配。所有 run 为 420 s warmup + 约 420.29–420.31 s measurement、84 个 rolling-100 `tick query` 摘要、exit 0、无 overload。

独立从六份 `latest.log` 与 `summary.json` 复算：

| pair | rolling-window mean 改善 | process CPU 改善 | sprint 改善 |
|---:|---:|---:|---:|
| 1 | -0.784% | -8.597% | 12.000% |
| 2 | 3.422% | 12.907% | 28.125% |
| 3 | 1.575% | -47.366% | 4.000% |

配对中心与 `result.json` 一致：rolling-window mean 仅 1.575%，CPU 为 -8.597%，且三对方向不一致；远未满足预注册的 >=5% 同向门槛。撤销结论可靠且无需为已失败候选追加 A/B。

### 证据边界与纠错

1. runner 没有写逐 tick raw vector。84 个值是约 5 s 间隔的 rolling-100 摘要；窗口均值的均值只能作近似 average，窗口 P50/P95/P99 的中位数不是整段 P50/P95/P99。
2. 所有收益 run 同时开启 JFR（ExecutionSample period 10 ms；ObjectAllocationSample throttle 300/s），没有独立低开销/无 JFR benefit repeat。
3. 完成量只在窗口开始检查一次：六次 villager 均为 256；**A1 farmer=71**，其余五次=72；各有一个 farmer job_site。没有窗口结束复查，也没有 acquisition/path/成功占用/失败重试计数。原结果“每次 farmer 72”不实，已修正。
4. measurement-window real-tick AcquirePoi/PoiAccess execution samples分别为 A1/B1/B2/A2/A3/B3=`82/73/68/85/71/61`，只证明目标仍活跃，不能把样本数直接作 CPU 比例。
5. measurement-window 目标栈 allocation weighted MiB 为 `108.00/96.58/76.72/123.43/134.37/83.50`，但每窗目标 allocation samples 仅 `89/26/30/114/126/20`；total weighted allocation 为 491.97–536.81 MiB。方向性 allocation 信号不能覆盖 CPU/完成量门槛，也不足以作精确 bytes/op 声明。
6. 三个 B 窗口各发生一次 GC pause（16.074/14.495/15.406 ms），三个 A 窗口为 0；Server-thread JavaMonitorEnter 均为 0。ThreadPark 没有完成逐事件 real-tick 深栈复算。GC/竞争样本太少，不声称统计差异。

因此这组 A/B 只支持“未达到保留门槛并撤销”，不支持候选正收益、精确尾延迟改善或 allocation 收益。

## 其余候选与许可/CMI

- #6：当前 `ServerExplosion#getSeenFraction` 已使用 `LazyEntityCollisionContext` 与 boolean `clipsAnything`，并缓存 block/collision shape；目标 HitResult 路径重复。
- #7：当前 `CACHED_RAYS` 仅枚举 16^3 外壳，重复候选；未与 #6 做伪组合。
- #13：`StateHolder` 已使用 Moonrise `ZeroCollidingReferenceStateTable` 的 table index/get/set/trySet，重复候选。
- #1：t16已将`BlockFromToEvent` family固定为flennium / PR #14173 / `947bb910...`，按Paper root presumption为GPL-3.0；最小差集是两个`FlowingFluid`构造前guard。仍缺L0/L1/LN/LD流体专项admission与动态listener oracle，未复制/实施。CMI明确为 **未验证**。

#6/#7/#13的静态差集支持重复排除；#1只确定许可与最小差集，仍须以流体profile裁决。以上均无runtime patch或单patch A/B，不能计为性能成功。

## 测试与 frozen upstream 边界

- t18 在 detached worktree `run/perf/round2/upstream-test` 实际固定 Winds-Studio/Leaf `7a5d08d50189770d2295c56eccf09873c49b8267`；该提交的 `gradle.properties` 固定 Paper `50018799f282010f7eb15d32c29cadbbe7c9d740`。JDK为Zulu 25.0.3+9 LTS。
- 只对该进程设置proxy与`core.longpaths=true`，并把现有 `run/perf-tools/git.exe`（SHA-256 `F47E39E3...EEC62`）放在该进程PATH前部；没有改全局Git/Harness/用户配置。实际命令见 `runtime-followup.md:17-27`：Leaf `applyAllPatches`成功（16m46s），随后 `:leaf-server:test --rerun-tasks --stacktrace --no-daemon`实际执行并因10项失败返回1。
- 独立Leaf与当前Fork各有114份XML，统一XML testcase-node口径均为 **8,967 tests / 10 failures / 0 errors / 22 skipped**。显式skip identity集合相同；failure identity/根因归一化后相同，唯一需归一化的是`MaterialReroutingTest`显示名中的volatile `ZipFileInflaterInputStream@<hex>`。
- Gradle console的8,951/10/7与XML 8,967/10/22是不同JUnit/Gradle层级，不能混称。旧t2“23 skipped”没有保存raw XML/identity，无法追溯或静默改写；它保留为历史不可复核缺口，而当前可审计比较使用双方实际22-node集合。
- 因此R6-004的“独立完整Leaf测试未执行”已由t18实证修复；Fork的10项失败可准确写为“在独立Leaf `7a5d08d`匹配签名”，但不能泛化到其他Leaf/Paper版本。此前pinned Paper `50018799f`的Paper-only 9,245/0/22只作依赖层诊断，不替代Leaf结论。
- 原始比较、normalized identity、JDK/commit/wrapper hash在ignored `run/perf/round2/followup/leaf-test-comparison.json`；整体证据manifest为`BB6545A85D970A11AE63A1A0CFB6D34041D1852A6826B64B5EEE8D9F7EB6F4BB`。

## Collector 实机边界与测量系统护栏

- t17最终collector jar为`C05D0DCAF4FF2B86B7FD40F4E662BDCE5E5870920FE3EEFB0D2EADEAF781DE35`。5-tick smoke得到连续133..137、零缺失/重复/乱序/invalid，End后发布、下一Start完成边界实机成立。
- 2s正常回调timeout在39个完整tick后ABORT；外部no-capture watchdog模拟按2s deadline+2s grace将run标invalid，仅停止自有PID并观察exit0。它验证所有权/失效流程，不冒充真实JVM deadlock。
- collector on/off三组短对process CPU变化为+3.87%/+50.40%/+6.54%，420s warmup单长对为-22.54%，方向矛盾，开销**未量定**；不得称零或可忽略。未来candidate A/B必须同collector jar、同capture状态。
- 长无JFR capture得到2,400个连续ticks 8424..10823，诊断全0；absolute busy=922,085,800ns，mean0.384202ms，nearest-rank P50/P95/P99=0.2155/2.4215/3.9956ms，full-loop total=1,305,054,000ns。边界前后256 villagers/72 farmers，但这只是stock guard，不是POI attempt/path/occupation/retry完成量。
- 以上证明raw vector/index可用，不关闭measurement-system overhead与业务完成量finding。

## Repair admission 实测与计数

### S1：两次探索profile均不准入

- 工程repair的500-nitwit单层run使用控制artifact `23D296C4...C5046`、422.40s warmup和独立120.23s JFR，前后count均500；1,079个全Server-thread runnable samples仅2个命中`AcquirePoi/PoiAccess`。该0.185%不是real-tick子树CPU比例，且无Brain lifetime/RNG/section-load完成量。
- t18另做256 stored-nitwit、180s JFR，但仅60s warmup；1,575个Server-thread samples中仅2个进入real `tickServer`子树，0个命中`AcquirePoi/PoiAccess`。`/data merge`只证明stored profession，不证明captured profession或Brain refresh生命周期。
- 两次都不是完整0/100/500/1000+POI层正式矩阵，也缺attempt/path/occupation/retry计数；结合t11副作用oracle，S1仍为**不准入**，未实现literal removal/fast path或A/B。

### S2：workload + oracle阻塞

没有connected bot/player、duplicated maps、frames、renderer/packet观察和同tick inventory/component/frame mutation workload；源码也没有局部完整mutation token。无玩家数据不能冒充admission，简单tick stamp已拒绝，未实现或A/B，也未扩跨生命周期缓存。

### `BlockFromToEvent`流体guard：初步cold/不足

- t18的L0 workload每20tick维护8x8水源网格，cycles/source writes/observed flowed cells由60/3,840/18,880推进到244/15,616/77,760，说明流体工作持续。
- 180s JFR含1,531个Server-thread samples，但real-tick子树仅5个，`FlowingFluid`/`BlockFromToEvent` CPU命中为0；allocation仅1个event sample和2个`CraftBlock` sample，过于稀疏。且warmup只有60s，低于正式>=420s。
- 结论限定为**本次初步profile cold/insufficient，未准入**；不证明guard零成本。没有实现两个callsite，也没有L0/L1/LN/LD A/B。

### 分类计数

- 静态重复排除：3（#6、#7、#13）。
- 实际探索profile：S1两次但均不满足完整正式矩阵；流体L0一次但warmup/profile样本不足；S2有效run为0。
- 合规candidate实现+A/B：0；H1仅列准入前探索历史。
- 保留runtime patch：0。

### 仍开放的运行findings

1. collector overhead方向矛盾，未量定；raw vector可用不等于无扰动。
2. POI完成量仍只有stock boundary，没有attempt/path/occupation/retry throughput。
3. S1缺Brain-lifetime正确矩阵；S2缺player/renderer/mutation-token基础设施。
4. S1/流体follow-up仍不是满足>=420s warmup的正式admission，不能据此实施。

## 独立结论

最终零runtime patch，不宣称任何性能提升。H1仍是准入前探索，只支持保守撤销。t18已完成独立Leaf `7a5d08d`匹配测试与collector边界实机，但collector开销/POI完成量仍开放；S1、S2和流体guard均未获正式准入，没有candidate实现+A/B。因此文档可关闭身份误述和“未执行Leaf测试”两项，但不能把Round2写成已完成合规多候选收益实验。
