/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */

package org.exist.xquery.value;

import java.text.Collator;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import org.exist.xquery.XPathException;

/**
 * @author wolf
 */
public abstract class AbstractDateTimeValue extends ComputableValue {

	public final static int YEAR = 0;
	public final static int MONTH = 1;
	public final static int DAY = 2;
	public final static int HOUR = 3;
	public final static int MINUTE = 4;
	public final static int SECOND = 5;
	public final static int MILLISECOND = 6;
	
	protected GregorianCalendar calendar;
	protected int tzOffset = 0;
	protected boolean explicitTimeZone = false;
	protected Date date;

	public AbstractDateTimeValue() {
	}

	public abstract String getStringValue() throws XPathException;

	public abstract AtomicValue convertTo(int requiredType) throws XPathException;

	public abstract boolean compareTo(Collator collator, int operator, AtomicValue other)
		throws XPathException;

	public int getPart(int part) {
		switch (part) {
			case YEAR :
				return calendar.get(Calendar.YEAR);
			case MONTH :
				return calendar.get(Calendar.MONTH) + 1;
			case DAY :
				return calendar.get(Calendar.DATE);
			case HOUR :
				return calendar.get(Calendar.HOUR_OF_DAY);
			case MINUTE :
				return calendar.get(Calendar.MINUTE);
			case SECOND :
				return calendar.get(Calendar.SECOND);
			case MILLISECOND :
				return calendar.get(Calendar.MILLISECOND);
			default :
				throw new IllegalArgumentException("Invalid argument to method getPart");
		}
	}

	public long getTimezoneOffset() {
		return tzOffset * 60000;
	}
	
	protected void formatString(StringBuffer buf, int value, int size) {
		String s = "000" + value;
		buf.append(s.substring(s.length() - size));
	}
}
