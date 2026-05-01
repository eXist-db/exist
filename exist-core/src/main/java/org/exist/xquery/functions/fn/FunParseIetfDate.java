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
import org.exist.xquery.value.*;

import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.XMLGregorianCalendar;

import java.math.BigDecimal;
import java.math.BigInteger;

import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;

/**
 * Parses a string containing the date and time in IETF format,
 * returning the corresponding xs:dateTime value.
 *
 * @author Juri Leino (juri@existsolutions.com)
 */
public class FunParseIetfDate extends BasicFunction {

    private static FunctionParameterSequenceType IETF_DATE =
            new FunctionParameterSequenceType(
                    "value", Type.STRING, Cardinality.ZERO_OR_ONE, "The IETF-dateTime string");

    private static FunctionReturnSequenceType RETURN =
            new FunctionReturnSequenceType(
                    Type.DATE_TIME, Cardinality.ZERO_OR_ONE, "The parsed date");


    public final static FunctionSignature FNS_PARSE_IETF_DATE = new FunctionSignature(
            new QName("parse-ietf-date", Function.BUILTIN_FUNCTION_NS),
            "Parses a string containing the date and time in IETF format,\n" +
                    "returning the corresponding xs:dateTime value.",
            new SequenceType[]{IETF_DATE},
            RETURN
    );

    public FunParseIetfDate(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        if (args[0].isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }
        final String value = args[0].getStringValue();
        final Parser p = new Parser(value.trim());

        try {
            return new DateTimeValue(this, p.parse());
        } catch (final IllegalArgumentException i) {
            throw new XPathException(this, ErrorCodes.FORG0010, "Invalid Date time " + value, i);
        }
    }

    private class Parser {
        private final char[] WS = {0x000A, 0x0009, 0x000D, 0x0020};
        private final String WS_STR = new String(WS);

        private final String[] lowerDayNames = {
                "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
                "mon", "tue", "wed", "thu", "fri", "sat", "sun"
        };

        private final String[] lowerMonthNames = {
                "jan", "feb", "mar", "apr", "may", "jun",
                "jul", "aug", "sep", "oct", "nov", "dec"
        };

        private final String[] tzNames = {
                "UT", "UTC", "GMT", "EST", "EDT", "CST", "CDT", "MST", "MDT", "PST", "PDT"
        };

        private final Map<String, Integer> TZ_MAP = initMap();
        private final String value;
        private final int vlen;
        private int vidx;

        private BigInteger year = null;
        private int month = DatatypeConstants.FIELD_UNDEFINED;
        private int day = DatatypeConstants.FIELD_UNDEFINED;

        private int hour = DatatypeConstants.FIELD_UNDEFINED;
        private int minute = DatatypeConstants.FIELD_UNDEFINED;
        private int second = DatatypeConstants.FIELD_UNDEFINED;
        private BigDecimal fractionalSecond = null;

        private int timezone = DatatypeConstants.FIELD_UNDEFINED;

        private Parser(String value) {
            this.value = value;
            this.vlen = value.length();
        }

        private Map<String, Integer> initMap() {
            Map<String, Integer> result = new HashMap<>();
            result.put("UT", 0);
            result.put("UTC", 0);
            result.put("GMT", 0);
            result.put("EST", -5);
            result.put("EDT", -4);
            result.put("CST", -6);
            result.put("CDT", -5);
            result.put("MST", -7);
            result.put("MDT", -6);
            result.put("PST", -8);
            result.put("PDT", -7);
            return result;
        }

        public XMLGregorianCalendar parse() throws IllegalArgumentException {
            dayName();
            dateSpec();
            if (vidx != vlen) {
                throw new IllegalArgumentException(value);
            }
            // Default to UTC when no timezone was supplied (per spec note in tests).
            if (timezone == DatatypeConstants.FIELD_UNDEFINED) {
                timezone = 0;
            }
            // Handle 24:00 / 24:00:00 as midnight at the end of the day.
            final boolean midnightEndOfDay = hour == 24 && minute == 0
                    && (second == DatatypeConstants.FIELD_UNDEFINED || second == 0)
                    && (fractionalSecond == null || fractionalSecond.signum() == 0);
            if (midnightEndOfDay) {
                hour = 0;
                if (second == DatatypeConstants.FIELD_UNDEFINED) {
                    second = 0;
                }
                final XMLGregorianCalendar cal = TimeUtils
                        .getInstance()
                        .getFactory()
                        .newXMLGregorianCalendar(year, month, day, hour, minute, second, fractionalSecond, timezone);
                cal.add(TimeUtils.getInstance().getFactory().newDuration(true, 0, 0, 1, 0, 0, 0));
                return cal;
            }
            if (second == DatatypeConstants.FIELD_UNDEFINED) {
                second = 0;
            }
            return TimeUtils
                    .getInstance()
                    .getFactory()
                    .newXMLGregorianCalendar(year, month, day, hour, minute, second, fractionalSecond, timezone);
        }

        private void dayName() {
            final String lower = value.substring(vidx).toLowerCase();
            String matched = null;
            for (final String dn : lowerDayNames) {
                if (lower.startsWith(dn)) {
                    matched = dn;
                    break;
                }
            }
            if (matched == null) {
                return;
            }
            vidx += matched.length();
            // The character after the day name must be whitespace or a comma
            // (followed by whitespace). "Wed,20 ..." is invalid (errs15).
            boolean ateComma = false;
            if (peek() == ',') {
                vidx++;
                ateComma = true;
            }
            if (!isWS(peek())) {
                if (ateComma) {
                    throw new IllegalArgumentException(value);
                }
                // No comma either: only valid if we're at end (unlikely for full input)
                if (vidx != vlen) {
                    throw new IllegalArgumentException(value);
                }
            }
        }

        private void dateSpec() throws IllegalArgumentException {
            if (isWS(peek())) {
                skipWS();
            }
            if (startsWithMonthName(value.substring(vidx))) {
                asctime();
            } else {
                rfcDate();
            }
        }

        private boolean startsWithMonthName(final String s) {
            final String lower = s.toLowerCase();
            for (final String mn : lowerMonthNames) {
                if (lower.startsWith(mn)) {
                    return true;
                }
            }
            return false;
        }

        private void rfcDate() throws IllegalArgumentException {
            day();
            dsep();
            month();
            dsep();
            year();
            skipWS();
            time();
        }

        private void asctime() throws IllegalArgumentException {
            month();
            dsep();
            day();
            skipWS();
            time();
            skipWS();
            year();
        }

        private void year() throws IllegalArgumentException {
            final int vstart = vidx;

            while (isDigit(peek())) {
                vidx++;
            }
            final int digits = vidx - vstart;
            String yearString;
            if (digits == 2) {
                yearString = "19" + value.substring(vstart, vidx);
            } else if (digits == 4) {
                yearString = value.substring(vstart, vidx);
            } else {
                throw new IllegalArgumentException(value);
            }

            year = new BigInteger(yearString);
        }

        private void month() throws IllegalArgumentException {
            final int vstart = vidx;
            vidx += 3;
            if (vidx > vlen) {
                throw new IllegalArgumentException(value);
            }
            final String monthName = value.substring(vstart, vidx).toLowerCase();
            final int idx = Arrays.asList(lowerMonthNames).indexOf(monthName);
            if (idx < 0) {
                throw new IllegalArgumentException(value);
            }
            month = idx + 1;
        }

        private void day() throws IllegalArgumentException {
            day = parseInt(1, 2);
        }

        private void time() throws IllegalArgumentException {
            hours();
            minutes();
            seconds();
            // Whitespace before the timezone is optional. We must avoid greedily
            // consuming digits following the time (which can be the year in
            // asctime form, e.g. "Aug 20 19:36 2014").
            final int wsStart = vidx;
            if (isWS(peek())) {
                skipWS();
            }
            if (looksLikeTimezone()) {
                timezone();
            } else {
                vidx = wsStart;
            }
        }

        private boolean looksLikeTimezone() {
            if (vidx >= vlen) {
                return false;
            }
            final char c = peek();
            return c == '+' || c == '-' || isAsciiLetter(c);
        }

        private void hours() throws IllegalArgumentException {
            hour = parseInt(1, 2);
        }

        private void minutes() throws IllegalArgumentException {
            skip(':');
            minute = parseInt(2, 2);
            checkMinutes(minute);
        }

        private void seconds() throws IllegalArgumentException {
            if (peek() != ':') {
                // No colon means no seconds component
                second = 0;
                return;
            }
            skip(':');
            second = parseInt(2, 2);
            fractionalSecond = parseBigDecimal();
        }

        private void timezone() throws IllegalArgumentException {
            if (startsWithTzName()) {
                parseTimezoneName();
            } else {
                tzoffset();
            }
        }

        private boolean startsWithTzName() {
            final String upper = value.substring(vidx).toUpperCase();
            for (final String tz : tzNames) {
                if (upper.startsWith(tz)) {
                    // Make sure the following character isn't another letter
                    // (so "GMT" matches but "GMTSomething" doesn't accidentally
                    // produce the wrong timezone). The longest valid name is 3.
                    final int after = vidx + tz.length();
                    if (after >= vlen || !isAsciiLetter(value.charAt(after))) {
                        return true;
                    }
                }
            }
            return false;
        }

        private void parseTimezoneName() {
            final int vstart = vidx;
            while (isAsciiLetter(peek())) {
                vidx++;
            }
            final String tzName = value.substring(vstart, vidx).toUpperCase();
            if (!TZ_MAP.containsKey(tzName)) {
                throw new IllegalArgumentException(value);
            }
            timezone = TZ_MAP.get(tzName) * 60;
        }

        private void tzoffset() throws IllegalArgumentException {
            final char sign = peek();
            if (!(sign == '+' || sign == '-')) {
                throw new IllegalArgumentException(value);
            }
            vidx++;
            final int digitsStart = vidx;
            while (isDigit(peek()) && (vidx - digitsStart) < 4) {
                vidx++;
            }
            final int totalDigits = vidx - digitsStart;
            if (totalDigits == 0) {
                throw new IllegalArgumentException(value);
            }

            int h;
            int m = 0;
            if (peek() == ':') {
                // Hour digits before colon, then optional minutes
                if (totalDigits > 2) {
                    throw new IllegalArgumentException(value);
                }
                h = Integer.parseInt(value.substring(digitsStart, vidx));
                skip(':');
                if (isDigit(peek())) {
                    final int mStart = vidx;
                    while (isDigit(peek()) && (vidx - mStart) < 2) {
                        vidx++;
                    }
                    final int mDigits = vidx - mStart;
                    if (mDigits != 2) {
                        throw new IllegalArgumentException(value);
                    }
                    m = Integer.parseInt(value.substring(mStart, vidx));
                }
                // else: trailing colon with no minutes is allowed (test 47, 60)
            } else {
                // No colon: split based on number of digits
                switch (totalDigits) {
                    case 1, 2 -> h = Integer.parseInt(value.substring(digitsStart, vidx));
                    case 3 -> {
                        h = Integer.parseInt(value.substring(digitsStart, digitsStart + 1));
                        m = Integer.parseInt(value.substring(digitsStart + 1, vidx));
                    }
                    default -> { // 4
                        h = Integer.parseInt(value.substring(digitsStart, digitsStart + 2));
                        m = Integer.parseInt(value.substring(digitsStart + 2, vidx));
                    }
                }
            }
            checkMinutes(m);
            checkHours(h);
            timezone = (h * 60 + m) * (sign == '+' ? 1 : -1);

            // After the offset, an optional parenthesized timezone-name comment
            // is allowed (e.g. "-05:00(EST)" or "-05:00  (  EST  )"). Do NOT
            // greedily consume any other trailing whitespace; the year may
            // follow in asctime form.
            final int beforeTrailingWs = vidx;
            if (isWS(peek())) {
                skipWS();
            }
            if (peek() == '(') {
                // Parenthesized comment (e.g. "(CET)") — per W3C XPath F&O
                // §19.1.5, the comment is informational only and need not match
                // a known timezone abbreviation. Skip everything to the closing
                // parenthesis.
                vidx++;
                while (vidx < vlen && value.charAt(vidx) != ')') {
                    vidx++;
                }
                if (vidx < vlen && value.charAt(vidx) == ')') {
                    vidx++;
                }
            } else {
                vidx = beforeTrailingWs;
            }
        }

        private void dsep() throws IllegalArgumentException {
            boolean consumed = false;
            if (isWS(peek())) {
                skipWS();
                consumed = true;
            }
            if (peek() == '-') {
                skip('-');
                consumed = true;
                if (isWS(peek())) {
                    skipWS();
                }
            }
            if (!consumed) {
                throw new IllegalArgumentException(value);
            }
        }

        private void skipWS() throws IllegalArgumentException {
            if (!isWS(peek())) {
                throw new IllegalArgumentException(value);
            }

            while (isWS(peek())) {
                vidx++;
            }
        }

        private char peek() {
            if (vidx == vlen) {
                return (char) -1;
            }
            return value.charAt(vidx);
        }

        private char read() throws IllegalArgumentException {
            if (vidx == vlen) {
                throw new IllegalArgumentException(value);
            }
            return value.charAt(vidx++);
        }

        private void skip(char ch) throws IllegalArgumentException {
            if (read() != ch) throw new IllegalArgumentException(value);
        }

        private int parseInt(int minDigits, int maxDigits) throws IllegalArgumentException {
            final int vstart = vidx;
            while (isDigit(peek()) && (vidx - vstart) < maxDigits) {
                vidx++;
            }
            if ((vidx - vstart) < minDigits) {
                // we are expecting more digits
                throw new IllegalArgumentException(value);
            }

            return Integer.parseInt(value.substring(vstart, vidx));
        }

        private BigDecimal parseBigDecimal() throws IllegalArgumentException {
            final int vstart = vidx;

            if (peek() == '.') {
                vidx++;
            } else {
                return new BigDecimal("0");
            }
            while (isDigit(peek())) {
                vidx++;
            }
            if (vidx - vstart < 2) {
                // Just "." with no digits is invalid (errs27)
                throw new IllegalArgumentException(value);
            }
            return new BigDecimal(value.substring(vstart, vidx));
        }

        private void checkMinutes(int m) {
            if (m >= 60 || m < 0) {
                throw new IllegalArgumentException(value);
            }
        }

        private void checkHours(int h) {
            // Per XSD, timezone offset hours range is 0..14
            if (h < 0 || h > 14) {
                throw new IllegalArgumentException(value);
            }
        }

        private boolean isWS(char c) {
            return (WS_STR.indexOf(c) >= 0);
        }

        private boolean isDigit(char ch) {
            return '0' <= ch && ch <= '9';
        }

        private boolean isAsciiLetter(char ch) {
            return ('A' <= ch && ch <= 'Z') || ('a' <= ch && ch <= 'z');
        }
    }
}
