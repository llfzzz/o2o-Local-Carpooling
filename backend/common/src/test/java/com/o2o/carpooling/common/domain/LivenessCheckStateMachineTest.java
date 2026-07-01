package com.o2o.carpooling.common.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class LivenessCheckStateMachineTest {

    private final LivenessCheckStateMachine stateMachine = new LivenessCheckStateMachine();

    @Test
    void allowsPendingToEveryOutcome() {
        assertThat(stateMachine.canTransition(LivenessCheckStatus.PENDING, LivenessCheckStatus.PASSED)).isTrue();
        assertThat(stateMachine.canTransition(LivenessCheckStatus.PENDING, LivenessCheckStatus.FAILED)).isTrue();
        assertThat(stateMachine.canTransition(LivenessCheckStatus.PENDING, LivenessCheckStatus.TIMEOUT)).isTrue();
        assertThat(stateMachine.canTransition(LivenessCheckStatus.PENDING, LivenessCheckStatus.RETRY_REQUIRED)).isTrue();
    }

    @Test
    void retryLoopsBackToPendingOnly() {
        assertThat(stateMachine.canTransition(LivenessCheckStatus.RETRY_REQUIRED, LivenessCheckStatus.PENDING)).isTrue();
        assertThat(stateMachine.canTransition(LivenessCheckStatus.RETRY_REQUIRED, LivenessCheckStatus.PASSED)).isFalse();
    }

    @Test
    void terminalStatesCannotTransition() {
        assertThat(LivenessCheckStatus.PASSED.isTerminal()).isTrue();
        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> stateMachine.require(LivenessCheckStatus.FAILED, LivenessCheckStatus.PENDING));
    }
}
