package org.exist.xquery.value;

import java.util.function.Predicate;

/**
 * @author Adam Retter <adam@existsolutions.com>
 */
public interface BinaryValueManager {

    void registerBinaryValueInstance(final BinaryValue binaryValue);

    void runCleanupTasks(final Predicate<Object> predicate);
    default void runCleanupTasks() {
        runCleanupTasks(o -> true);
    }

    String getCacheClass();
}
