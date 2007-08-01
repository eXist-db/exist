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
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NumericValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class FunAbs extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("abs", Function.BUILTIN_FUNCTION_NS),
			"Returns the absolute value of the argument. If the argument is negative " +
			"returns -arg otherwise returns arg.",
			new SequenceType[] { new SequenceType(Type.NUMBER, Cardinality.ZERO_OR_ONE) },
			new SequenceType(Type.NUMBER, Cardinality.EXACTLY_ONE)
		);
				
	/**
	 * @param context
	 */
	public FunAbs(XQueryContext context) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
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
        
        if(contextItem != null)
			contextSequence = contextItem.toSequence();
		
        Sequence result;
        Sequence seq = getArgument(0).eval(contextSequence, contextItem);
		if(seq.isEmpty())
			result = Sequence.EMPTY_SEQUENCE;
        else {
    		NumericValue value = (NumericValue)seq.itemAt(0).convertTo(Type.NUMBER);
    		result = value.abs();
        }
        
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result); 
        
        return result;
	}

}
