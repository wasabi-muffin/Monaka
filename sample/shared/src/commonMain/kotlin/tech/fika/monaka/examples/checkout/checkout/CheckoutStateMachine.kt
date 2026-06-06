package tech.fika.monaka.examples.checkout.checkout

import tech.fika.monaka.dsl.StateMachine
import tech.fika.monaka.dsl.stateMachine
import tech.fika.monaka.examples.checkout.data.PaymentRepository
import tech.fika.monaka.plugin.LoggingPlugin

class CheckoutStateMachine(
    paymentRepository: PaymentRepository,
) : StateMachine<CheckoutState, CheckoutAction, CheckoutEffect> by stateMachine(builder = {
    initialState(CheckoutState.Idle)

    state<CheckoutState.Idle> {
        on<CheckoutAction.Begin> {
            transition(state.toReviewingOrder(userId = action.userId, items = action.items, total = action.total))
        }
    }

    state<CheckoutState.ReviewingOrder> {
        on<CheckoutAction.SyncCart> {
            transition(state.copy(items = action.items, total = action.total))
        }
        on<CheckoutAction.Confirm> {
            transition(state.toProcessingPayment())
        }
    }

    state<CheckoutState.ProcessingPayment> {
        onEnter {
            runCatching { paymentRepository.charge(state.userId, state.items, state.total) }
                .fold(
                    onSuccess = { orderId -> dispatch(CheckoutAction.PaymentSucceeded(orderId)) },
                    onFailure = { e -> dispatch(CheckoutAction.PaymentFailed(e.message ?: "Payment error")) },
                )
        }
        on<CheckoutAction.PaymentSucceeded> {
            transition(state.toDone(orderId = action.orderId))
            sideEffect(CheckoutEffect.OrderConfirmed(action.orderId))
        }
        on<CheckoutAction.PaymentFailed> {
            transition(state.toPaymentFailed(reason = action.reason))
            sideEffect(CheckoutEffect.ShowPaymentError("Payment declined: ${action.reason}"))
        }
        on<CheckoutAction.SyncCart> {
            transition(state.copy(items = action.items, total = action.total))
        }
    }

    state<CheckoutState.PaymentFailed> {
        on<CheckoutAction.RetryPayment> {
            transition(state.toProcessingPayment())
        }
    }

    state<CheckoutState> {
        on<CheckoutAction.Cancel> { transition(CheckoutState.Idle) }
    }

    install(LoggingPlugin(tag = "Checkout"))
})
