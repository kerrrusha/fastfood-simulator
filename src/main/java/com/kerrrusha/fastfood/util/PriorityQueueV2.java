package com.kerrrusha.fastfood.util;


import java.util.ArrayList;
import java.util.List;

public abstract class PriorityQueueV2<T> {
    private final List<T> elements = new ArrayList<>();

    public boolean add(T element) {
        return elements.add(element);
    }

    /**
     * @return first prioritized element if exists, else - first element to out (FIFO)
     */
    public T peek() {
        if (isEmpty()) {
            throw new UnsupportedOperationException("Can't poll element - queue is empty.");
        }

        for (T element : elements) {
            if (isPrioritizedElement(element)) {
                return element;
            }
        }

        return elements.get(0);
    }

    /**
     * This method should mark some elements as more prioritized than others
     */
    public abstract boolean isPrioritizedElement(T element);

    public void remove(T element) {
        elements.remove(element);
    }

    public int size() {
        return elements.size();
    }

    private boolean isEmpty() {
        return elements.isEmpty();
    }
}
