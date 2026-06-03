package tech.fika.monaka.examples.login

import kotlin.test.Test
import tech.fika.monaka.test.testStore

private class FakeLoginRepository(
    private val result: Result<String> = Result.success("alice"),
) : LoginRepository {
    override suspend fun login(username: String, password: String): String =
        result.getOrThrow()
}

class LoginStateMachineTest {

    @Test
    fun updateCredentialsFromIdleTransitionsToTyping() = testStore(
        machine = LoginStateMachine(FakeLoginRepository()),
    ) {
        scenario("UpdateCredentials from Idle moves to Typing") {
            trigger(LoginAction.UpdateCredentials("alice", "secret")) {
                expectState<LoginState.Typing> {
                    state.username == "alice" && state.password == "secret"
                }
            }
        }
    }

    @Test
    fun submitWithEmptyFieldsEmitsValidationError() = testStore(
        machine = LoginStateMachine(FakeLoginRepository()),
    ) {
        scenario("Submit with blank credentials emits ShowValidationError") {
            given(LoginState.Typing(username = "", password = ""))

            trigger(LoginAction.Submit) {
                expectEffect(LoginEffect.ShowValidationError("Please fill in all fields."))
            }
        }
    }

    @Test
    fun successfulLoginTransitionsToAuthenticated() = testStore(
        machine = LoginStateMachine(FakeLoginRepository(Result.success("alice"))),
    ) {
        scenario("happy-path login: Typing → Submitting → Authenticated") {
            given(LoginState.Typing(username = "alice", password = "secret"))

            trigger(LoginAction.Submit) {
                expectState<LoginState.Submitting>()
                expectState<LoginState.Authenticated> { state.username == "alice" }
                expectEffect(LoginEffect.NavigateToHome)
            }
        }
    }

    @Test
    fun failedLoginTransitionsToError() = testStore(
        machine = LoginStateMachine(
            FakeLoginRepository(Result.failure(RuntimeException("invalid credentials"))),
        ),
    ) {
        scenario("failed login: Submitting → Error") {
            given(LoginState.Typing(username = "alice", password = "wrong"))

            trigger(LoginAction.Submit) {
                expectState<LoginState.Submitting>()
                expectState<LoginState.Error> { state.message == "invalid credentials" }
            }
        }
    }

    @Test
    fun retryFromErrorResubmits() = testStore(
        machine = LoginStateMachine(FakeLoginRepository(Result.success("alice"))),
    ) {
        scenario("Retry from Error goes through Submitting to Authenticated") {
            given(LoginState.Error(username = "alice", password = "secret", message = "timeout"))

            trigger(LoginAction.Retry) {
                expectState<LoginState.Submitting>()
                expectState<LoginState.Authenticated> { state.username == "alice" }
                expectEffect(LoginEffect.NavigateToHome)
            }
        }
    }

    @Test
    fun logoutFromAnyStateReturnsToIdle() = testStore(
        machine = LoginStateMachine(FakeLoginRepository()),
    ) {
        scenario("Logout from Authenticated navigates to login and resets state") {
            given(LoginState.Authenticated(username = "alice"))

            trigger(LoginAction.Logout) {
                expectState<LoginState.Idle>()
                expectEffect(LoginEffect.NavigateToLogin)
            }
        }

        scenario("Logout from Typing also resets") {
            given(LoginState.Typing(username = "alice", password = "secret"))

            trigger(LoginAction.Logout) {
                expectState<LoginState.Idle>()
                expectEffect(LoginEffect.NavigateToLogin)
            }
        }
    }
}
