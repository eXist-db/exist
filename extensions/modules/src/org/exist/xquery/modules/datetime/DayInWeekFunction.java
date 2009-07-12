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
 *  $Id: EchoFunction.java 3063 2006-04-05 20:49:44Z brihaye $
 */
package org.exist.xquery.modules.datetime;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.DateValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * @author Adam Retter <adam.retter@devon.gov.uk>
 */
public class DayInWeekFunction extends BasicFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("day-in-week", DateTimeModule.NAMESPACE_URI, DateTimeModule.PREFIX),
			"Returns the day in the week of the given date. Result is in the range 1 to 7 where 1 = Sunday, 7 = Saturday.",
			new SequenceType[] { 
				new FunctionParameterSequenceType("date", Type.DATE, Cardinality.EXACTLY_ONE, "The date that the day of the week is to be calculated.")
			},
			new FunctionParameterSequenceType("day-of-week", Type.INTEGER, Cardinality.EXACTLY_ONE, "The day of the week in the range of 1 to 7."));

	public DayInWeekFunction(XQueryContext context)
	{
		super(context, signature);
	}

	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException
	{	
		DateValue d = (DateValue)args[0].itemAt(0);
		GregorianCalendar cal = d.calendar.toGregorianCalendar();
		int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
		return new IntegerValue(dayOfWeek);
	}
}
