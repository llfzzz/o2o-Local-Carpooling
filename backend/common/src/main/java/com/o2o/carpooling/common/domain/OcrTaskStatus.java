package com.o2o.carpooling.common.domain;

/**
 * Lifecycle of an asynchronous OCR task, modeled to match a real OCR vendor (submit returns a
 * task that is polled to completion) so the demo provider can be swapped for a real one without
 * changing the flow. Terminal states: COMPLETED, FAILED.
 */
public enum OcrTaskStatus {
    SUBMITTED,
    PROCESSING,
    COMPLETED,
    FAILED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }
}
