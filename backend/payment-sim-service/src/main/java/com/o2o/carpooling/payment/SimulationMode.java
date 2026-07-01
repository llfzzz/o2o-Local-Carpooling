package com.o2o.carpooling.payment;

/**
 * How the Demo Control console should deliver the operator-chosen outcome, all through the signed
 * ingestion pipeline:
 *
 * <ul>
 *   <li>{@code NORMAL} — a single signed callback.</li>
 *   <li>{@code DUPLICATE} — the same event id delivered twice (fresh nonce each), exercising
 *       event-id idempotency; the second is a no-op.</li>
 *   <li>{@code OUT_OF_ORDER} — the chosen outcome followed by a conflicting terminal outcome,
 *       exercising terminal-state protection; the second is ignored.</li>
 * </ul>
 *
 * <p>The "delayed" option is orthogonal: a {@code delaySeconds} back-dates the signed timestamp, so
 * a value beyond the freshness window is rejected by the verifier (visible to the operator).
 */
enum SimulationMode {
    NORMAL,
    DUPLICATE,
    OUT_OF_ORDER
}
