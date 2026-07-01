package com.o2o.carpooling.common.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class IdentityVerificationStateMachineTest {

    private final IdentityVerificationStateMachine stateMachine = new IdentityVerificationStateMachine();

    @Test
    void allowsPendingToEveryOutcome() {
        assertThat(stateMachine.canTransition(IdentityVerificationStatus.PENDING, IdentityVerificationStatus.APPROVED)).isTrue();
        assertThat(stateMachine.canTransition(IdentityVerificationStatus.PENDING, IdentityVerificationStatus.REJECTED)).isTrue();
        assertThat(stateMachine.canTransition(IdentityVerificationStatus.PENDING, IdentityVerificationStatus.TIMEOUT)).isTrue();
        assertThat(stateMachine.canTransition(IdentityVerificationStatus.PENDING, IdentityVerificationStatus.RETRY_REQUIRED)).isTrue();
    }

    @Test
    void retryLoopsBackToPendingOnly() {
        assertThat(stateMachine.canTransition(IdentityVerificationStatus.RETRY_REQUIRED, IdentityVerificationStatus.PENDING)).isTrue();
        assertThat(stateMachine.canTransition(IdentityVerificationStatus.RETRY_REQUIRED, IdentityVerificationStatus.APPROVED)).isFalse();
    }

    @Test
    void terminalStatesCannotTransition() {
        assertThat(IdentityVerificationStatus.APPROVED.isTerminal()).isTrue();
        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> stateMachine.require(IdentityVerificationStatus.APPROVED, IdentityVerificationStatus.PENDING));
    }
}
