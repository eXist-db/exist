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
import org.exist.xquery.functions.fn.FnFormatDates;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.TimeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;

/**
 * @author Adam Retter <adam@exist-db.org>
 */
public class FormatTimeFunction extends BasicFunction
{
	protected static final Logger logger = LogManager.getLogger(FormatTimeFunction.class);

    public final static FunctionSignature signature =
        new FunctionSignature(
                new QName("format-time", DateTimeModule.NAMESPACE_URI, DateTimeModule.PREFIX),
                "Returns a xs:string of the xs:time formatted according to the SimpleDateFormat format.",
                new SequenceType[] { 
                        new FunctionParameterSequenceType("time", Type.TIME, Cardinality.EXACTLY_ONE, "The time to to be formatted."),
                        new FunctionParameterSequenceType("simple-date-format", Type.STRING, Cardinality.EXACTLY_ONE, "The format string according to the Java java.text.SimpleDateFormat class")
                },      
                new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "the formatted time string"),
                FnFormatDates.FNS_FORMAT_TIME_2
    );


    public FormatTimeFunction(XQueryContext context)
    {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException
    {
        TimeValue t = (TimeValue)args[0].itemAt(0);
        String timeFormat = args[1].itemAt(0).toString();

        SimpleDateFormat sdf = new SimpleDateFormat(timeFormat);

        GregorianCalendar cal = t.calendar.toGregorianCalendar();
        String formattedDate = sdf.format(cal.getTime());

        return new StringValue(formattedDate);
    }
}
