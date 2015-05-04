package org.exist.indexing.range.conversion;

import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.NumericUtils;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.*;

import javax.xml.datatype.XMLGregorianCalendar;

public class TypeConversion {

    public static BytesRef convertToBytes(final AtomicValue content) throws XPathException {
        final BytesRef bytes;
        switch(content.getType()) {
            case Type.INTEGER:
            case Type.LONG:
            case Type.UNSIGNED_LONG:
                bytes = new BytesRef(NumericUtils.BUF_SIZE_LONG);
                NumericUtils.longToPrefixCoded(((IntegerValue)content).getLong(), 0, bytes);
                break;

            case Type.SHORT:
            case Type.UNSIGNED_SHORT:
            case Type.INT:
            case Type.UNSIGNED_INT:
                bytes = new BytesRef(NumericUtils.BUF_SIZE_INT);
                NumericUtils.intToPrefixCoded(((IntegerValue)content).getInt(), 0, bytes);
                break;

            case Type.DECIMAL:
                long dv = NumericUtils.doubleToSortableLong(((DecimalValue)content).getDouble());
                bytes = new BytesRef(NumericUtils.BUF_SIZE_LONG);
                NumericUtils.longToPrefixCoded(dv, 0, bytes);
                break;

            case Type.DOUBLE:
                long lv = NumericUtils.doubleToSortableLong(((DoubleValue)content).getDouble());
                bytes = new BytesRef(NumericUtils.BUF_SIZE_LONG);
                NumericUtils.longToPrefixCoded(lv, 0, bytes);
                break;

            case Type.FLOAT:
                int iv = NumericUtils.floatToSortableInt(((FloatValue)content).getValue());
                bytes = new BytesRef(NumericUtils.BUF_SIZE_INT);
                NumericUtils.longToPrefixCoded(iv, 0, bytes);
                break;

            case Type.DATE:
                long dl = TypeConversion.dateToLong((DateValue) content);
                bytes = new BytesRef(NumericUtils.BUF_SIZE_LONG);
                NumericUtils.longToPrefixCoded(dl, 0, bytes);
                break;

            case Type.TIME:
                long tl = TypeConversion.timeToLong((TimeValue) content);
                bytes = new BytesRef(NumericUtils.BUF_SIZE_LONG);
                NumericUtils.longToPrefixCoded(tl, 0, bytes);
                break;

            case Type.DATE_TIME:
                final String dt = TypeConversion.dateTimeToString((DateTimeValue) content);
                bytes = new BytesRef(dt);
                break;

            default:
                bytes = new BytesRef(content.getStringValue());
                break;
        }
        return bytes;
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
