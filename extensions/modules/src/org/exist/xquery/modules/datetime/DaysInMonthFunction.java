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
 * @author Adam Retter <adam.retter@exist-db.org>
 * @version 1.1
 */
public class DaysInMonthFunction extends BasicFunction
{
	protected static final Logger logger = LogManager.getLogger(DaysInMonthFunction.class);

    public final static FunctionSignature signature = new FunctionSignature(
        new QName("days-in-month", DateTimeModule.NAMESPACE_URI, DateTimeModule.PREFIX),
        "Returns the number of days in the month of the date.",
        new SequenceType[] {
            new FunctionParameterSequenceType("date", Type.DATE, Cardinality.EXACTLY_ONE, "The date for extracting the number of days in the month component.")
        },
        new FunctionReturnSequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE, "the number of days in the given month.")
    );

    public DaysInMonthFunction(XQueryContext context)
    {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException
    {
        DateValue d = (DateValue)args[0].itemAt(0);
        GregorianCalendar cal = d.calendar.toGregorianCalendar();
        int days = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

        return new IntegerValue(days);
    }
}
