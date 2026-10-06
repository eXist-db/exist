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
package org.exist.storage;

import org.exist.xquery.XPathException;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.Type;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class NativeValueIndexTest {

    public static java.util.Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                { "xs:string", Type.STRING },
                { "xs:int", Type.INT }
        });
    }
    public String typeName;
    public int type;

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    void convertToAtomicNull(String typeName, int type) {
        initNativeValueIndexTest(typeName, type);
        final AtomicValue result = NativeValueIndex.convertToAtomic(type, null);
        assertNull(result);
    }

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    void convertToAtomicEmptyString(String typeName, int type) {
        initNativeValueIndexTest(typeName, type);
        final AtomicValue result = NativeValueIndex.convertToAtomic(type, "");
        assertNull(result);
    }

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    void convertToAtomic(String typeName, int type) throws XPathException {
        initNativeValueIndexTest(typeName, type);
        final String mockValue = "1234567890";
        final AtomicValue result = NativeValueIndex.convertToAtomic(type, mockValue);
        assertEquals(type, result.getType());
        assertEquals(mockValue, result.getStringValue());
    }

    public void initNativeValueIndexTest(String typeName, int type) {
        this.typeName = typeName;
        this.type = type;
    }
}
