package com.kerrrusha.fastfood.simulation;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.SortedMap;
import java.util.TreeMap;

@Getter
public class SimulationStats {
    private final SortedMap<LocalDateTime, Integer> acceptedOrdersQueueSizeHistory = new TreeMap<>();
}
