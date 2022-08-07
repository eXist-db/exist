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
package org.exist.indexing.lucene;

import org.apache.lucene.document.*;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.DateTimeValue;
import org.exist.xquery.value.DateValue;
import org.exist.xquery.value.TimeValue;
import org.exist.xquery.value.Type;
import org.junit.Test;

import java.math.BigInteger;

import static org.junit.Assert.*;

/**
 * Tests to check for collisions between indexed XDM Type values in eXist-db's Lucene Index.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class LuceneFieldConfigTest {

    @Test
    public void dateToLongPreservesTimezoneNeg() throws XPathException {
        // xs:date dateValue1 and dateValue2 MUST not be indexed as the same value - both are stored as Lucene LongField
        final DateValue dateValue1 = new DateValue("2022-08-07-02:00");
        final DateValue dateValue2 = new DateValue("2022-08-07Z");

        final long dateValue1Long = LuceneFieldConfig.dateToLong(dateValue1);
        final long dateValue2Long = LuceneFieldConfig.dateToLong(dateValue2);

        assertNotEquals(dateValue1Long, dateValue2Long);
    }

    @Test
    public void dateToLongPreservesTimezonePos() throws XPathException {
        // xs:date dateValue1 and dateValue2 MUST not be indexed as the same value - both are stored as Lucene LongField
        final DateValue dateValue1 = new DateValue("2022-08-07+02:00");
        final DateValue dateValue2 = new DateValue("2022-08-06Z");

        final long dateValue1Long = LuceneFieldConfig.dateToLong(dateValue1);
        final long dateValue2Long = LuceneFieldConfig.dateToLong(dateValue2);

        assertNotEquals(dateValue1Long, dateValue2Long);
    }

    @Test
    public void timeToLongPreservesTimezoneNeg() throws XPathException {
        // xs:time timeValue1 and timeValue2 MUST not be indexed as the same value - both are stored as Lucene LongField
        final TimeValue timeValue1 = new TimeValue("11:45:00.000-02:00");
        final TimeValue timeValue2 = new TimeValue("13:45:00.000Z");

        final long timeValue1Long = LuceneFieldConfig.timeToLong(timeValue1);
        final long timeValue2Long = LuceneFieldConfig.timeToLong(timeValue2);

        assertNotEquals(timeValue1Long, timeValue2Long);
    }

    @Test
    public void timeToLongPreservesTimezonePos() throws XPathException {
        // xs:time timeValue1 and timeValue2 MUST not be indexed as the same value - both are stored as Lucene LongField
        final TimeValue timeValue1 = new TimeValue("11:45:00.000+02:00");
        final TimeValue timeValue2 = new TimeValue("09:45:00.000Z");

        final long timeValue1Long = LuceneFieldConfig.timeToLong(timeValue1);
        final long timeValue2Long = LuceneFieldConfig.timeToLong(timeValue2);

        assertNotEquals(timeValue1Long, timeValue2Long);
    }

    @Test
    public void timeToLongAndDateToLongMustNotConflict() throws XPathException {
        // xs:time and xs:date values MUST not be indexed as the same value - both are stored as Lucene LongField
        final TimeValue timeValue = new TimeValue("11:00:11.265+01:00");
        final DateValue dateValue = new DateValue("1444153-01-01");

        final long timeValueLong = LuceneFieldConfig.timeToLong(timeValue);
        final long dateValueLong = LuceneFieldConfig.dateToLong(dateValue);

        assertNotEquals(timeValueLong, dateValueLong);
    }

    @Test
    public void dateConvertToFieldPreservesTimezoneNeg() {
        // xs:date dateValue1 and dateValue2 MUST not be indexed as the same value - both are stored as Lucene LongField
        final Field dateValue1Field = LuceneFieldConfig.convertToField(Type.DATE, "dateValue1", "2022-08-07-02:00", false);
        final Field dateValue2Field = LuceneFieldConfig.convertToField(Type.DATE, "dateValue2", "2022-08-07Z", false);

        assertTrue(dateValue1Field instanceof LongField);
        assertTrue(dateValue2Field instanceof LongField);
        assertNotEquals(dateValue1Field.numericValue(), dateValue2Field.numericValue());
    }

    @Test
    public void dateConvertToFieldPreservesTimezonePos() {
        // xs:date dateValue1 and dateValue2 MUST not be indexed as the same value - both are stored as Lucene LongField
        final Field dateValue1Field = LuceneFieldConfig.convertToField(Type.DATE, "dateValue1", "2022-08-07+02:00", false);
        final Field dateValue2Field = LuceneFieldConfig.convertToField(Type.DATE, "dateValue2", "2022-08-06Z", false);

        assertTrue(dateValue1Field instanceof LongField);
        assertTrue(dateValue2Field instanceof LongField);
        assertNotEquals(dateValue1Field.numericValue(), dateValue2Field.numericValue());
    }

    @Test
    public void timeConvertToFieldPreservesTimezoneNeg() {
        // xs:time timeValue1 and timeValue2 MUST not be indexed as the same value - both are stored as Lucene LongField
        final Field timeValue1Field = LuceneFieldConfig.convertToField(Type.TIME, "timeValue1", "11:45:00.000-02:00", false);
        final Field timeValue2Field = LuceneFieldConfig.convertToField(Type.TIME, "timeValue2", "13:45:00.000Z", false);

        assertTrue(timeValue1Field instanceof LongField);
        assertTrue(timeValue2Field instanceof LongField);
        assertNotEquals(timeValue1Field.numericValue(), timeValue2Field.numericValue());
    }

    @Test
    public void timeConvertToFieldPreservesTimezonePos() {
        // xs:time timeValue1 and timeValue2 MUST not be indexed as the same value - both are stored as Lucene LongField
        final Field timeValue1Field = LuceneFieldConfig.convertToField(Type.TIME, "timeValue1", "11:45:00.000+02:00", false);
        final Field timeValue2Field = LuceneFieldConfig.convertToField(Type.TIME, "timeValue2", "09:45:00.000Z", false);

        assertTrue(timeValue1Field instanceof LongField);
        assertTrue(timeValue2Field instanceof LongField);
        assertNotEquals(timeValue1Field.numericValue(), timeValue2Field.numericValue());
    }

    @Test
    public void timeAndDateConvertToFieldMustNotConflict() {
        // xs:time and xs:date values MUST not be indexed as the same value - both are stored as Lucene LongField
        final Field timeValueField = LuceneFieldConfig.convertToField(Type.TIME, "timeValue", "11:00:11.265+01:00", false);
        final Field dateValueField = LuceneFieldConfig.convertToField(Type.DATE, "dateValue", "1444153-01-01", false);

        assertTrue(timeValueField instanceof LongField);
        assertTrue(dateValueField instanceof LongField);
        assertNotEquals(timeValueField.numericValue(), dateValueField.numericValue());
    }

    @Test
    public void timeAndIntegerConvertToFieldMustNotConflict() {
        // xs:time and xs:integer values MUST not be indexed as the same value - both are stored as Lucene LongField
        final Field timeValueField = LuceneFieldConfig.convertToField(Type.TIME, "timeValue", "11:00:11.265+01:00", false);
        final Field integerValueField = LuceneFieldConfig.convertToField(Type.INTEGER, "integerValue", "94644011265", false);

        assertTrue(timeValueField instanceof LongField);
        assertTrue(integerValueField instanceof LongField);
        assertNotEquals(timeValueField.numericValue(), integerValueField.numericValue());
    }

    @Test
    public void timeAndLongConvertToFieldMustNotConflict() {
        // xs:time and xs:long values MUST not be indexed as the same value - both are stored as Lucene LongField
        final Field timeValueField = LuceneFieldConfig.convertToField(Type.TIME, "timeValue", "11:00:11.265+01:00", false);
        final Field longValueField = LuceneFieldConfig.convertToField(Type.LONG, "longValue", "94644011265", false);

        assertTrue(timeValueField instanceof LongField);
        assertTrue(longValueField instanceof LongField);
        assertNotEquals(timeValueField.numericValue(), longValueField.numericValue());
    }

    @Test
    public void timeAndUnsignedLongConvertToFieldMustNotConflict() {
        // xs:time and xs:long values MUST not be indexed as the same value - both are stored as Lucene LongField
        final Field timeValueField = LuceneFieldConfig.convertToField(Type.TIME, "timeValue", "11:00:11.265+01:00", false);
        final Field unsignedLongValueField = LuceneFieldConfig.convertToField(Type.UNSIGNED_LONG, "unsignedLongValue", "94644011265", false);

        assertTrue(timeValueField instanceof LongField);
        assertTrue(unsignedLongValueField instanceof LongField);
        assertNotEquals(timeValueField.numericValue(), unsignedLongValueField.numericValue());
    }

    @Test
    public void dateAndIntegerConvertToFieldMustNotConflict() {
        // xs:date and xs:integer values MUST not be indexed as the same value - both are stored as Lucene LongField
        final Field dateValueField = LuceneFieldConfig.convertToField(Type.DATE, "dateValue", "1444153-01-01", false);
        final Field integerValueField = LuceneFieldConfig.convertToField(Type.INTEGER, "integerValue", "94644011265", false);

        assertTrue(dateValueField instanceof LongField);
        assertTrue(integerValueField instanceof LongField);
        assertNotEquals(dateValueField.numericValue(), integerValueField.numericValue());
    }

    @Test
    public void dateAndLongConvertToFieldMustNotConflict() {
        // xs:date and xs:long values MUST not be indexed as the same value - both are stored as Lucene LongField
        final Field dateValueField = LuceneFieldConfig.convertToField(Type.DATE, "dateValue", "1444153-01-01", false);
        final Field longValueField = LuceneFieldConfig.convertToField(Type.LONG, "longValue", "94644011265", false);

        assertTrue(dateValueField instanceof LongField);
        assertTrue(longValueField instanceof LongField);
        assertNotEquals(dateValueField.numericValue(), longValueField.numericValue());
    }

    @Test
    public void dateAndUnsignedLongConvertToFieldMustNotConflict() {
        // xs:date and xs:unsignedLong values MUST not be indexed as the same value - both are stored as Lucene LongField
        final Field dateValueField = LuceneFieldConfig.convertToField(Type.DATE, "dateValue", "1444153-01-01", false);
        final Field unsignedLongValueField = LuceneFieldConfig.convertToField(Type.UNSIGNED_LONG, "unsignedLongValue", "94644011265", false);

        assertTrue(dateValueField instanceof LongField);
        assertTrue(unsignedLongValueField instanceof LongField);
        assertNotEquals(dateValueField.numericValue(), unsignedLongValueField.numericValue());
    }

    @Test
    public void integerAndLongConvertToFieldMustNotConflict() {
        // xs:integer and xs:long values MUST not be indexed as the same value - both are stored as Lucene LongField
        final Field integerValueField = LuceneFieldConfig.convertToField(Type.INTEGER, "integerValue", "123", false);
        final Field longValueField = LuceneFieldConfig.convertToField(Type.LONG, "longValue", "123", false);

        assertTrue(integerValueField instanceof LongField);
        assertTrue(longValueField instanceof LongField);
        assertNotEquals(integerValueField.numericValue(), longValueField.numericValue());
    }

    @Test
    public void integerAndUnsignedLongConvertToFieldMustNotConflict() {
        // xs:integer and xs:unsignedLong values MUST not be indexed as the same value - both are stored as Lucene LongField
        final Field integerValueField = LuceneFieldConfig.convertToField(Type.INTEGER, "integerValue", "123", false);
        final Field unsignedLongValueField = LuceneFieldConfig.convertToField(Type.UNSIGNED_LONG, "unsignedLongValue", "123", false);

        assertTrue(integerValueField instanceof LongField);
        assertTrue(unsignedLongValueField instanceof LongField);
        assertNotEquals(integerValueField.numericValue(), unsignedLongValueField.numericValue());
    }

    @Test
    public void longAndUnsignedLongConvertToFieldMustNotConflict() {
        // xs:long and xs:unsignedLong values MUST not be indexed as the same value - both are stored as Lucene LongField
        final Field longValueField = LuceneFieldConfig.convertToField(Type.LONG, "longValue", "123", false);
        final Field unsignedLongValueField = LuceneFieldConfig.convertToField(Type.UNSIGNED_LONG, "unsignedLongValue", "123", false);

        assertTrue(longValueField instanceof LongField);
        assertTrue(unsignedLongValueField instanceof LongField);
        assertNotEquals(longValueField.numericValue(), unsignedLongValueField.numericValue());
    }

    @Test
    public void largeIntegerConvertToField() {
        // xs:integer can be larger than 64 bits signed - should either work or raise an exception (i.e. not return null)
        final String longPlusOne = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE).toString();
        final Field integerValue1Field = LuceneFieldConfig.convertToField(Type.INTEGER, "integerValue", longPlusOne, false);
        assertNotNull(integerValue1Field);
    }

    @Test
    public void smallIntegerConvertToField() {
        // xs:integer can be larger than 64 bits signed - should either work or raise an exception (i.e. not return null)
        final String longMinusOne = BigInteger.valueOf(Long.MIN_VALUE).subtract(BigInteger.ONE).toString();
        final Field integerValue1Field = LuceneFieldConfig.convertToField(Type.INTEGER, "integerValue", longMinusOne, false);
        assertNotNull(integerValue1Field);
    }

    @Test
    public void maxUnsignedLongConvertToField() {
        // xs:unsignedLong values should be supported
        final Field unsignedLongValueField = LuceneFieldConfig.convertToField(Type.UNSIGNED_LONG, "unsignedLongValue", "18446744073709551615", false);
        assertNotNull(unsignedLongValueField);
    }

    @Test
    public void intAndUnsignedIntConvertToFieldMustNotConflict() {
        // xs:int and xs:unsignedInt values MUST not be indexed as the same value - both are stored as Lucene IntField
        final Field intValueField = LuceneFieldConfig.convertToField(Type.INT, "intValue", "123", false);
        final Field unsignedIntValueField = LuceneFieldConfig.convertToField(Type.UNSIGNED_INT, "unsignedIntValue", "123", false);

        assertTrue(intValueField instanceof IntField);
        assertTrue(unsignedIntValueField instanceof IntField);
        assertNotEquals(intValueField.numericValue(), unsignedIntValueField.numericValue());
    }

    @Test
    public void intAndShortConvertToFieldMustNotConflict() {
        // xs:int and xs:short values MUST not be indexed as the same value - both are stored as Lucene IntField
        final Field intValueField = LuceneFieldConfig.convertToField(Type.INT, "intValue", "123", false);
        final Field shortValueField = LuceneFieldConfig.convertToField(Type.SHORT, "shortValue", "123", false);

        assertTrue(intValueField instanceof IntField);
        assertTrue(shortValueField instanceof IntField);
        assertNotEquals(intValueField.numericValue(), shortValueField.numericValue());
    }

    @Test
    public void intAndUnsignedShortConvertToFieldMustNotConflict() {
        // xs:int and xs:unsignedShort values MUST not be indexed as the same value - both are stored as Lucene IntField
        final Field intValueField = LuceneFieldConfig.convertToField(Type.INT, "intValue", "123", false);
        final Field unsignedShortValueField = LuceneFieldConfig.convertToField(Type.UNSIGNED_SHORT, "unsignedShortValue", "123", false);

        assertTrue(intValueField instanceof IntField);
        assertTrue(unsignedShortValueField instanceof IntField);
        assertNotEquals(intValueField.numericValue(), unsignedShortValueField.numericValue());
    }

    @Test
    public void unsignedIntAndShortConvertToFieldMustNotConflict() {
        // xs:unsignedInt and xs:short values MUST not be indexed as the same value - both are stored as Lucene IntField
        final Field unsignedIntValueField = LuceneFieldConfig.convertToField(Type.UNSIGNED_INT, "unsignedIntValue", "123", false);
        final Field shortValueField = LuceneFieldConfig.convertToField(Type.SHORT, "shortValue", "123", false);

        assertTrue(unsignedIntValueField instanceof IntField);
        assertTrue(shortValueField instanceof IntField);
        assertNotEquals(unsignedIntValueField.numericValue(), shortValueField.numericValue());
    }

    @Test
    public void unsignedIntAndUnsigendShortConvertToFieldMustNotConflict() {
        // xs:unsignedInt and xs:unsignedShort values MUST not be indexed as the same value - both are stored as Lucene IntField
        final Field unsignedIntValueField = LuceneFieldConfig.convertToField(Type.UNSIGNED_INT, "unsignedIntValue", "123", false);
        final Field unsignedShortValueField = LuceneFieldConfig.convertToField(Type.UNSIGNED_SHORT, "unsignedShortValue", "123", false);

        assertTrue(unsignedIntValueField instanceof IntField);
        assertTrue(unsignedShortValueField instanceof IntField);
        assertNotEquals(unsignedIntValueField.numericValue(), unsignedShortValueField.numericValue());
    }

    @Test
    public void shortAndUnsignedShortConvertToFieldMustNotConflict() {
        // xs:short and xs:unsignedShort values MUST not be indexed as the same value - both are stored as Lucene IntField
        final Field shortValueField = LuceneFieldConfig.convertToField(Type.SHORT, "shortValue", "123", false);
        final Field unsignedShortValueField = LuceneFieldConfig.convertToField(Type.UNSIGNED_SHORT, "unsignedShortValue", "123", false);

        assertTrue(shortValueField instanceof IntField);
        assertTrue(unsignedShortValueField instanceof IntField);
        assertNotEquals(shortValueField.numericValue(), unsignedShortValueField.numericValue());
    }

    @Test
    public void maxUnsignedIntConvertToField() {
        // xs:unsignedInt values should be supported
        final Field unsignedIntValueField = LuceneFieldConfig.convertToField(Type.UNSIGNED_INT, "unsignedIntValue", "4294967295", false);
        assertNotNull(unsignedIntValueField);
    }

    @Test
    public void maxUnsignedShortConvertToField() {
        // xs:unsignedShort values should be supported
        final Field unsignedShortValueField = LuceneFieldConfig.convertToField(Type.UNSIGNED_SHORT, "unsignedShortValue", "65535", false);
        assertNotNull(unsignedShortValueField);
    }

    @Test
    public void decimalAndDoubleConvertToFieldMustNotConflict() {
        // xs:decimal and xs:double values MUST not be indexed as the same value - both are stored as Lucene DoubleField
        final Field decimalValueField = LuceneFieldConfig.convertToField(Type.DECIMAL, "decimalValue", "0.123", false);
        final Field doubleValueField = LuceneFieldConfig.convertToField(Type.DOUBLE, "doubleValue", "0.123", false);

        assertTrue(decimalValueField instanceof DoubleField);
        assertTrue(doubleValueField instanceof DoubleField);
        assertNotEquals(decimalValueField.numericValue(), doubleValueField.numericValue());
    }

    @Test
    public void decimalMinimallyConfirmingProcessorConvertToField() {
        // xs:decimal - "All minimally conforming processors must support decimal numbers with a minimum of 18 decimal digits" - https://www.w3.org/TR/xmlschema-2/#decimal
        final Field decimalValueField = LuceneFieldConfig.convertToField(Type.DECIMAL, "decimalValue", "0.12345678912345678", false);
        assertTrue(decimalValueField instanceof DoubleField);
        assertEquals("0.12345678912345678", decimalValueField.numericValue().toString());
    }

    @Test
    public void dateTimeToStringPreservesTimezoneNeg() throws XPathException {
        // xs:dateTime dateValue1 and dateValue2 MUST not be indexed as the same value - both are stored as Lucene TextField
        final DateTimeValue dateTimeValue1 = new DateTimeValue("2022-08-07T00:00:00.000-02:00");
        final DateTimeValue dateTimeValue2 = new DateTimeValue("2022-08-07T02:00:00.000Z");

        final String dateTimeValue1Str = LuceneFieldConfig.dateTimeToString(dateTimeValue1);
        final String dateTimeValue2Str = LuceneFieldConfig.dateTimeToString(dateTimeValue2);

        assertNotEquals(dateTimeValue1Str, dateTimeValue2Str);
    }

    @Test
    public void dateTimeToStringPreservesTimezonePos() throws XPathException {
        // xs:dateTime dateValue1 and dateValue2 MUST not be indexed as the same value - both are stored as Lucene TextField
        final DateTimeValue dateTimeValue1 = new DateTimeValue("2022-08-07T00:00:00.000+02:00");
        final DateTimeValue dateTimeValue2 = new DateTimeValue("2022-08-06T22:00:00.000Z");

        final String dateTimeValue1Str = LuceneFieldConfig.dateTimeToString(dateTimeValue1);
        final String dateTimeValue2Str = LuceneFieldConfig.dateTimeToString(dateTimeValue2);

        assertNotEquals(dateTimeValue1Str, dateTimeValue2Str);
    }

    @Test
    public void dateTimeConvertToFieldPreservesTimezoneNeg() {
        // xs:dateTime dateValue1 and dateValue2 MUST not be indexed as the same value - both are stored as Lucene TextField
        final Field dateTimeValue1Field = LuceneFieldConfig.convertToField(Type.DATE_TIME, "dateTimeValue1", "2022-08-07T00:00:00.000-02:00", false);
        final Field dateTimeValue2Field = LuceneFieldConfig.convertToField(Type.DATE_TIME, "dateTimeValue2", "2022-08-07T02:00:00.000Z", false);

        assertTrue(dateTimeValue1Field instanceof TextField);
        assertTrue(dateTimeValue2Field instanceof TextField);
        assertNotEquals(dateTimeValue1Field.stringValue(), dateTimeValue2Field.stringValue());
    }

    @Test
    public void dateTimeConvertToFieldPreservesTimezonePos() {
        // xs:dateTime dateValue1 and dateValue2 MUST not be indexed as the same value - both are stored as Lucene TextField
        final Field dateTimeValue1Field = LuceneFieldConfig.convertToField(Type.DATE_TIME, "dateTimeValue1", "2022-08-07T00:00:00.000+02:00", false);
        final Field dateTimeValue2Field = LuceneFieldConfig.convertToField(Type.DATE_TIME, "dateTimeValue2", "2022-08-06T22:00:00.000Z", false);

        assertTrue(dateTimeValue1Field instanceof TextField);
        assertTrue(dateTimeValue2Field instanceof TextField);
        assertNotEquals(dateTimeValue1Field.stringValue(), dateTimeValue2Field.stringValue());
    }

    @Test
    public void dateTimeAndStringConvertToFieldMustNotConflict() {
        // xs:dateTime and xs:string values MUST not be indexed as the same value - both are stored as Lucene TextField
        final Field dateTimeValueField = LuceneFieldConfig.convertToField(Type.DATE_TIME, "dateTimeValue", "2022-08-07T00:00:00.000+02:00", false);
        final Field stringValueField = LuceneFieldConfig.convertToField(Type.STRING, "stringValue", "20220806220000000", false);

        assertTrue(dateTimeValueField instanceof TextField);
        assertTrue(stringValueField instanceof TextField);
        assertNotEquals(dateTimeValueField.stringValue(), stringValueField.stringValue());
    }
}
