package dev.gmvalentino.monaka.examples.checkout.checkout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gmvalentino.monaka.core.Store
import dev.gmvalentino.monaka.examples.checkout.coordinator.AppCoordinator
import dev.gmvalentino.monaka.examples.checkout.auth.AuthAction
import dev.gmvalentino.monaka.examples.checkout.auth.AuthEffect
import dev.gmvalentino.monaka.examples.checkout.auth.AuthState
import dev.gmvalentino.monaka.examples.checkout.auth.AuthStore
import dev.gmvalentino.monaka.examples.checkout.cart.CartAction
import dev.gmvalentino.monaka.examples.checkout.cart.CartEffect
import dev.gmvalentino.monaka.examples.checkout.data.CartItem
import dev.gmvalentino.monaka.examples.checkout.cart.CartState
import dev.gmvalentino.monaka.examples.checkout.cart.CartStore
import dev.gmvalentino.monaka.examples.checkout.data.FakeAuthRepository
import dev.gmvalentino.monaka.examples.checkout.data.FakeCartRepository
import dev.gmvalentino.monaka.examples.checkout.data.FakePaymentRepository
import dev.gmvalentino.monaka.ext.format

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val coordinator = remember(scope) {
        AppCoordinator(
            authRepository = FakeAuthRepository(),
            cartRepository = FakeCartRepository(),
            paymentRepository = FakePaymentRepository(),
            scope = scope,
        )
    }
    DisposableEffect(coordinator) {
        onDispose {
            coordinator.registry.get(AuthStore::class)?.stop()
            coordinator.registry.get(CartStore::class)?.stop()
            coordinator.registry.get(CheckoutStore::class)?.stop()
        }
    }
    @Suppress("UNCHECKED_CAST")
    val authMachine = coordinator.registry.get(AuthStore::class) as Store<AuthState, AuthAction, AuthEffect>

    @Suppress("UNCHECKED_CAST")
    val cartMachine = coordinator.registry.get(CartStore::class) as Store<CartState, CartAction, CartEffect>

    @Suppress("UNCHECKED_CAST")
    val checkoutMachine = coordinator.registry.get(CheckoutStore::class) as Store<CheckoutState, CheckoutAction, CheckoutEffect>

    val authState by authMachine.state.collectAsStateWithLifecycle()
    val cartState by cartMachine.state.collectAsStateWithLifecycle()
    val checkoutState by checkoutMachine.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Checkout") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                AuthSection(
                    state = authState,
                    onSignIn = { u, p -> authMachine.dispatch(AuthAction.Attempt(u, p)) },
                    onSignOut = { authMachine.dispatch(AuthAction.SignOut) },
                )
            }

            if (authState is AuthState.SignedIn) {
                item {
                    CartSection(
                        cartState = cartState,
                        checkoutActive = checkoutState !is CheckoutState.Idle,
                        onBeginCheckout = { userId, items, total ->
                            checkoutMachine.dispatch(CheckoutAction.Begin(userId, items, total))
                        },
                    )
                }
            }

            if (checkoutState !is CheckoutState.Idle) {
                item {
                    CheckoutSection(
                        state = checkoutState,
                        onConfirm = { checkoutMachine.dispatch(CheckoutAction.Confirm) },
                        onRetry = { checkoutMachine.dispatch(CheckoutAction.RetryPayment) },
                        onDone = { checkoutMachine.dispatch(CheckoutAction.Cancel) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthSection(
    state: AuthState,
    onSignIn: (String, String) -> Unit,
    onSignOut: () -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("1 · Sign in", style = MaterialTheme.typography.titleMedium)

            when (state) {
                is AuthState.SignedOut -> {
                    Text(
                        "Hint: any username, password = \"password\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { onSignIn(username, password) },
                        enabled = username.isNotBlank() && password.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Sign in") }
                }

                is AuthState.SigningIn -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("Signing in…")
                }

                is AuthState.SignedIn -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Signed in as ${state.user.displayName}")
                    TextButton(onClick = onSignOut) { Text("Sign out") }
                }

                is AuthState.SignInFailed -> {
                    Text(
                        "Sign-in failed. Check your password.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(
                        onClick = { onSignIn(username, password) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Try again") }
                }
            }
        }
    }
}

@Composable
private fun CartSection(
    cartState: CartState,
    checkoutActive: Boolean,
    onBeginCheckout: (String, List<CartItem>, Double) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("2 · Cart", style = MaterialTheme.typography.titleMedium)

            when (cartState) {
                is CartState.Empty -> Text(
                    "Cart is empty.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                is CartState.Loading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("Loading cart…")
                }

                is CartState.WithItems -> {
                    cartState.items.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(item.name, modifier = Modifier.weight(1f))
                            Text("×${item.quantity}")
                            Spacer(Modifier.width(12.dp))
                            Text(item.subtotal.format())
                        }
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Total", style = MaterialTheme.typography.titleSmall)
                        Text(cartState.total.format(), style = MaterialTheme.typography.titleSmall)
                    }
                    if (!checkoutActive) {
                        Button(
                            onClick = {
                                onBeginCheckout(cartState.userId, cartState.items, cartState.total)
                            },
                            enabled = !cartState.isEmpty,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Begin checkout") }
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckoutSection(
    state: CheckoutState,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
    onDone: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("3 · Checkout", style = MaterialTheme.typography.titleMedium)

            when (state) {
                is CheckoutState.ReviewingOrder -> {
                    state.items.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(item.name, modifier = Modifier.weight(1f))
                            Text(item.subtotal.format())
                        }
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Total", style = MaterialTheme.typography.titleSmall)
                        Text(state.total.format(), style = MaterialTheme.typography.titleSmall)
                    }
                    Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) {
                        Text("Confirm & Pay")
                    }
                }

                is CheckoutState.ProcessingPayment -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("Processing payment…")
                }

                is CheckoutState.Done -> {
                    Text(
                        "Order placed!",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "Order ID: ${state.orderId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                        Text("Back to shopping")
                    }
                }

                is CheckoutState.PaymentFailed -> {
                    Text(
                        "Payment failed: ${state.reason}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                        Text("Retry payment")
                    }
                }

                is CheckoutState.Idle -> Unit
            }
        }
    }
}
