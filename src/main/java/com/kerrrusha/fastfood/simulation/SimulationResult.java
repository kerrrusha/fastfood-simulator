package com.kerrrusha.fastfood.simulation;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.text.DecimalFormat;

@Getter
@RequiredArgsConstructor
public class SimulationResult {
    private static final DecimalFormat decimalFormat = new DecimalFormat("#.###");

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
        return "Середня кількість замовлень в очікуванні NwAvg=" + decimalFormat.format(NwAvg) +
                ",\nСередній час очікування замовлень з драйв-ін TwdAvg=" + decimalFormat.format(TwdAvg) +
                ",\nСередній час очікування замовлень з каси TwcAvg=" + decimalFormat.format(TwcAvg) +
                ",\nКількість пропущених замовлень Nf=" + decimalFormat.format(Nf);
    }
}
