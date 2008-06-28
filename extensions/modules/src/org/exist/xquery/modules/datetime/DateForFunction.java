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
import org.exist.xquery.value.TimeUtils;
import org.exist.xquery.value.Type;

import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * @author Adam Retter <adam.retter@devon.gov.uk>
 */
public class DateForFunction extends BasicFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("date-for", DateTimeModule.NAMESPACE_URI, DateTimeModule.PREFIX),
			"Returns the date for a given set of parameters. $a the year of interest. $b the month of interest (1 = January, 12 = December). $c The week in the month of interest (1 = first week, 4 or 5 = last week). $d The day in the week of interest (1 = Sunday, 7 = Saturday).",
			new SequenceType[] {
				new SequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE),
			},
			new SequenceType(Type.DATE, Cardinality.EXACTLY_ONE));

	public DateForFunction(XQueryContext context)
	{
		super(context, signature);
	}

	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException
	{	
		int yearOfInterest = ((IntegerValue)args[0].itemAt(0)).getInt();
		int monthOfInterest = ((IntegerValue)args[1].itemAt(0)).getInt();
		int weekInMonth = ((IntegerValue)args[2].itemAt(0)).getInt();
		int dayInWeek = ((IntegerValue)args[3].itemAt(0)).getInt();
		
		//check bounds of supplied parameters
		if(monthOfInterest < 1 || monthOfInterest > 12)
			throw new XPathException(getASTNode(), "The month of interest must be between 1 and 12");
		
		if(weekInMonth < 1 || weekInMonth > 5)
			throw new XPathException(getASTNode(), "The week in the month of interest must be between 1 and 5");
		
		if(dayInWeek < 1 || dayInWeek > 7)
			throw new XPathException(getASTNode(), "The day in the week of interest must be between 1 and 7");
		
		//create date
		GregorianCalendar cal = new GregorianCalendar();
		cal.set(Calendar.YEAR, yearOfInterest);
		cal.set(Calendar.MONTH, monthOfInterest - 1);
		cal.set(Calendar.WEEK_OF_MONTH, weekInMonth);
		cal.set(Calendar.DAY_OF_WEEK, dayInWeek);
		
		return new DateValue(TimeUtils.getInstance().newXMLGregorianCalendar(cal));
	}
}
