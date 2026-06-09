package dev.gmvalentino.monaka.examples.checkout.auth

import dev.gmvalentino.monaka.core.Effect

// Auth has no effects — state changes are the signal other machines observe
sealed interface AuthEffect : Effect
