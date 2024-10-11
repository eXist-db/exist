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

import com.ibm.icu.text.Collator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.xerces.util.DatatypeMessageFormatter;
import org.exist.xquery.Constants;
import org.exist.xquery.Constants.Comparison;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;

import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.Duration;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.time.Instant;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author wolf
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 * @author ljo
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public abstract class AbstractDateTimeValue extends ComputableValue {

    public final static int YEAR = 0;
    public final static int MONTH = 1;
    public final static int DAY = 2;
    public final static int HOUR = 3;
    public final static int MINUTE = 4;
    public final static int SECOND = 5;
    public final static int MILLISECOND = 6;
    protected static final Pattern negativeDateStart = Pattern.compile("^\\d\\d?-(\\d+)-(.*)");
    protected static final short[] monthData = {306, 337, 0, 31, 61, 92, 122, 153, 184, 214, 245, 275};
    private final static Logger LOG = LogManager.getLogger(AbstractDateTimeValue.class);
    private static final Duration tzLowerBound = TimeUtils.getInstance().newDurationDayTime("-PT14H");
    private static final Duration tzUpperBound = tzLowerBound.negate();
    protected static byte[] daysPerMonth = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
    //Provisionally public
    public final XMLGregorianCalendar calendar;
    private XMLGregorianCalendar implicitCalendar, canonicalCalendar, trimmedCalendar;

    /**
     * Create a new date time value based on the given calendar.  The calendar is
     * <em>not</em> cloned, so it is the subclass's responsibility to make sure there are
     * no external references to it that would allow for mutation.
     *
     * @param calendar the calendar to wrap into an XPath value
     */
    protected AbstractDateTimeValue(final XMLGregorianCalendar calendar) {
        this(null, calendar);
    }

    protected AbstractDateTimeValue(final Expression expression, XMLGregorianCalendar calendar) {
        super(expression);
        this.calendar = calendar;
    }

    protected AbstractDateTimeValue(final String lexicalValue) throws XPathException {
        this(null, lexicalValue);
    }

    protected AbstractDateTimeValue(final Expression expression, String lexicalValue) throws XPathException {
        super(expression);
        lexicalValue = StringValue.trimWhitespace(lexicalValue);

        //lexicalValue = normalizeDate(lexicalValue);
        //lexicalValue = normalizeTime(getType(), lexicalValue);
        try {
            calendar = parse(lexicalValue);
        } catch (final IllegalArgumentException e) {
            throw new XPathException(getExpression(), ErrorCodes.FORG0001, "illegal lexical form for date-time-like value '" + lexicalValue + "' " + e.getMessage(), e);
        }
    }

    /**
     * Utility method that is able to clone a calendar whose year is 0
     * (whatever a year 0 means).
     * It looks like the JDK is unable to do that.
     *
     * @param calendar The Calendar to clone
     * @return the cloned Calendar
     */
    public static XMLGregorianCalendar cloneXMLGregorianCalendar(XMLGregorianCalendar calendar) {
        boolean hacked = false;
        if (calendar.getYear() == 0) {
            calendar.setYear(1);
            hacked = true;
        }
        final XMLGregorianCalendar result = (XMLGregorianCalendar) calendar.clone();
        if (hacked) {
            //reset everything
            calendar.setYear(0);
            //-1 could also be considered
            result.setYear(0);
        }
        return result;
    }

    private static boolean isDigit(char ch) {
        return '0' <= ch && ch <= '9';
    }

    /**
     * Calculate the Julian day number at 00:00 on a given date. Code taken from saxon,
     * see <a href="http://saxon.sourceforge.net">http://saxon.sourceforge.net</a>
     * Original algorithm is taken from
     * http://vsg.cape.com/~pbaum/date/jdalg.htm and
     * http://vsg.cape.com/~pbaum/date/jdalg2.htm
     * (adjusted to handle BC dates correctly)
     *
     * Note that this assumes dates in the proleptic Gregorian calendar.
     *
     * @param year  the year
     * @param month the month (1-12)
     * @param day   the day (1-31)
     * @return the Julian day number
     */
    public static int getJulianDayNumber(int year, int month, int day) {
        int z = year - (month < 3 ? 1 : 0);
        final short f = monthData[month - 1];
        if (z >= 0) {
            return day + f + 365 * z + z / 4 - z / 100 + z / 400 + 1721118;
        } else {
            // for negative years, add 12000 years and then subtract the days!
            z += 12000;
            final int j = day + f + 365 * z + z / 4 - z / 100 + z / 400 + 1721118;
            return j - (365 * 12000 + 12000 / 4 - 12000 / 100 + 12000 / 400);  // number of leap years in 12000 years
        }
    }

    /**
     * Return a calendar with the timezone field set, to be used for order comparison.
     * If the original calendar did not specify a timezone, set the local timezone (unadjusted
     * for daylight savings).  The returned calendars will be totally ordered between themselves.
     * We also set any missing fields to ensure that normalization doesn't discard important data!
     * (This is probably a bug in the JAXP implementation, but the workaround doesn't hurt us,
     * so it's faster to just fix it here.)
     *
     * @return the calendar represented by this object, with the timezone field filled in with an implicit value if necessary
     */
    protected XMLGregorianCalendar getImplicitCalendar() {
        if (implicitCalendar == null) {
            implicitCalendar = (XMLGregorianCalendar) calendar.clone();
            if (calendar.getTimezone() == DatatypeConstants.FIELD_UNDEFINED) {
                implicitCalendar.setTimezone(TimeUtils.getInstance().getLocalTimezoneOffsetMinutes());
            }
            // fill in fields from default reference; don't have to worry about weird combinations of fields being set, since we control that on creation
            switch (getType()) {
                case Type.DATE:
                    implicitCalendar.setTime(0, 0, 0);
                    break;
                case Type.TIME:
                    implicitCalendar.setYear(1972);
                    implicitCalendar.setMonth(12);
                    implicitCalendar.setDay(31);
                    break;
                default:
            }
            implicitCalendar = implicitCalendar.normalize();    // the comparison routines will normalize it anyway, just do it once here
        }
        return implicitCalendar;
    }

    // TODO: method not currently used, apparently the XPath spec never needs to canonicalize
    // date/times after all (see section 17.1.2 on casting)
    protected XMLGregorianCalendar getCanonicalCalendar() {
        if (canonicalCalendar == null) {
            canonicalCalendar = getTrimmedCalendar().normalize();
        }
        return canonicalCalendar;
    }

    public XMLGregorianCalendar getTrimmedCalendar() {
        if (trimmedCalendar == null) {
            trimmedCalendar = cloneXMLGregorianCalendar(calendar);
            final BigDecimal fract = trimmedCalendar.getFractionalSecond();
            if (fract != null) {
                // TODO: replace following algorithm in JDK 1.5 with fract.stripTrailingZeros();
                final String s = fract.toString();
                int i = s.length();
                while (i > 0 && s.charAt(i - 1) == '0') i--;
                if (i == 0) {
                    trimmedCalendar.setFractionalSecond(null);
                } else if (i != s.length()) {
                    trimmedCalendar.setFractionalSecond(new BigDecimal(s.substring(0, i)));
                }
            }
        }
        return trimmedCalendar;
    }

    protected XMLGregorianCalendar getCanonicalOrTrimmedCalendar() {
        try {
            return getCanonicalCalendar();
        } catch (final Exception e) {
            return getTrimmedCalendar();
        }

    }

    protected abstract AbstractDateTimeValue createSameKind(XMLGregorianCalendar cal) throws XPathException;

    public long getTimeInMillis() {
        // use getImplicitCalendar() rather than relying on toGregorianCalendar timezone defaulting
        // to maintain consistency
        return getImplicitCalendar().toGregorianCalendar().getTimeInMillis();
    }

    protected abstract QName getXMLSchemaType();

    public String getStringValue() throws XPathException {
        String r = getTrimmedCalendar().toXMLFormat();
        // hacked to match the format mandated in XPath 2 17.1.2, which is different from the XML Schema canonical format
        //if (r.charAt(r.length()-1) == 'Z') r = r.substring(0, r.length()-1) + "+00:00";

        //Let's try these lexical transformations...
        final boolean startsWithDashDash = r.startsWith("--");
        r = r.replaceAll("--", "");
        if (startsWithDashDash) {
            r = "--" + r;
        }

        final Matcher m = negativeDateStart.matcher(r);
        if (m.matches()) {
            final int year = Integer.parseInt(m.group(1));
            final DecimalFormat df = new DecimalFormat("0000");
            r = "-" + df.format(year) + "-" + m.group(2);
        }

        return r;
    }

    public boolean effectiveBooleanValue() throws XPathException {
        throw new XPathException(getExpression(), ErrorCodes.FORG0006, "effective boolean value invalid operand type: " + Type.getTypeName(getType()));
    }

    public abstract AtomicValue convertTo(int requiredType) throws XPathException;

    public int getPart(int part) {
        switch (part) {
            case YEAR:
                return calendar.getYear();
            case MONTH:
                return calendar.getMonth();
            case DAY:
                return calendar.getDay();
            case HOUR:
                return calendar.getHour();
            case MINUTE:
                return calendar.getMinute();
            case SECOND:
                return calendar.getSecond();
            case MILLISECOND:
                final int mSec = calendar.getMillisecond();
                if (mSec == DatatypeConstants.FIELD_UNDEFINED) {
                    return 0;
                } else {
                    return calendar.getMillisecond();
                }
            default:
                throw new IllegalArgumentException("Invalid argument to method getPart");
        }
    }

    /**
     * Returns true if a timezone is defined.
     *
     * @return true if a timezone is defined.
     */
    public boolean hasTimezone() {
        return calendar.getTimezone() != DatatypeConstants.FIELD_UNDEFINED;
    }

    protected void validateTimezone(DayTimeDurationValue offset) throws XPathException {
        final Duration tz = offset.duration;
        final Number secs = tz.getField(DatatypeConstants.SECONDS);
        if (secs != null && ((BigDecimal) secs).compareTo(BigDecimal.valueOf(0)) != 0) {
            throw new XPathException(getExpression(), ErrorCodes.FODT0003, "duration " + offset + " has fractional minutes so cannot be used as a timezone offset");
        }
        if (!(
                tz.equals(tzLowerBound) ||
                        tz.equals(tzUpperBound) ||
                        (tz.isLongerThan(tzLowerBound) && tz.isShorterThan(tzUpperBound))
        )) {
            throw new XPathException(getExpression(), ErrorCodes.FODT0003, "duration " + offset + " outside valid timezone offset range");
        }
    }

    public AbstractDateTimeValue adjustedToTimezone(DayTimeDurationValue offset) throws XPathException {
        if (offset == null) {
            offset = new DayTimeDurationValue(getExpression(), TimeUtils.getInstance().getLocalTimezoneOffsetMillis());
        }
        validateTimezone(offset);
        XMLGregorianCalendar xgc = (XMLGregorianCalendar) calendar.clone();
        if (xgc.getTimezone() != DatatypeConstants.FIELD_UNDEFINED) {
            if (getType() == Type.DATE) {
                xgc.setTime(0, 0, 0);
            }    // set the fields so we don't lose precision when shifting timezones
            xgc = xgc.normalize();
            xgc.add(offset.duration);
        }
        try {
            xgc.setTimezone((int) (offset.getValue() / 60));
        } catch (final IllegalArgumentException e) {
            throw new XPathException(getExpression(), ErrorCodes.FORG0001, "illegal timezone offset " + offset, e);
        }
        return createSameKind(xgc);
    }

    public AbstractDateTimeValue withoutTimezone() throws XPathException {
        final XMLGregorianCalendar xgc = (XMLGregorianCalendar) calendar.clone();
        xgc.setTimezone(DatatypeConstants.FIELD_UNDEFINED);
        return createSameKind(xgc);
    }

    public Sequence getTimezone() throws XPathException {
        final int tz = calendar.getTimezone();
        if (tz == DatatypeConstants.FIELD_UNDEFINED) {
            return Sequence.EMPTY_SEQUENCE;
        }
        return new DayTimeDurationValue(getExpression(), tz * 60000L);
    }

    @Override
    public boolean compareTo(Collator collator, Comparison operator, AtomicValue other) throws XPathException {
        final int cmp = compareTo(collator, other);
        return switch (operator) {
            case EQ -> cmp == 0;
            case NEQ -> cmp != 0;
            case LT -> cmp < 0;
            case LTEQ -> cmp <= 0;
            case GT -> cmp > 0;
            case GTEQ -> cmp >= 0;
            default -> throw new XPathException(getExpression(), "Unknown operator type in comparison");
        };
    }

    public int compareTo(Collator collator, AtomicValue other) throws XPathException {
        if (other.getType() == getType()) {
            // filling in missing timezones with local timezone, should be total order as per XPath 2.0 10.4
            final int r = getImplicitCalendar().compare(((AbstractDateTimeValue) other).getImplicitCalendar());
            if (r == DatatypeConstants.INDETERMINATE) {
                throw new RuntimeException("indeterminate order between " + this + " and " + other);
            }
            return r;
        }
        throw new XPathException(getExpression(), ErrorCodes.XPTY0004, "Type error: cannot compare " + Type.getTypeName(getType())
                + " to " + Type.getTypeName(other.getType()));
    }

    public AtomicValue max(Collator collator, AtomicValue other) throws XPathException {
        final AbstractDateTimeValue otherDate = other.getType() == getType() ? (AbstractDateTimeValue) other : (AbstractDateTimeValue) other.convertTo(getType());
        return getImplicitCalendar().compare(otherDate.getImplicitCalendar()) > 0 ? this : other;
    }

    public AtomicValue min(Collator collator, AtomicValue other) throws XPathException {
        final AbstractDateTimeValue otherDate = other.getType() == getType() ? (AbstractDateTimeValue) other : (AbstractDateTimeValue) other.convertTo(getType());
        return getImplicitCalendar().compare(otherDate.getImplicitCalendar()) < 0 ? this : other;
    }

    // override for xs:time
    public ComputableValue plus(ComputableValue other) throws XPathException {
        return switch (other.getType()) {
            case Type.YEAR_MONTH_DURATION, Type.DAY_TIME_DURATION -> other.plus(this);
            default -> throw new XPathException(getExpression(),
                    "Operand to plus should be of type xdt:dayTimeDuration or xdt:yearMonthDuration; got: "
                            + Type.getTypeName(other.getType()));
        };
    }

    public ComputableValue mult(ComputableValue other) throws XPathException {
        throw new XPathException(getExpression(), "multiplication is not supported for type " + Type.getTypeName(getType()));
    }

    public ComputableValue div(ComputableValue other) throws XPathException {
        throw new XPathException(getExpression(), "division is not supported for type " + Type.getTypeName(getType()));
    }

    public int conversionPreference(Class<?> javaClass) {
        if (javaClass.isAssignableFrom(DateValue.class)) {
            return 0;
        }
        if (javaClass.isAssignableFrom(XMLGregorianCalendar.class)) {
            return 1;
        }
        if (javaClass.isAssignableFrom(GregorianCalendar.class)) {
            return 2;
        }
        if (Date.class.equals(javaClass)) {
            return 3;
        }
        if (Instant.class.equals(javaClass)) {
            return 4;
        }
        return Integer.MAX_VALUE;
    }

    @Override
    public <T> T toJavaObject(Class<T> target) throws XPathException {
        if (target == Object.class || target.isAssignableFrom(DateValue.class)) {
            return (T) this;
        } else if (target.isAssignableFrom(XMLGregorianCalendar.class)) {
            return (T) calendar.clone();
        } else if (target.isAssignableFrom(GregorianCalendar.class)) {
            return (T) calendar.toGregorianCalendar();
        } else if (Date.class.equals(target)) {
            return (T) calendar.toGregorianCalendar().getTime();
        } else if (Instant.class.equals(target)) {
            return (T)calendar.toGregorianCalendar().toInstant();
        }

        throw new XPathException(getExpression(), "cannot convert value of type " + Type.getTypeName(getType()) + " to Java object of type " + target.getName());
    }

    /* (non-Javadoc)
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    public int compareTo(Object o) {
        if (o instanceof AbstractDateTimeValue dt) {
            return calendar.compare(dt.calendar);
        }

        final AtomicValue other = (AtomicValue) o;
        if (Type.subTypeOf(other.getType(), Type.DATE_TIME))
            try {
                //TODO : find something that will consume less resources
                return calendar.compare(TimeUtils.getInstance().newXMLGregorianCalendar(other.getStringValue()));
            } catch (final XPathException e) {
                LOG.error("Failed to get string value of '{}'", other, e);
                //Why not ?
                return Constants.SUPERIOR;
            }
        else {
            return getType() > other.getType() ? Constants.SUPERIOR : Constants.INFERIOR;
        }
    }

    public boolean equals(Object obj) {
        if (obj instanceof AbstractDateTimeValue dt) {
            return calendar.equals(dt.calendar);
        }

        return false;
    }

    public int hashCode() {
        return calendar.hashCode();
    }

    public int hashCodeWithTimeZone() {
        if (hasTimezone()) {
            return hashCode();
        }

        final TimeZone implicitTimeZone = TimeZone.getDefault();
        if (implicitTimeZone.inDaylightTime(new Date())) {
            implicitTimeZone.setRawOffset(implicitTimeZone.getRawOffset() + implicitTimeZone.getDSTSavings());
        }

        final XMLGregorianCalendar xgc = (XMLGregorianCalendar) calendar.clone();
        xgc.setTimezone((implicitTimeZone.getRawOffset() / 1000) / 60);

        return xgc.hashCode();
    }

    /**
     * Get's the numeric day of the week.
     *
     * Note that numbering starts from {@link Calendar#SUNDAY} which
     * is day 1.
     *
     * @return the day of the week.
     */
    public int getDayOfWeek() {
        return calendar.toGregorianCalendar().get(Calendar.DAY_OF_WEEK);
    }

    public int getDayWithinYear() {
        final int j = getJulianDayNumber(calendar.getYear(), calendar.getMonth(), calendar.getDay());
        final int k = getJulianDayNumber(calendar.getYear(), 1, 1);
        return j - k + 1;
    }

    public int getWeekWithinYear() {
        return calendar.toGregorianCalendar().get(Calendar.WEEK_OF_YEAR);
    }

    public int getWeekWithinMonth() {
        return calendar.toGregorianCalendar().get(Calendar.WEEK_OF_MONTH);
    }

    //copy from org.apache.xerces.jaxp.datatype.XMLGregorianCalendarImpl
    private XMLGregorianCalendar parse(String lexicalRepresentation) {
        // compute format string for this lexical representation.
        String format = null;
        final String lexRep = lexicalRepresentation;
        final int NOT_FOUND = -1;
        int lexRepLength = lexRep.length();

        // current parser needs a format string,
        // use following heuristics to figure out what xml schema date/time
        // datatype this lexical string could represent.
        if (lexRep.indexOf('T') != NOT_FOUND) {
            // found Date Time separater, must be xsd:DateTime
            format = "%Y-%M-%DT%h:%m:%s" + "%z";
        } else if (lexRepLength >= 3 && lexRep.charAt(2) == ':') {
            // found ":", must be xsd:Time
            format = "%h:%m:%s" + "%z";
        } else if (lexRep.startsWith("--")) {
            // check for GDay || GMonth || GMonthDay
            if (lexRepLength >= 3 && lexRep.charAt(2) == '-') {
                // GDAY
                // Fix 4971612: invalid SCCS macro substitution in data string
                format = "---%D" + "%z";
            } else if (lexRepLength == 4 || (lexRepLength >= 6 && (lexRep.charAt(4) == '+' || (lexRep.charAt(4) == '-' && (lexRep.charAt(5) == '-' || lexRepLength == 10))))) {
                // GMonth
                // Fix 4971612: invalid SCCS macro substitution in data string
                format = "--%M--%Z";
                final Parser p = new Parser(format, lexRep);
                try {
                    final XMLGregorianCalendar c = p.parse();
                    // check for validity
                    if (!c.isValid()) {
                        throw new IllegalArgumentException(
                                DatatypeMessageFormatter.formatMessage(null, "InvalidXGCRepresentation", new Object[]{lexicalRepresentation})
                                //"\"" + lexicalRepresentation + "\" is not a valid representation of an XML Gregorian Calendar value."
                        );
                    }
                    return c;
                } catch (final IllegalArgumentException e) {
                    format = "--%M%z";
                }
            } else {
                // GMonthDay or invalid lexicalRepresentation
                format = "--%M-%D" + "%z";
            }
        } else {
            // check for Date || GYear | GYearMonth
            int countSeparator = 0;

            // start at index 1 to skip potential negative sign for year.


            final int timezoneOffset = lexRep.indexOf(':');
            if (timezoneOffset != NOT_FOUND) {

                // found timezone, strip it off for distinguishing
                // between Date, GYear and GYearMonth so possible
                // negative sign in timezone is not mistaken as
                // a separator.
                lexRepLength -= 6;
            }

            for (int i = 1; i < lexRepLength; i++) {
                if (lexRep.charAt(i) == '-') {
                    countSeparator++;
                }
            }
            if (countSeparator == 0) {
                // GYear
                format = "%Y" + "%z";
            } else if (countSeparator == 1) {
                // GYearMonth
                format = "%Y-%M" + "%z";
            } else {
                // Date or invalid lexicalRepresentation
                // Fix 4971612: invalid SCCS macro substitution in data string
                format = "%Y-%M-%D" + "%z";
            }
        }
        final Parser p = new Parser(format, lexRep);
        final XMLGregorianCalendar c = p.parse();

        // check for validity
        if (!c.isValid()) {
            throw new IllegalArgumentException(
                    DatatypeMessageFormatter.formatMessage(null, "InvalidXGCRepresentation", new Object[]{lexicalRepresentation})
                    //"\"" + lexicalRepresentation + "\" is not a valid representation of an XML Gregorian Calendar value."
            );
        }
        return c;
    }

    private final class Parser {
        private final String format;
        private final String value;

        private final int flen;
        private final int vlen;

        private int fidx;
        private int vidx;

        private BigInteger year = null;
        private int month = DatatypeConstants.FIELD_UNDEFINED;
        private int day = DatatypeConstants.FIELD_UNDEFINED;

        private int timezone = DatatypeConstants.FIELD_UNDEFINED;

        private int hour = DatatypeConstants.FIELD_UNDEFINED;
        private int minute = DatatypeConstants.FIELD_UNDEFINED;
        private int second = DatatypeConstants.FIELD_UNDEFINED;

        private BigDecimal fractionalSecond = null;

        private Parser(String format, String value) {
            this.format = format;
            this.value = value;
            this.flen = format.length();
            this.vlen = value.length();
        }

        /**
         * Parse a formated <code>String</code> into an <code>XMLGregorianCalendar</code>.
         *
         * If <code>String</code> is not formated as a legal <code>XMLGregorianCalendar</code> value,
         * an <code>IllegalArgumentException</code> is thrown.
         *
         * @throws IllegalArgumentException If <code>String</code> is not formated as a legal <code>XMLGregorianCalendar</code> value.
         */
        public XMLGregorianCalendar parse() throws IllegalArgumentException {
            char vch;
            while (fidx < flen) {
                final char fch = format.charAt(fidx++);

                if (fch != '%') { // not a meta character
                    skip(fch);
                    continue;
                }

                // seen meta character. we don't do error check against the format
                switch (format.charAt(fidx++)) {
                    case 'Y': // year
                        parseYear();
                        break;

                    case 'M': // month
                        month = parseInt(2, 2);
                        break;

                    case 'D': // days
                        day = parseInt(2, 2);
                        break;

                    case 'h': // hours
                        hour = parseInt(2, 2);
                        break;

                    case 'm': // minutes
                        minute = parseInt(2, 2);
                        break;

                    case 's': // parse seconds.
                        second = parseInt(2, 2);

                        if (peek() == '.') {
                            fractionalSecond = parseBigDecimal();
                        }
                        break;

                    case 'z': // time zone. missing, 'Z', or [+-]nn:nn
                        vch = peek();
                        if (vch == 'Z') {
                            vidx++;
                            timezone = 0;
                        } else if (vch == '+' || vch == '-') {
                            vidx++;
                            final int h = parseInt(2, 2);
                            skip(':');
                            final int m = parseInt(2, 2);

                            if (m >= 60 || m < 0)
                                throw new IllegalArgumentException(
                                        DatatypeMessageFormatter.formatMessage(null, "InvalidFieldValue", new Object[]{m, "timezone minutes"})
                                );

                            timezone = (h * 60 + m) * (vch == '+' ? 1 : -1);
                        }
                        break;

                    case 'Z': // time zone. 'Z', or [+-]nn:nn
                        vch = peek();
                        if (vch == 'Z') {
                            vidx++;
                            timezone = 0;
                        } else if (vch == '+' || vch == '-') {
                            vidx++;
                            final int h = parseInt(2, 2);
                            skip(':');
                            final int m = parseInt(2, 2);

                            if (m >= 60 || m < 0)
                                throw new IllegalArgumentException(
                                        DatatypeMessageFormatter.formatMessage(null, "InvalidFieldValue", new Object[]{m, "timezone minutes"})
                                );

                            timezone = (h * 60 + m) * (vch == '+' ? 1 : -1);
                        } else {
                            throw new IllegalArgumentException(
                                    DatatypeMessageFormatter.formatMessage(null, "InvalidFieldValue", new Object[]{"do not defined", "timezone"})
                            );
                        }
                        break;

                    default:
                        // illegal meta character. impossible.
                        throw new InternalError();
                }
            }

            if (vidx != vlen) {
                // some tokens are left in the input
                throw new IllegalArgumentException(value); //,vidx);
            }

            if (hour == 24 && minute == 0 && second == 0) {
                if (getType() == Type.TIME) {
                    hour = 0;
                }
            }

            return TimeUtils.getInstance().getFactory()
                    .newXMLGregorianCalendar(year, month, day, hour, minute, second, fractionalSecond, timezone);
        }

        private char peek() throws IllegalArgumentException {
            if (vidx == vlen) {
                return (char) -1;
            }
            return value.charAt(vidx);
        }

        private char read() throws IllegalArgumentException {
            if (vidx == vlen) {
                throw new IllegalArgumentException(value); //,vidx);
            }
            return value.charAt(vidx++);
        }

        private void skip(char ch) throws IllegalArgumentException {
            if (read() != ch) {
                throw new IllegalArgumentException(value); //,vidx-1);
            }
        }

        private void parseYear()
                throws IllegalArgumentException {
            final int vstart = vidx;
            int sign = 0;

            // skip leading negative, if it exists
            if (peek() == '-') {
                vidx++;
                sign = 1;
            }
            while (isDigit(peek())) {
                vidx++;
            }
            final int digits = vidx - vstart - sign;
            if (digits < 4) {
                // we are expecting more digits
                throw new IllegalArgumentException(value); //,vidx);
            }
            final String yearString = value.substring(vstart, vidx);
//            if (digits < 10) {
//            	year = Integer.parseInt(yearString);
//            }
//            else {
            year = new BigInteger(yearString);
//            }
        }

        private int parseInt(int minDigits, int maxDigits)
                throws IllegalArgumentException {
            final int vstart = vidx;
            while (isDigit(peek()) && (vidx - vstart) < maxDigits) {
                vidx++;
            }
            if ((vidx - vstart) < minDigits) {
                // we are expecting more digits
                throw new IllegalArgumentException(value); //,vidx);
            }

            // NumberFormatException is IllegalArgumentException
            //           try {
            return Integer.parseInt(value.substring(vstart, vidx));
            //            } catch( NumberFormatException e ) {
            //                // if the value is too long for int, NumberFormatException is thrown
            //                throw new IllegalArgumentException(value,vstart);
            //            }
        }

        private BigDecimal parseBigDecimal()
                throws IllegalArgumentException {
            final int vstart = vidx;

            if (peek() == '.') {
                vidx++;
            } else {
                throw new IllegalArgumentException(value);
            }
            while (isDigit(peek())) {
                vidx++;
            }
            return new BigDecimal(value.substring(vstart, vidx));
        }
    }

}