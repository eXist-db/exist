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

import org.apache.log4j.Logger;

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
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * Implements function fn:exactly-one().
 * 
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class FunExactlyOne extends Function {
	protected static final Logger logger = Logger.getLogger(FunExactlyOne.class);
	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("exactly-one", Function.BUILTIN_FUNCTION_NS),
			"Returns the argument sequence, $items, if it contains exactly one item. Otherwise, " +
			"raises an error.",
			new SequenceType[] {
                new FunctionParameterSequenceType("items", Type.ITEM, Cardinality.ZERO_OR_MORE, "The item sequence")
			},
			new FunctionReturnSequenceType(Type.ITEM, Cardinality.EXACTLY_ONE, "the sole item in $items if it contains exactly one item. Otherwise, an error is raised."));
				
	/**
	 * @param context
	 */
	public FunExactlyOne(XQueryContext context) {
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
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);}
            if (contextItem != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());}
        }
        
		final Sequence result = getArgument(0).eval(contextSequence, contextItem);
		if (!result.hasOne()) {
            logger.error("fn:exactly-one called with a sequence containing " + result.getItemCount() + " items");
			throw new XPathException(this, ErrorCodes.FOCA0005, "fn:exactly-one called with a sequence containing " + result.getItemCount() + " items", result);
        }

        if (context.getProfiler().isEnabled()) 
            {context.getProfiler().end(this, "", result);} 
        
        return result;      
	}

}
