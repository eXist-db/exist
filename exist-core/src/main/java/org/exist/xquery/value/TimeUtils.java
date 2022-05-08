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
package org.exist.xquery.value;

import net.jcip.annotations.NotThreadSafe;

import javax.xml.datatype.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/**
 * Centralizes access to time-related utility functions.  Mostly delegates to the
 * XML datatype factory, serving as a central chokepoint to control concurrency
 * issues.  It's not clear if instances of the factory are in fact thread-safe or not;
 * if they turn out not to be, it will be easy to either synchronize access or create
 * more instances here as required.
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
@NotThreadSafe
public class TimeUtils {

    private static final TimeUtils INSTANCE = new TimeUtils();
    public static final Duration ONE_DAY = INSTANCE.factory.newDuration(true, 0, 0, 1, 0, 0, 0);

    private final DatatypeFactory factory;

    // assume it's thread-safe, if not synchronize all access
    private int timezoneOffset;
    private boolean timezoneOverriden;

    private TimeUtils() {
        // singleton, keep constructor private
        try {
            factory = DatatypeFactory.newInstance();
        } catch (final DatatypeConfigurationException e) {
            throw new RuntimeException("unable to instantiate an XML datatype factory", e);
        }
    }

    public static TimeUtils getInstance() {
        return INSTANCE;
    }

    public DatatypeFactory getFactory() {
        return factory;
    }

    /**
     * Set the offset of the local timezone, ignoring the default provided by the OS.
     * Mainly useful for testing.
     *
     * NOTE calling this method is not thread-safe and has a global impact
     * it should only be used for test cases.
     *
     * @param millis the timezone offset in milliseconds, positive or negative
     */
    void overrideLocalTimezoneOffset(final int millis) {
        timezoneOffset = millis;
        timezoneOverriden = true;
    }

    /**
     * Cancel any timezone override that may be in effect, reverting back to the OS value.
     *
     * NOTE calling this method is not thread-safe and has a global impact
     * it should only be used for test cases.
     */
    void resetLocalTimezoneOffset() {
        timezoneOverriden = false;
    }

    public int getLocalTimezoneOffsetMillis() {
        final int dstOffset = Calendar.getInstance(TimeZone.getDefault()).get(Calendar.DST_OFFSET);
        return timezoneOverriden ? timezoneOffset : TimeZone.getDefault().getRawOffset() + dstOffset;
    }

    public int getLocalTimezoneOffsetMinutes() {
        return getLocalTimezoneOffsetMillis() / 60000;
    }

    public Duration newDuration(final long durationInMilliSeconds) {
        return factory.newDuration(durationInMilliSeconds);
    }

    public Duration newDuration(final String lexicalRepresentation) {
        return factory.newDuration(lexicalRepresentation);
    }

    public Duration newDuration(final boolean isPositive, final int years, final int months, final int days, final int hours, final int minutes, final int seconds) {
        return factory.newDuration(isPositive, years, months, days, hours, minutes, seconds);
    }

    public Duration newDuration(final boolean isPositive, final BigInteger years, final BigInteger months, final BigInteger days, final BigInteger hours, final BigInteger minutes, final BigDecimal seconds) {
        return factory.newDuration(isPositive, years, months, days, hours, minutes, seconds);
    }

    public Duration newDurationDayTime(final long durationInMilliseconds) {
        return factory.newDurationDayTime(durationInMilliseconds);
    }

    public Duration newDurationDayTime(final String lexicalRepresentation) {
        return factory.newDurationDayTime(lexicalRepresentation);
    }

    public Duration newDurationDayTime(final boolean isPositive, final int day, final int hour, final int minute, final int second) {
        return factory.newDurationDayTime(isPositive, day, hour, minute, second);
    }

    public Duration newDurationDayTime(final boolean isPositive, final BigInteger day, final BigInteger hour, final BigInteger minute, final BigInteger second) {
        return factory.newDurationDayTime(isPositive, day, hour, minute, second);
    }

    public Duration newDurationYearMonth(final long durationInMilliseconds) {
        return factory.newDurationYearMonth(durationInMilliseconds);
    }

    public Duration newDurationYearMonth(final String lexicalRepresentation) {
        return factory.newDurationYearMonth(lexicalRepresentation);
    }

    public Duration newDurationYearMonth(final boolean isPositive, final int year, final int month) {
        return factory.newDurationYearMonth(isPositive, year, month);
    }

    public Duration newDurationYearMonth(final boolean isPositive, final BigInteger year, final BigInteger month) {
        return factory.newDurationYearMonth(isPositive, year, month);
    }

    public XMLGregorianCalendar newXMLGregorianCalendar() {
        return factory.newXMLGregorianCalendar();
    }

    public XMLGregorianCalendar newXMLGregorianCalendar(final int year, final int month, final int day, final int hour, final int minute, final int second, final int millisecond, final int timezone) {
        return factory.newXMLGregorianCalendar(year, month, day, hour, minute, second, millisecond, timezone);
    }

    public XMLGregorianCalendar newXMLGregorianCalendar(final String lexicalRepresentation) {
        return factory.newXMLGregorianCalendar(lexicalRepresentation);
    }

    public XMLGregorianCalendar newXMLGregorianCalendar(final BigInteger year, final int month, final int day, final int hour, final int minute, final int second, final BigDecimal fractionalSecond, final int timezone) {
        return factory.newXMLGregorianCalendar(year, month, day, hour, minute, second, fractionalSecond, timezone);
    }

    public XMLGregorianCalendar newXMLGregorianCalendar(final GregorianCalendar cal) {
        return factory.newXMLGregorianCalendar(cal);
    }

    public XMLGregorianCalendar newXMLGregorianCalendarDate(final int year, final int month, final int day,final  int timezone) {
        return factory.newXMLGregorianCalendarDate(year, month, day, timezone);
    }

    public XMLGregorianCalendar newXMLGregorianCalendarDate(final BigInteger year, final int month, final int day, final int timezone) {
        return factory.newXMLGregorianCalendar(
                year,
                month,
                day,
                DatatypeConstants.FIELD_UNDEFINED,  // hour
                DatatypeConstants.FIELD_UNDEFINED,  // minute
                DatatypeConstants.FIELD_UNDEFINED,  // second
                null,  // fractionalSecond
                timezone);
    }

    public XMLGregorianCalendar newXMLGregorianCalendarTime(final int hours, final int minutes, final int seconds, final int timezone) {
        return factory.newXMLGregorianCalendarTime(hours, minutes, seconds, timezone);
    }

    public XMLGregorianCalendar newXMLGregorianCalendarTime(final int hours, final int minutes, final int seconds, final int milliseconds, final int timezone) {
        return factory.newXMLGregorianCalendarTime(hours, minutes, seconds, milliseconds, timezone);
    }

    public XMLGregorianCalendar newXMLGregorianCalendarTime(final int hours, final int minutes, final int seconds, final BigDecimal fractionalSecond, final int timezone) {
        return factory.newXMLGregorianCalendarTime(hours, minutes, seconds, fractionalSecond, timezone);
    }
}
