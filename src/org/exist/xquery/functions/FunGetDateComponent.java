/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
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
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.DateValue;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class FunGetDateComponent extends Function {

	public final static FunctionSignature fnGetDayFromDate =
		new FunctionSignature(
			new QName("day-from-date", Function.BUILTIN_FUNCTION_NS),
			"Returns an xs:integer between 1 and 31, both inclusive, representing " +
			"the day component in the localized value of $a.",
			new SequenceType[] { new SequenceType(Type.DATE, Cardinality.ZERO_OR_ONE)},
			new SequenceType(Type.INTEGER, Cardinality.ZERO_OR_ONE));
	
	public final static FunctionSignature fnGetMonthFromDate =
		new FunctionSignature(
			new QName("month-from-date", Function.BUILTIN_FUNCTION_NS),
			"Returns an xs:integer between 1 and 12, both inclusive, representing the month " +
			"component in the localized value of $a.",
			new SequenceType[] {  new SequenceType(Type.DATE, Cardinality.ZERO_OR_ONE) },
			new SequenceType(Type.INTEGER, Cardinality.ZERO_OR_ONE));
	
	public final static FunctionSignature fnGetYearFromDate =
		new FunctionSignature(
			new QName("year-from-date", Function.BUILTIN_FUNCTION_NS),
			"Returns an xs:integer representing the year in the localized value of $a. The value may be negative.",
			new SequenceType[] {
				 new SequenceType(Type.DATE, Cardinality.ZERO_OR_ONE)
			},
			new SequenceType(Type.INTEGER, Cardinality.ZERO_OR_ONE));
	
	public final static FunctionSignature fnGetTimezoneFromDate =
		new FunctionSignature(
			new QName("timezone-from-date", Function.BUILTIN_FUNCTION_NS),
			"Returns an xs:integer representing the year in the localized value of $a. The value may be negative.",
			new SequenceType[] {
				 new SequenceType(Type.DATE, Cardinality.ZERO_OR_ONE)
			},
			new SequenceType(Type.DAY_TIME_DURATION, Cardinality.ZERO_OR_ONE));

	/**
	 * @param context
	 * @param signature
	 */
	public FunGetDateComponent(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            if (contextItem != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
        }
        
        Sequence result;
		Sequence arg = getArgument(0).eval(contextSequence, contextItem);
		if (arg.isEmpty())
            result = Sequence.EMPTY_SEQUENCE;
        else {
    		DateValue date = (DateValue) arg.itemAt(0);
    		if (isCalledAs("day-from-date"))
                result = new IntegerValue(date.getPart(DateValue.DAY), Type.INTEGER);
    		else if (isCalledAs("month-from-date"))
                result = new IntegerValue(date.getPart(DateValue.MONTH), Type.INTEGER);
    		else if (isCalledAs("timezone-from-date"))
                result = date.getTimezone();
    		else if (isCalledAs("year-from-date"))
                result = new IntegerValue(date.getPart(DateValue.YEAR), Type.INTEGER);
    		else throw new Error("Can't handle function " + mySignature.getName().getLocalName());
        }
        
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result); 
        
        return result;            
	}
}
