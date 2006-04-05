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

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Function;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.DateValue;
import org.exist.xquery.value.DecimalValue;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.TimeValue;
import org.exist.xquery.value.Type;

/**
 * @author wolf
 */
public class FunGetTimeComponent extends BasicFunction {

	public final static FunctionSignature fnHoursFromTime =
		new FunctionSignature(
			new QName("hours-from-time", Function.BUILTIN_FUNCTION_NS),
			"Returns an xs:integer between 0 and 23, both inclusive, representing the " +
			"value of the hours component in the localized value of $arg.",
			new SequenceType[] { new SequenceType(Type.TIME, Cardinality.ZERO_OR_ONE)},
			new SequenceType(Type.INTEGER, Cardinality.ZERO_OR_ONE));
	
	public final static FunctionSignature fnMinutesFromTime =
		new FunctionSignature(
			new QName("minutes-from-time", Function.BUILTIN_FUNCTION_NS),
			"Returns an xs:integer value between 0 to 59, both inclusive, representing the value of " +
			"the minutes component in the localized value of $arg.",
			new SequenceType[] { new SequenceType(Type.TIME, Cardinality.ZERO_OR_ONE)},
			new SequenceType(Type.INTEGER, Cardinality.ZERO_OR_ONE));
	
	public final static FunctionSignature fnSecondsFromTime =
		new FunctionSignature(
			new QName("seconds-from-time", Function.BUILTIN_FUNCTION_NS),
			"Returns an xs:decimal value between 0 and 60.999..., both inclusive, representing the " +
			"seconds and fractional seconds in the localized value of $arg. Note that the value can be " +
			"greater than 60 seconds to accommodate occasional leap seconds used to keep human time " +
			"synchronized with the rotation of the planet.",
			new SequenceType[] { new SequenceType(Type.TIME, Cardinality.ZERO_OR_ONE)},
			new SequenceType(Type.DECIMAL, Cardinality.ZERO_OR_ONE));
	
	public final static FunctionSignature fnTimezoneFromTime =
		new FunctionSignature(
			new QName("timezone-from-time", Function.BUILTIN_FUNCTION_NS),
			"Returns the timezone component of $arg if any. If $arg has a timezone component, " +
			"then the result is an xdt:dayTimeDuration that indicates deviation from UTC; its value may " +
			"range from +14:00 to -14:00 hours, both inclusive. Otherwise, the result is the empty sequence.",
			new SequenceType[] { new SequenceType(Type.TIME, Cardinality.ZERO_OR_ONE)},
			new SequenceType(Type.DAY_TIME_DURATION, Cardinality.ZERO_OR_ONE));
	
	/**
	 * @param context
	 * @param signature
	 */
	public FunGetTimeComponent(XQueryContext context,
			FunctionSignature signature) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
        }
        
        Sequence result;
        Sequence arg = args[0];
		if (arg.isEmpty())
            result = Sequence.EMPTY_SEQUENCE;
        else {
    		TimeValue time = (TimeValue) arg.itemAt(0);
    		if (isCalledAs("hours-from-time"))
                result = new IntegerValue(time.getPart(DateValue.HOUR), Type.INTEGER);
    		else if (isCalledAs("minutes-from-time"))
                result = new IntegerValue(time.getPart(DateValue.MINUTE), Type.INTEGER);
    		else if (isCalledAs("seconds-from-time")) {
    			long millis = time.getPart(DateValue.SECOND) * 1000 + time.getPart(DateValue.MILLISECOND);
                result = new DecimalValue(millis / 1000);
    		} else if (isCalledAs("timezone-from-time"))
                result = time.getTimezone();
    		else throw new Error("can't handle function " + mySignature.getName().getLocalName());
        }
        
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result); 
        
        return result;         

	}

}
