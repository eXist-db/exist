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

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

/**
 * xpath-library function: position()
 *
 */
public class FunPosition extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("position", Function.BUILTIN_FUNCTION_NS),
			"Returns the context position from the dynamic context. " +
			"If the context item is undefined, raises an error.",
			null,
			new FunctionReturnSequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE, "the context position"));
	
    public FunPosition(XQueryContext context) {
        super(context, signature);
    }

	/* (non-Javadoc)
	 * @see org.exist.xquery.functions.Function#getDependencies()
	 */
	public int getDependencies() {
		return Dependency.CONTEXT_SET + Dependency.CONTEXT_ITEM +
		Dependency.CONTEXT_POSITION;
	}

    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);}
            if (contextItem != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());}
        }
        
        Sequence inSequence = context.getContextSequence();
        if (inSequence == null)
        	{inSequence = contextSequence;}

		if (inSequence == null)
			{throw new XPathException(this, ErrorCodes.XPDY0002, "Undefined context item");}
		
		Sequence result;
		if (inSequence.isEmpty())
            {result = Sequence.EMPTY_SEQUENCE;}
        else
            {result = new IntegerValue(context.getContextPosition() + 1);}
        
        if (context.getProfiler().isEnabled()) 
            {context.getProfiler().end(this, "", result);} 
        
        return result;         
    }
}
