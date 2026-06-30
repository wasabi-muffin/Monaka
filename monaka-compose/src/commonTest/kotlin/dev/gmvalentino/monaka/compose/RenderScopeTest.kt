package dev.gmvalentino.monaka.compose

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RenderScopeTest {

    @Test
    fun invokesBlockWhenTypeMatches() {
        var rendered: FixtureState.Count? = null
        val state: FixtureState = FixtureState.Count(5)

        state.render<FixtureState.Count> { rendered = renderState }

        assertEquals(FixtureState.Count(5), rendered)
    }

    @Test
    fun skipsBlockWhenTypeDoesNotMatch() {
        var called = false
        val state: FixtureState = FixtureState.Count(0)

        state.render<FixtureState.Loading> { called = true }

        assertFalse(called)
    }
}
