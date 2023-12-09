package com.kerrrusha.fastfood.simulation;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class SimulationResult {
    /**
     * Середня кількість замовлень в очікуванні
     */
    private final double NwAvg;

    /**
     * Середній час очікування замовлень з драйв-ін
     */
    private final double TwdAvg;

    /**
     * Середній час очікування замовлень з каси
     */
    private final double TwcAvg;

    /**
     * Кількість пропущених замовлень
     */
    private final int Nf;

    @Override
    public String toString() {
        return "Середня кількість замовлень в очікуванні NwAvg=" + NwAvg +
                ",\n Середній час очікування замовлень з драйв-ін TwdAvg=" + TwdAvg +
                ",\n Середній час очікування замовлень з каси TwcAvg=" + TwcAvg +
                ",\n Кількість пропущених замовлень Nf=" + Nf;
    }
}
