package org.exist.fluent;

import java.util.*;

import javax.xml.datatype.*;

import org.exist.util.Base64Encoder;

/**
 * A bunch of static data conversion utility methods.
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class DataUtils {
	private DataUtils() {}

	/**
	 * A comparator for dateTimes (XMLGregorianCalendar objects), that uses the partial order defined on dateTimes and throws an exception if the order is indeterminate.
	 */
	public static final Comparator<XMLGregorianCalendar> DATE_TIME_COMPARATOR = new Comparator<XMLGregorianCalendar>() {
		public int compare(XMLGregorianCalendar a, XMLGregorianCalendar b) {
			int r = a.compare(b);
			if (r == DatatypeConstants.INDETERMINATE) throw new RuntimeException("date-times not comparable:  " + a + " and " + b);
			return r;
		}
	};
	
	private static DatatypeFactory datatypeFactory;
	static {
		try {
			datatypeFactory = DatatypeFactory.newInstance();
		} catch (DatatypeConfigurationException e) {
			throw new RuntimeException("unable to configure datatype factory", e);
		}
	}
	
	/**
	 * Return a shared instance of a datatype factory, used for creating new XML data objects.
	 *
	 * @return a shared datatype factory
	 */
	public static DatatypeFactory datatypeFactory() {
		return datatypeFactory;
	}

	/**
	 * Convert an XML date/time to its <code>java.util.Date</code> equivalent.
	 * 
	 * @param dateTime the XML date/time to convert
	 * @return a Java date
	 */
	public static Date toDate(XMLGregorianCalendar dateTime) {
		return dateTime.toGregorianCalendar().getTime();
	}

	/**
	 * Convert a Java date to its XML date/time equivalent.
	 *
	 * @param date the Java date to convert
	 * @return an XML date/time
	 */
	public static XMLGregorianCalendar toDateTime(Date date) {
		GregorianCalendar cal = new GregorianCalendar();
		cal.setTime(date);
		return datatypeFactory().newXMLGregorianCalendar(cal).normalize();
	}

	/**
	 * Convert milliseconds offset to its XML date/time equivalent.
	 *
	 * @param millis a millisecond count since the epoch
	 * @return an XML date/time
	 */
	public static XMLGregorianCalendar toDateTime(long millis) {
		GregorianCalendar cal = new GregorianCalendar();
		cal.setTimeInMillis(millis);
		return datatypeFactory().newXMLGregorianCalendar(cal).normalize();
	}

	/**
	 * Convert a Java object to its equivalent XML datatype string representation.
	 * At the moment, there is special treatment for <code>java.util.Date</code>,
	 * <code>java.util.Calendar</code> and <code>byte[]</code> (Base64 encoding);
	 * for all other objects, we simply invoke <code>toString()</code>.
	 * 
	 * @param o the object to convert
	 * @return a string representation of the object, according to XML Schema Datatype rules if possible
	 */
	public static String toXMLString(Object o) {
		if (o instanceof Date) {
			return toDateTime((Date) o).toString();
		} else if (o instanceof Calendar) {
			return toDateTime(((Calendar) o).getTimeInMillis()).toString();
		} else if (o instanceof byte[]) {
			Base64Encoder encoder = new Base64Encoder();
			encoder.translate((byte[]) o);
			return String.valueOf(encoder.getCharArray());
		} else {
			return o.toString();
		}
	}
	
	
}
