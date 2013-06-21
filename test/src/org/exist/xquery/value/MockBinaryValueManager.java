package org.exist.xquery.value;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.fail;

/**
 *
 * @author aretter
 */

public class MockBinaryValueManager implements BinaryValueManager {

    private List<BinaryValue> values = new ArrayList<BinaryValue>();

    @Override
    public void registerBinaryValueInstance(BinaryValue binaryValue) {
        values.add(binaryValue);
    }

    @Override
    public void runCleanupTasks() {
        for(BinaryValue value : values) {
            try {
                value.close();
            } catch(IOException ex) {
                fail(ex.getMessage());
            }
        }
        values.clear();
    }

    @Override
    public String getCacheClass() {
        return "org.exist.util.io.MemoryFilterInputStreamCache";
    }
}