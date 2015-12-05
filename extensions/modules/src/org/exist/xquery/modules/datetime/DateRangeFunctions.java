/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009-09 The eXist Project
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
import org.exist.xquery.value.AbstractDateTimeValue;
import org.exist.xquery.value.DurationValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NumericValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;


/**
 * @author Michael Westbay <westbay@japanesebaseball.com>
 */
public class DateRangeFunctions extends BasicFunction {

	protected static final Logger logger = LogManager.getLogger(DateRangeFunctions.class);

	public final static FunctionSignature signature[] = {
        new FunctionSignature(
            new QName("datetime-range", DateTimeModule.NAMESPACE_URI, DateTimeModule.PREFIX),
            "Generates a range of xs:dateTime values, incremented cumulatively by a fixed duration from a start xs:dateTime",
            new SequenceType[] {
                new FunctionParameterSequenceType("start-date-time", Type.DATE_TIME, Cardinality.EXACTLY_ONE, "The dateTime to start at."),
                new FunctionParameterSequenceType("increment", Type.DURATION, Cardinality.EXACTLY_ONE, "The duration increment."),
                new FunctionParameterSequenceType("iterations", Type.INTEGER, Cardinality.EXACTLY_ONE, "The number of increments to generate.")
            },
            new FunctionReturnSequenceType(Type.DATE_TIME, Cardinality.ZERO_OR_MORE, "the range(s)")
        ),

        new FunctionSignature(
            new QName("date-range", DateTimeModule.NAMESPACE_URI, DateTimeModule.PREFIX),
           "Generates a range of xs:date values, incremented cumulatively by a fixed duration from a start xs:date",
            new SequenceType[] {
                new FunctionParameterSequenceType("start-date", Type.DATE, Cardinality.EXACTLY_ONE, "The date to start at."),
                new FunctionParameterSequenceType("increment", Type.DURATION, Cardinality.EXACTLY_ONE, "The duration increment."),
                new FunctionParameterSequenceType("iterations", Type.INTEGER, Cardinality.EXACTLY_ONE, "The number of increments to generate.")
            },
            new FunctionReturnSequenceType(Type.DATE, Cardinality.ZERO_OR_MORE, "the range(s)")
        ),

        new FunctionSignature(
            new QName("time-range", DateTimeModule.NAMESPACE_URI, DateTimeModule.PREFIX),
            "Generates a range of xs:time values, incremented cumulatively by a fixed duration from a start xs:time",
            new SequenceType[] {
                new FunctionParameterSequenceType("start-time", Type.TIME, Cardinality.EXACTLY_ONE, "The time to start at."),
                new FunctionParameterSequenceType("increment", Type.DAY_TIME_DURATION, Cardinality.EXACTLY_ONE, "The duration increment."),
                new FunctionParameterSequenceType("iterations", Type.INTEGER, Cardinality.EXACTLY_ONE, "The number of increments to generate.")
            },
            new FunctionReturnSequenceType(Type.TIME, Cardinality.ZERO_OR_MORE, "the range(s)")
        )
    };

    
    public DateRangeFunctions(XQueryContext context, FunctionSignature signature)
    {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException
    {
        Item item = args[0].itemAt(0);

        if(!(item instanceof AbstractDateTimeValue))
          throw new XPathException("Function requires one of xs:dateTime, xs:date, or xs:time as first parameter.");

        ValueSequence result = new ValueSequence();
        AbstractDateTimeValue d1 = (AbstractDateTimeValue)item;
        DurationValue diff = (DurationValue)args[1].itemAt(0);
        Sequence seq = args[2].convertTo(Type.INTEGER);
        int count = ((NumericValue)seq.itemAt(0).convertTo(Type.INTEGER)).getInt();
        if (count < 0)
            count = 0;

        // loop through dates/times
        while(count-- > 0)
        {
          result.add(d1);
          d1 = (AbstractDateTimeValue)d1.plus(diff);
        }

        return result;
    }
}
