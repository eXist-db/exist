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
 *  $Id: $
 */
package org.exist.xquery.modules.datetime;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AbstractDateTimeValue;
import org.exist.xquery.value.DurationValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
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

        public final static FunctionSignature signature[] = {
                new FunctionSignature(
                        new QName("datetime-range", DateTimeModule.NAMESPACE_URI, DateTimeModule.PREFIX),
                        "Returns a range of items of xs:dateTime* " +
                        "starting at $date-time incremented by the duration",
                        new SequenceType[] { 
                                new FunctionParameterSequenceType("date-time", Type.DATE_TIME, Cardinality.EXACTLY_ONE, "dateTime at the beginning of the range(s)"),
                                new FunctionParameterSequenceType("duration", Type.DURATION, Cardinality.EXACTLY_ONE, "The xs:duration to generate the dateTime range"),
                                new FunctionParameterSequenceType("number-of-ranges", Type.INTEGER, Cardinality.EXACTLY_ONE, "The number of ranges to generate")
                        }, new FunctionParameterSequenceType("result", Type.DATE_TIME, Cardinality.ZERO_OR_MORE, "The range(s)")),
                new FunctionSignature(
                    new QName("date-range", DateTimeModule.NAMESPACE_URI, DateTimeModule.PREFIX),
                    "Returns a range of items of xs:date* " +
                    "starting at $date incremented by the duration ",
                    new SequenceType[] { 
                            new FunctionParameterSequenceType("date", Type.DATE, Cardinality.EXACTLY_ONE, "date at the beginning of the range(s)"),
                            new FunctionParameterSequenceType("duration", Type.DURATION, Cardinality.EXACTLY_ONE, "The xs:duration to generate the date range"),
                            new FunctionParameterSequenceType("number-of-ranges", Type.INTEGER, Cardinality.EXACTLY_ONE, "The number of ranges to generate")
                    }, new FunctionParameterSequenceType("result", Type.DATE_TIME, Cardinality.ZERO_OR_MORE, "The range(s)")),
                new FunctionSignature(
                    new QName("time-range", DateTimeModule.NAMESPACE_URI, DateTimeModule.PREFIX),
                    "Returns a range of items of xs:time* " +
                    "starting at $time incremented by duration",
                    new SequenceType[] { 
                            new FunctionParameterSequenceType("time", Type.TIME, Cardinality.EXACTLY_ONE, "time at the beginning of the range(s)"),
                            new FunctionParameterSequenceType("duration", Type.DURATION, Cardinality.EXACTLY_ONE, "The xs:duration to generate the time range"),
                            new FunctionParameterSequenceType("number-of-ranges", Type.INTEGER, Cardinality.EXACTLY_ONE, "The number of ranges to generate")
                    }, new FunctionParameterSequenceType("result", Type.DATE_TIME, Cardinality.ZERO_OR_MORE, "The range(s)"))
        };

        public DateRangeFunctions(XQueryContext context, FunctionSignature signature)
        {
                super(context, signature);
        }

        public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException
        {
                Item item = (Item)args[0].itemAt(0);
                if (!(item instanceof AbstractDateTimeValue)) {
                  throw new XPathException("Function requires one of xs:dateTime, xs:date, or xs:time as first parameter.");
                }
                ValueSequence result = new ValueSequence();
                AbstractDateTimeValue d1 = (AbstractDateTimeValue)item;
                DurationValue diff = (DurationValue)args[1].itemAt(0);
                Sequence seq = args[2].convertTo(Type.INTEGER);
                int count = ((NumericValue)seq.itemAt(0).convertTo(Type.INTEGER)).getInt();
                if (count < 0) count = 0;

                // loop through dates/times
                while(count-- > 0) {
                  result.add(d1);
                  d1 = (AbstractDateTimeValue)d1.plus(diff);
                }
                return result;
        }
}
 	  	 
