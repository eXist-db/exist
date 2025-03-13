/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
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
            for (final BinaryValue bv : values) {
                try {
                    if (predicate.test(bv)) {
                        bv.close();
                        if (removable == null) {
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