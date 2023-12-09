package com.kerrrusha.fastfood.simulation.model;

import lombok.Getter;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Getter
public class CompletableOrder extends GeneratedOrder {
    private final LocalDateTime completedAt;

    public CompletableOrder(GeneratedOrder generatedOrder) {
        super(generatedOrder);
        this.completedAt = LocalDateTime.now();
    }

    public long getCompletionTime(ChronoUnit chronoUnit) {
        return chronoUnit.between(getCreatedAt(), completedAt);
    }
}
