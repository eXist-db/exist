package org.exist.xquery.value;

/**
 *
 * @author Adam Retter <adam@existsolutions.com>
 */
public interface BinaryValueManager {

    public void registerBinaryValueInstance(BinaryValue binaryValue);

    public void runCleanupTasks();

    public String getCacheClass();
}
