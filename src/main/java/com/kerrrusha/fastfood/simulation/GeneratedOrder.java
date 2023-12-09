package com.kerrrusha.fastfood.simulation;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class GeneratedOrder {
    private final Order order;
    private final LocalDateTime createdAt;

    public GeneratedOrder(OrderType orderType) {
        this.order = new Order(orderType);
        createdAt = LocalDateTime.now();
    }

    public GeneratedOrder(GeneratedOrder other) {
        this.order = other.order;
        this.createdAt = other.createdAt;
    }
}
