/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2007-2009 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.functions.fn;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Constants.Comparison;
import org.exist.xquery.Dependency;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.DateTimeValue;
import org.exist.xquery.value.DateValue;
import org.exist.xquery.value.DayTimeDurationValue;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.TimeValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 *
 * @author perig
 *
 */
public class FunDateTime extends BasicFunction {

    public final static FunctionSignature signature =
        new FunctionSignature(
            new QName("dateTime", Function.BUILTIN_FUNCTION_NS, FnModule.PREFIX),
            "Creates an xs:dateTime from an xs:date, $date, and an xs:time, $time.",
            new SequenceType[] {
                new FunctionParameterSequenceType("date", Type.DATE,
                    Cardinality.ZERO_OR_ONE, "The date as xs:date"),
                new FunctionParameterSequenceType("time", Type.TIME,
                    Cardinality.ZERO_OR_ONE, "The time as xs:time")
            },
            new FunctionReturnSequenceType(Type.DATE_TIME,
                Cardinality.ZERO_OR_ONE, "the combined date and time as xs:dateTime")
        );

    public FunDateTime(XQueryContext context) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES,
                "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES,
                    "CONTEXT SEQUENCE", contextSequence);}
        }
        Sequence result;
        if (args[0].isEmpty() || args[1].isEmpty()) {
            result = Sequence.EMPTY_SEQUENCE;
        } else if (args[0].hasMany()) {
            throw new XPathException(this, ErrorCodes.XPTY0004,
                "Expected at most one xs:date", args[0]);
        } else if (args[1].hasMany()) {
            throw new XPathException(this, ErrorCodes.XPTY0004,
                "Expected at most one xs:time", args[1]);
        } else {
            final DateValue dv = (DateValue)args[0].itemAt(0);
            final TimeValue tv = (TimeValue)args[1].itemAt(0);
            if (!dv.getTimezone().isEmpty()) {
                if (!tv.getTimezone().isEmpty()) {
                    if (!((DayTimeDurationValue)dv.getTimezone().itemAt(0))
                    		.compareTo(null, Comparison.EQ,
                    				((DayTimeDurationValue)tv.getTimezone().itemAt(0)))) {
                        
                    	final ValueSequence argsSeq = new ValueSequence();
                        argsSeq.add(dv);
                        argsSeq.add(tv);
                        
                        throw new XPathException(this, ErrorCodes.FORG0008,
                            "Operands have different timezones", argsSeq);
                    }
                }
            }
            String dtv = ((DateTimeValue)dv.convertTo(Type.DATE_TIME)).getTrimmedCalendar().toXMLFormat();
            
            if (dv.getTimezone().isEmpty()) {
                dtv = dtv.substring(0, dtv.length() - 8);
                result = new DateTimeValue(dtv + tv.getStringValue());
            
            } else if ("PT0S".equals(((DayTimeDurationValue)dv.getTimezone().itemAt(0)).getStringValue())) {
                dtv = dtv.substring(0, dtv.length() - 9);
                if (tv.getTimezone().isEmpty()) {
                    result = new DateTimeValue(dtv + tv.getStringValue() + "Z");
                } else {
                    result = new DateTimeValue(dtv + tv.getStringValue());
                }
            
            } else {
                if (tv.getTimezone().isEmpty()) {
                    final String tz = dtv.substring(19);
                    dtv = dtv.substring(0, dtv.length() - 14);
                    result = new DateTimeValue(dtv + tv.getStringValue() + tz);

                } else {
                    dtv = dtv.substring(0, dtv.length() - 14);
                    result = new DateTimeValue(dtv + tv.getStringValue());
                }
            }
        }
        if (context.getProfiler().isEnabled())
            {context.getProfiler().end(this, "", result);}
        return result;
    }
}
