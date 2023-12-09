package com.kerrrusha.fastfood.util;

import lombok.SneakyThrows;

public class SimulatorUtils {
    private SimulatorUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    @SneakyThrows
    public static void sleepFor(int seconds) {
        Thread.sleep(seconds);
    }
}
