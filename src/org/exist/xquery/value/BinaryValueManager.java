package org.exist.xquery.value;

/**
 * @author Adam Retter <adam@existsolutions.com>
 */
public interface BinaryValueManager {

    void registerBinaryValueInstance(BinaryValue binaryValue);

    void runCleanupTasks();

    String getCacheClass();
}
