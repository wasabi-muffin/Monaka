package tech.fika.monaka.examples.checkout.auth

import tech.fika.monaka.core.Effect

// Auth has no effects — state changes are the signal other machines observe
sealed interface AuthEffect : Effect
