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

package org.exist.xpath.value;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import org.exist.xpath.XPathException;

/**
 * @author wolf
 */
public abstract class AbstractDateTimeValue extends ComputableValue {

	protected GregorianCalendar calendar;
	protected int tzOffset = 0;
	protected boolean explicitTimeZone = false;
	protected Date date;
	
	public AbstractDateTimeValue() {
		calendar = new GregorianCalendar();
		tzOffset =
		(calendar.get(Calendar.ZONE_OFFSET) + calendar.get(Calendar.DST_OFFSET))
		/ 60000;
		date = calendar.getTime();
	}
	
	public abstract String getStringValue() throws XPathException;

	public abstract AtomicValue convertTo(int requiredType) throws XPathException;

	public abstract boolean compareTo(int operator, AtomicValue other)
		throws XPathException;
	
	protected void formatString(StringBuffer buf, int value, int size) {
		String s = "000" + value;
		buf.append(s.substring(s.length() - size));
	}
}
