/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2005-2009 The eXist Project
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
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AbstractDateTimeValue;
import org.exist.xquery.value.DayTimeDurationValue;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * Implements the xpath-functions for timezone adjustment 
 *
 */
public class FunAdjustTimezone extends BasicFunction {

    public final static FunctionParameterSequenceType DATE_TIME_01_PARAM = new FunctionParameterSequenceType("date-time", Type.DATE_TIME, Cardinality.ZERO_OR_ONE, "The date-time");
    public final static FunctionParameterSequenceType DATE_01_PARAM = new FunctionParameterSequenceType("date", Type.DATE, Cardinality.ZERO_OR_ONE, "The date");
    public final static FunctionParameterSequenceType TIME_01_PARAM = new FunctionParameterSequenceType("time", Type.TIME, Cardinality.ZERO_OR_ONE, "The time");
    public final static FunctionParameterSequenceType DURATION_01_PARAM = new FunctionParameterSequenceType("duration", Type.DAY_TIME_DURATION, Cardinality.ZERO_OR_ONE, "The duration");

    public final static FunctionReturnSequenceType DATE_TIME_01_RETURN = new FunctionReturnSequenceType(Type.DATE_TIME, Cardinality.ZERO_OR_ONE, "the adjusted date-time");
    public final static FunctionReturnSequenceType DATE_01_RETURN = new FunctionReturnSequenceType(Type.DATE, Cardinality.ZERO_OR_ONE, "the adjusted date");
    public final static FunctionReturnSequenceType TIME_01_RETURN = new FunctionReturnSequenceType(Type.TIME, Cardinality.ZERO_OR_ONE, "the adjusted time");

    public final static FunctionSignature fnAdjustDateTimeToTimezone[] = {
        new FunctionSignature(
            new QName("adjust-dateTime-to-timezone", Function.BUILTIN_FUNCTION_NS),
            "Adjusts the xs:dateTime value $date-time to the implicit timezone of the current locale.",
            new SequenceType[] { 
                DATE_TIME_01_PARAM
            },
            DATE_TIME_01_RETURN),
        new FunctionSignature(
            new QName("adjust-dateTime-to-timezone", Function.BUILTIN_FUNCTION_NS),
            "Adjusts the xs:dateTime value $date-time to a specific timezone, or to no timezone at all. " +
            "If $duration is the empty sequence, returns an xs:dateTime without a timezone.",
            new SequenceType[] {
                DATE_TIME_01_PARAM,
                DURATION_01_PARAM
            },
            DATE_TIME_01_RETURN)
    };

    public final static FunctionSignature fnAdjustDateToTimezone[] = {
        new FunctionSignature(
            new QName("adjust-date-to-timezone", Function.BUILTIN_FUNCTION_NS),
            "Adjusts the xs:date value $date to the implicit timezone of the current locale.",
            new SequenceType[] {
                DATE_01_PARAM
            },
            DATE_01_RETURN),
        new FunctionSignature(
            new QName("adjust-date-to-timezone", Function.BUILTIN_FUNCTION_NS),
            "Adjusts the xs:date value $date to a specific timezone, or to no timezone at all. " +
            "If $duration is the empty sequence, returns an xs:date without a timezone.",
            new SequenceType[] { 
                DATE_01_PARAM,
                DURATION_01_PARAM
            },
            DATE_01_RETURN)
    };

    public final static FunctionSignature fnAdjustTimeToTimezone[] = {
        new FunctionSignature(
            new QName("adjust-time-to-timezone", Function.BUILTIN_FUNCTION_NS),
            "Adjusts the xs:time value $time to the implicit timezone of the current locale.",
            new SequenceType[] { 
                TIME_01_PARAM
            },
            TIME_01_RETURN),
        new FunctionSignature(
            new QName("adjust-time-to-timezone", Function.BUILTIN_FUNCTION_NS),
            "Adjusts the xs:time value $time to a specific timezone, or to no timezone at all. " +
            "If $duration is the empty sequence, returns an xs:time without a timezone.",
            new SequenceType[] { 
                TIME_01_PARAM,
                DURATION_01_PARAM
            },
            TIME_01_RETURN)
    };

    public FunAdjustTimezone(XQueryContext context, FunctionSignature signature) {
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
        if (args[0].isEmpty()) 
            {result =Sequence.EMPTY_SEQUENCE;}
        else {
            final AbstractDateTimeValue time = (AbstractDateTimeValue) args[0].itemAt(0);
            if (getSignature().getArgumentCount() == 2) {
                if (args[1].isEmpty()) {
                    result = time.withoutTimezone();
                } else {
                    final DayTimeDurationValue offset = (DayTimeDurationValue) args[1].itemAt(0);
                    result = time.adjustedToTimezone(offset);
                }
            } else {
                result = time.adjustedToTimezone(null);
            }
        }
        if (context.getProfiler().isEnabled())
            {context.getProfiler().end(this, "", result);}
        return result;
    }
}
