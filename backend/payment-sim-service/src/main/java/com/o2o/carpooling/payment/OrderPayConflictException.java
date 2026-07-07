package com.o2o.carpooling.payment;

/**
 * The order-side state machine refused {@code markPaid} (409) — typically a payment success that
 * raced the order's timeout-cancellation. The money-side fact (intent SUCCEEDED) still stands;
 * the mismatch is a reconciliation/refund concern, which is a real-provider responsibility and
 * out of demo scope.
 */
class OrderPayConflictException extends RuntimeException {

    private final String orderId;

    OrderPayConflictException(String orderId, Throwable cause) {
        super("order refused payment: " + orderId, cause);
        this.orderId = orderId;
    }

    String orderId() {
        return orderId;
    }
}
