# Claude Code skills

Monaka ships a [Claude Code](https://claude.com/claude-code) plugin so an AI coding assistant can
help you use the library correctly in your own project — adding dependencies, authoring state
machines, writing tests, wiring Compose, coordinating multiple machines, and setting up code
generation.

The plugin lives in this repository under [`plugins/monaka`](https://github.com/wasabi-muffin/monaka/tree/main/plugins/monaka),
and the repository doubles as a Claude Code marketplace.

## Install

From any project where you use Claude Code:

```
/plugin marketplace add wasabi-muffin/monaka
/plugin install monaka@monaka
```

The first command registers this repository as a marketplace; the second installs the `monaka`
plugin. Once installed, Claude automatically loads the relevant skill when you ask it to work with
Monaka — you don't invoke skills by hand.

!!! tip "No marketplace?"
    Prefer not to use the marketplace? Copy the skill folders from `plugins/monaka/skills/` into
    your project's `.claude/skills/` directory (or `~/.claude/skills/` for all projects). Each skill
    is self-contained.

## Skills

| Skill | Use it when you… |
|---|---|
| `monaka-setup` | Add Monaka to a module — Gradle dependencies, version catalog, targets. |
| `monaka-state-machines` | Author state machines — `State`/`Action`/`Effect`, the DSL, handler verbs, hooks, hierarchical states, plugins. |
| `monaka-testing` | Test state machines with the `monaka-test` DSL. |
| `monaka-compose` | Wire a `Store` into Compose Multiplatform UI. |
| `monaka-multi-machine` | Coordinate several stores with `StoreRegistry` + `relay`. |
| `monaka-codegen` | Set up the `@Transition` KSP processor or the Gradle YAML/PlantUML/stub generators. |

## Versioning

The skills track the library. Pin the Monaka version in your own `libs.versions.toml` and check the
[Maven Central release](https://central.sonatype.com/artifact/dev.gmvalentino.monaka/monaka) for the
latest. The skills point back to this documentation site for full detail.
