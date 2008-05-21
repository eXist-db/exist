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
package org.exist.xquery.modules.datetime;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.DateValue;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;

/**
 * @author Adam Retter <adam.retter@devon.gov.uk>
 */
public class CountDayInMonthFunction extends BasicFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("count-day-in-month", DateTimeModule.NAMESPACE_URI, DateTimeModule.PREFIX),
			"Returns the count of a specific day $a in a month for the date given in $b. $a in the range 1 to 7 where 1 = Sunday and 7 = Saturday. For example it can tell you there are 5 Fridays in February 2008.",
			new SequenceType[] { 
				new SequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.DATE, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE));

	public CountDayInMonthFunction(XQueryContext context)
	{
		super(context, signature);
	}

	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException
	{	
		int dayOfInterest = ((IntegerValue)args[0].itemAt(0)).getInt();
		DateValue d = (DateValue)args[1].itemAt(0);
		
		GregorianCalendar cal = new GregorianCalendar(Locale.getDefault());
		cal.set(Calendar.YEAR, d.getPart(DateValue.YEAR));
		cal.set(Calendar.MONTH, d.getPart(DateValue.MONTH) - 1);
		cal.set(Calendar.DAY_OF_MONTH, 1);
		
		//start on the first instance of the day in the month
		cal.set(Calendar.DAY_OF_WEEK, dayOfInterest);
		
		int dayOfInterestCount = 0;
		int monthOfInterest = cal.get(Calendar.MONTH);
		
		while(cal.get(Calendar.MONTH) == monthOfInterest)
		{
			dayOfInterestCount++;
			cal.add(Calendar.DATE, 7);
		}
		
		return new IntegerValue(dayOfInterestCount);
	}
}
