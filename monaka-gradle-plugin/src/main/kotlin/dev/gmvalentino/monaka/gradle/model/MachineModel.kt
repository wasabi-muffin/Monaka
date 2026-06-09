package dev.gmvalentino.monaka.gradle.model

data class MachineModel(
    val name: String,
    val initial: String,
    val states: Map<String, StateNode> = emptyMap(),
)

data class StateNode(
    val onEnter: HookModel? = null,
    val onExit: HookModel? = null,
    val onUpdate: HookModel? = null,
    val lifecycleHooks: Map<String, HookModel> = emptyMap(),
    val on: Map<String, HandlerModel> = emptyMap(),
    val states: Map<String, StateNode> = emptyMap(),
)

data class HookModel(
    val task: TaskModel? = null,
    val effects: List<String> = emptyList(),
    val cancel: String? = null,
    val dispatch: String? = null,
    val transitions: List<String> = emptyList(),
)

data class HandlerModel(
    val transition: String? = null,
    val effects: List<String> = emptyList(),
    val reject: Boolean = false,
    val dispatch: String? = null,
    val cancel: String? = null,
    val task: TaskModel? = null,
)

data class TaskModel(
    val key: String? = null,
    val autoCancel: Boolean = false,
    val dispatches: List<String> = emptyList(),
)
