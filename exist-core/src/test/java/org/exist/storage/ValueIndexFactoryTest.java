package org.exist.storage;

import java.math.BigDecimal;
import java.nio.ByteBuffer;

import org.exist.EXistException;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.exist.storage.btree.Value;
import org.exist.xquery.value.DecimalValue;
import org.junit.Ignore;
import org.junit.Test;


public class ValueIndexFactoryTest {

    @Ignore
    @Test
    public void negativeNumbersComparison() {

        // -8.6...
        final ByteBuffer data1 = encode(-8.612328);

        // 1.0
        final ByteBuffer data2 = encode(1.0);

//        // print data
//        print(data1);
//        print(data2);

        // -8.6 < 1.0
        assertTrue(data1.compareTo(data2) <= -1);

        // -8.6 < 1.0
        assertEquals("v1 < v2", -1, new Value(data1.array()).compareTo(new Value(data2.array())));
    }

    @Test
    public void numbersComparison() {

        // -8.6...
        final ByteBuffer data1 = encode(8.612328);

        // 1.0
        final ByteBuffer data2 = encode(1.0);

//        // print data
//        print(data1);
//        print(data2);

        // -8.6 < 1.0
        assertTrue(data1.compareTo(data2) >= 1);

        // -8.6 < 1.0
        assertEquals("v1 < v2", 1, new Value(data1.array()).compareTo(new Value(data2.array())));
    }

    @Ignore
    @Test
    public void negativeNumbersComparison2() {

        // -8.6...
        final ByteBuffer data1 = encode(8.612328);

        // 1.0
        final ByteBuffer data2 = encode(-1.0);

//        // print data
//        print(data1);
//        print(data2);

        // -8.6 < 1.0
        assertTrue(data1.compareTo(data2) >= 1);

        // -8.6 < 1.0
        assertEquals("v1 < v2", 1, new Value(data1.array()).compareTo(new Value(data2.array())));
    }

    @Test
    public void roundTripDecimal() throws EXistException {
        BigDecimal dec = new BigDecimal("123456789123456789123456789123456789.123456789123456789123456789");

        byte data[] = ValueIndexFactory.serialize(new DecimalValue(dec), 0);

        Indexable value = ValueIndexFactory.deserialize(data, 0, data.length);
        assertTrue(value instanceof DecimalValue);

        assertEquals(dec, ((DecimalValue)value).getValue());
    }
	
    private ByteBuffer encode(final double number) {
        final ByteBuffer buf = ByteBuffer.allocate(8);
        buf.putDouble(number);
        buf.flip();
        return buf;
    }
}
