# Leaf-Performance

**Leaf-Performance** is a private experimental performance fork of [Leaf](https://github.com/Winds-Studio/Leaf), currently tracking **`ver/26.2`**.

It is not an official Leaf release and is not endorsed or supported by the Leaf maintainers. Fork-specific issues belong in the [Leaf-Performance issue tracker](https://github.com/TaoXiaoBai/Leaf-Performance/issues), not upstream.

**English** | [中文](public/readme/README_CN.md)

## Project scope

Leaf-Performance is a place to test focused server optimizations on top of Leaf. It keeps the Paper-style server model and does not attempt to become a Folia clone.

> **Keep cheap wins. Expensive complexity must earn its keep.**

- Small, local changes may be retained when they clearly remove work or allocation, preserve behavior, and remain easy to maintain.
- Complex caches, lifecycle changes, concurrency, and asynchronous work require stronger correctness arguments and workload-specific measurements.
- Changes already covered by Leaf, Paper, or Moonrise are not carried locally.
- Negative optimizations, unsafe concurrency, stale-state risks, and complexity without credible benefit are removed.
- No invented TPS figures, decorative percentages, or universal performance claims.

## Compatibility

Full plugin compatibility is not the first priority, but breaking established Vanilla, Bukkit, Paper, or Leaf behavior for a minor optimization is not acceptable by default. Event parity, threading assumptions, persistent data, player lifecycle, and common plugin-facing paths are reviewed according to the risk of each change.

Back up worlds before testing experimental builds. This repository is a development workbench, not a drop-in promise for every production server.

## Experiment records

Performance decisions and rejected approaches are recorded under [`docs/performance/`](docs/performance/). The current retained/reverted candidate summary is in [`docs/performance/pending-audit.md`](docs/performance/pending-audit.md).

These records apply to specific revisions and workloads. Results from an upstream project do not automatically describe this fork.

## Build

Leaf-Performance requires JDK 25.

```bash
./gradlew applyAllPatches
./gradlew leaf-server:createPaperclipJar
```

## CI and releases

This fork uses its own GitHub Actions and does not depend on Leaf's official Blacksmith runners, download API, secrets, or Maven publication pipeline.

- [Leaf-Performance CI](https://github.com/TaoXiaoBai/Leaf-Performance/actions/workflows/leaf-performance-ci.yml) applies patches, builds a Paperclip JAR on Zulu JDK 25, and uploads the JAR as a workflow artifact.
- [Upstream Watch](https://github.com/TaoXiaoBai/Leaf-Performance/actions/workflows/leaf-performance-upstream-watch.yml) reports new `Winds-Studio/Leaf` `ver/26.2` commits without rebasing or pushing this fork.
- [Leaf-Performance releases](https://github.com/TaoXiaoBai/Leaf-Performance/releases) are rebuilt from `v26.2-lp.*` tags and include source provenance plus a SHA-256 checksum.

Inherited Leaf workflow files remain guarded for `Winds-Studio/Leaf` and do not publish from this repository.

## Links

- [Leaf-Performance issues](https://github.com/TaoXiaoBai/Leaf-Performance/issues)
- [Leaf source](https://github.com/Winds-Studio/Leaf)
- [Leaf documentation](https://www.leafmc.one/docs/getting-started)
- [Leaf releases](https://github.com/Winds-Studio/Leaf/releases)

Leaf's website, documentation, communities, downloads, bStats page, Maven repository, and other hosted services belong to the upstream Leaf project. Their presence does not imply support for or endorsement of this fork.

## Provenance and licenses

Leaf-Performance is derived from Leaf, which includes work from Paper and other open-source projects. Source-file and patch headers remain authoritative for authorship and licensing. Preserve all required copyright notices, license headers, and attribution when adapting external work.

See [LICENSE.md](LICENSE.md) for the repository-level license summary.
