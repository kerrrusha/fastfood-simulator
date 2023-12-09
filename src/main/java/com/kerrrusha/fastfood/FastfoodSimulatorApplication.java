package com.kerrrusha.fastfood;

import com.kerrrusha.fastfood.simulation.FastfoodSimulator;
import com.kerrrusha.fastfood.simulation.SimulationResult;
import com.kerrrusha.fastfood.util.TimeInterval;

public class FastfoodSimulatorApplication {
    public static void main(String[] args) {
        int Nwmax = 20;
        TimeInterval Tdd = new TimeInterval(30, 10);
        TimeInterval Tdp = new TimeInterval(15, 5);
        TimeInterval Tc = new TimeInterval(10, 5);
        TimeInterval Tp = new TimeInterval(6, 2);
        double kStart = 0.4;
        double kEnd = 0.6;

        SimulationResult simulationResult = new FastfoodSimulator(Nwmax, Tdd, Tdp, Tc, Tp, kStart, kEnd).simulate();
        System.out.println();
        System.out.println(simulationResult);
    }
}