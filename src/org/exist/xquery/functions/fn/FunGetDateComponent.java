/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2004-2009 The eXist Project
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
import org.exist.xquery.value.DateValue;
import org.exist.xquery.value.DecimalValue;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 *
 * @author wolf
 *
 */
public class FunGetDateComponent extends BasicFunction {
	protected static final Logger logger = LogManager.getLogger(FunGetDateComponent.class);
    public final static FunctionParameterSequenceType DATE_01_PARAM = new FunctionParameterSequenceType("date", Type.DATE, Cardinality.ZERO_OR_ONE, "The date as xs:date");
    public final static FunctionParameterSequenceType TIME_01_PARAM = new FunctionParameterSequenceType("time", Type.TIME, Cardinality.ZERO_OR_ONE, "The time as xs:time");
        public final static FunctionParameterSequenceType DATE_TIME_01_PARAM = new FunctionParameterSequenceType("date-time", Type.DATE_TIME, Cardinality.ZERO_OR_ONE, "The date-time as xs:dateTime");

	// ----- fromDate
	public final static FunctionSignature fnDayFromDate =
		new FunctionSignature(
			new QName("day-from-date", Function.BUILTIN_FUNCTION_NS),
			"Returns an xs:integer between 1 and 31, both inclusive, representing " +
			"the day component in the localized value of $date.",
			new SequenceType[] {
               DATE_01_PARAM 
            },
			new FunctionReturnSequenceType(Type.INTEGER, Cardinality.ZERO_OR_ONE, "the day component from $date"));
	
	public final static FunctionSignature fnMonthFromDate =
		new FunctionSignature(
			new QName("month-from-date", Function.BUILTIN_FUNCTION_NS),
			"Returns an xs:integer between 1 and 12, both inclusive, representing the month " +
			"component in the localized value of $date.",
			new SequenceType[] {
                DATE_01_PARAM
            },
			new FunctionReturnSequenceType(Type.INTEGER, Cardinality.ZERO_OR_ONE, "the month component from $date"));
	
	public final static FunctionSignature fnYearFromDate =
		new FunctionSignature(
			new QName("year-from-date", Function.BUILTIN_FUNCTION_NS),
			"Returns an xs:integer representing the year in the localized value of $date. The value may be negative.",
			new SequenceType[] {
                DATE_01_PARAM
            },
			new FunctionReturnSequenceType(Type.INTEGER, Cardinality.ZERO_OR_ONE, "the year component from $date"));
	
	public final static FunctionSignature fnTimezoneFromDate =
		new FunctionSignature(
			new QName("timezone-from-date", Function.BUILTIN_FUNCTION_NS),
            "Returns the timezone component of $date if any. If $date has a timezone component, then the result is an xs:dayTimeDuration that indicates deviation from UTC; its value may range from +14:00 to -14:00 hours, both inclusive. Otherwise, the result is the empty sequence." +
            "If $date is the empty sequence, returns the empty sequence.",
			new SequenceType[] {
                DATE_01_PARAM
            },
            new FunctionReturnSequenceType(Type.DAY_TIME_DURATION, Cardinality.ZERO_OR_ONE, "the timezone component from $date"));

	// ----- fromTime
	public final static FunctionSignature fnHoursFromTime =
		new FunctionSignature(
			new QName("hours-from-time", Function.BUILTIN_FUNCTION_NS),
			"Returns an xs:integer between 0 and 23, both inclusive, representing the " +
			"value of the hours component in the localized value of $time.",
			new SequenceType[] {
                TIME_01_PARAM
            },
			new FunctionReturnSequenceType(Type.INTEGER, Cardinality.ZERO_OR_ONE, "the hours component from $time"));
	
	public final static FunctionSignature fnMinutesFromTime =
		new FunctionSignature(
			new QName("minutes-from-time", Function.BUILTIN_FUNCTION_NS),
			"Returns an xs:integer value between 0 to 59, both inclusive, representing the value of " +
			"the minutes component in the localized value of $time.",
			new SequenceType[] {
                TIME_01_PARAM
            },
			new FunctionReturnSequenceType(Type.INTEGER, Cardinality.ZERO_OR_ONE, "the minutes component from $time"));
	
	public final static FunctionSignature fnSecondsFromTime =
		new FunctionSignature(
			new QName("seconds-from-time", Function.BUILTIN_FUNCTION_NS),
			"Returns an xs:decimal value between 0 and 60.999..., both inclusive, representing the " +
			"seconds and fractional seconds in the localized value of $date. Note that the value can be " +
			"greater than 60 seconds to accommodate occasional leap seconds used to keep human time " +
			"synchronized with the rotation of the planet.",
			new SequenceType[] {
                TIME_01_PARAM
            },
			new FunctionReturnSequenceType(Type.DECIMAL, Cardinality.ZERO_OR_ONE, "the seconds component from $time"));
	
	public final static FunctionSignature fnTimezoneFromTime =
		new FunctionSignature(
			new QName("timezone-from-time", Function.BUILTIN_FUNCTION_NS),
			"Returns the timezone component of $time if any. If $time has a timezone component, " +
			"then the result is an xdt:dayTimeDuration that indicates deviation from UTC; its value may " +
			"range from +14:00 to -14:00 hours, both inclusive. Otherwise, the result is the empty sequence.",
			new SequenceType[] {
                TIME_01_PARAM
            },
			new FunctionReturnSequenceType(Type.DAY_TIME_DURATION, Cardinality.ZERO_OR_ONE, "the timezone component from $time"));

	
	// ----- fromDateTime
	public final static FunctionSignature fnDayFromDateTime =
		new FunctionSignature(
			new QName("day-from-dateTime", Function.BUILTIN_FUNCTION_NS),
			"Returns an xs:integer between 1 and 31, both inclusive, representing " +
			"the day component in the localized value of $date-time.",
			new SequenceType[] {
                DATE_TIME_01_PARAM
            },
			new FunctionReturnSequenceType(Type.INTEGER, Cardinality.ZERO_OR_ONE, "the day component from $date-time"));
	
	public final static FunctionSignature fnMonthFromDateTime =
		new FunctionSignature(
			new QName("month-from-dateTime", Function.BUILTIN_FUNCTION_NS),
			"Returns an xs:integer between 1 and 12, both inclusive, representing the month " +
			"component in the localized value of $date-time.",
			new SequenceType[] {
                DATE_TIME_01_PARAM
            },
			new FunctionReturnSequenceType(Type.INTEGER, Cardinality.ZERO_OR_ONE, "the month component from $date-time"));
	
	public final static FunctionSignature fnYearFromDateTime =
		new FunctionSignature(
			new QName("year-from-dateTime", Function.BUILTIN_FUNCTION_NS),
			"Returns an xs:integer representing the year in the localized value of $date-time. The value may be negative.",
			new SequenceType[] {
                DATE_TIME_01_PARAM
            },
			new FunctionReturnSequenceType(Type.INTEGER, Cardinality.ZERO_OR_ONE, "the year component from $date-time"));
	
	public final static FunctionSignature fnHoursFromDateTime =
		new FunctionSignature(
			new QName("hours-from-dateTime", Function.BUILTIN_FUNCTION_NS),
			"Returns an xs:integer between 0 and 23, both inclusive, representing the " +
			"value of the hours component in the localized value of $date-time.",
			new SequenceType[] {
                DATE_TIME_01_PARAM
            },
			new FunctionReturnSequenceType(Type.INTEGER, Cardinality.ZERO_OR_ONE, "the hours component from $date-time"));
	
	public final static FunctionSignature fnMinutesFromDateTime =
		new FunctionSignature(
			new QName("minutes-from-dateTime", Function.BUILTIN_FUNCTION_NS),
			"Returns an xs:integer value between 0 to 59, both inclusive, representing the value of " +
			"the minutes component in the localized value of $date-time.",
			new SequenceType[] {
                DATE_TIME_01_PARAM
            },
			new FunctionReturnSequenceType(Type.INTEGER, Cardinality.ZERO_OR_ONE, "the minutes component from $date-time"));
	
	public final static FunctionSignature fnSecondsFromDateTime =
		new FunctionSignature(
			new QName("seconds-from-dateTime", Function.BUILTIN_FUNCTION_NS),
			"Returns an xs:decimal value between 0 and 60.999..., both inclusive, representing the " +
			"seconds and fractional seconds in the localized value of $date-time. Note that the value can be " +
			"greater than 60 seconds to accommodate occasional leap seconds used to keep human time " +
			"synchronized with the rotation of the planet.",
			new SequenceType[] {
                DATE_TIME_01_PARAM
            },
			new FunctionReturnSequenceType(Type.DECIMAL, Cardinality.ZERO_OR_ONE, "the seconds component from $date-time"));
	
	public final static FunctionSignature fnTimezoneFromDateTime =
		new FunctionSignature(
			new QName("timezone-from-dateTime", Function.BUILTIN_FUNCTION_NS),
			"Returns the timezone component of $date-time if any. If $date-time has a timezone component, " +
			"then the result is an xdt:dayTimeDuration that indicates deviation from UTC; its value may " +
			"range from +14:00 to -14:00 hours, both inclusive. Otherwise, the result is the empty sequence.",
			new SequenceType[] {
                DATE_TIME_01_PARAM
            },
			new FunctionReturnSequenceType(Type.DAY_TIME_DURATION, Cardinality.ZERO_OR_ONE, "the timezone component from $date-time"));
	
	
	public FunGetDateComponent(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
		if (context.getProfiler().isEnabled()) {
			context.getProfiler().start(this);
			context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES",
					Dependency.getDependenciesName(this.getDependencies()));
			if (contextSequence != null)
				{context.getProfiler().message(this, Profiler.START_SEQUENCES,
						"CONTEXT SEQUENCE", contextSequence);}
		}
        
		Sequence result;
		if (args.length == 0 || args[0].isEmpty()) {
			result = Sequence.EMPTY_SEQUENCE;
		} else {
			final Sequence arg = args[0];
			final AbstractDateTimeValue date = (AbstractDateTimeValue) arg.itemAt(0);
			if (isCalledAs("day-from-dateTime") || isCalledAs("day-from-date")) {
				result = new IntegerValue(date.getPart(DateValue.DAY), Type.INTEGER);
			} else if (isCalledAs("month-from-dateTime") || isCalledAs("month-from-date")) {
				result = new IntegerValue(date.getPart(DateValue.MONTH),
						Type.INTEGER);
			} else if (isCalledAs("year-from-dateTime") || isCalledAs("year-from-date")) {
				result = new IntegerValue(date.getPart(DateValue.YEAR),
						Type.INTEGER);
			} else if (isCalledAs("hours-from-dateTime") || isCalledAs("hours-from-time")) {
				result = new IntegerValue(date.getPart(DateValue.HOUR),
						Type.INTEGER);
			} else if (isCalledAs("minutes-from-dateTime") || isCalledAs("minutes-from-time")) {
				result = new IntegerValue(date.getPart(DateValue.MINUTE),
						Type.INTEGER);
			} else if (isCalledAs("seconds-from-dateTime") || isCalledAs("seconds-from-time")) {
				result = new IntegerValue(date.calendar.getSecond()).convertTo(Type.DECIMAL);
				if (date.calendar.getFractionalSecond() != null)						
					{result = ((DecimalValue)result).plus(new DecimalValue(date.calendar.getFractionalSecond()));}			
			} else if (isCalledAs("timezone-from-dateTime") || isCalledAs("timezone-from-date") || isCalledAs("timezone-from-time")) {
				result = date.getTimezone();
			} else {
                logger.error("can't handle function " + mySignature.getName().getLocalPart());
				throw new Error("can't handle function " + mySignature.getName().getLocalPart());
			}
		}
		
		if (context.getProfiler().isEnabled()) {context.getProfiler().end(this, "", result);}
		
		return result;
		
	}

}
