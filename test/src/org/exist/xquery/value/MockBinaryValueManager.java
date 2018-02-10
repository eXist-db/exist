package org.exist.xquery.value;

import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;

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
    public void runCleanupTasks(final Predicate<Object> predicate) {
        if (values != null) {
            List<BinaryValue> removable = null;
            for(final Iterator<BinaryValue> iterator = values.iterator(); iterator.hasNext();) {
                final BinaryValue bv = iterator.next();
                try {
                    if (predicate.test(bv)) {
                        bv.close();
                        if(removable == null) {
                            removable = new ArrayList<>();
                        }
                        removable.add(bv);
                    }
                } catch (final IOException e) {
                    fail(e.getMessage());
                }
            }

            if(removable != null) {
                for(final BinaryValue bv : removable) {
                    values.remove(bv);
                }
            }
        }
    }

    @Override
    public String getCacheClass() {
        return "org.exist.util.io.MemoryFilterInputStreamCache";
    }
}