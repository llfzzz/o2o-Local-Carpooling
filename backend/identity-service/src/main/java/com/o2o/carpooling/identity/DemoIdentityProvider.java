package com.o2o.carpooling.identity;

import com.o2o.carpooling.common.domain.IdentityVerificationStatus;
import com.o2o.carpooling.common.domain.LivenessCheckStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Interactive mock KYC provider. Sessions start PENDING (both overall and liveness); the actual
 * outcome is driven later by an operator through the Demo Control endpoint, and the result is
 * delivered asynchronously to the user's Demo inbox — never returned inline at start time. Active
 * when providers.identity.type=demo.
 */
@Component
@ConditionalOnProperty(prefix = "providers.identity", name = "type", havingValue = "demo")
class DemoIdentityProvider implements IdentityVerificationProvider {

    @Override
    public String name() {
        return "demo";
    }

    @Override
    public ProviderVerification start(StartVerificationCommand command) {
        return new ProviderVerification(
            IdentityVerificationStatus.PENDING,
            LivenessCheckStatus.PENDING,
            "demo-idv-" + command.verificationId());
    }
}
