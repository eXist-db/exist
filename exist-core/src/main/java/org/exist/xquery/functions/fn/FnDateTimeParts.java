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
import javax.xml.datatype.Duration;
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
            "Decomposes a date/time value into a map of its components. " +
                    "Accepts xs:dateTime, xs:date, xs:time, xs:gYearMonth, xs:gYear, " +
                    "xs:gMonth, xs:gMonthDay, or xs:gDay. " +
                    "Returns a map with the keys appropriate for the input type: " +
                    "year, month, day, hours, minutes, seconds, timezone.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("value", Type.ANY_ATOMIC_TYPE,
                            Cardinality.ZERO_OR_ONE, "The date/time value to decompose")
            },
            new FunctionReturnSequenceType(Type.MAP_ITEM, Cardinality.ZERO_OR_ONE,
                    "A map with keys appropriate for the input type"));

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

        final boolean hasYear = hasField(record, "year");
        final boolean hasMonth = hasField(record, "month");
        final boolean hasDay = hasField(record, "day");
        final boolean hasHours = hasField(record, "hours");
        final boolean hasMinutes = hasField(record, "minutes");
        final boolean hasSeconds = hasField(record, "seconds");
        final boolean hasTimezone = hasField(record, "timezone");

        final boolean hasAnyDate = hasYear || hasMonth || hasDay;
        final boolean hasAnyTime = hasHours || hasMinutes || hasSeconds;

        // Empty record (or only timezone) is FODT0005.
        if (!hasAnyDate && !hasAnyTime) {
            throw new XPathException(this, ErrorCodes.FODT0005,
                    "Cannot build a date/time value: no date or time components supplied");
        }

        // If time fields are present they must all be present.
        if (hasAnyTime && !(hasHours && hasMinutes && hasSeconds)) {
            throw new XPathException(this, ErrorCodes.FODT0005,
                    "Time components require all of hours, minutes, and seconds");
        }

        // If we have time but no date fields: build xs:time.
        if (hasAnyTime && !hasAnyDate) {
            return buildTime(record, hasTimezone);
        }

        // If we have date and time, the date must be complete (year+month+day).
        if (hasAnyTime) {
            if (!(hasYear && hasMonth && hasDay)) {
                throw new XPathException(this, ErrorCodes.FODT0005,
                        "When time components are supplied with date components, " +
                                "all of year, month, and day are required");
            }
            return buildFullDateTime(record, hasTimezone);
        }

        // Date-only combinations.
        if (hasYear && hasMonth && hasDay) {
            return buildDate(record, hasTimezone);
        }
        if (hasYear && hasMonth) { // && !hasDay
            return buildGYearMonth(record, hasTimezone);
        }
        if (hasYear && !hasMonth && !hasDay) {
            return buildGYear(record, hasTimezone);
        }
        if (!hasYear && hasMonth && hasDay) {
            return buildGMonthDay(record, hasTimezone);
        }
        if (!hasYear && hasMonth && !hasDay) {
            return buildGMonth(record, hasTimezone);
        }
        if (!hasYear && !hasMonth && hasDay) {
            return buildGDay(record, hasTimezone);
        }
        // Remaining: hasYear && !hasMonth && hasDay — invalid (year+day without month).
        throw new XPathException(this, ErrorCodes.FODT0005,
                "Invalid combination of date components: year and day without month");
    }

    private boolean hasField(final MapType record, final String key) throws XPathException {
        final Sequence seq = record.get(new StringValue(this, key));
        return seq != null && !seq.isEmpty();
    }

    /**
     * Coerce a numeric record field to int. Accepts xs:integer (any subtype) and
     * xs:decimal that is exactly an integer. Rejects xs:double/xs:float
     * (XPTY0004), as well as xs:decimal with a fractional part (XPTY0004).
     */
    private int getIntField(final MapType record, final String key) throws XPathException {
        final Sequence seq = record.get(new StringValue(this, key));
        if (seq == null || seq.isEmpty()) {
            return 0;
        }
        final Item item = seq.itemAt(0);
        if (item instanceof IntegerValue) {
            return ((IntegerValue) item).getInt();
        }
        if (item instanceof DecimalValue) {
            final BigDecimal d = ((DecimalValue) item).getValue();
            try {
                return d.intValueExact();
            } catch (final ArithmeticException e) {
                throw new XPathException(this, ErrorCodes.XPTY0004,
                        "Field '" + key + "' must be an integer; got " + d);
            }
        }
        // Untyped atomic and node values: parse the string value as an integer.
        if (item instanceof NodeValue || Type.subTypeOf(item.getType(), Type.UNTYPED_ATOMIC)) {
            final String s = item.getStringValue().trim();
            try {
                return Integer.parseInt(s);
            } catch (final NumberFormatException e) {
                throw new XPathException(this, ErrorCodes.XPTY0004,
                        "Field '" + key + "' is not a valid integer: '" + s + "'");
            }
        }
        throw new XPathException(this, ErrorCodes.XPTY0004,
                "Field '" + key + "' must be xs:integer or xs:decimal; got "
                        + Type.getTypeName(item.getType()));
    }

    /**
     * Coerce the seconds field to a BigDecimal. Accepts xs:integer, xs:decimal,
     * xs:double, xs:float — but rejects NaN/Infinity (XPTY0004).
     */
    private BigDecimal getSecondsField(final MapType record) throws XPathException {
        final Sequence seq = record.get(new StringValue(this, "seconds"));
        if (seq == null || seq.isEmpty()) {
            return null;
        }
        final Item item = seq.itemAt(0);
        if (item instanceof IntegerValue) {
            return BigDecimal.valueOf(((IntegerValue) item).getLong());
        }
        if (item instanceof DecimalValue) {
            return ((DecimalValue) item).getValue();
        }
        if (item instanceof DoubleValue) {
            final double d = ((DoubleValue) item).getDouble();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                throw new XPathException(this, ErrorCodes.XPTY0004,
                        "Field 'seconds' must be a finite numeric value; got " + d);
            }
            return BigDecimal.valueOf(d);
        }
        if (item instanceof FloatValue) {
            final float f = ((FloatValue) item).getValue();
            if (Float.isNaN(f) || Float.isInfinite(f)) {
                throw new XPathException(this, ErrorCodes.XPTY0004,
                        "Field 'seconds' must be a finite numeric value; got " + f);
            }
            return BigDecimal.valueOf((double) f);
        }
        throw new XPathException(this, ErrorCodes.XPTY0004,
                "Field 'seconds' must be numeric; got " + Type.getTypeName(item.getType()));
    }

    /**
     * Compute the timezone suffix string from the record. Returns "" for absent timezone.
     */
    private String getTimezone(final MapType record) throws XPathException {
        final Sequence tzSeq = record.get(new StringValue(this, "timezone"));
        if (tzSeq == null || tzSeq.isEmpty()) {
            return "";
        }
        final Item tzItem = tzSeq.itemAt(0);
        if (!(tzItem instanceof DurationValue)) {
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "Field 'timezone' must be xs:dayTimeDuration; got "
                            + Type.getTypeName(tzItem.getType()));
        }
        // Reject xs:duration values that contain year/month components.
        if (!(tzItem instanceof DayTimeDurationValue)) {
            final Duration dur = ((DurationValue) tzItem).getCanonicalDuration();
            if (dur.isSet(DatatypeConstants.YEARS) || dur.isSet(DatatypeConstants.MONTHS)) {
                throw new XPathException(this, ErrorCodes.FODT0006,
                        "Timezone duration must not contain year or month components");
            }
        }
        final DayTimeDurationValue dtv = (DayTimeDurationValue) ((DurationValue) tzItem)
                .convertTo(Type.DAY_TIME_DURATION);
        final long totalMillis = dtv.getValueInMilliseconds();
        // Per XSD, timezone offset must be expressible in whole minutes and within ±14:00.
        if (totalMillis % 60000L != 0L) {
            throw new XPathException(this, ErrorCodes.FODT0006,
                    "Timezone duration must be a whole number of minutes");
        }
        final long totalMinutes = totalMillis / 60000L;
        if (Math.abs(totalMinutes) > 14 * 60) {
            throw new XPathException(this, ErrorCodes.FODT0006,
                    "Timezone offset out of range (-14:00..+14:00): " + totalMinutes + " minutes");
        }
        if (totalMinutes == 0) {
            return "Z";
        }
        final int tzH = (int) (totalMinutes / 60);
        final int tzM = (int) Math.abs(totalMinutes % 60);
        return String.format("%+03d:%02d", tzH, tzM);
    }

    private void requireRange(final String field, final int value, final int min, final int max) throws XPathException {
        if (value < min || value > max) {
            throw new XPathException(this, ErrorCodes.FODT0006,
                    "Value " + value + " out of range for '" + field + "' (expected " + min + " to " + max + ")");
        }
    }

    private void validateDate(final int year, final int month, final int day) throws XPathException {
        requireRange("month", month, 1, 12);
        requireRange("day", day, 1, daysInMonth(year, month));
    }

    private int daysInMonth(final int year, final int month) {
        return switch (month) {
            case 1, 3, 5, 7, 8, 10, 12 -> 31;
            case 4, 6, 9, 11 -> 30;
            case 2 -> isLeapYear(year) ? 29 : 28;
            default -> 31;
        };
    }

    private boolean isLeapYear(final int year) {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0);
    }

    private String formatYear(final int year) {
        if (year < 0) {
            return "-" + String.format("%04d", -year);
        }
        if (year < 10000) {
            return String.format("%04d", year);
        }
        return Integer.toString(year);
    }

    private Sequence buildFullDateTime(final MapType record, final boolean hasTimezone) throws XPathException {
        final int year = getIntField(record, "year");
        final int month = getIntField(record, "month");
        final int day = getIntField(record, "day");
        final int hours = getIntField(record, "hours");
        final int minutes = getIntField(record, "minutes");

        validateDate(year, month, day);
        requireRange("hours", hours, 0, 23);
        requireRange("minutes", minutes, 0, 59);

        final BigDecimal seconds = getSecondsField(record);
        final String secStr = formatSeconds(seconds);
        final String tz = hasTimezone ? getTimezone(record) : "";

        final String lexical = formatYear(year) + String.format("-%02d-%02dT%02d:%02d:%s%s",
                month, day, hours, minutes, secStr, tz);
        if (hasTimezone) {
            return new DateTimeStampValue(this, lexical);
        }
        return new DateTimeValue(this, lexical);
    }

    private Sequence buildDate(final MapType record, final boolean hasTimezone) throws XPathException {
        final int year = getIntField(record, "year");
        final int month = getIntField(record, "month");
        final int day = getIntField(record, "day");
        validateDate(year, month, day);
        final String tz = hasTimezone ? getTimezone(record) : "";
        final String lexical = formatYear(year) + String.format("-%02d-%02d%s", month, day, tz);
        return new DateValue(this, lexical);
    }

    private Sequence buildTime(final MapType record, final boolean hasTimezone) throws XPathException {
        final int hours = getIntField(record, "hours");
        final int minutes = getIntField(record, "minutes");
        requireRange("hours", hours, 0, 23);
        requireRange("minutes", minutes, 0, 59);
        final BigDecimal seconds = getSecondsField(record);
        final String secStr = formatSeconds(seconds);
        final String tz = hasTimezone ? getTimezone(record) : "";
        final String lexical = String.format("%02d:%02d:%s%s", hours, minutes, secStr, tz);
        return new TimeValue(this, lexical);
    }

    private Sequence buildGYear(final MapType record, final boolean hasTimezone) throws XPathException {
        final int year = getIntField(record, "year");
        final String tz = hasTimezone ? getTimezone(record) : "";
        final String lexical = formatYear(year) + tz;
        return new GYearValue(this, lexical);
    }

    private Sequence buildGYearMonth(final MapType record, final boolean hasTimezone) throws XPathException {
        final int year = getIntField(record, "year");
        final int month = getIntField(record, "month");
        requireRange("month", month, 1, 12);
        final String tz = hasTimezone ? getTimezone(record) : "";
        final String lexical = formatYear(year) + String.format("-%02d%s", month, tz);
        return new GYearMonthValue(this, lexical);
    }

    private Sequence buildGMonthDay(final MapType record, final boolean hasTimezone) throws XPathException {
        final int month = getIntField(record, "month");
        final int day = getIntField(record, "day");
        requireRange("month", month, 1, 12);
        requireRange("day", day, 1, daysInMonth(2000, month)); // 2000 is a leap year for Feb 29
        final String tz = hasTimezone ? getTimezone(record) : "";
        final String lexical = String.format("--%02d-%02d%s", month, day, tz);
        return new GMonthDayValue(this, lexical);
    }

    private Sequence buildGMonth(final MapType record, final boolean hasTimezone) throws XPathException {
        final int month = getIntField(record, "month");
        requireRange("month", month, 1, 12);
        final String tz = hasTimezone ? getTimezone(record) : "";
        final String lexical = String.format("--%02d%s", month, tz);
        return new GMonthValue(this, lexical);
    }

    private Sequence buildGDay(final MapType record, final boolean hasTimezone) throws XPathException {
        final int day = getIntField(record, "day");
        requireRange("day", day, 1, 31);
        final String tz = hasTimezone ? getTimezone(record) : "";
        final String lexical = String.format("---%02d%s", day, tz);
        return new GDayValue(this, lexical);
    }

    private String formatSeconds(final BigDecimal seconds) throws XPathException {
        if (seconds == null) {
            return "00";
        }
        final int sign = seconds.signum();
        // Accept signed -0 (treated as 0); reject negative.
        if (sign < 0) {
            throw new XPathException(this, ErrorCodes.FODT0006,
                    "Seconds value out of range (must be 0..<60): " + seconds);
        }
        if (seconds.compareTo(BigDecimal.valueOf(60)) >= 0) {
            throw new XPathException(this, ErrorCodes.FODT0006,
                    "Seconds value out of range (must be 0..<60): " + seconds);
        }
        // Strip trailing zeroes from fractional part for canonical form.
        BigDecimal val = seconds;
        if (val.scale() > 0) {
            val = val.stripTrailingZeros();
            if (val.scale() < 0) {
                val = val.setScale(0, RoundingMode.UNNECESSARY);
            }
        }
        final BigDecimal whole = val.setScale(0, RoundingMode.DOWN);
        final BigDecimal frac = val.subtract(whole);
        final String wholeStr = String.format("%02d", whole.intValueExact());
        if (frac.signum() == 0) {
            return wholeStr;
        }
        // Build fractional part as ".XXXX..." with no trailing zeroes.
        String fracStr = frac.toPlainString();
        // frac is in [0,1); toPlainString returns "0.XXX"
        final int dotIdx = fracStr.indexOf('.');
        if (dotIdx >= 0) {
            fracStr = fracStr.substring(dotIdx);
        } else {
            fracStr = "";
        }
        return wholeStr + fracStr;
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

        final org.exist.xquery.value.AtomicValue av = (org.exist.xquery.value.AtomicValue) args[0].itemAt(0);
        if (!(av instanceof AbstractDateTimeValue)) {
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "fn:parts-of-dateTime expects a date/time value, got: " + Type.getTypeName(av.getType()));
        }
        final AbstractDateTimeValue dt = (AbstractDateTimeValue) av;
        final int t = dt.getType();
        final boolean hasYear = (t == Type.DATE_TIME || t == Type.DATE_TIME_STAMP || t == Type.DATE
                || t == Type.G_YEAR_MONTH || t == Type.G_YEAR);
        final boolean hasMonth = (t == Type.DATE_TIME || t == Type.DATE_TIME_STAMP || t == Type.DATE
                || t == Type.G_YEAR_MONTH || t == Type.G_MONTH || t == Type.G_MONTH_DAY);
        final boolean hasDay = (t == Type.DATE_TIME || t == Type.DATE_TIME_STAMP || t == Type.DATE
                || t == Type.G_DAY || t == Type.G_MONTH_DAY);
        final boolean hasTime = (t == Type.DATE_TIME || t == Type.DATE_TIME_STAMP || t == Type.TIME);

        final MapType result = new MapType(this, context, null);

        if (hasYear) {
            result.add(new StringValue("year"),
                    new IntegerValue(this, dt.getPart(AbstractDateTimeValue.YEAR)));
        }
        if (hasMonth) {
            result.add(new StringValue("month"),
                    new IntegerValue(this, dt.getPart(AbstractDateTimeValue.MONTH)));
        }
        if (hasDay) {
            result.add(new StringValue("day"),
                    new IntegerValue(this, dt.getPart(AbstractDateTimeValue.DAY)));
        }
        if (hasTime) {
            // XQ4 spec uses plural "hours"/"minutes"/"seconds"
            result.add(new StringValue("hours"),
                    new IntegerValue(this, dt.getPart(AbstractDateTimeValue.HOUR)));
            result.add(new StringValue("minutes"),
                    new IntegerValue(this, dt.getPart(AbstractDateTimeValue.MINUTE)));
            final int sec = dt.getPart(AbstractDateTimeValue.SECOND);
            final int millis = dt.getPart(AbstractDateTimeValue.MILLISECOND);
            final BigDecimal seconds = BigDecimal.valueOf(sec)
                    .add(BigDecimal.valueOf(millis, 3));
            result.add(new StringValue("seconds"),
                    new DecimalValue(this, seconds));
        }

        // timezone as xs:dayTimeDuration (or absent)
        final Sequence tz = dt.getTimezone();
        if (!tz.isEmpty()) {
            result.add(new StringValue("timezone"), tz);
        }

        return result;
    }
}
