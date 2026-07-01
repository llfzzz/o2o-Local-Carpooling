package com.o2o.carpooling.common.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Authoritative liveness sub-check transitions, structured like the session state machine.
 * RETRY_REQUIRED loops back to PENDING; PASSED/FAILED/TIMEOUT are terminal.
 */
public final class LivenessCheckStateMachine {

    private static final Map<LivenessCheckStatus, Set<LivenessCheckStatus>> ALLOWED =
        new EnumMap<>(LivenessCheckStatus.class);

    static {
        ALLOWED.put(LivenessCheckStatus.PENDING, EnumSet.of(
            LivenessCheckStatus.PASSED,
            LivenessCheckStatus.FAILED,
            LivenessCheckStatus.TIMEOUT,
            LivenessCheckStatus.RETRY_REQUIRED));
        ALLOWED.put(LivenessCheckStatus.RETRY_REQUIRED, EnumSet.of(
            LivenessCheckStatus.PENDING));
        ALLOWED.put(LivenessCheckStatus.PASSED, EnumSet.noneOf(LivenessCheckStatus.class));
        ALLOWED.put(LivenessCheckStatus.FAILED, EnumSet.noneOf(LivenessCheckStatus.class));
        ALLOWED.put(LivenessCheckStatus.TIMEOUT, EnumSet.noneOf(LivenessCheckStatus.class));
    }

    public boolean canTransition(LivenessCheckStatus from, LivenessCheckStatus to) {
        return ALLOWED.getOrDefault(from, EnumSet.noneOf(LivenessCheckStatus.class)).contains(to);
    }

    public LivenessCheckStatus require(LivenessCheckStatus from, LivenessCheckStatus to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException("liveness check cannot transition from " + from + " to " + to);
        }
        return to;
    }
}
