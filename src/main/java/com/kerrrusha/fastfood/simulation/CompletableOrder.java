package com.kerrrusha.fastfood.simulation;

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

    public long getCompletionTime() {
        return ChronoUnit.SECONDS.between(getCreatedAt(), getCompletedAt());
    }
}
