# Monaka — Claude Code plugin

Skills that teach Claude Code how to help you use [**Monaka**](https://monaka.gmvalentino.dev),
a Kotlin Multiplatform MVI state-machine library, in *your* project.

Once installed, Claude automatically pulls in the right skill when you ask it to add Monaka to a
module, write a state machine, test one, wire it into Compose, coordinate several machines, or set
up code generation — no need to invoke anything by hand.

## Install

From any project where you use Claude Code:

```
/plugin marketplace add wasabi-muffin/monaka
/plugin install monaka@monaka
```

The first command registers this repository as a marketplace; the second installs the `monaka`
plugin from it. Restart or reload when prompted.

> Prefer not to use the marketplace? Copy the `skills/` subdirectories into your project's
> `.claude/skills/` directory (or `~/.claude/skills/` for all projects). Each skill is
> self-contained.

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

These skills track the Monaka library. Pin the library version in your own `libs.versions.toml`
and check the [Maven Central badge](https://central.sonatype.com/artifact/dev.gmvalentino.monaka/monaka)
for the latest release. Full documentation lives at **https://monaka.gmvalentino.dev**.

## License

Apache 2.0 — same as Monaka.
