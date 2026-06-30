# Contributing to Monaka

Thanks for your interest in contributing! This document explains how to get set up and what we expect from contributions.

By participating in this project you agree to abide by our [Code of Conduct](CODE_OF_CONDUCT.md).

## Ways to contribute

- 🐞 **Report bugs** — open a [bug report](https://github.com/wasabi-muffin/Monaka/issues/new/choose).
- ✨ **Request features** — open a [feature request](https://github.com/wasabi-muffin/Monaka/issues/new/choose). For larger ideas, start a [discussion](https://github.com/wasabi-muffin/Monaka/discussions) first.
- 📖 **Improve docs** — fixes to KDoc, the `docs/` site (EN + JA), or the README are always welcome.
- 🔧 **Send pull requests** — see below.

## Project layout

Monaka is a multi-module Kotlin Multiplatform build:

| Module | Role |
|---|---|
| `:monaka` | Core KMP MVI state machine library |
| `:monaka-compose` | Compose Multiplatform helpers |
| `:monaka-test` | Test DSL (Turbine + coroutines-test) |
| `:monaka-transitions` | KSP processor for transition helpers |
| `:monaka-gradle-plugin` | Gradle plugin with code generators |
| `sample/` | Sample CMP app (Android + iOS) |
| `docs/` | mkdocs site (English + Japanese) |

See [`CLAUDE.md`](CLAUDE.md) and the [docs](https://monaka.gmvalentino.dev) for an in-depth architecture overview.

## Development setup

**Requirements:** JDK 21 and a recent Android SDK. A macOS machine with Xcode is required to build the Apple targets and run the iOS sample.

```bash
git clone https://github.com/wasabi-muffin/Monaka.git
cd Monaka

./gradlew :monaka:build        # Build the core library (all targets)
./gradlew allTests             # Run the full test suite
```

The Gradle wrapper is committed — always use `./gradlew`.

## Before you open a pull request

Run the same checks CI runs:

```bash
./gradlew allTests        # Tests across all targets
./gradlew apiCheck        # Binary-compatibility validation
./gradlew spotlessCheck   # Kotlin formatting (Spotless + ktlint)
```

To auto-fix formatting:

```bash
./gradlew spotlessApply
```

### Public API changes

The library runs in **explicit-API mode** and uses `binary-compatibility-validator`. If you intentionally change the public API:

```bash
./gradlew apiDump
```

…and commit the updated `*.api` files alongside your change. `apiCheck` will fail in CI otherwise.

### Tests

New behavior needs tests. Prefer the `:monaka-test` DSL (`testStore { }`) for state-machine behavior — see the [test DSL guide](https://monaka.gmvalentino.dev). Keep handler tests deterministic; the runtime processes actions sequentially, so assertions should too.

### Documentation

Any code change must keep documentation in sync:

- Update **KDoc** on changed public declarations.
- Update the relevant `docs/` pages **in every language** (English and Japanese).
- Add a **`CHANGELOG.md`** entry under the appropriate heading.

## Pull request guidelines

- Keep PRs focused on one logical change.
- Write a clear title and description; link the issue it resolves (`Closes #123`).
- Fill out the PR template checklist.
- Match the surrounding code style; let Spotless handle formatting.
- Be responsive to review feedback — we aim to review promptly and appreciate the same.

## Commit messages

We follow a lightweight conventional style, matching the existing history:

```
fix: cancel keyed task on state-type change
add: relay DSL for inter-machine communication
docs: clarify reject() semantics
```

Common prefixes: `fix:`, `add:` / `feat:`, `docs:`, `refactor:`, `test:`, `chore:`, `delete:`.

## License

By contributing, you agree that your contributions will be licensed under the [Apache License 2.0](LICENSE), the same license that covers the project.
