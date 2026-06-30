package dev.gmvalentino.monaka.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import dev.gmvalentino.monaka.core.Store
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class RememberStoreTest {

    @Test
    fun buildsOnceAcrossRecompositionsAndStopsOnDisposal() = runComposeUiTest {
        var factoryCalls = 0
        lateinit var captured: Store<FixtureState, FixtureAction, FixtureEffect>
        var mounted by mutableStateOf(true)
        var tick by mutableStateOf(0)

        setContent {
            if (mounted) {
                @Suppress("UNUSED_EXPRESSION")
                tick // read to force recomposition without changing the remember key
                captured = rememberStore { scope ->
                    factoryCalls++
                    counterStore(scope)
                }
            }
        }

        waitForIdle()
        assertEquals(1, factoryCalls)
        assertTrue(captured.isActive)

        // A recomposition must not rebuild the store.
        tick++
        waitForIdle()
        assertEquals(1, factoryCalls)

        // Leaving the composition must stop the store.
        mounted = false
        waitForIdle()
        assertFalse(captured.isActive)
    }
}
