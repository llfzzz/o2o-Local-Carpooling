package com.o2o.carpooling.common.domain;

public final class SeatInventoryPolicy {

    public SeatInventory lock(SeatInventory inventory, int requestedSeats) {
        if (requestedSeats <= 0) {
            throw new IllegalArgumentException("requestedSeats must be positive");
        }
        if (inventory.availableSeats() < requestedSeats) {
            throw new IllegalArgumentException("not enough seats for trip " + inventory.tripId());
        }
        return new SeatInventory(inventory.tripId(), inventory.totalSeats(), inventory.lockedSeats() + requestedSeats);
    }
}
