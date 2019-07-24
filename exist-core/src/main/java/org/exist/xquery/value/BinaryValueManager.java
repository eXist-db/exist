package org.exist.xquery.value;

import java.util.function.Predicate;

/**
 * @author <a href="mailto:adam@existsolutions.com">Adam Retter</a>
 */
public interface BinaryValueManager {

    void registerBinaryValueInstance(final BinaryValue binaryValue);

    void runCleanupTasks(final Predicate<Object> predicate);
    default void runCleanupTasks() {
        runCleanupTasks(o -> true);
    }

    String getCacheClass();
}
