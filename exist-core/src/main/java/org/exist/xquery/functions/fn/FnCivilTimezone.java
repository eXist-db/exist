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
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AbstractDateTimeValue;
import org.exist.xquery.value.DayTimeDurationValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.datatype.DatatypeConstants;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.zone.ZoneRulesException;

/**
 * Implements XQuery 4.0 fn:civil-timezone.
 *
 * fn:civil-timezone($value as xs:dateTime, $place as xs:string?) as xs:dayTimeDuration
 *
 * Returns the civil timezone offset for a given dateTime at a given IANA timezone location,
 * accounting for daylight savings time transitions.
 */
public class FnCivilTimezone extends BasicFunction {

    private static final ErrorCodes.ErrorCode FODT0004 = new ErrorCodes.ErrorCode("FODT0004",
            "No timezone data available");

    public static final FunctionSignature[] FN_CIVIL_TIMEZONE = {
            new FunctionSignature(
                    new QName("civil-timezone", Function.BUILTIN_FUNCTION_NS),
                    "Returns the civil timezone offset for a dateTime at a place.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("value", Type.DATE_TIME, Cardinality.EXACTLY_ONE, "The dateTime to look up"),
                            new FunctionParameterSequenceType("place", Type.STRING, Cardinality.ZERO_OR_ONE, "IANA timezone name (e.g. 'America/New_York')")
                    },
                    new FunctionReturnSequenceType(Type.DAY_TIME_DURATION, Cardinality.EXACTLY_ONE, "the civil timezone offset")),
            new FunctionSignature(
                    new QName("civil-timezone", Function.BUILTIN_FUNCTION_NS),
                    "Returns the civil timezone offset for a dateTime using the default place.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("value", Type.DATE_TIME, Cardinality.EXACTLY_ONE, "The dateTime to look up")
                    },
                    new FunctionReturnSequenceType(Type.DAY_TIME_DURATION, Cardinality.EXACTLY_ONE, "the civil timezone offset"))
    };

    public FnCivilTimezone(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final AbstractDateTimeValue dtv = (AbstractDateTimeValue) args[0].itemAt(0);
        final XMLGregorianCalendar cal = (XMLGregorianCalendar) dtv.calendar.clone();

        // Determine the IANA zone
        final ZoneId zone;
        if (args.length > 1 && !args[1].isEmpty()) {
            final String place = args[1].getStringValue();
            try {
                zone = ZoneId.of(place);
            } catch (final java.time.DateTimeException e) {
                throw new XPathException(this, FODT0004,
                        "Unknown timezone: " + place);
            }
        } else {
            // Use system default timezone as the "default place"
            zone = ZoneId.systemDefault();
        }

        // Convert the dateTime to a LocalDateTime (ignoring any timezone on the value)
        final int year = cal.getYear();
        final int month = cal.getMonth();
        final int day = cal.getDay();
        final int hour = cal.getHour() == DatatypeConstants.FIELD_UNDEFINED ? 0 : cal.getHour();
        final int minute = cal.getMinute() == DatatypeConstants.FIELD_UNDEFINED ? 0 : cal.getMinute();
        final int second = cal.getSecond() == DatatypeConstants.FIELD_UNDEFINED ? 0 : cal.getSecond();

        final LocalDateTime ldt = LocalDateTime.of(year, month, day, hour, minute, second);

        // Get the offset at that local date-time in the given zone
        final ZonedDateTime zdt = ldt.atZone(zone);
        final ZoneOffset offset = zdt.getOffset();
        final int totalSeconds = offset.getTotalSeconds();

        // Convert to xs:dayTimeDuration
        final String dur = secondsToDayTimeDuration(totalSeconds);
        return new DayTimeDurationValue(this, dur);
    }

    private static String secondsToDayTimeDuration(final int totalSeconds) {
        final boolean negative = totalSeconds < 0;
        int abs = Math.abs(totalSeconds);
        final int hours = abs / 3600;
        abs %= 3600;
        final int minutes = abs / 60;
        final int seconds = abs % 60;

        final StringBuilder sb = new StringBuilder();
        if (negative) {
            sb.append('-');
        }
        sb.append("PT");
        if (hours > 0) {
            sb.append(hours).append('H');
        }
        if (minutes > 0) {
            sb.append(minutes).append('M');
        }
        if (seconds > 0) {
            sb.append(seconds).append('S');
        }
        // If all zero, output PT0S
        if (hours == 0 && minutes == 0 && seconds == 0) {
            sb.append("0S");
        }
        return sb.toString();
    }
}
