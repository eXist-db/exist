package org.exist.xquery.value;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;
import javax.xml.datatype.XMLGregorianCalendar;

/**
 * Centralizes access to time-related utility functions.  Mostly delegates to the
 * XML datatype factory, serving as a central chokepoint to control concurrency
 * issues.  It's not clear if instances of the factory are in fact thread-safe or not;
 * if they turn out not to be, it will be easy to either synchronize access or create
 * more instances here as required.
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class TimeUtils {
	
	private static final TimeUtils INSTANCE = new TimeUtils();
	public static TimeUtils getInstance() {return INSTANCE;}
	
	public static Duration ONE_DAY = INSTANCE.factory.newDuration(true, 0, 0, 1, 0, 0, 0); 

	// assume it's thread-safe, if not synchronize all access
	private final DatatypeFactory factory;
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
	
	public DatatypeFactory getFactory() {
		return factory;
	}
	
	/**
	 * Set the offset of the local timezone, ignoring the default provided by the OS.
	 * Mainly useful for testing.
	 *
	 * @param millis the timezone offset in milliseconds, positive or negative
	 */
	public void overrideLocalTimezoneOffset(int millis) {
		timezoneOffset = millis;
		timezoneOverriden = true;
	}
	
	/**
	 * Cancel any timezone override that may be in effect, reverting back to the OS value.
	 */
	public void resetLocalTimezoneOffset() {
		timezoneOverriden = false;
	}
	
	public int getLocalTimezoneOffsetMillis() {
		final int dstOffset = Calendar.getInstance(TimeZone.getDefault()).get(Calendar.DST_OFFSET);
		return timezoneOverriden ? timezoneOffset : TimeZone.getDefault().getRawOffset() + dstOffset;
	}
	
	public int getLocalTimezoneOffsetMinutes() {
		return getLocalTimezoneOffsetMillis() / 60000;
	}
	
	public Duration newDuration(long arg0) {
		return factory.newDuration(arg0);
	}

	public Duration newDuration(String arg0) {
		return factory.newDuration(arg0);
	}

	public Duration newDuration(
            final boolean isPositive,
            final int years,
            final int months,
            final int days,
            final int hours,
            final int minutes,
            final int seconds) {

		return factory.newDuration(isPositive, years, months, days, hours, minutes, seconds);
	}

	public Duration newDuration(boolean arg0, BigInteger arg1, BigInteger arg2, BigInteger arg3, BigInteger arg4, BigInteger arg5, BigDecimal arg6) {
		return factory.newDuration(arg0, arg1, arg2, arg3, arg4, arg5, arg6);
	}

	public Duration newDurationDayTime(long arg0) {
		return factory.newDurationDayTime(arg0);
	}

	public Duration newDurationDayTime(String arg0) {
		return factory.newDurationDayTime(arg0);
	}

	public Duration newDurationDayTime(boolean arg0, int arg1, int arg2, int arg3, int arg4) {
		return factory.newDurationDayTime(arg0, arg1, arg2, arg3, arg4);
	}

	public Duration newDurationDayTime(boolean arg0, BigInteger arg1, BigInteger arg2, BigInteger arg3, BigInteger arg4) {
		return factory.newDurationDayTime(arg0, arg1, arg2, arg3, arg4);
	}

	public Duration newDurationYearMonth(long arg0) {
		return factory.newDurationYearMonth(arg0);
	}

	public Duration newDurationYearMonth(String arg0) {
		return factory.newDurationYearMonth(arg0);
	}

	public Duration newDurationYearMonth(boolean arg0, int arg1, int arg2) {
		return factory.newDurationYearMonth(arg0, arg1, arg2);
	}

	public Duration newDurationYearMonth(boolean arg0, BigInteger arg1, BigInteger arg2) {
		return factory.newDurationYearMonth(arg0, arg1, arg2);
	}

	public XMLGregorianCalendar newXMLGregorianCalendar() {
		return factory.newXMLGregorianCalendar();
	}

	public XMLGregorianCalendar newXMLGregorianCalendar(int arg0, int arg1, int arg2, int arg3, int arg4, int arg5, int arg6, int arg7) {
		return factory.newXMLGregorianCalendar(arg0, arg1, arg2, arg3, arg4, arg5,
				arg6, arg7);
	}

	public XMLGregorianCalendar newXMLGregorianCalendar(String lexicalRepresentation) {
		return factory.newXMLGregorianCalendar(lexicalRepresentation);
	}

	public XMLGregorianCalendar newXMLGregorianCalendar(BigInteger arg0, int arg1, int arg2, int arg3, int arg4, int arg5, BigDecimal arg6, int arg7) {
		return factory.newXMLGregorianCalendar(arg0, arg1, arg2, arg3, arg4, arg5,
				arg6, arg7);
	}

	public XMLGregorianCalendar newXMLGregorianCalendar(GregorianCalendar arg0) {
		return factory.newXMLGregorianCalendar(arg0);
	}

	public XMLGregorianCalendar newXMLGregorianCalendarDate(int arg0, int arg1, int arg2, int arg3) {
		return factory.newXMLGregorianCalendarDate(arg0, arg1, arg2, arg3);
	}

	public XMLGregorianCalendar newXMLGregorianCalendarTime(int arg0, int arg1, int arg2, int arg3) {
		return factory.newXMLGregorianCalendarTime(arg0, arg1, arg2, arg3);
	}

	public XMLGregorianCalendar newXMLGregorianCalendarTime(int arg0, int arg1, int arg2, int arg3, int arg4) {
		return factory.newXMLGregorianCalendarTime(arg0, arg1, arg2, arg3, arg4);
	}

	public XMLGregorianCalendar newXMLGregorianCalendarTime(int arg0, int arg1, int arg2, BigDecimal arg3, int arg4) {
		return factory.newXMLGregorianCalendarTime(arg0, arg1, arg2, arg3, arg4);
	}
}
