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
import org.exist.xquery.*;
import org.exist.xquery.value.*;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class FunCurrentDateTime extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("current-dateTime", Function.BUILTIN_FUNCTION_NS),
			"Returns the xs:dateTime (with timezone) that is current at some time "
				+ "during the evaluation of a query or transformation in which fn:current-dateTime() "
				+ "is executed.",
			null,
			new SequenceType(Type.DATE_TIME, Cardinality.EXACTLY_ONE));

	public FunCurrentDateTime(XQueryContext context) {
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
        
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result);        
        
        return result;        
	}
    
    public int getDependencies() {
        return Dependency.CONTEXT_SET;
    }

}
