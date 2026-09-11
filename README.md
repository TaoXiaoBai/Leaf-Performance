# Leaf-Performance

> [!IMPORTANT]
> **Leaf-Performance is an unofficial, private experimental fork based on [Leaf](https://github.com/Winds-Studio/Leaf).** It is not a Leaf release, is not endorsed by the Leaf maintainers, and should not be used to report fork-specific bugs to them.

The logo is text now because the benchmark does not care how pretty the banner is. This repository is a personal performance workbench: measure first, cut once, and throw the patch into the bin when the numbers say “nice placebo.”

**English** | [中文（上游 Leaf 说明）](public/readme/README_CN.md)

## 🧪 Scope and policy

- Tracks the upstream Leaf `ver/26.2` line. Versions before 26.2 are not currently planned.
- Stays in the Paper-style server model; this is **not a Folia clone** and will not disguise thread-model changes as free speed.
- Full plugin compatibility is not the first priority. Vanilla/Paper behavior and plugin-facing contracts are preserved unless an experiment explicitly documents a trade-off.
- Important optimizations require a reproducible workload and profiler evidence, followed by the same-condition A/B run. No credible benefit means the change is removed.
- Runtime safety, lifecycle rules, snapshots, unknown chunk semantics, completed work and queues are not negotiable benchmark fuel. Resource-for-latency trades require an explicit budget.
- Back up worlds before testing. This fork is allowed to be opinionated; your only copy of a production world is not.

The first local experiment and its limitations are documented in [`docs/performance/round-1-baseline.md`](docs/performance/round-1-baseline.md). Those measurements describe one controlled machine and workload, not public-server performance and not a universal speed claim.

## 🔗 Project links

- Fork issues: <https://github.com/TaoXiaoBai/Leaf-Performance/issues>
- Upstream source: <https://github.com/Winds-Studio/Leaf>
- Upstream documentation: <https://www.leafmc.one/docs/getting-started>
- Upstream releases: <https://github.com/Winds-Studio/Leaf/releases>

## 📈 Upstream services

Leaf's [bStats page](https://bstats.org/plugin/server-implementation/Leaf), Discord, website, donation pages and other services belong to the upstream Leaf project. This fork does not claim a separate bStats service or statistics ID.

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

## ⚖️ License
Leaf is licensed under various open source licenses from its upstream projects. See [LICENSE.md](LICENSE.md) for full details.

## 📜 Credits
Thanks to these projects below. Leaf includes some patches taken from them.<br>
If these excellent projects hadn't existed, Leaf wouldn't have become great.

- [Gale](https://github.com/Dreeam-qwq/Gale) ([Original Repo](https://github.com/GaleMC/Gale))
- [Pufferfish](https://github.com/pufferfish-gg/Pufferfish)
- [Purpur](https://github.com/PurpurMC/Purpur)
- <details>
    <summary>🍴 Expand to see forks that Leaf takes patches from.</summary>
    <p>
      • <a href="https://github.com/KeYiMC/KeYi">KeYi</a> (R.I.P.)
        <a href="https://github.com/MikuMC/KeYiBackup">(Backup)</a><br>
      • <a href="https://github.com/etil2jz/Mirai">Mirai</a><br>
      • <a href="https://github.com/Bloom-host/Petal">Petal</a><br>
      • <a href="https://github.com/fxmorin/carpet-fixes">Carpet Fixes</a><br>
      • <a href="https://github.com/Akarin-project/Akarin">Akarin</a><br>
      • <a href="https://github.com/Cryptite/Slice">Slice</a><br>
      • <a href="https://github.com/ProjectEdenGG/Parchment">Parchment</a><br>
      • <a href="https://github.com/LeavesMC/Leaves">Leaves</a><br>
      • <a href="https://github.com/KaiijuMC/Kaiiju">Kaiiju</a><br>
      • <a href="https://github.com/PlazmaMC/PlazmaBukkit">Plazma</a><br>
      • <a href="https://github.com/SparklyPower/SparklyPaper">SparklyPaper</a><br>
      • <a href="https://github.com/HaHaWTH/Polpot">Polpot</a><br>
      • <a href="https://github.com/plasmoapp/matter">Matter</a><br>
      • <a href="https://github.com/LuminolMC/Luminol">Luminol</a><br>
      • <a href="https://github.com/Gensokyo-Reimagined/Nitori">Nitori</a><br>
      • <a href="https://github.com/Tuinity/Moonrise">Moonrise</a> (during 1.21.1)<br> 
      • <a href="https://github.com/Samsuik/Sakura">Sakura</a><br> 
    </p>
</details>

## 🔥 Special Thanks

<table>
  <tr>
    <td width="50%" align="center">
      <a href="https://cloud.swordsman.com.cn/?i8ab42c">
        <img src="public/image/JiankeServer.jpg" alt="Jianke Cloud Host" width="250">
      </a>
      <br>
      <b>cloud of swordsman | 剑客云</b>
      <p>If you want to find a cheaper, high performance, stable, lower latency host, then cloud of swordsman is a good choice! Registers and purchases in <a href="https://cloud.swordsman.com.cn/?i8ab42c">here</a>.</p>
      <p>如果你想找一个低价高性能、低延迟的云服务商，剑客云是个不错的选择！你可以在 <a href="https://cloud.swordsman.com.cn/?i8ab42c">这里</a> 注册。</p>
    </td>
    <td width="50%" align="center">
      <a href="https://www.rainyun.com/NzE2NTc1_">
        <img src="public/image/RainYun.jpg" alt="雨云" width="250">
      </a>
      <br>
      <b>RainYun | 雨云</b>
      <p>Global multi-line routing with cloud storage. Refund available within 7 days. Reliable uptime and expert support. RainYun — stable, cost-effective, and ready for fast cloud deployment. Visit <a href="https://www.rainyun.com/NzE2NTc1_">RainYun</a>.</p>
      <p>国际多线路选择，配套云存储 — 购买服务后七天内不满意可以申请退订，强大的技术支持团队和高在线率客服。雨云云服务器，用稳定和性价比，助力您快速上云。点击前往 <a href="https://www.rainyun.com/NzE2NTc1_">雨云</a>。</p>
    </td>
  </tr>
  <tr>
    <td colspan="2" align="center">
      <a href="https://www.yourkit.com/">
        <img src="https://www.yourkit.com/images/yklogo.png" alt="YourKit" width="300">
      </a>
      <p>YourKit supports open source projects with innovative and intelligent tools for monitoring and profiling Java and .NET applications. YourKit is the creator of <a href="https://www.yourkit.com/java/profiler/">YourKit Java Profiler</a>, <a href="https://www.yourkit.com/dotnet-profiler/">YourKit .NET Profiler</a>, and <a href="https://www.yourkit.com/youmonitor/">YourKit YouMonitor</a>.</p>
    </td>
  </tr>
</table>
