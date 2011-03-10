package org.exist.xquery.value;


import org.easymock.classextension.EasyMock;
import static org.easymock.classextension.EasyMock.replay;
import static org.easymock.classextension.EasyMock.verify;
import static org.easymock.classextension.EasyMock.expect;
import static org.easymock.classextension.EasyMock.isA;
import org.exist.xquery.XPathException;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * @author Adam Retter <adam@existsolutions.com>
 */
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