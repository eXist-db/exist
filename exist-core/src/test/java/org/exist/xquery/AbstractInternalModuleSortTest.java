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
 * Verifies that {@link AbstractInternalModule} always sorts its function table by
 * {@link FunctionId} order, regardless of the caller's declaration order. Pre-fix
 * (#6376 / #6378), modules that opted into binary search via {@code functionsOrdered=true}
 * had to pre-sort their array themselves; a forgotten sort silently broke function lookup
 * (the function appeared in {@code util:registered-functions} but was unreachable via
 * direct call or {@code fn:function-lookup}). The constructor now always sorts, so this
 * footgun is structurally impossible.
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
        return new FunctionDef(sig, Function.class);
    }

    /**
     * Unsorted declaration order — {@code close} sorts before {@code eval}/{@code fetch} —
     * must still find all functions via binary search. This is the regression case from
     * <a href="https://github.com/eXist-db/exist/issues/6376">#6376</a>.
     */
    @Test
    public void unsortedDeclarationOrderStillFindsAllFunctions() {
        final FunctionDef[] declarationOrder = {
                def("eval", 1),
                def("fetch", 2),
                def("close", 1)
        };
        final TestModule module = new TestModule(declarationOrder);

        assertNotNull("close#1 must be discoverable",
                module.getFunctionDef(new QName("close", NS, PREFIX), 1));
        assertNotNull("eval#1 must be discoverable",
                module.getFunctionDef(new QName("eval", NS, PREFIX), 1));
        assertNotNull("fetch#2 must be discoverable",
                module.getFunctionDef(new QName("fetch", NS, PREFIX), 2));
        assertNull("unknown function still returns null",
                module.getFunctionDef(new QName("close", NS, PREFIX), 99));
    }

    /**
     * The defensive sort must not mutate the caller's {@code static final} array.
     */
    @Test
    public void callerArrayIsNotMutated() {
        final FunctionDef[] callerArray = {
                def("eval", 1),
                def("fetch", 2),
                def("close", 1)
        };
        final String beforeFirst = callerArray[0].getSignature().getName().getLocalPart();
        new TestModule(callerArray);
        assertEquals("caller's array must remain in declaration order",
                beforeFirst, callerArray[0].getSignature().getName().getLocalPart());
    }

    /**
     * Already-sorted arrays still work correctly (verifies the always-sort path doesn't
     * break the common case where the caller happened to declare in order).
     */
    @Test
    public void alreadySortedArrayFindsAllFunctions() {
        final FunctionDef[] sorted = {
                def("close", 1),
                def("eval", 1),
                def("fetch", 2)
        };
        final TestModule module = new TestModule(sorted);
        assertNotNull(module.getFunctionDef(new QName("close", NS, PREFIX), 1));
        assertNotNull(module.getFunctionDef(new QName("eval", NS, PREFIX), 1));
        assertNotNull(module.getFunctionDef(new QName("fetch", NS, PREFIX), 2));
    }

    /**
     * Same qname at different arities — secondary sort key (arity) is honored.
     */
    @Test
    public void sameQnameDifferentAritiesAllFound() {
        final FunctionDef[] mixed = {
                def("scan", 3),
                def("scan", 1),
                def("scan", 2)
        };
        final TestModule module = new TestModule(mixed);
        assertNotNull(module.getFunctionDef(new QName("scan", NS, PREFIX), 1));
        assertNotNull(module.getFunctionDef(new QName("scan", NS, PREFIX), 2));
        assertNotNull(module.getFunctionDef(new QName("scan", NS, PREFIX), 3));
    }

    /**
     * Backwards-compat check: an upstream module that still uses the pre-#6378
     * 3-arg constructor (with either flag value) must continue to compile and
     * produce a functioning module. The flag is documented as ignored.
     */
    @Test
    public void deprecatedThreeArgConstructorStillWorks() {
        final FunctionDef[] unsorted = {
                def("eval", 1),
                def("fetch", 2),
                def("close", 1)
        };
        // Both legacy call shapes must compile and produce an equivalent module.
        final LegacyTestModule withTrue = new LegacyTestModule(unsorted, true);
        final LegacyTestModule withFalse = new LegacyTestModule(unsorted, false);

        for (final LegacyTestModule m : new LegacyTestModule[]{withTrue, withFalse}) {
            assertNotNull("close#1 must be found via the legacy 3-arg ctor",
                    m.getFunctionDef(new QName("close", NS, PREFIX), 1));
            assertNotNull("eval#1 must be found via the legacy 3-arg ctor",
                    m.getFunctionDef(new QName("eval", NS, PREFIX), 1));
            assertNotNull("fetch#2 must be found via the legacy 3-arg ctor",
                    m.getFunctionDef(new QName("fetch", NS, PREFIX), 2));
        }
    }

    private static final class TestModule extends AbstractInternalModule {
        TestModule(final FunctionDef[] functions) {
            super(functions, Map.of());
        }

        @Override public String getNamespaceURI() { return NS; }
        @Override public String getDefaultPrefix() { return PREFIX; }
        @Override public String getDescription() { return "test"; }
        @Override public String getReleaseVersion() { return "1"; }
    }

    /**
     * Mimics an external module compiled against pre-#6378 eXist that uses
     * the legacy 3-arg constructor signature. The {@code @SuppressWarnings} is
     * intentional — this class exists to exercise the deprecation path.
     */
    @SuppressWarnings({"deprecation", "removal"})
    private static final class LegacyTestModule extends AbstractInternalModule {
        LegacyTestModule(final FunctionDef[] functions, final boolean functionsOrdered) {
            super(functions, Map.of(), functionsOrdered);
        }

        @Override public String getNamespaceURI() { return NS; }
        @Override public String getDefaultPrefix() { return PREFIX; }
        @Override public String getDescription() { return "legacy test"; }
        @Override public String getReleaseVersion() { return "1"; }
    }
}
