package com.o2o.carpooling.common.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeatInventoryPolicyTest {

    private final SeatInventoryPolicy policy = new SeatInventoryPolicy();

    @Test
    void locksRequestedSeatsWhenInventoryIsAvailable() {
        SeatInventory inventory = new SeatInventory("trip-1", 4, 1);

        SeatInventory locked = policy.lock(inventory, 2);

        assertThat(locked.lockedSeats()).isEqualTo(3);
        assertThat(locked.availableSeats()).isEqualTo(1);
    }

    @Test
    void rejectsOverbooking() {
        SeatInventory inventory = new SeatInventory("trip-2", 2, 1);

        assertThatThrownBy(() -> policy.lock(inventory, 2))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not enough seats");
    }
}
