# Leaf-Performance

<div align="center">

**[LOGO PLACEHOLDER]**

_The logo is still text. The profiler has not found a hot path in the banner renderer._

</div>

> [!IMPORTANT]
> **Leaf-Performance is an unofficial, private experimental fork based on [Leaf](https://github.com/Winds-Studio/Leaf).** It is not a Leaf release, is not endorsed by the Leaf maintainers, and fork-specific bugs belong in the [Leaf-Performance issue tracker](https://github.com/TaoXiaoBai/Leaf-Performance/issues), not upstream.

This repository is a personal performance workbench with a small amount of bad taste included free of charge. Measure first, change one thing at a time, and throw the patch into the bin when the numbers say “premium placebo.” No invented TPS, no decorative percentages, and no imaginary speed crown.

**English** | [中文](public/readme/README_CN.md)

## 🧪 Scope and policy

- Tracks the upstream Leaf **`ver/26.2`** line. Versions before 26.2 are not currently planned.
- Stays in the Paper-style server model. This is **not a Folia clone**, and thread-model changes are not free speed.
- Full plugin compatibility is not the first priority. Vanilla/Paper behavior and plugin-facing contracts remain the default unless an experiment explicitly documents and tests a trade-off.
- Performance work starts with a reproducible workload and profiler evidence. At most one main optimization is evaluated at a time, with no more than two tightly related small changes.
- The same-condition A/B record should include average MSPT, P50/P95/P99, CPU, allocation, GC and contention where applicable. A change with no credible benefit, a regression, unsafe concurrency, or merely shifted work with higher total CPU is removed.
- Runtime safety, lifecycle rules, snapshots, unknown chunk semantics, completed work and queues are not benchmark fuel. Resource-for-latency trades need an explicit budget.
- Back up worlds before testing. This fork may be opinionated; your only production world should not be part of the experiment.

## 🧾 Experiment records

> Experimental performance patches are currently being integrated. Strict benchmarking and compatibility audits are deferred to a later audit round.

- [Round 1 baseline and limitations](docs/performance/round-1-baseline.md)
- [Round 2 external candidate/source audit](docs/performance/round-2/candidates.md)

These records describe controlled machines and workloads. They are not universal public-server claims, and an upstream author's benchmark is not a benchmark of this fork.

## 🔗 Project links

- **Fork issues:** <https://github.com/TaoXiaoBai/Leaf-Performance/issues>
- **Upstream Leaf source:** <https://github.com/Winds-Studio/Leaf>
- **Upstream documentation:** <https://www.leafmc.one/docs/getting-started>
- **Upstream releases:** <https://github.com/Winds-Studio/Leaf/releases>

## 📈 Upstream services

Leaf's [bStats page](https://bstats.org/plugin/server-implementation/Leaf), website, documentation, downloads, Discord/QQ communities, donation pages, Maven repository and other hosted services belong to the upstream Leaf project. Leaf-Performance does not claim a separate bStats service or statistics ID, and those services must not be read as upstream support or endorsement of this fork.

## 📦 Building

Building a Paperclip JAR for distribution:

```bash
./gradlew applyAllPatches && ./gradlew createPaperclipJar
```

## 📦 API

<details>
<summary>Click to expand</summary>

### Gradle

```kotlin
repositories {
  maven {
    // This repository is operated by upstream Leaf.
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

## ⚖️ Upstream provenance and licenses

Leaf-Performance is derived from Leaf, which in turn carries code and patches from Paper and other projects. This is provenance and licensing information, not a claim that those projects helped, sponsored, reviewed, or endorsed this private fork.

- Primary upstream: [Leaf](https://github.com/Winds-Studio/Leaf)
- Major upstream/source projects represented in Leaf include [Paper](https://github.com/PaperMC/Paper), [Gale](https://github.com/GaleMC/Gale), [Pufferfish](https://github.com/pufferfish-gg/Pufferfish), [Purpur](https://github.com/PurpurMC/Purpur), [Leaves](https://github.com/LeavesMC/Leaves), [Moonrise](https://github.com/Tuinity/Moonrise), [Lithium](https://github.com/CaffeineMC/lithium), [C2ME](https://github.com/RelativityMC/C2ME-fabric), [SparklyPaper](https://github.com/SparklyPower/SparklyPaper), [Luminol](https://github.com/LuminolMC/Luminol), [Sakura](https://github.com/Samsuik/Sakura) and others identified by source-file or patch headers.

Leaf inherits multiple open-source licenses from its upstream projects. Preserve all copyright notices, source/patch headers and attribution required by those licenses. See [LICENSE.md](LICENSE.md) for the repository-level summary and the applicable source headers for exact terms.
