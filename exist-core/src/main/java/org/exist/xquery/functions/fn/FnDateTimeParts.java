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
package org.exist.xquery.functions.fn;

import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.value.*;

import javax.xml.datatype.DatatypeConstants;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * fn:build-dateTime($date, $time) — Combine xs:date + xs:time into xs:dateTime (XQ 3.1).
 * fn:build-dateTime($record) — Build xs:dateTime from a record/map of components (XQ 4.0).
 * fn:parts-of-dateTime($dateTime) — Decompose xs:dateTime into a map of components.
 *
 * The map accepted/returned has keys: year, month, day, hours, minutes,
 * seconds (as xs:decimal including fractional), timezone (as xs:dayTimeDuration or string).
 */
public class FnDateTimeParts extends BasicFunction {

    public static final FunctionSignature FN_BUILD_DATETIME = new FunctionSignature(
            new QName("build-dateTime", Function.BUILTIN_FUNCTION_NS),
            "Combines an xs:date and an xs:time into an xs:dateTime.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("date", Type.DATE,
                            Cardinality.EXACTLY_ONE, "The date component"),
                    new FunctionParameterSequenceType("time", Type.TIME,
                            Cardinality.EXACTLY_ONE, "The time component")
            },
            new FunctionReturnSequenceType(Type.DATE_TIME, Cardinality.EXACTLY_ONE,
                    "The combined xs:dateTime"));

    public static final FunctionSignature FN_BUILD_DATETIME_RECORD = new FunctionSignature(
            new QName("build-dateTime", Function.BUILTIN_FUNCTION_NS),
            "Builds a date/time value from a record (map) of components. " +
            "Returns xs:dateTime, xs:date, xs:time, or a Gregorian type depending on which fields are present. " +
            "Keys: year, month, day, hours, minutes, seconds, timezone.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("components", Type.MAP_ITEM,
                            Cardinality.ZERO_OR_ONE, "A map with date/time component entries")
            },
            new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_ONE,
                    "The constructed date/time value"));

    public static final FunctionSignature FN_PARTS_OF_DATETIME = new FunctionSignature(
            new QName("parts-of-dateTime", Function.BUILTIN_FUNCTION_NS),
            "Decomposes an xs:dateTime into a map of its components.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("dateTime", Type.DATE_TIME,
                            Cardinality.ZERO_OR_ONE, "The dateTime to decompose")
            },
            new FunctionReturnSequenceType(Type.MAP_ITEM, Cardinality.ZERO_OR_ONE,
                    "A map with keys: year, month, day, hour, minute, seconds, timezone"));

    public FnDateTimeParts(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        if (isCalledAs("build-dateTime")) {
            if (args[0].isEmpty()) {
                return Sequence.EMPTY_SEQUENCE;
            }
            if (args.length == 1 && args[0].itemAt(0) instanceof MapType) {
                return buildDateTimeFromRecord(args);
            }
            return buildDateTime(args);
        } else {
            return partsOfDateTime(args);
        }
    }

    private Sequence buildDateTimeFromRecord(final Sequence[] args) throws XPathException {
        final MapType record = (MapType) args[0].itemAt(0);

        // Determine which fields are present
        final boolean hasYear = hasField(record, "year");
        final boolean hasMonth = hasField(record, "month");
        final boolean hasDay = hasField(record, "day");
        final boolean hasHours = hasField(record, "hours");
        final boolean hasMinutes = hasField(record, "minutes");
        final boolean hasSeconds = hasField(record, "seconds");

        final boolean hasDate = hasYear || hasMonth || hasDay;
        final boolean hasTime = hasHours || hasMinutes || hasSeconds;

        // Validate field values are numeric (not NaN/INF)
        validateNumericFields(record);

        // Get timezone
        final String tzSuffix = getTimezone(record);

        // Determine target type based on present fields
        if (hasYear && hasMonth && hasDay && hasTime) {
            // Full dateTime
            return buildFullDateTime(record, tzSuffix);
        } else if (hasYear && hasMonth && hasDay && !hasTime) {
            // xs:date
            return buildDate(record, tzSuffix);
        } else if (!hasDate && hasTime) {
            // xs:time — require at least hours
            if (!hasHours) {
                throw new XPathException(this, ErrorCodes.FODT0005,
                        "Missing required field 'hours' for time construction");
            }
            return buildTime(record, tzSuffix);
        } else if (hasYear && hasMonth && !hasDay && !hasTime) {
            // xs:gYearMonth
            return buildGYearMonth(record, tzSuffix);
        } else if (hasYear && !hasMonth && !hasDay && !hasTime) {
            // xs:gYear
            return buildGYear(record, tzSuffix);
        } else if (!hasYear && hasMonth && hasDay && !hasTime) {
            // xs:gMonthDay
            return buildGMonthDay(record, tzSuffix);
        } else if (!hasYear && hasMonth && !hasDay && !hasTime) {
            // xs:gMonth
            return buildGMonth(record, tzSuffix);
        } else if (!hasYear && !hasMonth && hasDay && !hasTime) {
            // xs:gDay
            return buildGDay(record, tzSuffix);
        } else if (hasYear && hasMonth && hasDay && hasTime) {
            return buildFullDateTime(record, tzSuffix);
        } else {
            // Invalid combination: date fields present but incomplete, plus time fields
            // Check for unexpected combinations
            if (hasDate && hasTime) {
                // Some date fields missing — fill in defaults
                return buildFullDateTime(record, tzSuffix);
            }
            throw new XPathException(this, ErrorCodes.FODT0005,
                    "Invalid combination of date/time components");
        }
    }

    private boolean hasField(final MapType record, final String key) throws XPathException {
        final Sequence seq = record.get(new StringValue(this, key));
        return seq != null && !seq.isEmpty();
    }

    private void validateNumericFields(final MapType record) throws XPathException {
        final String[] numericFields = {"year", "month", "day", "hours", "minutes", "seconds"};
        for (final String field : numericFields) {
            final Sequence seq = record.get(new StringValue(this, field));
            if (seq != null && !seq.isEmpty()) {
                final Item item = seq.itemAt(0);
                if (item instanceof DoubleValue) {
                    final double d = ((DoubleValue) item).getDouble();
                    if (Double.isNaN(d) || Double.isInfinite(d)) {
                        throw new XPathException(this, ErrorCodes.XPTY0004,
                                "Invalid value for '" + field + "': " + d);
                    }
                }
            }
        }
    }

    private int getIntField(final MapType record, final String key) throws XPathException {
        final Sequence seq = record.get(new StringValue(this, key));
        if (seq == null || seq.isEmpty()) {
            return 0;
        }
        return ((NumericValue) seq.itemAt(0).convertTo(Type.INTEGER)).getInt();
    }

    private String getTimezone(final MapType record) throws XPathException {
        final Sequence tzSeq = record.get(new StringValue(this, "timezone"));
        if (tzSeq == null || tzSeq.isEmpty()) {
            return "";
        }
        final Item tzItem = tzSeq.itemAt(0);
        if (tzItem instanceof DayTimeDurationValue) {
            final long totalMinutes = ((DayTimeDurationValue) tzItem).getValueInMilliseconds() / 60000L;
            // Validate timezone range: must be -14:00 to +14:00
            if (Math.abs(totalMinutes) > 14 * 60) {
                throw new XPathException(this, ErrorCodes.FODT0006,
                        "Timezone offset out of range: " + totalMinutes + " minutes");
            }
            if (totalMinutes == 0) {
                return "Z";
            }
            final int tzH = (int) (totalMinutes / 60);
            final int tzM = (int) Math.abs(totalMinutes % 60);
            return String.format("%+03d:%02d", tzH, tzM);
        } else {
            return tzItem.getStringValue();
        }
    }

    private void validateRange(final String field, final int value, final int min, final int max) throws XPathException {
        if (value < min || value > max) {
            throw new XPathException(this, ErrorCodes.FODT0006,
                    "Value " + value + " out of range for '" + field + "' (expected " + min + " to " + max + ")");
        }
    }

    private Sequence buildFullDateTime(final MapType record, final String tz) throws XPathException {
        final int year = getIntField(record, "year");
        final int month = hasField(record, "month") ? getIntField(record, "month") : 1;
        final int day = hasField(record, "day") ? getIntField(record, "day") : 1;
        final int hours = getIntField(record, "hours");
        final int minutes = getIntField(record, "minutes");

        validateRange("month", month, 1, 12);
        validateRange("day", day, 1, 31);
        validateRange("hours", hours, 0, 23);
        validateRange("minutes", minutes, 0, 59);

        final String secStr = getSecondsStr(record);
        final String lexical = String.format("%04d-%02d-%02dT%02d:%02d:%s%s",
                year, month, day, hours, minutes, secStr, tz);
        return new DateTimeValue(this, lexical);
    }

    private Sequence buildDate(final MapType record, final String tz) throws XPathException {
        final int year = getIntField(record, "year");
        final int month = getIntField(record, "month");
        final int day = getIntField(record, "day");
        validateRange("month", month, 1, 12);
        validateRange("day", day, 1, 31);
        final String lexical = String.format("%04d-%02d-%02d%s", year, month, day, tz);
        return new DateValue(this, lexical);
    }

    private Sequence buildTime(final MapType record, final String tz) throws XPathException {
        final int hours = getIntField(record, "hours");
        final int minutes = hasField(record, "minutes") ? getIntField(record, "minutes") : 0;
        validateRange("hours", hours, 0, 23);
        validateRange("minutes", minutes, 0, 59);
        final String secStr = getSecondsStr(record);
        final String lexical = String.format("%02d:%02d:%s%s", hours, minutes, secStr, tz);
        return new TimeValue(this, lexical);
    }

    private Sequence buildGYear(final MapType record, final String tz) throws XPathException {
        final int year = getIntField(record, "year");
        final String lexical = String.format("%04d%s", year, tz);
        return new GYearValue(this, lexical);
    }

    private Sequence buildGYearMonth(final MapType record, final String tz) throws XPathException {
        final int year = getIntField(record, "year");
        final int month = getIntField(record, "month");
        validateRange("month", month, 1, 12);
        final String lexical = String.format("%04d-%02d%s", year, month, tz);
        return new GYearMonthValue(this, lexical);
    }

    private Sequence buildGMonthDay(final MapType record, final String tz) throws XPathException {
        final int month = getIntField(record, "month");
        final int day = getIntField(record, "day");
        validateRange("month", month, 1, 12);
        validateRange("day", day, 1, 31);
        final String lexical = String.format("--%02d-%02d%s", month, day, tz);
        return new GMonthDayValue(this, lexical);
    }

    private Sequence buildGMonth(final MapType record, final String tz) throws XPathException {
        final int month = getIntField(record, "month");
        validateRange("month", month, 1, 12);
        final String lexical = String.format("--%02d%s", month, tz);
        return new GMonthValue(this, lexical);
    }

    private Sequence buildGDay(final MapType record, final String tz) throws XPathException {
        final int day = getIntField(record, "day");
        validateRange("day", day, 1, 31);
        final String lexical = String.format("---%02d%s", day, tz);
        return new GDayValue(this, lexical);
    }

    private String getSecondsStr(final MapType record) throws XPathException {
        final Sequence secSeq = record.get(new StringValue(this, "seconds"));
        if (secSeq == null || secSeq.isEmpty()) {
            return "00";
        }
        final BigDecimal secVal = new BigDecimal(secSeq.getStringValue());
        final int whole = secVal.intValue();
        final BigDecimal frac = secVal.subtract(BigDecimal.valueOf(whole));
        if (frac.compareTo(BigDecimal.ZERO) > 0) {
            final String fracStr = frac.toPlainString().substring(1); // ".123"
            return String.format("%02d%s", whole, fracStr);
        }
        return String.format("%02d", whole);
    }

    private Sequence buildDateTime(final Sequence[] args) throws XPathException {
        final DateValue date = (DateValue) args[0].itemAt(0);
        final TimeValue time = (TimeValue) args[1].itemAt(0);

        final int year = date.getPart(AbstractDateTimeValue.YEAR);
        final int month = date.getPart(AbstractDateTimeValue.MONTH);
        final int day = date.getPart(AbstractDateTimeValue.DAY);
        final int hour = time.getPart(AbstractDateTimeValue.HOUR);
        final int minute = time.getPart(AbstractDateTimeValue.MINUTE);
        final int second = time.getPart(AbstractDateTimeValue.SECOND);
        final int millis = time.getPart(AbstractDateTimeValue.MILLISECOND);

        // Timezone: both must agree or one must be absent
        final Sequence dateTz = date.getTimezone();
        final Sequence timeTz = time.getTimezone();

        String tzSuffix = "";
        if (!dateTz.isEmpty() && !timeTz.isEmpty()) {
            // Both have timezones — they must be equal
            final String dateTzStr = dateTz.getStringValue();
            final String timeTzStr = timeTz.getStringValue();
            if (!dateTzStr.equals(timeTzStr)) {
                throw new XPathException(this, ErrorCodes.FORG0008,
                        "Date and time timezone offsets do not match");
            }
            tzSuffix = formatTimezoneOffset(date);
        } else if (!dateTz.isEmpty()) {
            tzSuffix = formatTimezoneOffset(date);
        } else if (!timeTz.isEmpty()) {
            tzSuffix = formatTimezoneOffset(time);
        }

        // Build the lexical representation
        final String fracSeconds = millis > 0 ? "." + String.format("%03d", millis) : "";
        final String lexical = String.format("%04d-%02d-%02dT%02d:%02d:%02d%s%s",
                year, month, day, hour, minute, second, fracSeconds, tzSuffix);

        return new DateTimeValue(this, lexical);
    }

    private String formatTimezoneOffset(final AbstractDateTimeValue dt) throws XPathException {
        final Sequence tz = dt.getTimezone();
        if (tz.isEmpty()) {
            return "";
        }
        final DayTimeDurationValue dtv = (DayTimeDurationValue) tz;
        final int totalMinutes = (int) (dtv.getValueInMilliseconds() / 60000L);
        if (totalMinutes == 0) {
            return "Z";
        }
        final int hours = totalMinutes / 60;
        final int mins = Math.abs(totalMinutes % 60);
        return String.format("%+03d:%02d", hours, mins);
    }

    private Sequence partsOfDateTime(final Sequence[] args) throws XPathException {
        if (args[0].isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final DateTimeValue dt = (DateTimeValue) args[0].itemAt(0);
        final MapType result = new MapType(this, context);

        // year as xs:integer
        result.add(new StringValue("year"),
                new IntegerValue(this, dt.getPart(AbstractDateTimeValue.YEAR)));

        // month as xs:integer
        result.add(new StringValue("month"),
                new IntegerValue(this, dt.getPart(AbstractDateTimeValue.MONTH)));

        // day as xs:integer
        result.add(new StringValue("day"),
                new IntegerValue(this, dt.getPart(AbstractDateTimeValue.DAY)));

        // hour as xs:integer
        result.add(new StringValue("hour"),
                new IntegerValue(this, dt.getPart(AbstractDateTimeValue.HOUR)));

        // minute as xs:integer
        result.add(new StringValue("minute"),
                new IntegerValue(this, dt.getPart(AbstractDateTimeValue.MINUTE)));

        // seconds as xs:decimal (including fractional part)
        final int sec = dt.getPart(AbstractDateTimeValue.SECOND);
        final int millis = dt.getPart(AbstractDateTimeValue.MILLISECOND);
        final BigDecimal seconds = BigDecimal.valueOf(sec)
                .add(BigDecimal.valueOf(millis, 3));
        result.add(new StringValue("seconds"),
                new DecimalValue(this, seconds));

        // timezone as xs:dayTimeDuration (or absent)
        final Sequence tz = dt.getTimezone();
        if (!tz.isEmpty()) {
            result.add(new StringValue("timezone"), tz);
        }

        return result;
    }
}
