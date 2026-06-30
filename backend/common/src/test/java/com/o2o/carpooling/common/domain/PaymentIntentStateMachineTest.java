package com.o2o.carpooling.common.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentIntentStateMachineTest {

    private final PaymentIntentStateMachine stateMachine = new PaymentIntentStateMachine();

    @Test
    void allowsForwardTransitions() {
        assertThat(stateMachine.canTransition(PaymentIntentStatus.REQUIRES_PAYMENT, PaymentIntentStatus.AUTHORIZED)).isTrue();
        assertThat(stateMachine.canTransition(PaymentIntentStatus.REQUIRES_PAYMENT, PaymentIntentStatus.SUCCEEDED)).isTrue();
        assertThat(stateMachine.canTransition(PaymentIntentStatus.AUTHORIZED, PaymentIntentStatus.SUCCEEDED)).isTrue();
        assertThat(stateMachine.canTransition(PaymentIntentStatus.AUTHORIZED, PaymentIntentStatus.FAILED)).isTrue();
    }

    @Test
    void rejectsTransitionsOutOfTerminalStates() {
        assertThat(stateMachine.canTransition(PaymentIntentStatus.SUCCEEDED, PaymentIntentStatus.FAILED)).isFalse();
        assertThat(stateMachine.canTransition(PaymentIntentStatus.CANCELED, PaymentIntentStatus.SUCCEEDED)).isFalse();
        assertThatThrownBy(() -> stateMachine.require(PaymentIntentStatus.SUCCEEDED, PaymentIntentStatus.SUCCEEDED))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void marksTerminalStates() {
        assertThat(PaymentIntentStatus.SUCCEEDED.isTerminal()).isTrue();
        assertThat(PaymentIntentStatus.FAILED.isTerminal()).isTrue();
        assertThat(PaymentIntentStatus.CANCELED.isTerminal()).isTrue();
        assertThat(PaymentIntentStatus.EXPIRED.isTerminal()).isTrue();
        assertThat(PaymentIntentStatus.REQUIRES_PAYMENT.isTerminal()).isFalse();
        assertThat(PaymentIntentStatus.AUTHORIZED.isTerminal()).isFalse();
    }
}
