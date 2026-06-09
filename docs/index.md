# Monaka

A Kotlin Multiplatform MVI state machine library. It provides a type-safe DSL for defining
states, actions, effects, and their transitions, backed by a single-coroutine actor model that
guarantees deterministic, race-free state updates.

**Targets:** Android · iOS (arm64, x64, Simulator arm64) · JVM · macOS · watchOS · tvOS · Linux · Windows · JS · Wasm

---

## Core concepts

| Concept | Interface | Role |
|---|---|---|
| **State** | `State` | Snapshot of the machine at a point in time |
| **Action** | `Action` | Intent dispatched by the UI or system |
| **Effect** | `Effect` | One-shot side effect (navigation, toast, analytics) |
| **Store** | `Store<S,A,E>` | Running instance; exposes `state`, `actions`, `effects`, `dispatch` |

Effects are one-shot: emitted on a `SharedFlow` with no replay, so late subscribers miss past emissions.

---

## Getting started

New to Monaka? Start here:

- [Installation](getting-started/installation.md) — add the library to your Gradle build
- [Quick start](getting-started/quickstart.md) — define types, build a store, observe state, dispatch actions

---

## Guide

- [Handlers](guide/handlers.md) — handler patterns, all available verbs, and their semantics
- [Lifecycle hooks](guide/lifecycle-hooks.md) — `onEnter`, `onExit`, `onUpdate`, and app lifecycle forwarding
- [Hierarchical states](guide/hierarchical-states.md) — catch-all parent state blocks and dispatch priority
- [Plugins](guide/plugins.md) — built-in `LoggingPlugin` and writing your own

---

## Gradle plugin

The `dev.gmvalentino.monaka` Gradle plugin provides three code-generation tasks:

| Task | Output | Doc |
|---|---|---|
| `generateMonakaYaml` | `.yaml` spec per state machine | [YAML Generator](gradle-plugin/yaml-generator.md) |
| `generateMonakaPuml` | `.puml` PlantUML diagram per machine | [PlantUML Generator](gradle-plugin/puml-generator.md) |
| `generateMonakaStubs` | Kotlin `State`, `Action`, `Effect`, `StateMachine` files | [Stub Generator](gradle-plugin/stub-generator.md) |
