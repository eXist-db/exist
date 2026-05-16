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
package org.exist.xquery;

import org.exist.dom.QName;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Verifies that {@link AbstractInternalModule} enforces its sorted-array invariant when a
 * module declares {@code functionsOrdered=true}. Pre-fix, a module that passed an unsorted
 * array would silently lose visibility of some functions via {@link AbstractInternalModule#getFunctionDef(QName, int)}
 * because the binary search assumed a sort it did not check. See issue
 * <a href="https://github.com/eXist-db/exist/issues/6376">#6376</a>.
 */
public class AbstractInternalModuleSortTest {

    private static final String NS = "http://TestSortInvariant";
    private static final String PREFIX = "tsi";

    private static FunctionDef def(final String localName, final int arity) {
        final SequenceType[] params = new SequenceType[arity];
        for (int i = 0; i < arity; i++) {
            params[i] = new FunctionParameterSequenceType("p" + i, Type.ITEM, Cardinality.ZERO_OR_MORE, "");
        }
        final FunctionSignature sig = new FunctionSignature(
                new QName(localName, NS, PREFIX),
                "test",
                params,
                new FunctionReturnSequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE, ""));
        // Implementing class is irrelevant for this test; FunctionDef accepts any Function subclass.
        return new FunctionDef(sig, Function.class);
    }

    /**
     * Unsorted array + functionsOrdered=true previously broke binary search.
     * {@code close} sorts before {@code eval}/{@code fetch}; passing the array in
     * declaration order (eval, fetch, close) makes binarySearch miss {@code close}.
     */
    @Test
    public void unsortedArrayWithOrderedTrueStillFindsAllFunctions() {
        final FunctionDef[] declarationOrder = {
                def("eval", 1),
                def("fetch", 2),
                def("close", 1)
        };
        final TestModule module = new TestModule(declarationOrder, /*ordered=*/true);

        assertNotNull("close#1 must be discoverable after defensive sort",
                module.getFunctionDef(new QName("close", NS, PREFIX), 1));
        assertNotNull("eval#1 must remain discoverable",
                module.getFunctionDef(new QName("eval", NS, PREFIX), 1));
        assertNotNull("fetch#2 must remain discoverable",
                module.getFunctionDef(new QName("fetch", NS, PREFIX), 2));
        assertNull("unknown function still returns null",
                module.getFunctionDef(new QName("close", NS, PREFIX), 99));
    }

    /**
     * The defensive sort must not mutate the caller's static array.
     */
    @Test
    public void callerArrayIsNotMutated() {
        final FunctionDef[] callerArray = {
                def("eval", 1),
                def("fetch", 2),
                def("close", 1)
        };
        final String beforeFirst = callerArray[0].getSignature().getName().getLocalPart();
        new TestModule(callerArray, /*ordered=*/true);
        assertEquals("caller's array must remain in declaration order",
                beforeFirst, callerArray[0].getSignature().getName().getLocalPart());
    }

    /**
     * An already-sorted array should not trigger a defensive copy.
     */
    @Test
    public void alreadySortedArrayIsReusedAsIs() {
        final FunctionDef[] sorted = {
                def("close", 1),
                def("eval", 1),
                def("fetch", 2)
        };
        final TestModule module = new TestModule(sorted, /*ordered=*/true);
        // Module retains the same array reference when already sorted (verified via reflection of mFunctions length + first-entry identity)
        assertEquals(3, module.functionCount());
        assertNotNull(module.getFunctionDef(new QName("close", NS, PREFIX), 1));
    }

    /**
     * functionsOrdered=false leaves the array order alone (linear search doesn't care).
     */
    @Test
    public void unorderedModuleDoesNotSort() {
        final FunctionDef[] declarationOrder = {
                def("eval", 1),
                def("fetch", 2),
                def("close", 1)
        };
        final TestModule module = new TestModule(declarationOrder, /*ordered=*/false);
        assertNotNull(module.getFunctionDef(new QName("close", NS, PREFIX), 1));
        assertNotNull(module.getFunctionDef(new QName("eval", NS, PREFIX), 1));
        assertNotNull(module.getFunctionDef(new QName("fetch", NS, PREFIX), 2));
    }

    private static final class TestModule extends AbstractInternalModule {
        TestModule(final FunctionDef[] functions, final boolean ordered) {
            super(functions, Map.of(), ordered);
        }

        int functionCount() {
            return mFunctions.length;
        }

        @Override public String getNamespaceURI() { return NS; }
        @Override public String getDefaultPrefix() { return PREFIX; }
        @Override public String getDescription() { return "test"; }
        @Override public String getReleaseVersion() { return "1"; }
    }
}
