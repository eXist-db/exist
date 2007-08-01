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

import java.util.Date;

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.DateTimeValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class FunCurrentDateTime extends Function {

	public final static FunctionSignature fnCurrentDateTime =
		new FunctionSignature(
			new QName("current-dateTime", Function.BUILTIN_FUNCTION_NS),
			"Returns the xs:dateTime (with timezone) that is current at some time " +
			"during the evaluation of a query or transformation in which fn:current-dateTime() " +
			"is executed.",
			null,
			new SequenceType(Type.DATE_TIME, Cardinality.EXACTLY_ONE));

	public final static FunctionSignature fnCurrentTime =
		new FunctionSignature(
			new QName("current-time", Function.BUILTIN_FUNCTION_NS),
			"Returns the xs:time (with timezone) that is current at " +
			"some time during the evaluation of a query or transformation " +
			"in which fn:current-time() is executed.",
			null,
			new SequenceType(Type.TIME, Cardinality.EXACTLY_ONE));

	public final static FunctionSignature fnCurrentDate =
		new FunctionSignature(
			new QName("current-date", Function.BUILTIN_FUNCTION_NS),
			"Returns the xs:date (with timezone) that is current at some " +
			"time during the evaluation of a query or transformation in which " +
			"fn:current-date() is executed.",
			null,
			new SequenceType(Type.DATE, Cardinality.EXACTLY_ONE));

	
	public FunCurrentDateTime(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
		if (context.getProfiler().isEnabled()) {
			context.getProfiler().start(this);       
			context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
			if (contextSequence != null)
				context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
			if (contextItem != null)
				context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
		}        

		Sequence result = new DateTimeValue(new Date(context.getWatchDog().getStartTime()));
		if (isCalledAs("current-dateTime")) {
			// do nothing, result already in right form
		} else if (isCalledAs("current-date")) {
			result = result.convertTo(Type.DATE);
		} else if (isCalledAs("current-time")) {
			result = result.convertTo(Type.TIME);
		} else {
			throw new Error("can't handle function " + mySignature.getName().getLocalName());
		}

		if (context.getProfiler().isEnabled()) context.getProfiler().end(this, "", result);        

		return result;
	}
    
    public int getDependencies() {
        return Dependency.CONTEXT_SET;
    }

}
