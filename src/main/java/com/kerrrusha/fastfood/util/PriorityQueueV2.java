package com.kerrrusha.fastfood.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PriorityQueueV2<T> {
    private final List<T> elements = new ArrayList<>();
    private final Comparator<T> comparator;

    public PriorityQueueV2(Comparator<T> comparator) {
        this.comparator = comparator;
    }

    public boolean add(T element) {
        return elements.add(element);
    }

    public T poll() {
        if (isEmpty()) {
            throw new UnsupportedOperationException("Can't poll element - queue is empty.");
        }
        trySortElementsByPriority();
        return elements.get(elements.size() - 1);
    }

    private void trySortElementsByPriority() {
        if (comparator == null) {
            return;
        }
        elements.sort(comparator);
    }

    public int size() {
        return elements.size();
    }

    private boolean isEmpty() {
        return elements.isEmpty();
    }
}
