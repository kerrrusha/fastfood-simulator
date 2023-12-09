package com.kerrrusha.fastfood.simulation.model;

import java.util.Comparator;

public enum OrderType implements Comparator<OrderType> {
    REGULAR_CASH,
    DRIVE_IN;

    @Override
    public int compare(OrderType order1, OrderType order2) {
        if (order1 == DRIVE_IN) {
            return 1;
        }
        if (order2 == DRIVE_IN) {
            return -1;
        }
        return order1.compareTo(order2);
    }
}
