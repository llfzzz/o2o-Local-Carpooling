package com.o2o.carpooling.common.domain;

public record Vehicle(
    String plateNo,
    String model,
    String color,
    int seats
) {
}
