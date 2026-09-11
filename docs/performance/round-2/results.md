# Performance Round 2：工程实验结果

> 日期：2026-09-11。控制源 `5355f563`（可执行源码等同 upstream `7a5d08d`），JDK 25。这里记录工程阶段的候选裁决；“未准入”与“撤销”都不是性能收益声明。原始世界、JFR、日志、jar 与逐 run JSON 留在 ignored `run/perf/round2/`，不提交二进制或世界。

## 结论

本轮短名单 **零保留性能补丁**。唯一进入完整 A/B 的 H1 未达到预注册的 5% CPU/MSPT 门槛，并且候选 total process CPU 在三对中有两对变差，已通过独立 revert commit 撤销。其余四项在写代码前被 applied-source 差集、许可或重复实现门禁淘汰。

## 有序逐项裁决

| 顺序 | 候选 | 终态 | 工程证据 |
|---:|---|---|---|
| 1 | H1 `AcquirePoi` 冗余结果集合 | 撤销 | 独立补丁 commit `46189bf2`，revert commit `202fe387`；3 对 420 s 交错 A/B 的 average MSPT 配对中心仅改善 1.57%，process CPU 配对中心反而回退 8.60%，不满足 5% 且方向不一致。 |
| 2 | #6 explosion entity-raycast hit factory | 未准入（重复） | 当前 `ServerExplosion#getSeenFraction` 已使用 `clipsAnything`、`LazyEntityCollisionContext` 与直接 boolean 命中，且复用 block/collision-shape cache；已消除候选针对的逐 ray `ClipContext`/`HitResult` 路径。 |
| 3 | #7 explosion 16³ shell rays | 未准入（重复） | 当前 `ServerExplosion.CACHED_RAYS` 静态初始化只枚举 16³ 外壳，`calculateExplodedPositions` 直接迭代 1352 条缓存 ray；与 Lithium PR #686 的目标等价。 |
| 4 | #1 无 listener event guard | 未准入（许可/profile） | Paper PR #14173 仍为 open patch hunk；本轮未建立逐文件许可/header 映射。基线 AI/POI JFR 未命中其 vehicle/fluid/inside-block call site，且 Leaf 已覆盖 collision/BlockPhysics 等多个 family。没有 CMI jar，故明确记为 CMI 未验证，不复制、不实现。 |
| 5 | #13 FerriteCore FastMap `StateHolder` | 未准入（重复） | 当前 `StateHolder` 已实现 Moonrise `PropertyAccessStateHolder`；`StateDefinition` 初始化 `ZeroCollidingReferenceStateTable`，get/set 经 table index / `optimisedTable.trySet`，不是待替换的 map lookup。 |

## H1 独立 A/B

### Patch 与语义边界

H1 让 `PoiAccess` 在完成“最近 5 个”选择之后应用现有 `validPoi`，并直接写入原来的 `HashSet<Pair<...>>`，删除 caller 的一层 `ArrayList`、backing array、iterator 与复制循环。它不改变搜索半径、最近五个选择、HashSet 目标、路径线程、pending `AsyncPath` owner、retry cache 或 tick 间隔。实现先以 `46189bf2` 单独提交；裁决失败后以 `202fe387` 正常 revert，没有 reset/amend。

### 冻结工件与运行设计

- A：`23D296C4C2D37F42909EB3F309E7CF3AB4F8DF768AB7611D8B0A000C236C5046`
- B：`68C1ED76A48C0FFA011B53BD25ADA666B7DE1858AF2FA6AD209228BB0C812EA0`
- 每次 fresh process、同一只读 template 的独立副本、`-Xms2G -Xmx2G -XX:+AlwaysPreTouch`、420 s warmup、420 s measurement、20 TPS。
- 顺序：`A1-B1 / B2-A2 / A3-B3`。每 run 有 84 个不重叠 100-tick `tick query` 窗口；JFR、process CPU、working set、sprint 完成量与日志全部保存。
- 每次工作完成量 oracle 均为 villager count 256、farmer count 72；退出码均为 0。CMI 未验证。

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

## 构建、测试与已知失败归因

候选 jar 使用 JDK 25 `leaf-server:createPaperclipJar` 成功构建。最终树仍需执行合同列出的 patch apply、paperclip 和 test 命令；test 只允许复现冻结基线的 10 项 known-upstream failures 与 7 skips，任何集合变化都阻塞。`applyAllPatches` 的普通入口在本机遭遇 Git-for-Windows `git-submodule` 的 `fork: Resource temporarily unavailable` / exit 254；这是 checkout wrapper 基础设施故障，不应伪装为源码或补丁通过。

## 外部来源

- #6：Lithium PR #623，LGPL-3.0；只用于差集核验，未复制。
- #7：Lithium PR #686，LGPL-3.0；只用于差集核验，未复制。
- #1：Paper PR #14173；许可门禁未过，未复制。
- #13：FerriteCore `FastMapStateHolderMixin` @ `0cef1f2a`，MIT；只用于差集核验，未复制。
- H1：本地 profile 驱动实现，无外部代码复制；已撤销。
