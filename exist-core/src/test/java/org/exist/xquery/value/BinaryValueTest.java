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


import com.googlecode.junittoolbox.ParallelRunner;
import org.easymock.EasyMock;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.isA;
import org.exist.xquery.XPathException;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:adam@existsolutions.com">Adam Retter</a>
 */
@RunWith(ParallelRunner.class)
public class BinaryValueTest {

    @Test
    public void cast_base64_to_base64() throws XPathException {
        final BinaryValueManager binaryValueManager = new MockBinaryValueManager();

        final BinaryValue mockBase64BinaryValue = EasyMock.createMockBuilder(BinaryValue.class)
                .withConstructor(BinaryValueManager.class, BinaryValueType.class)
                .withArgs(binaryValueManager, new Base64BinaryValueType())
                .createMock();

        replay(mockBase64BinaryValue);

        final AtomicValue result = mockBase64BinaryValue.convertTo(Type.BASE64_BINARY);

        verify(mockBase64BinaryValue);

        assertEquals(mockBase64BinaryValue, result);
    }

    @Test
    public void cast_base64_to_hexBinary() throws XPathException {
        final BinaryValueManager binaryValueManager = new MockBinaryValueManager();

        final BinaryValue mockBase64BinaryValue = EasyMock.createMockBuilder(BinaryValue.class)
                .withConstructor(BinaryValueManager.class, BinaryValueType.class)
                .withArgs(binaryValueManager, new Base64BinaryValueType())
                .addMockedMethod("convertTo", BinaryValueType.class)
                .createMock();

        final BinaryValue mockHexBinaryValue = EasyMock.createMockBuilder(BinaryValue.class)
                .withConstructor(BinaryValueManager.class, BinaryValueType.class)
                .withArgs(binaryValueManager, new HexBinaryValueType())
                .createMock();

        expect(mockBase64BinaryValue.convertTo(isA(HexBinaryValueType.class))).andReturn(mockHexBinaryValue);

        replay(mockBase64BinaryValue, mockHexBinaryValue);

        final AtomicValue result = mockBase64BinaryValue.convertTo(Type.HEX_BINARY);

        verify(mockBase64BinaryValue, mockHexBinaryValue);

        assertEquals(mockHexBinaryValue, result);
    }

    @Test
    public void cast_hexBinary_to_hexBase64() throws XPathException {

        final BinaryValueManager binaryValueManager = new MockBinaryValueManager();

        final BinaryValue mockHexBinaryValue = EasyMock.createMockBuilder(BinaryValue.class)
                .withConstructor(BinaryValueManager.class, BinaryValueType.class)
                .withArgs(binaryValueManager, new HexBinaryValueType())
                .addMockedMethod("convertTo", BinaryValueType.class)
                .createMock();

        final BinaryValue mockBase64BinaryValue = EasyMock.createMockBuilder(BinaryValue.class)
                .withConstructor(BinaryValueManager.class, BinaryValueType.class)
                .withArgs(binaryValueManager, new Base64BinaryValueType())
                .createMock();

        expect(mockHexBinaryValue.convertTo(isA(Base64BinaryValueType.class))).andReturn(mockBase64BinaryValue);

        replay(mockHexBinaryValue, mockBase64BinaryValue);

        final AtomicValue result = mockHexBinaryValue.convertTo(Type.BASE64_BINARY);

        verify(mockHexBinaryValue, mockBase64BinaryValue);

        assertEquals(mockBase64BinaryValue, result);
    }
}