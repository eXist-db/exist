/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2009 The eXist Project
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

import java.math.BigDecimal;

import javax.xml.datatype.DatatypeConstants;

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
import org.exist.xquery.value.DecimalValue;
import org.exist.xquery.value.DurationValue;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 *
 * @author wolf
 * @author piotr kaminski
 *
 */
public class FunGetDurationComponent extends BasicFunction {
	protected static final Logger logger = LogManager.getLogger(FunGetDurationComponent.class);
    public final static FunctionParameterSequenceType DAYTIME_DURA_01_PARAM = new FunctionParameterSequenceType("duration", Type.DAY_TIME_DURATION, Cardinality.ZERO_OR_ONE, "The duration as xs:dayTimeDuration");
    public final static FunctionParameterSequenceType YEARMONTH_DURA_01_PARAM = new FunctionParameterSequenceType("duration", Type.YEAR_MONTH_DURATION, Cardinality.ZERO_OR_ONE, "The duration as xs:yearMonthDuration");
    public final static FunctionParameterSequenceType DURA_01_PARAM = new FunctionParameterSequenceType("duration", Type.DURATION, Cardinality.ZERO_OR_ONE, "The duration as xs:duration");

	public final static FunctionSignature fnDaysFromDuration =
		new FunctionSignature(
			new QName("days-from-duration", Function.BUILTIN_FUNCTION_NS),
			"Returns an xs:integer representing the days component in the canonical lexical " +
			"representation of the value of $duration. The result may be negative.",
			new SequenceType[] {
                DURA_01_PARAM
            },
			new FunctionReturnSequenceType(Type.INTEGER, Cardinality.ZERO_OR_ONE, "the days component of $duration"));
	
	public final static FunctionSignature fnHoursFromDuration =
		new FunctionSignature(
			new QName("hours-from-duration", Function.BUILTIN_FUNCTION_NS),
			"Returns an xs:integer representing the hours component in the canonical lexical " +
			"representation of the value of $duration. The result may be negative.",
			new SequenceType[] {
                DURA_01_PARAM
            },
			new FunctionReturnSequenceType(Type.INTEGER, Cardinality.ZERO_OR_ONE, "the hours component of $duration"));
	
	public final static FunctionSignature fnMinutesFromDuration =
		new FunctionSignature(
			new QName("minutes-from-duration", Function.BUILTIN_FUNCTION_NS),
			"Returns an xs:integer representing the minutes component in the canonical " +
			"lexical representation of the value of $duration. The result may be negative.",
			new SequenceType[] {
                DURA_01_PARAM
            },
			new FunctionReturnSequenceType(Type.INTEGER, Cardinality.ZERO_OR_ONE, "the minutes component of $duration"));

	public final static FunctionSignature fnSecondsFromDuration =
		new FunctionSignature(
			new QName("seconds-from-duration", Function.BUILTIN_FUNCTION_NS),
			"Returns an xs:decimal representing the seconds component in the canonical lexical " +
			"representation of the value of $duration. The result may be negative",
			new SequenceType[] {
                DURA_01_PARAM
            },
			new FunctionReturnSequenceType(Type.DECIMAL, Cardinality.ZERO_OR_ONE, "the seconds component of $duration"));

   public final static FunctionSignature fnMonthsFromDuration = new FunctionSignature(
			new QName("months-from-duration", Function.BUILTIN_FUNCTION_NS),
			"Returns an xs:integer representing the months component in the canonical lexical " +
			"representation of the value of $duration. The result may be negative.",
			new SequenceType[] {
                DURA_01_PARAM
            },
			new FunctionReturnSequenceType(Type.INTEGER, Cardinality.ZERO_OR_ONE, "the months component of $duration"));

   public final static FunctionSignature fnYearsFromDuration = new FunctionSignature(
			new QName("years-from-duration", Function.BUILTIN_FUNCTION_NS),
			"Returns an xs:integer representing the years component in the canonical lexical " +
			"representation of the value of $duration. The result may be negative.",
			new SequenceType[] {
                DURA_01_PARAM
            },
			new FunctionReturnSequenceType(Type.INTEGER, Cardinality.ZERO_OR_ONE, "the years component of $duration"));

   
	public FunGetDurationComponent(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	public Sequence eval(Sequence[] args, Sequence contextSequence)
        throws XPathException {
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
			final DurationValue duration = new DurationValue(((DurationValue) arg.itemAt(0)).getCanonicalDuration());
			if (isCalledAs("days-from-duration")) {
            result = new IntegerValue(duration.getPart(DurationValue.DAY));
			} else if (isCalledAs("hours-from-duration")) {
            result = new IntegerValue(duration.getPart(DurationValue.HOUR));
			} else if (isCalledAs("minutes-from-duration")) {
            result = new IntegerValue(duration.getPart(DurationValue.MINUTE));
			} else if (isCalledAs("seconds-from-duration")) {
				if (duration.getCanonicalDuration().getField(DatatypeConstants.SECONDS) == null)
					{result = new DecimalValue(0);}
				else
					{result = new DecimalValue((BigDecimal)duration.getCanonicalDuration().getField(DatatypeConstants.SECONDS));}
				if (duration.getCanonicalDuration().getSign() < 0)
					{result = ((DecimalValue)result).negate();}
			} else if (isCalledAs("months-from-duration")) {
            result = new IntegerValue(duration.getPart(DurationValue.MONTH));
			} else if (isCalledAs("years-from-duration")) {
            result = new IntegerValue(duration.getPart(DurationValue.YEAR));
			} else {
                logger.error("can't handle function " + mySignature.getName().getLocalPart());
				throw new Error("can't handle function " + mySignature.getName().getLocalPart());
			}
		}
		
		if (context.getProfiler().isEnabled()) 
			{context.getProfiler().end(this, "", result);}
		
		return result;
		
	}

}
