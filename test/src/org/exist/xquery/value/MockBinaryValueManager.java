package org.exist.xquery.value;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import static org.junit.Assert.fail;

/**
 *
 * @author aretter
 */
public class MockBinaryValueManager implements BinaryValueManager {

    private Deque<BinaryValue> values = new ArrayDeque<>();

    @Override
    public void registerBinaryValueInstance(final BinaryValue binaryValue) {
        values.push(binaryValue);
    }

    @Override
    public void runCleanupTasks() {
        while(!values.isEmpty()) {
            final BinaryValue value = values.pop();
            try {
                value.close();
            } catch(final IOException e) {
                fail(e.getMessage());
            }
        }
    }

    @Override
    public String getCacheClass() {
        return "org.exist.util.io.MemoryFilterInputStreamCache";
    }
}