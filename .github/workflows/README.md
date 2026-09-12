# Workflow ownership

Files named `leaf-performance-*.yml` are the CI/CD workflows owned by this fork:

- `leaf-performance-ci.yml` builds and uploads a Paperclip artifact for `ver/26.2` pushes, pull requests, and manual runs.
- `leaf-performance-upstream-watch.yml` reports new `Winds-Studio/Leaf` commits without changing fork branches.
- `leaf-performance-release.yml` rebuilds tagged `v26.2-lp.*` source and publishes explicitly branded Leaf-Performance releases.

The other workflow files are inherited from `Winds-Studio/Leaf`. They intentionally retain their
`github.repository == 'Winds-Studio/Leaf'` guards and therefore remain inert in this fork. Keeping
them unmodified reduces conflicts during future rebases; they are not Leaf-Performance release or
publishing infrastructure and must not be enabled by removing the repository guards.
