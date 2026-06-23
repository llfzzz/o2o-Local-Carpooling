package com.o2o.carpooling.common.domain;

public record SeatInventory(String tripId, int totalSeats, int lockedSeats) {
    public SeatInventory {
        if (tripId == null || tripId.isBlank()) {
            throw new IllegalArgumentException("tripId is required");
        }
        if (totalSeats <= 0) {
            throw new IllegalArgumentException("totalSeats must be positive");
        }
        if (lockedSeats < 0 || lockedSeats > totalSeats) {
            throw new IllegalArgumentException("lockedSeats must be between 0 and totalSeats");
        }
    }

    public int availableSeats() {
        return totalSeats - lockedSeats;
    }
}
