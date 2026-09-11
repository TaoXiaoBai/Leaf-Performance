# Performance Round 2：外部候选池与来源审计

> 扫描：2026-09-11；目标 `ver/26.2`。本表是筛选输入，不是已证实收益；只读审计源码、Git 历史和公开来源，未编译/启动/压测。TPS-window 降级不重访。上游自报 benchmark 不能当成本 Fork 的收益。

## 口径与本地去重依据

- 每项给 PR、fixed commit/blob 或明确代码库；未知处明确标注。Fabric/NeoForge mod 只能移植 applied-source 思想，不能把 jar 当 Paper patch，也不免除许可证义务。
- Leaf `LICENSE.md`：patch 默认 MIT、header 可另为 GPL/LGPL/Apache，binary GPL-3.0-only；复制时逐文件保留原作者/header。Paper GitHub license API 为 `NOASSERTION`，因此 Paper PR 具体文件许可标“待逐 header 核验”。
- CMI 影响只是静态风险分级，未做 CMI 专项测试。
- 本地已确认：`CraftScheduler.pending` 仍是 `PriorityQueue`；`LivingEntity.lastClimbablePos` 已含 Paper #12764；`RedStoneWireBlock` 已调用 Alternate Current `WireHandler`；`TemptGoal` 已有 `GlobalTemptationLookup/BitSet`；Leaf 已有 `DespawnMap`、C2ME DFC、Lithium hopper/sleeping block entity/non-POI/palette/equipment，以及自有 activation/DAB/async spawning/random-tick/PWT。
- 相关历史：Paper sync `ba74603f`；Leaf `913024c9/a1e9333d` temptation、`977e8967` despawn、`b0bbe62d` C2ME DFC、`574e764d` sleeping BE/hopper、`a05f8902` non-POI、`1de741d5` palette、`821cb726` equipment、`c87a3db9` packet bundling、`bb9f3a56` non-flush、`73b1d759` path navigation、`e120f45b` POI、`f34001de` random tick。

## 候选池（30 项）

字段顺序：**来源/许可；版本/Fabric；原理与去重；风险/工作量/CMI；benchmark 与初判**。

### Paper PR / API 与主线程

1. **无 listener 时跳过 Bukkit event 构造/dispatch（事件热路径）**
   - 来源：[Paper #14173](https://github.com/PaperMC/Paper/pull/14173)，open；文件许可待 header 核验，作者 flennium。
   - 26.2：当前 Paper PR、须逐 hunk；Fabric=否。流体/碰撞/方块物理/`EntityInsideBlockEvent` 早退。Leaf 只有若干单点 guard，属部分重复。
   - 风险/量/CMI：中/中/高，动态注册与 cancellation 必须原样。测 CMI 真插件集下有/无 listener、event 次数、JFR allocation、avg/P95/P99 MSPT。**候选，先差集**。

2. **CraftScheduler timing wheel（插件调度）**
   - 来源：[Paper #13705](https://github.com/PaperMC/Paper/pull/13705)，open；文件许可待核，作者 Intybyte。PR 基于 1.21.11；Fabric=否。
   - O(1) wheel 替代 pending `PriorityQueue`；本地确认尚未有。风险/量/CMI：高/高/高（取消、同 tick 重入、超长 delay、稳定顺序、异步 producer）。
   - 先确认 `mainThreadHeartbeat` inclusive 热点；再测真实分布和 1k/10k/100k mixed tasks、取消风暴、CMI cooldown。**候选，仅热点出现时；高风险不降 5% 门槛**。

3. **`hasItemMeta` 避免构造 ItemMeta（API allocation）**
   - 来源：[Paper #13928](https://github.com/PaperMC/Paper/pull/13928)，open，正文承认兼容风险；文件许可待核。26.2 须适配；Fabric=否。
   - 直接检查 components/tag；本地未见。风险/量/CMI：中高/中/高（damage/default components/custom items）。
   - 先全种类 ItemMeta parity，再跑 CMI kit/GUI/item serialization 的 allocation/MSPT。**候选但不降门槛**。

4. **Copper Golem queue 降低 path validity 频率（AI）**
   - 来源：[Paper #13077](https://github.com/PaperMC/Paper/pull/13077)，closed、draft、未合并，作者 jpenilla；许可待核。来源 1.21.11，26.2 重做；Fabric=否。
   - 多 golem 竞争箱子时 full path check 改为约 60 ticks；Leaf 通用 path 优化不等同。风险/量/CMI：高/中/中，明确改变行为时序。
   - 100/500/2000 golems 同箱与无竞争 control，测导航 CPU、MSPT、行为 parity。**高风险专项候选，默认关闭**。

5. **RegionFile trailing-sector truncate（存储）**
   - 来源：[Paper #14209](https://github.com/PaperMC/Paper/pull/14209)，open；许可待核。当前 Paper；Fabric=否。
   - 只回收文件尾空洞，PR 自报 fresh world 0 reclaim；不是 steady tick CPU。风险/量/CMI：中/中/低。
   - 仅长期 churn 世界测空间、flush p99、崩溃一致性。**不适用本轮/搁置**。

### Lithium（LGPL-3.0；Fabric 源仅可移植）

6. **Explosion entity raycast 专用 hit factory**
   - 来源：[Lithium #623](https://github.com/CaffeineMC/lithium/pull/623)，merged；[LGPL-3.0](https://github.com/CaffeineMC/lithium/blob/develop/LICENSE.md)。公开稳定 branch 仅到 `26.1.x`；Fabric=是。
   - 降低爆炸 entity-raycast allocation/CPU；Leaf `ServerExplosion` 未见 Lithium 标记，仍须 diff Paper/Moonrise。风险/量/CMI：中高/中/中（爆炸事件/保护）。
   - 16–64 TNT 真实组 + 4096 TNT 专项，测 affected entities/blocks parity、CPU/allocation。与 #7 **合并为一个实验**。

7. **Explosion 仅生成 16³ 外壳 rays**
   - 来源：[Lithium #686](https://github.com/CaffeineMC/lithium/pull/686)，merged，LGPL-3.0；源为 1.21.8、26.2 需核算法；Fabric=是。
   - 跳过不会使用的内部点（4096→1352）。风险/量/CMI：中/低中/中。
   - 固定 seed 比 affected block set/damage、MSPT/CPU。**只与 #6 合并，不拆分刷数量**。

8. **Lithium chunk serialization 恢复优化**
   - 来源：[Lithium #709](https://github.com/CaffeineMC/lithium/pull/709)，merged，LGPL-3.0；1.21.9 源、26.2 移植；Fabric=是。
   - 恢复曾丢失的 serialization mixin；Leaf `SerializableChunkData` 有 Paper codec/anti-xray，未见该 attribution。风险/量/CMI：高/高/中（NBT、DFU、anti-xray）。
   - 高速探索/随机传送 load-save，NBT roundtrip、I/O CPU、main-thread stall p99。**仅 profile 命中时选**。

9. **Equipment clear 取消旧 ItemStack subscription（泄漏修复）**
   - 来源：[Lithium #770](https://github.com/CaffeineMC/lithium/pull/770)，merged 于 [`01597fb8`](https://github.com/CaffeineMC/lithium/commit/01597fb879b7a6d17c2420ec4e25a98df3bc8e7b)，LGPL-3.0；develop/Fabric。
   - `clear()` RETURN 太晚，改 HEAD 以取消旧订阅。Leaf 已有 equipment tracking，需精确核是否缺 fix。风险/量/CMI：中低/低/中。
   - 重复 equip/clear/突变旧 stack，测 retained refs、dirty updates、heap。**优先 correctness 审计；无性能收益不作为性能补丁保留**。

10. **Non-allocating voxel shapes 实验分支**
   - 来源：branch head [`b9506f1d`](https://github.com/CaffeineMC/lithium/commit/b9506f1d2991d5b523af05f11244240125b8d337)，LGPL-3.0；版本/成熟度未知，Fabric=是。
   - Leaf 有 collision/shape 优化，重叠未知。风险/量/CMI：很高/高/中（aliasing、活塞、载具、插件碰撞 API）。
   - dense collision + exhaustive shape parity/JFR allocation。**搁置，除非 profile 明确命中**。

11. **Movement caching 实验分支**
   - 来源：branch head [`0bca6e4a`](https://github.com/CaffeineMC/lithium/commit/0bca6e4a78b842dba1cb360a09b9d5c1f79a1a9e)，LGPL-3.0；26.2 未知，Fabric=是。
   - Leaf 已有 zero-movement/inside-block/collision 优化。风险/量/CMI：很高/高/高（cache invalidation、CMI teleport/velocity）。
   - 逐 tick position/velocity parity + dense collisions。**搁置/高风险**。

### 其他 mods

12. **FerriteCore BlockState cache 压缩（内存/locality）**
   - 来源：[`BlockStateCacheImpl` @0cef1f2a](https://github.com/malte0811/FerriteCore/blob/0cef1f2add1f1329aa6e690e8e292acd625c5c6d/Common/src/main/java/malte0811/ferritecore/impl/BlockStateCacheImpl.java)，[MIT](https://github.com/malte0811/FerriteCore/blob/26.1/LICENSE)。只有 26.1 branch；Fabric/NeoForge=是。
   - 共享/压缩 state cache；Leaf 未见 Ferrite，但 Paper/Moonrise 已改 internals。风险/量/CMI：高/高/低中。
   - 大 registry/datapack 下 heap/RSS/GC/startup/MSPT。**内存候选；未达 MSPT 门槛不保留**。

13. **FerriteCore FastMap StateHolder**
   - 来源：[`FastMapStateHolderMixin` @0cef1f2a](https://github.com/malte0811/FerriteCore/blob/0cef1f2add1f1329aa6e690e8e292acd625c5c6d/Common/src/main/java/malte0811/ferritecore/mixin/fastmap/FastMapStateHolderMixin.java)，MIT；26.1 only，Fabric/NeoForge=是。
   - 紧凑 property lookup；未见 Leaf 同实现。风险/量/CMI：中高/中高/低。
   - redstone/random-tick farm + all-state parity、CPU/allocation。**后备，先 profile**。

14. **VMP NearbyEntityTracking（高玩家 tracker）**
   - 来源：[`NearbyEntityTracking.java` @7e89476b](https://github.com/RelativityMC/VMP-fabric/blob/7e89476b51267d7ce64dad75cf001177b6188f1b/src/main/java/com/ishland/vmp/common/playerwatching/NearbyEntityTracking.java)，[MIT](https://github.com/RelativityMC/VMP-fabric/blob/ver/26.1.1/LICENSE)；VMP 26.1.1/Fabric。
   - area tracking 降 entity↔player lookup；Leaf 已有 AsyncTracker/legacy toggle/packet bundle，强重叠。风险/量/CMI：极高/高/高（vanish/NPC/teleport）。
   - bots+entities packet-set parity、tracker/netty CPU。**重复/搁置**。

15. **VMP spawn-density-cap delegate**
   - 来源：[`SpawnDensityCapperDensityCapDelegate`](https://github.com/RelativityMC/VMP-fabric/blob/7e89476b51267d7ce64dad75cf001177b6188f1b/src/main/java/com/ishland/vmp/common/general/spawn_density_cap/SpawnDensityCapperDensityCapDelegate.java)，MIT；26.1.1/Fabric。
   - Leaf 已有 OptimizeMobSpawning/NatureSpawnChunkMap/async spawn/collectSpawningChunks。风险/量/CMI：高/高/中。
   - 多玩家分散 mobcap parity。**大概率重复/搁置**。

16. **VMP async chunks on login**
   - 来源：[`AsyncChunkLoadUtil`](https://github.com/RelativityMC/VMP-fabric/blob/7e89476b51267d7ce64dad75cf001177b6188f1b/src/main/java/com/ishland/vmp/common/chunk/loading/async_chunks_on_player_login/AsyncChunkLoadUtil.java)，MIT；26.1.1/Fabric。
   - 登录预载异步化；Moonrise 已有异步 chunk system。风险/量/CMI：极高/高/高（join/quit/teleport lifecycle）。
   - 连接风暴 + CMI first-join，pause、出生区块、断连清理。**高风险搁置**。

17. **VMP no-flush networking**
   - 来源：[`MixinClientConnection`](https://github.com/RelativityMC/VMP-fabric/blob/7e89476b51267d7ce64dad75cf001177b6188f1b/src/main/java/com/ishland/vmp/mixins/networking/no_flush/MixinClientConnection.java)，MIT；26.1.1/Fabric。
   - Leaf 已有 `bb9f3a56` non-flush 与 `c87a3db9` packet bundling。风险/量/CMI：高/中/中。
   - packet latency/throughput/netty CPU。**已重复**。

18. **Alternate Current redstone**
   - 来源：明确 26.2 commit [`90fe0136`](https://github.com/SpaceWalkerRS/alternate-current/commit/90fe01364efd11f2d7c2f4569dbe60554c6e0ef6)，[MIT](https://github.com/SpaceWalkerRS/alternate-current/blob/main/LICENSE)；Fabric 源。
   - Paper/Leaf `WireHandler` 已移植 Alternate Current。CMI=中；再叠加会冲突。**重复，不测**。

19. **ScalableLux lighting concurrency**
   - 来源：26.2 commit [`77c290c4`](https://github.com/RelativityMC/ScalableLux/commit/77c290c4c84be1c87d006bc8221e5361d98e0c9a)，[LGPL-3.0](https://github.com/RelativityMC/ScalableLux/blob/ver/26.2.0/LICENSE)；Fabric。
   - 与 Moonrise/Starlight lighting 强重叠。风险/量/CMI：极高/极高/中高（FAWE/chunk lifecycle）。
   - lighting correctness/thread contention。**搁置**。

20. **C2ME DFC compiler**
   - 来源：26.2 head [`8138acfb`](https://github.com/RelativityMC/C2ME-fabric/commit/8138acfb32daf5d1b264780b6436eb8ff958c1b9)；[license](https://github.com/RelativityMC/C2ME-fabric/blob/dev/26.2.0/LICENSE.md) 默认 MIT，但 `c2me-opts-accel-opencl` **All Rights Reserved**。
   - Leaf 已有完整 DFC，历史 `b0bbe62d`。CMI=低。**重复；严禁取 ARR OpenCL 目录**。

21. **Noisium worldgen lookup/cache**
   - 来源：[`c640041c`](https://github.com/Steveplays28/noisium/commit/c640041c8c932b36753c0ccf43902ac8b0bd252d)，[LGPL-3.0-or-later](https://github.com/Steveplays28/noisium/blob/1.20-1.20.1/LICENSE)；26.2 未知/Fabric。
   - 与 C2ME/Moonrise/Leaf biome 优化重叠，且偏 chunkgen。风险/量/CMI：高/高/中。
   - 仅新地形高速生成。**版本未知、非默认 steady workload，搁置**。

22. **ServerCore activation range/inactive tick**
   - 来源：[`ActivationRange.java` @5ffab9c1](https://github.com/Wesley1808/ServerCore/blob/5ffab9c18b5da9992a732db67dd362259e5acf91/common/src/main/java/me/wesley1808/servercore/common/activation_range/ActivationRange.java)。仓库 root license endpoint 404；只有 `licenses/GPL.md`/`MIT.md` 且未找到逐文件映射，故**许可未知/阻塞**。Fabric/NeoForge；26.2 未核。
   - 与 Leaf activation+DAB 强重复且改变玩法。风险/量/CMI：高/高/高。**重复+许可阻塞**。

23. **ServerCore 防 pathfinder 同步 chunk load**
   - 来源：[`PathFinderMixin.java` @5ffab9c1](https://github.com/Wesley1808/ServerCore/blob/5ffab9c18b5da9992a732db67dd362259e5acf91/common/src/main/java/me/wesley1808/servercore/mixin/optimizations/misc/PathFinderMixin.java)；许可映射未知/阻塞；26.2 未核/Fabric-NeoForge。
   - 与 Paper/Moonrise/Leaf async pathfinding 有重叠但精确差集未知。风险/量/CMI：高/高/中高（NPC/unloaded border）。
   - chunk-border mobs + teleport churn，sync-load count/path parity/MSPT。**研究候选；许可解决前禁止抄代码**。

24. **ServerCore random-tick chunk cache**
   - 来源：[`ServerChunkCacheMixin` @5ffab9c1](https://github.com/Wesley1808/ServerCore/blob/5ffab9c18b5da9992a732db67dd362259e5acf91/common/src/main/java/me/wesley1808/servercore/mixin/optimizations/ticking/chunk/cache/ServerChunkCacheMixin.java)；许可未知，26.2 未核/Fabric-NeoForge。
   - Leaf 已有 `RandomTickSystem` 与多项 random-tick patch。风险/量/CMI：高/高/中。**重复面极大/搁置**。

### Paper forks 与插件思想

25. **Pufferfish DAB + async mob spawning**
   - 来源：Pufferfish head [`b98b265d`](https://github.com/pufferfish-gg/Pufferfish/commit/b98b265dae06fd59805c8811ea14f92b078e07a1)，逐 patch header 许可；26.2 不明确，非 Fabric。
   - Leaf 已有 `DynamicActivationofBrain`/`AsyncMobSpawning`。CMI=高。**已吸收/重复**。

26. **Leaves/Lithium sleeping block entities + hopper**
   - 来源：Leaves head [`a8bb2728`](https://github.com/LeavesMC/Leaves/commit/a8bb2728c548b4a3d5ab510f9888be7a06551bbc)，Lithium 部分保留 LGPL attribution；非 Fabric port。
   - Leaf 历史 `574e764d` 且本地类齐全。CMI=中高（container events）。**已吸收/重复**。

27. **FarmControl（农场实体节流策略）**
   - 来源：[`728efcfa`](https://github.com/froobynooby/FarmControl/commit/728efcfae298706f804ad08f2d90d5fd02f6b7e4)，[MIT](https://github.com/froobynooby/FarmControl/blob/master/LICENSE)；Paper/Spigot plugin，26.2 未核。
   - 通过密度/距离减少实体 tick，和 activation/DAB 重叠但属于行为/运营策略。风险/量/CMI：高/不移植/高。
   - 只用来启发 dense-farm workload，不把少 tick 当透明 core 收益。**搁置**。

28. **ViewDistanceTweaks（动态 view/simulation distance）**
   - 来源：[`4612810a`](https://github.com/froobynooby/ViewDistanceTweaks/commit/4612810a458b388626b7d4de5af2994ea11a798b)，[MIT](https://github.com/froobynooby/ViewDistanceTweaks/blob/master/LICENSE)；Paper plugin、26.2 未核。
   - 高负载降低 workload，是降级而非等价优化。CMI=中（teleport/per-player view）。**TPS-window/降级类搁置，不重访**。

29. **MobLimit（按附近数量/原因限制 mob）**
   - 来源：[`bdcc9562`](https://github.com/Minebench/MobLimit/commit/bdcc956265e153d6da52346fb9da5657cc6d25fb)，[GPL-3.0](https://github.com/Minebench/MobLimit/blob/master/LICENSE)；Paper plugin，26.2 未核。
   - 直接减少实体数，不是同 workload。风险/CMI=高/高。只可作密集实体对照，**不进入 core A/B**。

30. **Chunky 预生成**
   - 来源：[`ab45b8b3`](https://github.com/pop4959/Chunky/commit/ab45b8b3a4ada40f69fbdb3af63d2a7004ce82a1)，[GPL-3.0](https://github.com/pop4959/Chunky/blob/master/LICENSE)；多 loader/plugin，26.2 release 未核。
   - 把 worldgen 提前，不减少同一工作的 total CPU。CMI=低中。可作 benchmark 世界准备工具，**不适用性能 patch**。

## 独立筛选建议（不是最终结论）

可交给 reviewer 在 profile 证据下选 5–8 个实验输入：**#1、#2、#3、#4、#6+#7（一个实验）、#8、#9、#13 或 #23（二选一）**。其中 #23 许可未解前只能研究；#9 若仅 correctness/leak fix 而无本轮性能收益应另案；所有高风险项仍须默认目标 workload >=5%。

明确去重/排除：#18、#20、#25、#26；VMP 强重叠 #14/#15/#17；非 steady 等价工作 #5/#28/#29/#30；Moonrise 并发重叠 #16/#19；不成熟/版本未知 #10/#11/#21；ServerCore #22–#24 许可未映射。

## A/B 必备字段

每项实现前固定 base/candidate commit、配置、世界副本、seed、JVM、warmup、bot 行为；同 workload 记录 average MSPT、P50/P95/P99、main-thread inclusive CPU、total CPU、allocation、GC、contention，并做对应 parity（events、entities/blocks、packets、NBT）。至少加入真实 CMI 配置回归。`<5%`、回退、竞态、仅搬线程且 total CPU 更高或零收益均撤销并继续下一项。

## 许可证/版本锚点摘要

- Lithium LGPL-3.0；branches 有 `26.1.x`，未见 `26.2.x`。
- C2ME `dev/26.2.0`：默认 MIT，OpenCL 子目录 ARR。
- FerriteCore MIT；branch 到 26.1，未见 26.2。
- VMP MIT；latest fixed build 26.1.1。
- Alternate Current MIT，commit 明确 26.2。
- ScalableLux LGPL-3.0，`ver/26.2.0`。
- ServerCore root license 无法自动识别且双 license 文件未映射到具体源，保持未知。
