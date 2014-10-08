/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2009 The eXist team
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
 * $Id$
 */

package org.exist.xquery.functions.fn;

import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 * Implements the fn:reverse function.
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class FunReverse extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("reverse", Function.BUILTIN_FUNCTION_NS),
			"Reverses the order of items in a sequence.  If the argument is an empty" +
			"sequence, the empty sequence is returned.",
			new SequenceType[] {new FunctionParameterSequenceType("arg", Type.ITEM, Cardinality.ZERO_OR_MORE, "The sequence to reverse")},
			new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "the reverse order sequence"));
			
	public FunReverse(XQueryContext context) {
		super(context, signature);
	}

    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        // statically check the argument list
        checkArguments();
        // call analyze for each argument
    	inPredicate = (contextInfo.getFlags() & IN_PREDICATE) > 0;
    	contextId = contextInfo.getContextId();
    	contextInfo.setParent(this);

        final AnalyzeContextInfo argContextInfo = new AnalyzeContextInfo(contextInfo);
        getArgument(0).analyze(argContextInfo);
        contextInfo.setStaticReturnType(argContextInfo.getStaticReturnType());
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
        
        Sequence result;
        final Sequence seq = getArguments(contextSequence, contextItem)[0];
		if (seq.isEmpty()) 
            {result = Sequence.EMPTY_SEQUENCE;}
        else {
                final Sequence tmp = new ValueSequence();
                Item item;
                for(final SequenceIterator i = seq.iterate(); i.hasNext(); ) {
                    item = i.nextItem();
                    tmp.add(item);
                }
                result = new ValueSequence();
                for (int i = seq.getItemCount() - 1; i >= 0; i--) {
                    result.add(tmp.itemAt(i));
                }
        }

        if (context.getProfiler().isEnabled()) 
            {context.getProfiler().end(this, "", result);} 
        
        return result;
	}

}
