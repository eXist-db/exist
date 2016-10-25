package org.exist.indexing.range.conversion;

import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.BytesRefBuilder;
import org.apache.lucene.util.NumericUtils;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.*;

import javax.xml.datatype.XMLGregorianCalendar;

public class TypeConversion {

    public static BytesRef convertToBytes(final AtomicValue content) throws XPathException {
        final BytesRefBuilder bytes = new BytesRefBuilder();
        switch(content.getType()) {
            case Type.INTEGER:
            case Type.LONG:
            case Type.UNSIGNED_LONG:
                NumericUtils.longToPrefixCoded(((IntegerValue)content).getLong(), 0, bytes);
                break;

            case Type.SHORT:
            case Type.UNSIGNED_SHORT:
            case Type.INT:
            case Type.UNSIGNED_INT:
                final int iv = ((IntegerValue)content).getInt();
                NumericUtils.intToPrefixCoded(iv, 0, bytes);
                break;

            case Type.DECIMAL:
                final long dv = NumericUtils.doubleToSortableLong(((DecimalValue)content).getDouble());
                NumericUtils.longToPrefixCoded(dv, 0, bytes);
                break;

            case Type.DOUBLE:
                final long dblv = NumericUtils.doubleToSortableLong(((DoubleValue)content).getDouble());
                NumericUtils.longToPrefixCoded(dblv, 0, bytes);
                break;

            case Type.FLOAT:
                final int fv = NumericUtils.floatToSortableInt(((FloatValue)content).getValue());
                NumericUtils.longToPrefixCoded(fv, 0, bytes);
                break;

            case Type.DATE:
                final long dl = dateToLong((DateValue) content);
                NumericUtils.longToPrefixCoded(dl, 0, bytes);
                break;

            case Type.TIME:
                final long tl = timeToLong((TimeValue) content);
                NumericUtils.longToPrefixCoded(tl, 0, bytes);
                break;

            case Type.DATE_TIME:
                final String dt = dateTimeToString((DateTimeValue) content);
                bytes.copyChars(dt);
                break;

            default:
                bytes.copyChars(content.getStringValue());
                break;
        }

        return bytes.toBytesRef();
    }

    public static long dateToLong(final DateValue date) {
        final XMLGregorianCalendar utccal = date.calendar.normalize();
        return ((long)utccal.getYear() << 16) + ((long)utccal.getMonth() << 8) + ((long)utccal.getDay());
    }

    public static long timeToLong(final TimeValue time) {
        return time.getTimeInMillis();
    }

    public static String dateTimeToString(final DateTimeValue dtv) {
        final XMLGregorianCalendar utccal = dtv.calendar.normalize();
        final StringBuilder sb = new StringBuilder();
        formatNumber(utccal.getMillisecond(), 3, sb);
        formatNumber(utccal.getSecond(), 2, sb);
        formatNumber(utccal.getMinute(), 2, sb);
        formatNumber(utccal.getHour(), 2, sb);
        formatNumber(utccal.getDay(), 2, sb);
        formatNumber(utccal.getMonth(), 2, sb);
        formatNumber(utccal.getYear(), 4, sb);
        return sb.toString();
    }

    public static void formatNumber(final int number, final int digits, final StringBuilder sb) {
        int count = 0;
        long n = number;
        while (n > 0) {
            final int digit = '0' + (int)n % 10;
            sb.insert(0, (char)digit);
            count++;
            if (count == digits) {
                break;
            }
            n = n / 10;
        }
        if (count < digits) {
            for (int i = count; i < digits; i++) {
                sb.insert(0, '0');
            }
        }
    }
}
