/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2007 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.value;

import java.math.BigDecimal;
import java.text.Collator;
import java.text.DecimalFormat;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.Duration;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;

import org.exist.xquery.Constants;
import org.exist.xquery.XPathException;

/**
 * @author wolf
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 * @author ljo
 */
public abstract class AbstractDateTimeValue extends ComputableValue {
	
	//Provisionally public
	public final XMLGregorianCalendar calendar;
	private XMLGregorianCalendar implicitCalendar, canonicalCalendar, trimmedCalendar;
	
	protected static Pattern negativeDateStart = Pattern.compile("^\\d\\d?-(\\d+)-(.*)"); 
    protected static Pattern gYearWUTCTZ = Pattern.compile("^(\\d\\d\\d\\d)([+-])(\\d\\d):(\\d\\d)$");
    protected static Pattern gYearNoTZ = Pattern.compile("^(\\d\\d\\d\\d)$");
    protected static Pattern gDayWTZ = Pattern.compile("^(---\\d\\d)([+-])(\\d\\d):(\\d\\d)");
    protected static Pattern gMonthWTZ = Pattern.compile("^(--\\d\\d)([+-])(\\d\\d):(\\d\\d)");
    //protected static Pattern gMonthWUTCTZ = Pattern.compile("^(--\\d\\d)([+-])(\\d\\d):(\\d\\d)");
    protected static Pattern gYearMonthWTZ = Pattern.compile("^(\\d\\d\\d\\d-\\d\\d)([+-])(\\d\\d):(\\d\\d)");
    protected static Pattern gMonthDayWTZ = Pattern.compile("^(--\\d\\d-\\d\\d)([+-])(\\d\\d):(\\d\\d)");
    protected static Pattern dateWTZ = Pattern.compile("^(\\d\\d\\d\\d-\\d\\d-\\d\\d)([+-])(\\d\\d):(\\d\\d)");
    protected static Pattern timeNoTZ = Pattern.compile("^(\\d\\d):(\\d\\d):(\\d\\d)");
    protected static Pattern timeWUTCTZ = Pattern.compile("^(\\d\\d):(\\d\\d):(\\d\\d)([+-]\\d\\d:\\d\\d)");
    protected static Pattern timeMsWTZ = Pattern.compile("^(\\d\\d):(\\d\\d):(\\d\\d)(\\.)(\\d\\d\\d)([+-])(\\d\\d):(\\d\\d)");
    protected static Pattern dateTimeMsWTZ = Pattern.compile("^(\\d\\d\\d\\d-\\d\\d-\\d\\dT)(\\d\\d):(\\d\\d):(\\d\\d)(\\.)(\\d\\d\\d)([+-])(\\d\\d):(\\d\\d)");
    protected static Pattern dateTimeNoTZ = Pattern.compile("^(\\d\\d\\d\\d-\\d\\d-\\d\\d)(T)(\\d\\d):(\\d\\d):(\\d\\d)");


    protected static Pattern dateInvalidDay = Pattern.compile("^(---)\\d(\\d\\d)");
    protected static Pattern dateInvalidMonth = Pattern.compile("(^\\d\\d\\d\\d-\\d\\d\\d-\\d\\d$|^\\d\\d\\d\\d-\\d\\d\\d.*|^\\d\\d\\d\\d-\\d\\d\\d-\\d\\dT.*)");
    protected static Pattern dateInvalidYear = Pattern.compile("(^0(\\d\\d\\d\\d\\d*)-(\\d\\d)-(\\d\\d)|^0(\\d\\d\\d\\d)-(\\d\\d\\d)|^0(\\d\\d\\d\\d)-(\\d\\d)|^0(\\d\\d\\d\\d)|^0(\\d\\d\\d\\d\\d*)-(\\d\\d)-(\\d\\d)T(\\d\\d):(\\d\\d):(\\d\\d))");



	public final static int YEAR = 0;
	public final static int MONTH = 1;
	public final static int DAY = 2;
	public final static int HOUR = 3;
	public final static int MINUTE = 4;
	public final static int SECOND = 5;
	public final static int MILLISECOND = 6;

	/**
	 * Create a new date time value based on the given calendar.  The calendar is
	 * <em>not</em> cloned, so it is the subclass's responsibility to make sure there are
	 * no external references to it that would allow for mutation.
	 *
	 * @param calendar the calendar to wrap into an XPath value
	 */
	protected AbstractDateTimeValue(XMLGregorianCalendar calendar) {
		this.calendar = calendar;
	}
	
	protected AbstractDateTimeValue(String lexicalValue)
        throws XPathException {
        lexicalValue = StringValue.trimWhitespace(lexicalValue);

        lexicalValue = normalizeDate(lexicalValue);
        lexicalValue = normalizeTime(lexicalValue);
		try {
			this.calendar = TimeUtils.getInstance().newXMLGregorianCalendar(lexicalValue);
		} catch (IllegalArgumentException e) {
			throw new XPathException("err:FORG0001: illegal lexical form for date-time-like value '" + lexicalValue + "' " + e.getMessage(), e);
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
			switch(getType()) {
				case Type.DATE: implicitCalendar.setTime(0,0,0); break;
				case Type.TIME: implicitCalendar.setYear(1972); implicitCalendar.setMonth(12); implicitCalendar.setDay(31); break;
				default:
			}
			implicitCalendar = implicitCalendar.normalize();	// the comparison routines will normalize it anyway, just do it once here
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
	
	protected XMLGregorianCalendar getTrimmedCalendar() {
		if (trimmedCalendar == null) {
			trimmedCalendar = cloneXMLGregorianCalendar(calendar);
			BigDecimal fract = trimmedCalendar.getFractionalSecond();
			if (fract != null) {
				// TODO: replace following algorithm in JDK 1.5 with fract.stripTrailingZeros();
				String s = fract.toString();
				int i = s.length();
				while (i > 0 && s.charAt(i-1) == '0') i--;
				if (i == 0) trimmedCalendar.setFractionalSecond(null);
				else if (i != s.length()) trimmedCalendar.setFractionalSecond(new BigDecimal(s.substring(0, i)));
			}
		}
		return trimmedCalendar;
	}
	
	protected abstract AbstractDateTimeValue createSameKind(XMLGregorianCalendar cal) throws XPathException;
	
	protected long getTimeInMillis() {
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
		boolean startsWithDashDash = r.startsWith("--");
		r = r.replaceAll("--", "");
		if (startsWithDashDash)
			r = "--" + r;
		
		Matcher m = negativeDateStart.matcher(r);
		if (m.matches()) {
			int year = Integer.valueOf(m.group(1)).intValue();
			DecimalFormat df = new DecimalFormat("0000");
			r = "-" + df.format(year) + "-" + m.group(2);
		}
		
		return r;
	}

	public boolean effectiveBooleanValue() throws XPathException {
		throw new XPathException("FORG0006: effective boolean value invalid operand type: " + Type.getTypeName(getType()));
	}
	
	public abstract AtomicValue convertTo(int requiredType) throws XPathException;

	public int getPart(int part) {
		switch (part) {
			case YEAR: return calendar.getYear(); 
			case MONTH: return calendar.getMonth();
			case DAY: return calendar.getDay();
			case HOUR: return calendar.getHour();
			case MINUTE: return calendar.getMinute();
			case SECOND: return calendar.getSecond();
			case MILLISECOND: int mSec=calendar.getMillisecond();
                                          if(mSec == DatatypeConstants.FIELD_UNDEFINED)
                                              return 0;
                                          else
                                              return calendar.getMillisecond();
			default: throw new IllegalArgumentException("Invalid argument to method getPart");
		}
	}
	
	private static final Duration tzLowerBound = TimeUtils.getInstance().newDurationDayTime("-PT14H");
	private static final Duration tzUpperBound = tzLowerBound.negate();
	protected void validateTimezone(DayTimeDurationValue offset) throws XPathException {
		Duration tz = offset.duration;
		Number secs = tz.getField(DatatypeConstants.SECONDS);
		if (secs != null && ((BigDecimal) secs).compareTo(BigDecimal.valueOf(0)) != 0)
			throw new XPathException("duration " + offset + " has fractional minutes so cannot be used as a timezone offset");
		if (! (
				tz.equals(tzLowerBound) ||
				tz.equals(tzUpperBound) ||
				(tz.isLongerThan(tzLowerBound) && tz.isShorterThan(tzUpperBound))
			))
			throw new XPathException("duration " + offset + " outside valid timezone offset range");
	}

	public AbstractDateTimeValue adjustedToTimezone(DayTimeDurationValue offset) throws XPathException {
		if (offset == null) offset = new DayTimeDurationValue(TimeUtils.getInstance().getLocalTimezoneOffsetMillis());
		validateTimezone(offset);
		XMLGregorianCalendar xgc = (XMLGregorianCalendar) calendar.clone();
		if (xgc.getTimezone() != DatatypeConstants.FIELD_UNDEFINED) {
			if (getType() == Type.DATE) xgc.setTime(0,0,0);	// set the fields so we don't lose precision when shifting timezones
			xgc = xgc.normalize();
			xgc.add(offset.duration);
		}
		try {
			xgc.setTimezone((int) (offset.getValue()/60));
		} catch (IllegalArgumentException e) {
			throw new XPathException("illegal timezone offset " + offset, e);
		}
		return createSameKind(xgc);
	}
	
	public AbstractDateTimeValue withoutTimezone() throws XPathException {
		XMLGregorianCalendar xgc = (XMLGregorianCalendar) calendar.clone();
		xgc.setTimezone(DatatypeConstants.FIELD_UNDEFINED);
		return createSameKind(xgc);
	}
	
	public Sequence getTimezone() throws XPathException {
		int tz = calendar.getTimezone();
		if (tz == DatatypeConstants.FIELD_UNDEFINED) return Sequence.EMPTY_SEQUENCE;
		return new DayTimeDurationValue(tz * 60000L);
	}
	
	public boolean compareTo(Collator collator, int operator, AtomicValue other) throws XPathException {
		int cmp = compareTo(collator, other);
		switch (operator) {
			case Constants.EQ: return cmp == 0;
			case Constants.NEQ: return cmp != 0;
			case Constants.LT: return cmp < 0;
			case Constants.LTEQ: return cmp <= 0;
			case Constants.GT: return cmp > 0;
			case Constants.GTEQ: return cmp >= 0;
			default :
				throw new XPathException("Unknown operator type in comparison");
		}
	}

	public int compareTo(Collator collator, AtomicValue other) throws XPathException {
		if (other.getType() == getType()) {
			// filling in missing timezones with local timezone, should be total order as per XPath 2.0 10.4
			int r =  getImplicitCalendar().compare(((AbstractDateTimeValue) other).getImplicitCalendar());
			if (r == DatatypeConstants.INDETERMINATE) throw new RuntimeException("indeterminate order between " + this + " and " + other);
			return r;
		}
		throw new XPathException(
			"Type error: cannot compare " + Type.getTypeName(getType()) + " to "
				+ Type.getTypeName(other.getType()));
	}

	public AtomicValue max(Collator collator, AtomicValue other) throws XPathException {
		AbstractDateTimeValue otherDate = other.getType() == getType() ? (AbstractDateTimeValue) other : (AbstractDateTimeValue) other.convertTo(getType());
		return getImplicitCalendar().compare(otherDate.getImplicitCalendar()) > 0 ? this : other;
	}

	public AtomicValue min(Collator collator, AtomicValue other) throws XPathException {
		AbstractDateTimeValue otherDate = other.getType() == getType() ? (AbstractDateTimeValue) other : (AbstractDateTimeValue) other.convertTo(getType());
		return getImplicitCalendar().compare(otherDate.getImplicitCalendar()) < 0 ? this : other;
	}

	// override for xs:time
	public ComputableValue plus(ComputableValue other) throws XPathException {
		switch(other.getType()) {
			case Type.YEAR_MONTH_DURATION:
			case Type.DAY_TIME_DURATION:
				return other.plus(this);
			default:
				throw new XPathException(
						"Operand to plus should be of type xdt:dayTimeDuration or xdt:yearMonthDuration; got: "
							+ Type.getTypeName(other.getType()));
		}
	}

	public ComputableValue mult(ComputableValue other) throws XPathException {
		throw new XPathException("multiplication is not supported for type " + Type.getTypeName(getType()));
	}

	public ComputableValue div(ComputableValue other) throws XPathException {
		throw new XPathException("division is not supported for type " + Type.getTypeName(getType()));
	}

	public int conversionPreference(Class javaClass) {
		if (javaClass.isAssignableFrom(DateValue.class)) return 0;
		if (javaClass.isAssignableFrom(XMLGregorianCalendar.class)) return 1;
		if (javaClass.isAssignableFrom(GregorianCalendar.class)) return 2;
		if (javaClass == Date.class) return 3;
		return Integer.MAX_VALUE;
	}

	public Object toJavaObject(Class target) throws XPathException {
		if (target == Object.class || target.isAssignableFrom(DateValue.class))
			return this;
		else if (target.isAssignableFrom(XMLGregorianCalendar.class))
			return calendar.clone();
		else if (target.isAssignableFrom(GregorianCalendar.class))
			return calendar.toGregorianCalendar();
		else if (target == Date.class)
			return calendar.toGregorianCalendar().getTime();

		throw new XPathException("cannot convert value of type " + Type.getTypeName(getType()) + " to Java object of type " + target.getName());
	}
    
    /* (non-Javadoc)
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    public int compareTo(Object o)
    {
        final AtomicValue other = (AtomicValue)o;
        if(Type.subTypeOf(other.getType(), Type.DATE_TIME))
        	try {
        		//TODO : find something that will consume less resources
        		return calendar.compare(TimeUtils.getInstance().newXMLGregorianCalendar(other.getStringValue()));
        	} catch (XPathException e) {
        		System.out.println("Failed to get string value of '" + other + "'");
        		//Why not ?
        		return Constants.SUPERIOR;
        	}
        else
            return getType() > other.getType() ? Constants.SUPERIOR : Constants.INFERIOR;
    }	

    /**
     * The method <code>normalizeDate</code>
     *
     * @param dateValue a <code>String</code> value
     * @return a <code>String</code> value
     * @exception XPathException if an error occurs
     */
    public static String normalizeDate(String dateValue)
    throws XPathException {
        Matcher d = dateInvalidDay.matcher(dateValue);
        Matcher m = dateInvalidMonth.matcher(dateValue);
        Matcher y = dateInvalidYear.matcher(dateValue);
        if (d.matches() ||  m.matches() || y.matches()) {
            throw new XPathException("err:FORG0001: illegal lexical form for date-time-like value '" + dateValue + "'");
        }
        return dateValue;
    }

    /**
     * The method <code>normalizeTime</code>
     *
     * @param timeValue a <code>String</code> value
     * @return a <code>String</code> value
     */
    public static String normalizeTime(String timeValue)
    throws XPathException {
        int hours = 0;
        int mins = 0;
        int secs = 0;
        int mSecs = 0;
        int tzHours = 0;
        int tzMins = 0;
        DecimalFormat df = new DecimalFormat("00");
        DecimalFormat msf = new DecimalFormat("000");
		Matcher m = timeNoTZ.matcher(timeValue);
		if (m.matches()) {
			hours = Integer.valueOf(m.group(1)).intValue();
			mins = Integer.valueOf(m.group(2)).intValue();
			secs = Integer.valueOf(m.group(3)).intValue();
            if (mins >= 60 || mins < 0 || secs >= 60 || secs < 0) {
                throw new XPathException("err:FORG0001: illegal lexical form for date-time-like value '" + timeValue + "'");
            }
            
            if (hours == 24) {
                if (mins == 0) {
                    hours = 0;
                } else {
                    throw new XPathException("err:FORG0001: illegal lexical form for date-time-like value '" + timeValue + "'. If hours is 24, minutes must be 00.");
                }
            }
            // fixme!
            timeValue = df.format(hours) + ":" + df.format(mins) + ":" + df.format(secs);
		}
        
        m = dateTimeNoTZ.matcher(timeValue);
		if (m.matches()) {
            String date =  m.group(1);
            DateValue dateValue = null;

			hours = Integer.valueOf(m.group(3)).intValue();
			mins = Integer.valueOf(m.group(4)).intValue();
			secs = Integer.valueOf(m.group(5)).intValue();
            if (mins >= 60 || mins < 0 || secs >= 60 || secs < 0) {
                throw new XPathException("err:FORG0001: illegal lexical form for date-time-like value '" + timeValue + "'");
            }
            
            if (hours == 24) {
                if (mins == 0) {
                    hours = 0;
                    dateValue = (DateValue) new DateValue(date).plus(new DayTimeDurationValue("P1D"));
                } else {
                    throw new XPathException("err:FORG0001: illegal lexical form for date-time-like value '" + timeValue + "'. If hours is 24, minutes must be 00.");
                }
            }
            // fixme!
            timeValue = (dateValue == null ? date : dateValue.getStringValue()) + m.group(2) + df.format(hours) + ":" + df.format(mins) + ":" + df.format(secs);
		}

        m = dateTimeMsWTZ.matcher(timeValue);
		if (m.matches()) {
			hours = Integer.valueOf(m.group(2)).intValue();
			mins = Integer.valueOf(m.group(3)).intValue();
			secs = Integer.valueOf(m.group(4)).intValue();
			mSecs = Integer.valueOf(m.group(6)).intValue();
            tzHours = Integer.valueOf(m.group(8)).intValue();
			tzMins = Integer.valueOf(m.group(9)).intValue();
            if (mins >= 60 || mins < 0 || tzMins >= 60 || tzMins < 0) {
                throw new XPathException("err:FORG0001: illegal lexical form for date-time-like value '" + timeValue + "'");
            }
            
            if (hours == 24) {
                if (mins == 0) {
                    hours = 0;
                } else {
                    throw new XPathException("err:FORG0001: illegal lexical form for date-time-like value '" + timeValue + "'. If hours is 24, minutes must be 00.");
                }
            }
            // fixme!
            timeValue = m.group(1) + df.format(hours) + ":" + df.format(mins) + ":" + df.format(secs) + m.group(5) + msf.format(mSecs) + m.group(7) + df.format(tzHours) + ":" + df.format(tzMins);
		}
        
        m = timeMsWTZ.matcher(timeValue);
		if (m.matches()) {
			hours = Integer.valueOf(m.group(1)).intValue();
			mins = Integer.valueOf(m.group(2)).intValue();
			secs = Integer.valueOf(m.group(3)).intValue();
			mSecs = Integer.valueOf(m.group(5)).intValue();
            tzHours = Integer.valueOf(m.group(7)).intValue();
			tzMins = Integer.valueOf(m.group(8)).intValue();
            if (mins >= 60 || mins < 0 || tzMins >= 60 || tzMins < 0) {
                throw new XPathException("err:FORG0001: illegal lexical form for date-time-like value '" + timeValue + "'");
            }
            
            if (hours == 24) {
                if (mins == 0) {
                    hours = 0;
                } else {
                    throw new XPathException("err:FORG0001: illegal lexical form for date-time-like value '" + timeValue + "'. If hours is 24, minutes must be 00.");
                }
            }
            timeValue = df.format(hours) + ":" + df.format(mins) + ":" + df.format(secs) + m.group(4) + msf.format(mSecs) + m.group(6) + df.format(tzHours) + ":" + df.format(tzMins);
		}

        m = gYearWUTCTZ.matcher(timeValue);
		if (m.matches()) {
			tzHours = Integer.valueOf(m.group(3)).intValue();
			tzMins = Integer.valueOf(m.group(4)).intValue();
            if (tzMins >= 60 || tzMins < 0) {
                throw new XPathException("err:FORG0001: illegal lexical form for date-time-like value '" + timeValue + "'");
            }
            
            if (tzHours == 24) {
                if (tzMins == 0) {
                    tzHours = 0;
                } else {
                    throw new XPathException("err:FORG0001: illegal lexical form for date-time-like value '" + timeValue + "'. If hours is 24, minutes must be 00.");
                }
            }
            // fixme!
            timeValue = m.group(1) + m.group(2) + df.format(tzHours) + ":" + df.format(tzMins);
		}

        m = gDayWTZ.matcher(timeValue);
		if (m.matches()) {
			tzHours = Integer.valueOf(m.group(3)).intValue();
			tzMins = Integer.valueOf(m.group(4)).intValue();
            if (tzMins >= 60 || tzMins < 0) {
                throw new XPathException("err:FORG0001: illegal lexical form for date-time-like value '" + timeValue + "'");
            }
            
            if (tzHours == 24) {
                if (tzMins == 0) {
                    tzHours = 0;
                } else {
                    throw new XPathException("err:FORG0001: illegal lexical form for date-time-like value '" + timeValue + "'. If hours is 24, minutes must be 00.");
                }
            }
            // fixme!
            timeValue = m.group(1) + m.group(2) + df.format(tzHours) + ":" + df.format(tzMins);
		}

        m = gMonthWTZ.matcher(timeValue);
		if (m.matches()) {
            tzHours = Integer.valueOf(m.group(3)).intValue();
			tzMins = Integer.valueOf(m.group(4)).intValue();
            if (tzMins >= 60 || tzMins < 0) {
                throw new XPathException("err:FORG0001: illegal lexical form for date-time-like value '" + timeValue + "'");
            }
            
            if (tzHours == 24) {
                if (tzMins == 0) {
                    tzHours = 0;
                } else {
                    throw new XPathException("err:FORG0001: illegal lexical form for date-time-like value '" + timeValue + "'. If hours is 24, minutes must be 00.");
                }
            }
            // fixme!
            timeValue = m.group(1) + m.group(2) + df.format(tzHours) + ":" + df.format(tzMins);
		}

        m = gYearMonthWTZ.matcher(timeValue);
		if (m.matches()) {
            tzHours = Integer.valueOf(m.group(3)).intValue();
			tzMins = Integer.valueOf(m.group(4)).intValue();
            if (tzMins >= 60 || tzMins < 0) {
                throw new XPathException("err:FORG0001: illegal lexical form for date-time-like value '" + timeValue + "'");
            }
            
            if (tzHours == 24) {
                if (tzMins == 0) {
                    tzHours = 0;
                } else {
                    throw new XPathException("err:FORG0001: illegal lexical form for date-time-like value '" + timeValue + "'. If hours is 24, minutes must be 00.");
                }
            }
            // fixme!
            timeValue = m.group(1) + m.group(2) + df.format(tzHours) + ":" + df.format(tzMins);
		}

        m = gMonthDayWTZ.matcher(timeValue);
		if (m.matches()) {
            tzHours = Integer.valueOf(m.group(3)).intValue();
			tzMins = Integer.valueOf(m.group(4)).intValue();
            if (tzMins >= 60 || tzMins < 0) {
                throw new XPathException("err:FORG0001: illegal lexical form for date-time-like value '" + timeValue + "'");
            }
            
            if (tzHours == 24) {
                if (tzMins == 0) {
                    tzHours = 0;
                } else {
                    throw new XPathException("err:FORG0001: illegal lexical form for date-time-like value '" + timeValue + "'. If hours is 24, minutes must be 00.");
                }
            }
            // fixme!
            timeValue = m.group(1) + m.group(2) + df.format(tzHours) + ":" + df.format(tzMins);
		}
        
        m = dateWTZ.matcher(timeValue);
		if (m.matches()) {
			hours = Integer.valueOf(m.group(3)).intValue();
			mins = Integer.valueOf(m.group(4)).intValue();
            if (mins >= 60 || mins < 0 || tzMins >= 60 || tzMins < 0) {
                throw new XPathException("err:FORG0001: illegal lexical form for date-time-like value '" + timeValue + "'");
            }
            
            if (hours == 24) {
                if (mins == 0) {
                    hours = 0;
                } else {
                    throw new XPathException("err:FORG0001: illegal lexical form for date-time-like value '" + timeValue + "'. If hours is 24, minutes must be 00.");
                }
            }
            // fixme!
            timeValue = m.group(1) + m.group(2) + df.format(hours) + ":" + df.format(mins);
		}
        return timeValue;
    }
    
    /**
     * Utility method that is able to clone a calendar whose year is 0
     * (whatever a year 0 means). 
     * It looks like the JDK is unable to do that.
     * @param calendar The Calendar to clone
     * @return the cloned Calendar
     */
    public static XMLGregorianCalendar cloneXMLGregorianCalendar(XMLGregorianCalendar calendar) {
    	boolean hacked = false;
		if (calendar.getYear() == 0) {
			calendar.setYear(1);
			hacked = true;
		}
		XMLGregorianCalendar result = (XMLGregorianCalendar)calendar.clone();
		if (hacked) {
			//reset everything
			calendar.setYear(0);
			//-1 could also be considered
			result.setYear(0);
		}
		return result;
    }

}
