# Performance Round 2：可审查的冻结协议

> 本文件把 `run/perf/round2/` 下 ignored 的本地证据提炼为可提交合同。它记录“实际做过什么”和“候选实施前仍须做什么”，不构成性能收益声明。

## 1. 当前证据身份与边界

### 实际执行的唯一控制

- Fork commit：`5355f563ab18e6e2a395595abef380d282f1c49b`；当时 `origin/ver/26.2` 与之匹配。
- 记录的 upstream ref：`7a5d08d50189770d2295c56eccf09873c49b8267`。
- 在该控制点，`git diff upstream/ver/26.2..5355f563 -- leaf-api leaf-server` 为空。这只能说明用于构建的 applied API/server 源码等价；**实际没有启动或测试第二套独立 upstream checkout/artifact**。
- 实际控制工件：`leaf-paperclip-26.2.local-SNAPSHOT.jar`，SHA-256 `23D296C4C2D37F42909EB3F309E7CF3AB4F8DF768AB7611D8B0A000C236C5046`。
- JDK：Zulu 25.0.3+9 LTS；JVM：`-Xms2G -Xmx2G -XX:+AlwaysPreTouch`，profile 另加 `-XX:FlightRecorderOptions=stackdepth=128`、JFR `settings=profile`。

因此报告只能写“Fork 单控制 fresh 结果；控制源码对该 upstream ref 等价推断”，不能写“独立 upstream 已复现”。

### 现有 profile 只能作方向线索

现有 active-villager 最终时间窗只有 15 个 `Server thread` real-tick runnable execution samples：15/15 穿过 `Villager.customServerAiStep`，14/15 穿过 `Brain.tick`，12/15 穿过 `AcquirePoi -> PoiAccess.findNearestPoi*`。这些 inclusive samples 重叠、非加和：仅说明 Brain/POI 方向值得补测，不能稳定排序热点、估算 CPU 比例或归因候选收益。

同一记录中 real-tick Server-thread `ThreadPark` 与 `JavaMonitorEnter` 各为 0 条。准确结论仅是“本次未观测到 in-tick park/monitor event”；稀疏/采样证据不能证明没有阻塞。pacing parks 必须与 real-tick subtree 分开。

## 2. Runner 与精确复现方法

### 已使用的 profile runner

- 路径：`run/perf/round2/run-active-profile.ps1`
- SHA-256：`A0B9E2C24417B1CA9CFA410819FECB799833DB595666F21715AB45DC2F6C6428`
- 模板 manifest：`run/perf/round2/active-template-manifest.tsv`
- manifest SHA-256：`D74EF4F08740B6F6748146EBD6A9D71D657BB26279C1CA87B07040D84596F6A5`

从 repo root 复现现有 **profile-only** 运行（`RunId` 必须唯一，runner 会拒绝覆盖）：

```powershell
Get-FileHash -Algorithm SHA256 `
  .\run\perf\round2\run-active-profile.ps1, `
  .\run\perf\round2\active-template-manifest.tsv, `
  .\leaf-server\build\libs\leaf-paperclip-26.2.local-SNAPSHOT.jar

pwsh -NoProfile -File .\run\perf\round2\run-active-profile.ps1 `
  -Template .\run\perf\round2\templates\active-villager `
  -RunId active-villager-profile-repro-<unique-id> `
  -JarPath .\leaf-server\build\libs\leaf-paperclip-26.2.local-SNAPSHOT.jar `
  -WarmupSeconds 420 `
  -MeasureSeconds 60
```

该 runner 明确执行 `420 s` 预热，随后执行约 `60 s` 的 process/host/working-set 测量，并在预热前启动覆盖两段时间的 JFR；两者不是“420 s 测量”。它只发出滚动 `tick query`，没有产生正式 A/B 所需的精确 busy-tick 原始向量。因此现有 60 s 窗口只可复现方向性 profile，不能直接作为候选收益 A/B。

### 正式候选 runner 合同（实施前待补）

Engineer 在任何候选代码改动前须提交或在可审查 manifest 中冻结以下字段；不能只引用 ignored 输出：

1. baseline/candidate commit 与 fresh artifact hash；runner path/hash；模板/世界 manifest hash。
2. `warmupSeconds >= 420`；**另列** `measureSeconds` 与精确 `measuredTickCount`。测量时长由实施前控制重复的噪声/功效决定，一经冻结不得事后延长、截短或换主指标。
3. 真正的 busy-tick 边界：在 `ServerTickStartEvent(N)` 读取已经发布的 prior ring entry `tickTimesNanos[(N-1)%100]`；`ServerTickEndEvent.tickDuration/timeRemaining` 作为 full-loop/lost-tick 护栏另记，不与 busy MSPT 混合。
4. 每次使用停止状态的同一模板母本复制到全新 run dir；固定 seed、配置、插件、JDK/JVM、CPU/电源策略、bot 脚本、workload age/order。
5. 至少 3 对平衡交错 A/B，方向一致、改善 `>=5%` 且超过预运行 noise band；保存所有 raw vectors、编译/JFR事件、average/P50/P95/P99、main-thread inclusive CPU、total CPU、allocation、GC、contention、RSS 与业务完成量。
6. CPU 与 wall/park/monitor 分开归因；线程外工作使用预注册 all-thread/total CPU 主指标。禁止只降 tick CPU 却增加 total CPU 或降低完成量。
7. 不事后更换 workload、边界、主指标或例外。高风险项不降 `>=5%`；预先批准的低风险尾延迟/内存例外仍须超过噪声且不能零收益。

所有五项（H1/#6/#7/#1/#13）在实施前都必须先用自己的真实 workload 完成 admission profile，证明目标方法位于可解释、可重复的 inclusive 排名内，并记录真实计时边界及业务完成量。H1 还必须覆盖 POI 正常、低/高密度、无可达 POI/POI 耗尽，并记录 acquisition/path/成功占用/失败重试完成量。15 samples 不给任何候选豁免。

## 3. Fork fresh 测试基线

### 实际执行与汇总

实际命令（Fork 单控制）：

```powershell
.\gradlew.bat :leaf-server:test --rerun-tasks --stacktrace --no-daemon
```

实际 XML 汇总：**8,967 tests / 10 failures / 0 errors / 23 skipped**。来源目录：`leaf-server/build/test-results/test/`。比较时须核对测试集合、每个 testcase 签名与根因 token；不能只比较 10/23 总数，也不能沿用 Round 1 的 8,951/10/7。

### 10 个失败签名与 Fork 证据分类

| # | testcase 稳定签名 | XML/消息 token | 当前分类 |
|---:|---|---|---|
| 1 | `io.papermc.paper.inventory.recipe.ShapelessRecipeMatchTest#predicate_plusRegular_renamedSatisfiesPredicateOnly()` | `expected: <false> but was: <true>` | Shapeless predicate case |
| 2 | `org.bukkit.craftbukkit.inventory.ItemMetaCloneTest#testClone()` | `ExceptionInInitializerError`; root `GaleGlobalConfiguration.get()` / `logToConsole` null | CraftLegacy/Gale init family |
| 3 | `org.bukkit.craftbukkit.inventory.ItemMetaImplementationOverrideTest#initializationError` | `NoClassDefFoundError: ... ItemStackTest` | CraftLegacy/Gale init cascade |
| 4 | `org.bukkit.craftbukkit.inventory.ItemMetaTest#testEachExtraData()` | `ExceptionInInitializerError`; same Gale token | CraftLegacy/Gale init family |
| 5 | `org.bukkit.craftbukkit.inventory.NMSCraftItemStackTest#testCloneEnchantedItem()` | `NoClassDefFoundError: ... CraftLegacy` | CraftLegacy/Gale init cascade |
| 6 | `org.bukkit.craftbukkit.legacy.EvilTest#testTo()` | `NoClassDefFoundError: ... CraftLegacy` | CraftLegacy/Gale init cascade |
| 7 | `org.bukkit.craftbukkit.legacy.LegacyTest#testRestricted()` | `ExceptionInInitializerError`; same Gale token | CraftLegacy/Gale init family |
| 8 | `org.bukkit.craftbukkit.legacy.LegacyTest#fromLegacyMaterial()` | `NoClassDefFoundError: ... CraftLegacy` | CraftLegacy/Gale init cascade |
| 9 | `org.bukkit.craftbukkit.legacy.LegacyTest#toLegacyMaterial()` | `NoClassDefFoundError: ... CraftLegacy` | CraftLegacy/Gale init cascade |
| 10 | `org.bukkit.craftbukkit.legacy.MaterialReroutingTest#testBukkitClasses[137]` | `AntiXrayAdapter ... callBlockChange ... Material ... expected true but was false` | missing Material reroute |

以上“当前分类”来自 Fork XML stack/message，不是独立 upstream 归因证明。

### 独立 frozen-upstream 测试（实施前待补）

在不污染当前工作树的隔离目录准备完整 `upstream/ver/26.2` checkout，记录 exact commit、submodules/upstream refs、Gradle/JDK、依赖锁/配置和 applied-source 状态；然后运行与 Fork 完全相同的命令：

```powershell
.\gradlew.bat :leaf-server:test --rerun-tasks --stacktrace --no-daemon
```

保存 XML 与环境 manifest，再按 testcase 稳定签名和根因 token 分类。只有在该独立 checkout 实际得到同一签名后，单项才可标“独立 upstream 已复现”。构建/依赖/配置不匹配时标环境阻塞，不能把 Fork 失败推给 upstream。候选 A/B 的新增失败、既有签名变化、测试集合变化或 skip 集变化均阻塞保留。

## 4. 准入与停止规则

- `shortlist.md` 的五项均处于“待 admission benchmark”，不是已证明可实施。
- 未准入、静态重复、许可阻塞或没有 oracle 只计研究/排除结论，不计“实现 + A/B 完成”的候选数量。
- 每个准入候选仍独立 commit、独立 A/B、独立保留或 revert；失败后继续下一项。
- #6 与 #7 必须分别准入、分别 A/B；只有两者各自通过才做组合增量回归。
- CMI 无 jar 只标“未验证”，不称“应该兼容”，也不作为无关候选的机械硬阻塞。
- TPS-window 不重访；TPS 只作 20 TPS/overload 健康护栏。
