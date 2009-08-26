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
package org.exist.xquery.functions;

import org.apache.log4j.Logger;

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * @author wolf
 */
public class DeprecatedFunItemAt extends Function {
	protected static final Logger logger = Logger.getLogger(DeprecatedFunItemAt.class);
	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("item-at", Function.BUILTIN_FUNCTION_NS),
			"Returns the item in $source that is located at the position " +
			"specified by $index.",
			new SequenceType[] {
				 new FunctionParameterSequenceType("source", Type.ITEM, Cardinality.ZERO_OR_MORE, "The source sequence"),
				 new FunctionParameterSequenceType("index", Type.INTEGER, Cardinality.EXACTLY_ONE, "The index of the item in the source sequence to return")
			},
			new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_ONE, "the item"),
			"This function is eXist-specific and deprecated. It should not be in the standard functions namespace. " +
            "Use e.g. $x[1] instead. ");
	
	public DeprecatedFunItemAt(XQueryContext context) {
		super(context, signature);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Function#getDependencies()
	 */
	public int getDependencies() {
		return Dependency.CONTEXT_SET;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        logger.error("Use of deprecated, since 2008-04-02, function fn:item-at(). " +
                     "It will be removed really soon. Please " +
                     "use $x[1] instead.");
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            if (contextItem != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
        }
        
		Sequence seq = getArgument(0).eval(contextSequence, contextItem);
		IntegerValue posArg = (IntegerValue)
			getArgument(1).eval(contextSequence, contextItem).convertTo(Type.INTEGER);
		long pos = posArg.getValue();
		if (pos < 1 || pos > seq.getItemCount())
			throw new XPathException(this, "Invalid position: " + pos);
		Item item = seq.itemAt((int)pos - 1);
        
        Sequence result;
		if(item == null) {
            //TODO : throw an exception ? -pb
			logger.debug("Item is null: " + seq.getClass().getName() + "; len = " + seq.getItemCount());
			result = Sequence.EMPTY_SEQUENCE;
		}
        else result = item.toSequence();
        
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result); 
        
        return result;          
	}

}
