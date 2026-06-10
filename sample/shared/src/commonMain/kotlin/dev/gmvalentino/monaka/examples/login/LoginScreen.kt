package dev.gmvalentino.monaka.examples.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import dev.gmvalentino.monaka.compose.bindLifecycle
import dev.gmvalentino.monaka.dsl.store
import dev.gmvalentino.monaka.compose.handleEffects
import dev.gmvalentino.monaka.compose.rememberStore
import dev.gmvalentino.monaka.compose.toViewStore

private class FakeLoginRepository : LoginRepository {
    override suspend fun login(username: String, password: String): String {
        delay(1200)
        return if (password == "password") username
        else error("Wrong password. Hint: use \"password\"")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(onBack: () -> Unit) {
    val store = rememberStore { scope ->
        store(stateMachine = LoginStateMachine(FakeLoginRepository()), scope = scope)
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val (state, dispatch) = store
        .bindLifecycle()
        .handleEffects { effect ->
            when (effect) {
                is LoginEffect.ShowValidationError -> snackbarHostState.showSnackbar(effect.message)
                LoginEffect.NavigateToHome -> snackbarHostState.showSnackbar("Logged in successfully!")
                LoginEffect.NavigateToLogin -> { /* already here */
                }
            }
        }.toViewStore()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Login") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            when (val s = state) {
                is LoginState.Submitting -> LoadingContent()
                is LoginState.Authenticated -> AuthenticatedContent(
                    username = s.username,
                    onLogout = { dispatch(LoginAction.Logout) },
                )

                else -> LoginForm(
                    state = s,
                    onCredentialsChanged = { u, p ->
                        dispatch(LoginAction.UpdateCredentials(u, p))
                    },
                    onSubmit = { dispatch(LoginAction.Submit) },
                    onRetry = { dispatch(LoginAction.Retry) },
                )
            }
        }
    }
}

@Composable
private fun LoginForm(
    state: LoginState,
    onCredentialsChanged: (String, String) -> Unit,
    onSubmit: () -> Unit,
    onRetry: () -> Unit,
) {
    val username = when (state) {
        is LoginState.Typing -> state.username
        is LoginState.Error -> state.username
        else -> ""
    }
    val password = when (state) {
        is LoginState.Typing -> state.password
        is LoginState.Error -> state.password
        else -> ""
    }
    val isError = state is LoginState.Error

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Spacer(Modifier.height(16.dp))

        Text("Sign in", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "Hint: any username, password = \"password\"",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { onCredentialsChanged(it, password) },
            label = { Text("Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { onCredentialsChanged(username, it) },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (isError) {
            Text(
                text = state.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Button(
            onClick = if (isError) onRetry else onSubmit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (isError) "Retry" else "Sign in")
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator()
            Text("Signing in…")
        }
    }
}

@Composable
private fun AuthenticatedContent(username: String, onLogout: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Spacer(Modifier.height(48.dp))
        Text("Welcome, $username!", style = MaterialTheme.typography.headlineMedium)
        Text(
            "You are signed in.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onLogout) { Text("Sign out") }
    }
}
