package org.jetbrains.container

import java.util.*

public fun topologicalSort<T>(items: Iterable<T>, dependencies: (T) -> Iterable<T>): List<T> {
    val itemsInProgress = HashSet<T>();
    val completedItems = HashSet<T>();
    val result = ArrayList<T>()

    fun DfsVisit(item: T) {
        if (completedItems.contains(item))
            return;

        if (itemsInProgress.contains(item))
            throw CycleInTopoSortException();

        itemsInProgress.add(item);

        for (dependency in dependencies(item)) {
            DfsVisit(dependency);
        }

        itemsInProgress.remove(item);
        completedItems.add(item);
        result.add(item);
    }

    for (item in items)
        DfsVisit(item)

    return result.reverse();
}

public class CycleInTopoSortException : Exception()