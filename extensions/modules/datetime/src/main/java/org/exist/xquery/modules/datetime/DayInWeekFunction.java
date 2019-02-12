/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2007-09 The eXist Project
 *  http://exist-db.org
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * $Id$
 */
package org.exist.xquery.modules.datetime;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.DateValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * @author Adam Retter <adam@exist-db.org>
 * @version 1.1
 */
public class DayInWeekFunction extends BasicFunction {
	protected static final Logger logger = LogManager.getLogger(DayInWeekFunction.class);

	public final static FunctionSignature signature = new FunctionSignature(new QName("day-in-week", DateTimeModule.NAMESPACE_URI,
			DateTimeModule.PREFIX),
			"Returns the day in the week of the date. Result is in the range 1 to 7, where 1 = Sunday, 7 = Saturday.",
			new SequenceType[] { new FunctionParameterSequenceType("date", Type.DATE, Cardinality.EXACTLY_ONE,
					"The date to extract the day in the week from.") },
			new FunctionReturnSequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE,
					"the day in the week of the date in the range 1 to 7, where 1 = Sunday, 7 = Saturday."));

	public DayInWeekFunction(XQueryContext context) {
		super(context, signature);
	}

	@Override
	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
		DateValue d = (DateValue) args[0].itemAt(0);
		GregorianCalendar cal = d.calendar.toGregorianCalendar();
		int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);

		return new IntegerValue(dayOfWeek);
	}
}
