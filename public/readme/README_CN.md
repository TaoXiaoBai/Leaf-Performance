# Leaf-Performance

[English](../../README.md) | **中文**

> [!IMPORTANT]
> **Leaf-Performance 是基于 [Leaf](https://github.com/Winds-Studio/Leaf) 的非官方私人实验 Fork。** 它不是 Leaf 的正式发行版，也不代表 Leaf 维护者认可。本 Fork 特有的问题请提交到 [Leaf-Performance Issues](https://github.com/TaoXiaoBai/Leaf-Performance/issues)，不要提交给上游 Leaf。

Leaf-Performance 是一个跟随 Leaf `ver/26.2` 分支、用于小型且易审查的服务端性能实验的个人项目。

## 范围与原则

- 保持 Paper 风格的服务端模型。本项目**不是 Folia clone**，也不会因为线程存在就盲目增加线程。
- 完整插件兼容不是第一优先级，但默认仍保持 Vanilla/Paper 行为与插件可观察契约。任何有意的取舍都必须明确记录并测试。
- **保留低成本正收益；昂贵复杂度必须证明自身价值。** 小型、低维护、严格减少工作或分配的改动，不会因为未达到任意固定百分比而被拒绝。缓存、并发、生命周期改动和大范围重构需要更强的证据。
- 重要优化需要可重复 workload 与 profiler 证据。同条件 A/B 应按需记录 average MSPT、P50/P95/P99、CPU、allocation、GC 与 contention。
- 没有可信收益、出现负优化、引入不安全并发、维护成本过高，或只是转移工作且提高 total CPU 的改动会被删除。
- 运行时安全、区块与实体生命周期、持久化、事件语义和已完成工作都不是 benchmark 燃料。测试实验构建前请备份世界。

## 实验记录

- [Round 1 基线与限制](../../docs/performance/round-1-baseline.md)
- [Round 2 外部候选与来源审计](../../docs/performance/round-2/candidates.md)
- [当前保留/撤销审计状态](../../docs/performance/pending-audit.md)

这些记录只描述受控 workload 和源码审查，不是通用性能承诺。上游作者的 benchmark 也不等于本 Fork 的 benchmark。

## 构建

Leaf-Performance 使用 JDK 25。构建 Paperclip JAR：

```bash
./gradlew applyAllPatches
./gradlew leaf-server:createPaperclipJar
```

## CI 与发行版

本 Fork 使用自己维护的 GitHub Actions，不依赖 Leaf 官方 Blacksmith、下载 API 或 Maven 发布设施：

- [Leaf-Performance CI](https://github.com/TaoXiaoBai/Leaf-Performance/actions/workflows/leaf-performance-ci.yml) 使用 Zulu JDK 25 应用补丁、重新构建 Paperclip JAR，并上传临时 workflow artifact。
- [Upstream Watch](https://github.com/TaoXiaoBai/Leaf-Performance/actions/workflows/leaf-performance-upstream-watch.yml) 报告 `Winds-Studio/Leaf` `ver/26.2` 的新提交，但不会自动 rebase 或推送本 Fork。
- [Leaf-Performance Releases](https://github.com/TaoXiaoBai/Leaf-Performance/releases) 从 `v26.2-lp.*` tag 对应源码重新构建，附带来源信息与 SHA-256；它们仍是非官方实验构建。

继承自 Leaf 的 workflow 文件继续保留 `Winds-Studio/Leaf` 仓库 guard，不会从本 Fork 发布内容。

## 项目链接

- **Issues：** <https://github.com/TaoXiaoBai/Leaf-Performance/issues>
- **上游 Leaf：** <https://github.com/Winds-Studio/Leaf>
- **上游文档：** <https://www.leafmc.one/zh/docs/getting-started>
- **上游发行版：** <https://github.com/Winds-Studio/Leaf/releases>

## 来源与许可证

Leaf-Performance 派生自 Leaf；Leaf 又包含来自 Paper 和其他项目的代码与补丁。这里记录的是来源与许可证关系，并不表示这些项目赞助、审查或认可这个私人 Fork。

请保留适用许可证要求的 copyright notice、源码和 patch header 以及 attribution。仓库级摘要见 [LICENSE.md](../../LICENSE.md)，精确条款以对应源码 header 为准。
