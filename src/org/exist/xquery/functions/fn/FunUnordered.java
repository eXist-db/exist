/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2004-2009 The eXist Project
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
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;


/**
 * The XQuery fn:unordered function. Currently, this function has no effect in eXist,
 * but it might be used for future optimizations.
 * 
 * @author wolf
 */
public class FunUnordered extends Function {

    public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("unordered", Function.BUILTIN_FUNCTION_NS),
			"Takes a sequence $arg as input and returns an arbitrary implementation dependent permutation " +
			"of it. Currently, this has no effect in eXist, but it might be used for future optimizations.",
			new SequenceType[] {
				 new FunctionParameterSequenceType("arg", Type.ITEM, Cardinality.ZERO_OR_MORE, "The input sequence")
			},
			new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "the input sequence in an arbitrary implementation dependent permutation"));
    
    public FunUnordered(XQueryContext context) {
        super(context, signature);
    }
    
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        final AnalyzeContextInfo newContextInfo = new AnalyzeContextInfo(contextInfo);
        newContextInfo.setParent(this);
        newContextInfo.addFlag(UNORDERED);
        super.analyze(newContextInfo);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#eval(org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
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

        if (context.getProfiler().isEnabled()) 
            {context.getProfiler().end(this, "", result);}        
        
        return result;          
    }

    
    /* (non-Javadoc)
     * @see org.exist.xquery.Function#getCardinality()
     */
    public int getCardinality() {
        return getArgument(0).getCardinality();
    }
    
    
    /* (non-Javadoc)
     * @see org.exist.xquery.Function#returnsType()
     */
    public int returnsType() {
        return getArgument(0).returnsType();
    }
}
