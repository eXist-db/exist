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

/**
 * fn:build-dateTime($date, $time) — Combine xs:date + xs:time into xs:dateTime.
 * fn:parts-of-dateTime($dateTime) — Decompose xs:dateTime into a map of components.
 *
 * The map returned by parts-of-dateTime has keys: year, month, day, hour, minute,
 * seconds (as xs:decimal including fractional), timezone (as xs:dayTimeDuration).
 * When the Parser branch merges, these maps will be compatible with record type checking.
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
            return buildDateTime(args);
        } else {
            return partsOfDateTime(args);
        }
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
