package com.kerrrusha.fastfood.simulation;

import com.kerrrusha.fastfood.simulation.model.CompletableOrder;
import com.kerrrusha.fastfood.simulation.model.GeneratedOrder;
import com.kerrrusha.fastfood.simulation.model.OrderType;
import com.kerrrusha.fastfood.simulation.model.ProcessableOrder;
import com.kerrrusha.fastfood.util.PriorityQueueV2;
import com.kerrrusha.fastfood.util.TimeInterval;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Random;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.time.ZoneId.systemDefault;

@Slf4j
public class FastfoodSimulator {
    private static final ChronoUnit TIME_UNIT = ChronoUnit.MILLIS;
    private static final int MAX_PARALLEL_ORDERS_IN_PROCESS = 1;

    /**
     * Кількість замовлень в очікуванні, при досяганні якої заклад перестає приймати нові замовлення
     */
    private final int Nwmax;

    /**
     * Часовий проміжок надходження замовлень з драйв-ін у звичайні години
     */
    private final TimeInterval Tdd;

    /**
     * Часовий проміжок надходження замовлень з драйв-ін у години пік
     */
    private final TimeInterval Tdp;

    /**
     * Часовий проміжок надходження замовлень з каси
     */
    private final TimeInterval Tc;

    /**
     * Часовий проміжок виконання одного замовлення
     */
    private final TimeInterval Tp;

    /**
     * Коефіцієнт початку години-пік
     */
    private final double kStart;

    /**
     * Коефіцієнт закінчення години-пік
     */
    private final double kEnd;

    private final Duration simulationTime = Duration.of(1000, TIME_UNIT);
    private final boolean generateEntitiesAtSimulationStartup = true;
    private final Random random = new Random();

    private final Deque<GeneratedOrder> ordersHistory = new LinkedList<>();
    private final PriorityQueueV2<GeneratedOrder> acceptedOrdersQueue = new PriorityQueueV2<>() {
        @Override
        public boolean isPrioritizedElement(GeneratedOrder element) {
            return element.getOrder().orderType() == OrderType.DRIVE_IN;
        }
    };
    private final List<GeneratedOrder> declinedOrders = new ArrayList<>();
    private final List<ProcessableOrder> ordersInProcessing = new ArrayList<>();
    private final Deque<CompletableOrder> completedOrders = new LinkedList<>();

    private boolean orderAcceptingIsBlocked = false;
    private Duration generateNextDriveInOrderAfter;
    private Duration generateNextRegularCashOrderAfter;
    private Duration completeNextOrderAfter;

    private final SimulationStats stats = new SimulationStats();

    public FastfoodSimulator(int nwmax, TimeInterval tdd, TimeInterval tdp, TimeInterval tc, TimeInterval tp, double kStart, double kEnd) {
        Nwmax = nwmax;
        Tdd = tdd;
        Tdp = tdp;
        Tc = tc;
        Tp = tp;
        this.kStart = kStart;
        this.kEnd = kEnd;

        completeNextOrderAfter = Duration.of(getRandomTime(Tp), TIME_UNIT);
    }

    public SimulationResult simulate() {
        LocalDateTime startTime = LocalDateTime.now();
        LocalDateTime endTime = startTime.plus(simulationTime);
        LocalDateTime currentTime;

        log.info("Simulation is started.");
        while ((currentTime = LocalDateTime.now()).isBefore(endTime)) {
            doSimulationStep(startTime, currentTime);
            updateStats();
        }
        log.info("Simulation is finished.");

        return new SimulationResult(
                calculateAvgWaitingOrdersAmount(),
                calculateAvgWaitingTimeDriveInAmount(),
                calculateAvgWaitingTimeRegularCashAmount(),
                declinedOrders.size()
        );
    }

    private void updateStats() {
        stats.getAcceptedOrdersQueueSizeHistory().put(LocalDateTime.now(), acceptedOrdersQueue.size());
    }

    private void doSimulationStep(LocalDateTime startTime, LocalDateTime currentTime) {
        Queue<GeneratedOrder> generatedOrders = Stream.of(
                        tryGenerateRegularCashOrder(currentTime),
                        tryGenerateDriveInOrder(startTime, currentTime)
                )
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toCollection(LinkedList::new));

        if (generatedOrders.isEmpty()) {
            return;
        }

        updateAcceptingOrdersBlocking();
        processGeneratedOrders(generatedOrders);
        processAcceptedOrders();
    }

    private void processAcceptedOrders() {
        for (int i = 0; i < ordersInProcessing.size(); i++) {
            ProcessableOrder orderInProcess = ordersInProcessing.get(i);
            if (Duration.of(orderInProcess.getProcessingTime(TIME_UNIT), TIME_UNIT).compareTo(completeNextOrderAfter) > 0) {
                updateCompleteNextOrderAfter();
                ordersInProcessing.remove(orderInProcess);
                completedOrders.add(new CompletableOrder(orderInProcess));
                log.info(orderInProcess.getOrder().orderType() + " order is completed.");
            }
        }

        acceptOrdersToProcessingIfPossible();
    }

    private void acceptOrdersToProcessingIfPossible() {
        GeneratedOrder orderToProcess = acceptedOrdersQueue.peek();
        if (orderToProcess == null) {
            return;
        }

        if (ordersInProcessing.size() >= MAX_PARALLEL_ORDERS_IN_PROCESS) {
            return;
        }

        acceptedOrdersQueue.remove(orderToProcess);
        ordersInProcessing.add(new ProcessableOrder(orderToProcess));
    }

    private void updateCompleteNextOrderAfter() {
        completeNextOrderAfter = Duration.of(getRandomTime(Tp), TIME_UNIT);
    }

    private void processGeneratedOrders(Queue<GeneratedOrder> generatedOrders) {
        while (!generatedOrders.isEmpty()) {
            GeneratedOrder order = generatedOrders.poll();
            if (orderAcceptingIsBlocked) {
                declinedOrders.add(order);
            } else {
                acceptedOrdersQueue.add(order);
            }
        }
    }

    private Optional<GeneratedOrder> tryGenerateDriveInOrder(LocalDateTime startTime, LocalDateTime currentTime) {
        GeneratedOrder lastDriveInOrder = getDriveInNewOrdersDeque().peekLast();

        if (lastDriveInOrder == null && !generateEntitiesAtSimulationStartup) {
            updateGenerateNextDriveInOrderAfter(startTime, currentTime);
            return Optional.empty();
        }

        if (lastDriveInOrder == null
                || currentTime.isAfter(lastDriveInOrder.getCreatedAt().plus(generateNextDriveInOrderAfter))) {
            updateGenerateNextDriveInOrderAfter(startTime, currentTime);
            log.info("Generating drive-in order...");
            GeneratedOrder newOrder = new GeneratedOrder(OrderType.DRIVE_IN);
            ordersHistory.add(newOrder);
            return Optional.of(newOrder);
        }

        return Optional.empty();
    }

    private void updateGenerateNextDriveInOrderAfter(LocalDateTime startTime, LocalDateTime currentTime) {
        generateNextDriveInOrderAfter = Duration.of(getRandomTime(isTimePeek(startTime, currentTime) ? Tdp : Tdd), TIME_UNIT);
    }

    private void updateAcceptingOrdersBlocking() {
        if (!orderAcceptingIsBlocked && acceptedOrdersQueue.size() >= Nwmax) {
            log.info("Blocking order accepting...");
            orderAcceptingIsBlocked = true;
        } else if (orderAcceptingIsBlocked && acceptedOrdersQueue.isEmpty()) {
            log.info("Un-blocking order accepting...");
            orderAcceptingIsBlocked = false;
        }
    }

    private Optional<GeneratedOrder> tryGenerateRegularCashOrder(LocalDateTime currentTime) {
        GeneratedOrder lastRegularCashOrder = getRegularCashNewOrdersDeque().peekLast();

        if (lastRegularCashOrder == null && !generateEntitiesAtSimulationStartup) {
            updateGenerateNextRegularCashOrderAfter();
            return Optional.empty();
        }

        if (lastRegularCashOrder == null
                || currentTime.isAfter(lastRegularCashOrder.getCreatedAt().plus(generateNextRegularCashOrderAfter))) {
            updateGenerateNextRegularCashOrderAfter();
            log.info("Generating regular cash order...");
            GeneratedOrder newOrder = new GeneratedOrder(OrderType.REGULAR_CASH);
            ordersHistory.add(newOrder);
            return Optional.of(newOrder);
        }

        return Optional.empty();
    }

    private void updateGenerateNextRegularCashOrderAfter() {
        generateNextRegularCashOrderAfter = Duration.of(getRandomTime(Tc), TIME_UNIT);
    }

    private boolean isTimePeek(LocalDateTime startTime, LocalDateTime currentTime) {
        LocalDateTime endTime = startTime.plus(simulationTime);

        long startTimeEpochMilli = startTime.atZone(systemDefault()).toInstant().toEpochMilli();
        long endTimeEpochMilli = endTime.atZone(systemDefault()).toInstant().toEpochMilli();

        LocalDateTime timePeekStartTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli((long) (startTimeEpochMilli + (endTimeEpochMilli - startTimeEpochMilli) * kStart)),
                systemDefault()
        );
        LocalDateTime timePeekEndTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli((long) (startTimeEpochMilli + (endTimeEpochMilli - startTimeEpochMilli) * kEnd)),
                systemDefault()
        );

        boolean result = timePeekStartTime.isBefore(currentTime) && timePeekEndTime.isAfter(currentTime);
        
        if (result) {
            log.info("Time peek at the moment...");
        }
        return result;
    }

    private long getRandomTime(TimeInterval interval) {
        return (long) random.nextDouble(interval.time() - interval.delta(), interval.time() + interval.delta());
    }

    private LinkedList<GeneratedOrder> getDriveInNewOrdersDeque() {
        return filterNewOrdersByOrderType(OrderType.DRIVE_IN);
    }

    private LinkedList<GeneratedOrder> getRegularCashNewOrdersDeque() {
        return filterNewOrdersByOrderType(OrderType.REGULAR_CASH);
    }

    private LinkedList<GeneratedOrder> filterNewOrdersByOrderType(OrderType orderType) {
        return ordersHistory
                .stream()
                .filter(e -> e.getOrder().orderType() == orderType)
                .collect(Collectors.toCollection(LinkedList::new));
    }

    private double calculateAvgWaitingOrdersAmount() {
        return (double) stats.getAcceptedOrdersQueueSizeHistory().values().stream().mapToInt(i -> i).sum()
                / stats.getAcceptedOrdersQueueSizeHistory().size();
    }

    private double calculateAvgWaitingTimeDriveInAmount() {
        return completedOrders.stream()
                .filter(completableOrder -> completableOrder.getOrder().orderType() == OrderType.DRIVE_IN)
                .map(completableOrder -> completableOrder.getCompletionTime(TIME_UNIT))
                .mapToLong(l -> l)
                .average()
                .orElse(-1);
    }

    private double calculateAvgWaitingTimeRegularCashAmount() {
        return completedOrders.stream()
                .filter(completableOrder -> completableOrder.getOrder().orderType() == OrderType.REGULAR_CASH)
                .map(completableOrder -> completableOrder.getCompletionTime(TIME_UNIT))
                .mapToLong(l -> l)
                .average()
                .orElse(-1);
    }

    @Getter
    private static class SimulationStats {
        private final SortedMap<LocalDateTime, Integer> acceptedOrdersQueueSizeHistory = new TreeMap<>();
    }
}
