# Leaf-Performance

<div align="center">

**【LOGO 占位符】**

_目前 LOGO 还是文字。Profiler 尚未发现横幅渲染器里的热点。_

</div>

> [!IMPORTANT]
> **Leaf-Performance 是基于 [Leaf](https://github.com/Winds-Studio/Leaf) 的非官方私人实验 Fork。** 它不是 Leaf 的正式发行版，不代表 Leaf 维护者认可；本 Fork 特有的问题请提交到 [Leaf-Performance Issues](https://github.com/TaoXiaoBai/Leaf-Performance/issues)，不要甩给上游。

这是一个附赠少量恶趣味的个人性能实验台：先测量，一次只改一件事；数字说它是“尊贵安慰剂”，就把补丁丢进垃圾桶。不编 TPS，不拿装饰性百分比唬人，也不抢虚构的速度王冠。

[English](../../README.md) | **中文**

## 🧪 范围与原则

- 跟随上游 Leaf **`ver/26.2`** 分支；当前不计划支持 26.2 之前的版本。
- 保持 Paper 风格的服务端模型；这里**不是 Folia clone**，线程模型变化也不等于白送性能。
- 完整插件兼容不是第一优先级。默认仍保留 Vanilla/Paper 行为和面向插件的契约；若实验确有取舍，必须明确记录并验证。
- 性能修改必须先有可重复 workload 和 profiler 证据。每次最多评估一个主要优化，外加不超过两个高度相关的小改动。
- 同条件 A/B 应按需记录 average MSPT、P50/P95/P99、CPU、allocation、GC 与 contention。没有可信收益、发生回退、存在不安全并发，或只是把工作搬到别处且 total CPU 更高的改动，一律删除。
- 运行时安全、生命周期规则、snapshot、未知区块语义、已完成工作和队列都不是 benchmark 燃料。用资源换延迟必须写清预算。
- 测试前请备份世界。本 Fork 可以有脾气，但你的唯一生产世界不该加入实验组。

## 🧾 实验记录

> 当前正在整合实验性性能补丁。严格 Benchmark 与兼容性审计推迟到后续审计轮。

- [Round 1 基线与限制](../../docs/performance/round-1-baseline.md)
- [Round 2 外部候选与来源审计](../../docs/performance/round-2/candidates.md)

这些记录只描述受控机器和特定 workload，不是所有公开服务器的通用结论。上游作者的 benchmark 也不等于本 Fork 的 benchmark。

## 🔗 项目链接

- **本 Fork Issues：** <https://github.com/TaoXiaoBai/Leaf-Performance/issues>
- **上游 Leaf 源码：** <https://github.com/Winds-Studio/Leaf>
- **上游文档：** <https://www.leafmc.one/zh/docs/getting-started>
- **上游发行版：** <https://github.com/Winds-Studio/Leaf/releases>

## 📈 上游服务

Leaf 的 [bStats 页面](https://bstats.org/plugin/server-implementation/Leaf)、网站、文档、下载、Discord/QQ 社区、捐助页面、Maven 仓库及其他托管服务均属于上游 Leaf 项目。Leaf-Performance 不声称拥有单独的 bStats 服务或统计 ID；这些上游服务也不表示上游为本 Fork 提供支持或认可。

## 🤖 CI 与发行版

Leaf-Performance 使用 Fork 自己维护的 GitHub Actions，不复用 Leaf 官方的 Blacksmith、下载 API 或 Maven 发布设施：

- [Leaf-Performance CI](https://github.com/TaoXiaoBai/Leaf-Performance/actions/workflows/leaf-performance-ci.yml) 使用 Zulu JDK 25 应用全部补丁、重新构建 Paperclip JAR，并上传为临时 workflow artifact。
- [Upstream Watch](https://github.com/TaoXiaoBai/Leaf-Performance/actions/workflows/leaf-performance-upstream-watch.yml) 使用唯一 tracking Issue 报告 `Winds-Studio/Leaf` `ver/26.2` 的新提交；它不会自动 rebase，也不会修改或推送 Fork 分支。
- [Leaf-Performance Releases](https://github.com/TaoXiaoBai/Leaf-Performance/releases) 从 `v26.2-lp.*` tag 对应源码重新构建，附带上游/Fork revision 与 SHA-256。它们是非官方 Leaf-Performance 实验构建，不是 Leaf 正式发行版。

继承自 Leaf 的 workflow 文件继续保留 `Winds-Studio/Leaf` 仓库 guard，不会从本 Fork 发布内容。

## 📦 构建

构建用于分发的 Paperclip JAR：

```bash
./gradlew applyAllPatches && ./gradlew leaf-server:createPaperclipJar
```

## 📦 API

<details>
<summary>点击展开</summary>

### Gradle

```kotlin
repositories {
  maven {
    // 此仓库由上游 Leaf 运营。
    url = uri("https://maven.leafmc.one/snapshots/")
  }
}

dependencies {
    compileOnly("cn.dreeam.leaf:leaf-api:26.2.local-SNAPSHOT")
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}
```

### Maven

```xml
<repository>
    <id>leafmc</id>
    <url>https://maven.leafmc.one/snapshots/</url>
</repository>
```

```xml
<dependency>
    <groupId>cn.dreeam.leaf</groupId>
    <artifactId>leaf-api</artifactId>
    <version>26.2.local-SNAPSHOT</version>
    <scope>provided</scope>
</dependency>
```

</details>

## ⚖️ 上游来源与许可证

Leaf-Performance 派生自 Leaf；Leaf 又包含来自 Paper 和其他项目的代码与补丁。这里记录的是来源和许可证关系，并不表示这些项目帮助、赞助、审查或认可了这个私人 Fork。

- 主要上游：[Leaf](https://github.com/Winds-Studio/Leaf)
- Leaf 中具有代码或补丁来源关系的主要项目包括 [Paper](https://github.com/PaperMC/Paper)、[Gale](https://github.com/GaleMC/Gale)、[Pufferfish](https://github.com/pufferfish-gg/Pufferfish)、[Purpur](https://github.com/PurpurMC/Purpur)、[Leaves](https://github.com/LeavesMC/Leaves)、[Moonrise](https://github.com/Tuinity/Moonrise)、[Lithium](https://github.com/CaffeineMC/lithium)、[C2ME](https://github.com/RelativityMC/C2ME-fabric)、[SparklyPaper](https://github.com/SparklyPower/SparklyPaper)、[Luminol](https://github.com/LuminolMC/Luminol)、[Sakura](https://github.com/Samsuik/Sakura)，以及其他由源文件或 patch header 标明的项目。

Leaf 从上游继承了多种开源许可证。请保留各许可证要求的 copyright notice、源码/patch header 与 attribution。仓库级摘要见 [LICENSE.md](../../LICENSE.md)，精确条款以对应源码 header 为准。
