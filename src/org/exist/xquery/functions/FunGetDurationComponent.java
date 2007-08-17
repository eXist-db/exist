/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
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
package org.exist.xquery.functions;

import javax.xml.datatype.DatatypeConstants;

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
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;


public class FunGetDurationComponent extends BasicFunction {

	public final static FunctionSignature fnDaysFromDuration =
		new FunctionSignature(
			new QName("days-from-duration", Function.BUILTIN_FUNCTION_NS),
			"Returns an xs:integer representing the days component in the canonical lexical " +
			"representation of the value of $a. The result may be negative.",
			new SequenceType[] {new SequenceType(Type.DAY_TIME_DURATION, Cardinality.ZERO_OR_ONE)},
			new SequenceType(Type.INTEGER, Cardinality.ZERO_OR_ONE));
	
	public final static FunctionSignature fnHoursFromDuration =
		new FunctionSignature(
			new QName("hours-from-duration", Function.BUILTIN_FUNCTION_NS),
			"Returns an xs:integer representing the hours component in the canonical lexical " +
			"representation of the value of $a. The result may be negative.",
			new SequenceType[] {new SequenceType(Type.DAY_TIME_DURATION, Cardinality.ZERO_OR_ONE)},
			new SequenceType(Type.INTEGER, Cardinality.ZERO_OR_ONE));
	
	public final static FunctionSignature fnMinutesFromDuration =
		new FunctionSignature(
			new QName("minutes-from-duration", Function.BUILTIN_FUNCTION_NS),
			"Returns an xs:integer representing the minutes component in the canonical " +
			"lexical representation of the value of $a. The result may be negative.",
			new SequenceType[] {new SequenceType(Type.DAY_TIME_DURATION, Cardinality.ZERO_OR_ONE)},
			new SequenceType(Type.INTEGER, Cardinality.ZERO_OR_ONE));

	public final static FunctionSignature fnSecondsFromDuration =
		new FunctionSignature(
			new QName("seconds-from-duration", Function.BUILTIN_FUNCTION_NS),
			"Returns an xs:decimal representing the seconds component in the canonical lexical " +
			"representation of the value of $a. The result may be negative",
			new SequenceType[] {new SequenceType(Type.DAY_TIME_DURATION, Cardinality.ZERO_OR_ONE)},
			new SequenceType(Type.DECIMAL, Cardinality.ZERO_OR_ONE));

   public final static FunctionSignature fnMonthsFromDuration = new FunctionSignature(
			new QName("months-from-duration", Function.BUILTIN_FUNCTION_NS),
			"Returns an xs:integer representing the months component in the canonical lexical " +
			"representation of the value of $a. The result may be negative.",
			new SequenceType[] {new SequenceType(Type.YEAR_MONTH_DURATION, Cardinality.ZERO_OR_ONE)},
			new SequenceType(Type.INTEGER, Cardinality.ZERO_OR_ONE));

   public final static FunctionSignature fnYearsFromDuration = new FunctionSignature(
			new QName("years-from-duration", Function.BUILTIN_FUNCTION_NS),
			"Returns an xs:integer representing the years component in the canonical lexical " +
			"representation of the value of $a. The result may be negative.",
			new SequenceType[] {new SequenceType(Type.YEAR_MONTH_DURATION, Cardinality.ZERO_OR_ONE)},
			new SequenceType(Type.INTEGER, Cardinality.ZERO_OR_ONE));

   
	public FunGetDurationComponent(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
		if (context.getProfiler().isEnabled()) {
			context.getProfiler().start(this);
			context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES",
					Dependency.getDependenciesName(this.getDependencies()));
			if (contextSequence != null)
				context.getProfiler().message(this, Profiler.START_SEQUENCES,
						"CONTEXT SEQUENCE", contextSequence);
		}
        
		Sequence result;
		if (args.length == 0 || args[0].isEmpty()) {
			result = Sequence.EMPTY_SEQUENCE;
		} else {
			Sequence arg = args[0];
			DurationValue duration = new DurationValue(((DurationValue) arg.itemAt(0)).getCanonicalDuration());
			if (isCalledAs("days-from-duration")) {
            result = new IntegerValue(duration.getPart(DurationValue.DAY));
			} else if (isCalledAs("hours-from-duration")) {
            result = new IntegerValue(duration.getPart(DurationValue.HOUR));
			} else if (isCalledAs("minutes-from-duration")) {
            result = new IntegerValue(duration.getPart(DurationValue.MINUTE));
			} else if (isCalledAs("seconds-from-duration")) {
				if (duration.getCanonicalDuration().getField(DatatypeConstants.SECONDS) == null)
					//TODO sign inot account ?
					result = new DecimalValue(0);
				else
					result = new DecimalValue(duration.getCanonicalDuration().getField(DatatypeConstants.SECONDS).doubleValue() * duration.getCanonicalDuration().getSign());
			} else if (isCalledAs("months-from-duration")) {
            result = new IntegerValue(duration.getPart(DurationValue.MONTH));
			} else if (isCalledAs("years-from-duration")) {
            result = new IntegerValue(duration.getPart(DurationValue.YEAR));
			} else {
				throw new Error("can't handle function " + mySignature.getName().getLocalName());
			}
		}
		
		if (context.getProfiler().isEnabled()) 
			context.getProfiler().end(this, "", result);
		
		return result;
		
	}

}
