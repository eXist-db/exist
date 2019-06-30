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

import org.apache.commons.lang3.StringUtils;

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
            return new DateTimeValue(p.parse());
        } catch (final IllegalArgumentException i) {
            throw new XPathException(ErrorCodes.FORG0010, "Invalid Date time " + value, i);
        }
    }

    private class Parser {
        private final char[] WS = {0x000A, 0x0009, 0x000D, 0x0020};
        private final String WS_STR = new String(WS);

        private final String[] dayNames = {
                "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun",
                "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
        };

        private final String[] monthNames = {
                "Jan", "Feb", "Mar", "Apr", "May", "Jun",
                "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
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

        /**
         * <p>Parse a formatted <code>String</code> into an <code>XMLGregorianCalendar</code>.</p>
         * <p>
         * <p>If <code>String</code> is not formatted as a legal <code>IETF Date</code> value,
         * an <code>IllegalArgumentException</code> is thrown.</p>
         * <pre>
         * input	::=	(dayname ","? S)? ((datespec S time) | asctime)
         * datespec	::=	daynum dsep monthname dsep year
         * dsep	::=	S | (S? "-" S?)
         * daynum	::=	digit digit?
         * year	::=	digit digit (digit digit)?
         * digit	::=	[0-9]
         * time	::=	hours ":" minutes (":" seconds)? (S? timezone)?
         * hours	::=	digit digit?
         * minutes	::=	digit digit
         * seconds	::=	digit digit ("." digit+)?
         * S ::= (x0A|x09|x0D|x20)+
         * </pre>
         *
         * @throws IllegalArgumentException If <code>String</code> is not formatted as a legal <code>IETF Date</code> value.
         */
        public XMLGregorianCalendar parse() throws IllegalArgumentException {
            dayName();
            dateSpec();
            if (vidx != vlen) {
                throw new IllegalArgumentException(value);
            }
            return TimeUtils
                    .getInstance()
                    .getFactory()
                    .newXMLGregorianCalendar(year, month, day, hour, minute, second, fractionalSecond, timezone);
        }

        private void dayName() {
            if (StringUtils.startsWithAny(value, dayNames)) {
                skipTo(WS_STR);
                vidx++;
            }
        }

        private void dateSpec() throws IllegalArgumentException {
            if (isWS(peek())) {
                skipWS();
            }
            if (StringUtils.startsWithAny(value.substring(vidx), monthNames)) {
                asctime();
            } else {
                rfcDate();
            }
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
            if (vidx >= vlen) {
                throw new IllegalArgumentException(value);
            }
            final String monthName = value.substring(vstart, vidx);
            final int idx = Arrays.asList(monthNames).indexOf(monthName);
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
            skipWS();
            timezone();
        }

        private void hours() throws IllegalArgumentException {
            hour = parseInt(2, 2);
        }

        private void minutes() throws IllegalArgumentException {
            skip(':');
            minute = parseInt(2, 2);
            checkMinutes(minute);
        }

        private void seconds() throws IllegalArgumentException {
            if (isWS(peek())) {
                second = 0;
                return;
            }
            skip(':');
            second = parseInt(2, 2);
            fractionalSecond = parseBigDecimal();
        }

        private void timezone() throws IllegalArgumentException {
            if (!StringUtils.startsWithAny(value.substring(vidx), tzNames)) {
                tzoffset();
                return;
            }
            parseTimezoneName();
        }

        private void parseTimezoneName() {
            final int vstart = vidx;
            while (isUpperCaseLetter(peek())) {
                vidx++;
            }
            final String tzName = value.substring(vstart, vidx);
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
            final int h = parseInt(1, 2);

            if (peek() == ':') {
                skip(':');
            }

            int m = 0;
            if (isDigit(peek())) {
                m = parseInt(2, 2);
            }
            checkMinutes(m);

            final int offset = h * 60 + m;
            final int factor = (sign == '+' ? 1 : -1);
            timezone = offset * factor;

            // cut off whitespace and optional timezone in parenthesis
            if (isWS(peek()) || peek() == '(') {
                vidx = vlen;
            }
        }

        private void dsep() throws IllegalArgumentException {
            if (isWS(peek())) {
                skipWS();
            }
            if (peek() != '-') {
                return;
            }
            skip('-');
            if (isWS(peek())) {
                skipWS();
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

        private void skipTo(String sequence) throws IllegalArgumentException {
            while (sequence.indexOf(peek()) < 0) {
                read();
            }
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
            return new BigDecimal(value.substring(vstart, vidx));
        }

        private void checkMinutes(int m) {
            if (m >= 60 || m < 0) {
                throw new IllegalArgumentException(value);
            }
        }

        private boolean isWS(char c) {
            return (WS_STR.indexOf(c) >= 0);
        }

        private boolean isDigit(char ch) {
            return '0' <= ch && ch <= '9';
        }

        private boolean isUpperCaseLetter(char ch) {
            return 'A' <= ch && ch <= 'Z';
        }
    }
}
