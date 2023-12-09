package com.kerrrusha.fastfood.simulation.model;

import lombok.Getter;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Getter
public class ProcessableOrder extends GeneratedOrder {
    private final LocalDateTime processingStartedAt;

    public ProcessableOrder(GeneratedOrder generatedOrder) {
        super(generatedOrder);
        this.processingStartedAt = LocalDateTime.now();
    }

    public long getProcessingTime(ChronoUnit chronoUnit) {
        return chronoUnit.between(processingStartedAt, LocalDateTime.now());
    }
}
