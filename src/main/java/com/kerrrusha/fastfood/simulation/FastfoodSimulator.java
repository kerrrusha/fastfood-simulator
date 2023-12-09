package com.kerrrusha.fastfood.simulation;

import com.kerrrusha.fastfood.util.PriorityQueueV2;
import com.kerrrusha.fastfood.util.TimeInterval;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.kerrrusha.fastfood.util.SimulatorUtils.sleepFor;

@Slf4j
public class FastfoodSimulator {

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

    private final Duration simulationTime = Duration.of(100, ChronoUnit.SECONDS);
    private final boolean generateEntitiesAtSimulationStartup = true;
    private final Random random = new Random();

    private final Deque<GeneratedOrder> ordersHistory = new LinkedList<>();
    private final PriorityQueueV2<GeneratedOrder> acceptedOrdersQueue = new PriorityQueueV2<>(
            Comparator.comparing(generatedOrder -> generatedOrder.getOrder().orderType())
    );
    private final List<GeneratedOrder> declinedOrders = new ArrayList<>();
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

        completeNextOrderAfter = Duration.of(getRandomTime(Tp), ChronoUnit.SECONDS);
    }

    public SimulationResult simulate() {
        LocalDateTime startTime = LocalDateTime.now();
        LocalDateTime endTime = startTime.plus(simulationTime);
        LocalDateTime currentTime;

        log.info("Simulation is started.");
        while ((currentTime = LocalDateTime.now()).isBefore(endTime)) {
            doSimulationStep(startTime, currentTime);

            sleepFor(1);

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
                        tryGenerateDriveInOrder(startTime, currentTime),
                        tryGenerateRegularCashOrder(currentTime)
                )
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toCollection(LinkedList::new));

        if (generatedOrders.isEmpty()) {
            return;
        }

        updateAcceptingOrdersBlocking();
        processGeneratedOrders(generatedOrders);
        processAcceptedOrders(startTime, currentTime);
    }

    private void processAcceptedOrders(LocalDateTime startTime, LocalDateTime currentTime) {
        CompletableOrder lastProcessedOrder = completedOrders.peek();
        GeneratedOrder orderToProcess = acceptedOrdersQueue.poll();
        if (orderToProcess == null) {
            return;
        }

        if ((lastProcessedOrder == null && currentTime.isAfter(startTime.plus(completeNextOrderAfter)))
                || (lastProcessedOrder != null && currentTime.isAfter(lastProcessedOrder.getCompletedAt().plus(completeNextOrderAfter)))) {
            updateCompleteNextOrderAfter();

            completedOrders.add(new CompletableOrder(orderToProcess));
            log.info("Order is completed.");
        }
    }

    private void updateCompleteNextOrderAfter() {
        completeNextOrderAfter = Duration.of(getRandomTime(Tp), ChronoUnit.SECONDS);
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
        generateNextDriveInOrderAfter = Duration.of(getRandomTime(isTimePeek(startTime, currentTime) ? Tdp : Tdd), ChronoUnit.SECONDS);
    }

    private void updateAcceptingOrdersBlocking() {
        if (!orderAcceptingIsBlocked && acceptedOrdersQueue.size() >= Nwmax) {
            log.info("Blocking order accepting...");
            orderAcceptingIsBlocked = true;
        } else if (orderAcceptingIsBlocked && acceptedOrdersQueue.size() < Nwmax) {
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
        generateNextRegularCashOrderAfter = Duration.of(getRandomTime(Tc), ChronoUnit.SECONDS);
    }

    private boolean isTimePeek(LocalDateTime startTime, LocalDateTime currentTime) {
        LocalDateTime timePeekStartTime = startTime.plus(Duration.of((long) (simulationTime.toSeconds() * kStart), ChronoUnit.SECONDS));
        LocalDateTime timePeekEndTime = startTime.plus(Duration.of((long) (simulationTime.toSeconds() * kEnd), ChronoUnit.SECONDS));
        boolean result = timePeekStartTime.isBefore(currentTime) && timePeekEndTime.isAfter(currentTime);
        
        if (result) {
            log.info("Time peek at the moment...");
        }
        return result;
    }

    private int getRandomTime(TimeInterval interval) {
        return random.nextInt(interval.time() - interval.delta(), interval.time() + interval.delta());
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
                .map(CompletableOrder::getCompletionTime)
                .mapToLong(l -> l)
                .average()
                .orElse(-1);
    }

    private double calculateAvgWaitingTimeRegularCashAmount() {
        return completedOrders.stream()
                .filter(completableOrder -> completableOrder.getOrder().orderType() == OrderType.REGULAR_CASH)
                .map(CompletableOrder::getCompletionTime)
                .mapToLong(l -> l)
                .average()
                .orElse(-1);
    }
}
