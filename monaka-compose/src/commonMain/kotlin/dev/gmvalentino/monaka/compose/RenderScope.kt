package dev.gmvalentino.monaka.compose

import dev.gmvalentino.monaka.core.State as StateMarker

public class RenderScope<State : StateMarker>(public val renderState: State)

public inline fun <reified State : StateMarker> StateMarker.render(block: RenderScope<State>.() -> Unit) {
    if (this is State) RenderScope(renderState = this).block()
}
