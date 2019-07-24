/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2010 The eXist Team
 *
 *  http://exist-db.org
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.storage;

import java.math.BigDecimal;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.storage.btree.Value;
import org.exist.util.ByteConversion;
import org.exist.util.UTF8;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.*;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author wolf
 */
// TODO : rename as NativeIndexValueFactory ? -pb
public class ValueIndexFactory {

    private static final int LENGTH_VALUE_TYPE = 1; // sizeof byte

    public final static Indexable deserialize(final byte[] data, final int start, final int len) throws EXistException {
        final int type = data[start];
        // TODO : improve deserialization (use static methods in the org.exist.xquery.Value package
        /* xs:string */
        if (Type.subTypeOf(type, Type.STRING)) {
            final String s = new String(data, start + (ValueIndexFactory.LENGTH_VALUE_TYPE), len - (ValueIndexFactory.LENGTH_VALUE_TYPE), UTF_8);
            return new StringValue(s);
        }
        /* xs:dateTime */
        else if (Type.subTypeOf(type, Type.DATE_TIME)) {
            try {
                final XMLGregorianCalendar xmlutccal =
                        DatatypeFactory.newInstance().newXMLGregorianCalendar(
                                ByteConversion.byteToIntH(data, start + 1),
                                data[start + 5],
                                data[start + 6],
                                data[start + 7],
                                data[start + 8],
                                data[start + 9],
                                ByteConversion.byteToShortH(data, start + 10),
                                0);
                return new DateTimeValue(xmlutccal);
            } catch (final DatatypeConfigurationException dtce) {
                throw new EXistException("Could not deserialize xs:dateTime data type" +
                        "for range index key: " + Type.getTypeName(type) + " - " + dtce.getMessage());
            }
        }
        /* xs:date */
        else if (Type.subTypeOf(type, Type.DATE)) {
            try {
                final XMLGregorianCalendar xmlutccal =
                        DatatypeFactory.newInstance().newXMLGregorianCalendarDate(
                                ByteConversion.byteToIntH(data, start + 1),
                                data[start + 5],
                                data[start + 6],
                                0);
                return new DateValue(xmlutccal);
            } catch (final DatatypeConfigurationException dtce) {
                throw new EXistException("Could not deserialize xs:date data type" +
                        " for range index key: " + Type.getTypeName(type) + " - " + dtce.getMessage());
            } catch (final XPathException xpe) {
                throw new EXistException("Could not deserialize xs:date data type" +
                        " for range index key: " + Type.getTypeName(type) + " - " + xpe.getMessage());
            }
        }
        /* xs:integer */
        else if (Type.subTypeOf(type, Type.INTEGER)) {
            return new IntegerValue(ByteConversion.byteToLong(data, start +
                    (ValueIndexFactory.LENGTH_VALUE_TYPE)) ^ 0x8000000000000000L);
        }
        /* xs:double */
        else if (type == Type.DOUBLE) {
            final long bits = ByteConversion.byteToLong(data, start +
                    (ValueIndexFactory.LENGTH_VALUE_TYPE)) ^ 0x8000000000000000L;
            final double d = Double.longBitsToDouble(bits);
            return new DoubleValue(d);
        }
        /* xs:float */
        else if (type == Type.FLOAT) {
            final int bits = ByteConversion.byteToInt(data, start +
                    (ValueIndexFactory.LENGTH_VALUE_TYPE)) ^ 0x80000000;
            final float f = Float.intBitsToFloat(bits);
            return new FloatValue(f);
        }
        /* xs:decimal */
        else if (type == Type.DECIMAL) {
            //actually loaded from string data due to the uncertain length
            final String s = new String(data, start + (ValueIndexFactory.LENGTH_VALUE_TYPE), len - (ValueIndexFactory.LENGTH_VALUE_TYPE), UTF_8);
            return new DecimalValue(new BigDecimal(s));
        }
        /* xs:boolean */
        else if (type == Type.BOOLEAN) {
            return new BooleanValue(data[start + (ValueIndexFactory.LENGTH_VALUE_TYPE)] == 1);
        }
        /* unknown! */
        else {
            throw new EXistException("Unknown data type for deserialization: " + Type.getTypeName(type));
        }
    }

    public final static byte[] serialize(final Indexable value, final int offset) throws EXistException {
        // TODO : refactor (only strings are case sensitive)
        return serialize(value, offset, true);
    }

    public final static byte[] serialize(final Indexable value, final int offset, final boolean caseSensitive) throws EXistException {
        /* xs:string */
        if (Type.subTypeOf(value.getType(), Type.STRING)) {
            final String val = caseSensitive ?
                    ((StringValue) value).getStringValue() :
                    ((StringValue) value).getStringValue().toLowerCase();
            final byte[] data = new byte[offset + ValueIndexFactory.LENGTH_VALUE_TYPE + UTF8.encoded(val)];
            data[offset] = (byte) value.getType(); // TODO: cast to byte is not safe
            UTF8.encode(val, data, offset + ValueIndexFactory.LENGTH_VALUE_TYPE);
            return data;
        }
        /* xs:dateTime */
        else if (Type.subTypeOf(value.getType(), Type.DATE_TIME)) {
            final XMLGregorianCalendar utccal = ((AbstractDateTimeValue) value).calendar.normalize();
            final byte[] data = new byte[offset + 12]; // allocate an appropriately sized
            data[offset] = (byte) Type.DATE_TIME; // put the type in the byte array
            ByteConversion.intToByteH(utccal.getYear(), data, offset + 1);
            data[offset + 5] = (byte) utccal.getMonth();
            data[offset + 6] = (byte) utccal.getDay();
            data[offset + 7] = (byte) utccal.getHour();
            data[offset + 8] = (byte) utccal.getMinute();
            data[offset + 9] = (byte) utccal.getSecond();
            final int ms = utccal.getMillisecond();
            ByteConversion.shortToByteH((short) (ms == DatatypeConstants.FIELD_UNDEFINED ? 0 : ms),
                    data, offset + 10);
            return (data); // return the byte array
        }
        /* xs:date */
        else if (Type.subTypeOf(value.getType(), Type.DATE)) {
            final XMLGregorianCalendar utccal = ((AbstractDateTimeValue) value).calendar.normalize();
            final byte[] data = new byte[offset + 7]; // allocate an appropriately sized
            data[offset] = (byte) Type.DATE;
            ByteConversion.intToByteH(utccal.getYear(), data, offset + 1);
            data[offset + 5] = (byte) utccal.getMonth();
            data[offset + 6] = (byte) utccal.getDay();
            return data;
        }
        /* xs:integer */
        else if (Type.subTypeOf(value.getType(), Type.INTEGER)) {
            final byte[] data = new byte[offset + ValueIndexFactory.LENGTH_VALUE_TYPE + 8];
            data[offset] = (byte) Type.INTEGER;
            final long l = ((IntegerValue) value).getValue() - Long.MIN_VALUE;
            ByteConversion.longToByte(l, data, offset + ValueIndexFactory.LENGTH_VALUE_TYPE);
            return data;
        }
        /* xs:double */
        else if (value.getType() == Type.DOUBLE) {
            final byte[] data = new byte[offset + ValueIndexFactory.LENGTH_VALUE_TYPE + 8];
            data[offset] = (byte) Type.DOUBLE;
            final long bits = Double.doubleToLongBits(((DoubleValue) value).getValue()) ^ 0x8000000000000000L;
            ByteConversion.longToByte(bits, data, offset + ValueIndexFactory.LENGTH_VALUE_TYPE);
            return data;
        }
        /* xs:float */
        else if (value.getType() == Type.FLOAT) {
            final byte[] data = new byte[offset + ValueIndexFactory.LENGTH_VALUE_TYPE + 4];
            data[offset] = (byte) Type.FLOAT;
            final int bits = Float.floatToIntBits(((FloatValue) value).getValue()) ^ 0x80000000;
            ByteConversion.intToByteH(bits, data, offset + ValueIndexFactory.LENGTH_VALUE_TYPE);
            return data;
        }
        /* xs:boolean */
        else if (value.getType() == Type.BOOLEAN) {
            final byte[] data = new byte[offset + ValueIndexFactory.LENGTH_VALUE_TYPE + 1];
            data[offset] = Type.BOOLEAN;
            data[offset + ValueIndexFactory.LENGTH_VALUE_TYPE] = (byte) (((BooleanValue) value).getValue() ? 1 : 0);
            return data;
        } else if (value.getType() == Type.DECIMAL) {
            //actually stored as string data due to variable length
            final BigDecimal dec = ((DecimalValue) value).getValue();
            final String val = dec.toString();
            final byte[] data = new byte[offset + ValueIndexFactory.LENGTH_VALUE_TYPE + UTF8.encoded(val)];
            data[offset] = (byte) value.getType(); // TODO: cast to byte is not safe
            UTF8.encode(val, data, offset + ValueIndexFactory.LENGTH_VALUE_TYPE);
            return data;
        }
        /* unknown! */
        else {
            throw new EXistException("Unknown data type for serialization: " + Type.getTypeName(value.getType()));
        }
    }

    public static void main(final String[] args) {
        try {
            //******** DateTimeValue ********
            final DateTimeValue dtv = new DateTimeValue("0753-04-21T01:00:00+01:00");
            final byte[] b1 = ValueIndexFactory.serialize(dtv, 0);
            print(dtv, b1);
            final DateTimeValue dtv2 = new DateTimeValue("1960-03-19T19:03:59.782+01:00");
            final byte[] b2 = ValueIndexFactory.serialize(dtv2, 0);
            print(dtv2, b2);
            System.out.println(new Value(b1).compareTo(new Value(b2)));
            final DateTimeValue dtv2_ = (DateTimeValue) ValueIndexFactory.deserialize(b2, 0, b2.length);
            if (!dtv2.equals(dtv2_)) {
                System.out.println("ERROR! " + dtv2.toString() + " ne " + dtv2_.toString());
            }
            //******** DateValue ********
            final DateValue dv = new DateValue("1960-03-19Z");
            final byte[] b3 = ValueIndexFactory.serialize(dv, 0);
            print(dv, b3);
            final DateValue dv_ = (DateValue) ValueIndexFactory.deserialize(b3, 0, b3.length);
            if (!dv.equals(dv_)) {
                System.out.println("ERROR! " + dv.toString() + " ne " + dv_.toString());
            }
            //******** IntegerValue ********
            final IntegerValue iv = new IntegerValue(753);
            final byte[] i1 = ValueIndexFactory.serialize(iv, 0);
            print(iv, i1);
            final IntegerValue iv2 = new IntegerValue(1960);
            final byte[] i2 = ValueIndexFactory.serialize(iv2, 0);
            print(iv2, i2);
            System.out.println(new Value(i1).compareTo(new Value(i2)));
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    private static void print(final AtomicValue dtv, final byte[] data) throws XPathException {
        System.out.print(dtv.getStringValue() + " = ");
        for (int i = 0; i < data.length; i++) {
            System.out.print(" " + Integer.toHexString(data[i] & 0xff));
        }
        System.out.println();
    }
}
