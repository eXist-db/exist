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
import org.exist.xquery.Constants;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class FunTranslate extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("translate", Function.BUILTIN_FUNCTION_NS),
			"Returns the value of $a modified so that every character in the value of $a " +
			"that occurs at some position N in the value of $b has been replaced by " +
			"the character that occurs at position N in the value of $c.",
			new SequenceType[] { 
				new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE),
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE));
				
	/**
	 * @param context
	 */
	public FunTranslate(XQueryContext context) {
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
        Sequence seq = getArgument(0).eval(contextSequence);
		if(seq.isEmpty())
            result = Sequence.EMPTY_SEQUENCE;
        else {
    		String arg = seq.getStringValue();
    		String mapStr = getArgument(1).eval(contextSequence).getStringValue();
    		String transStr = getArgument(2).eval(contextSequence).getStringValue();
    		int p;
    		char ch;
    		StringBuffer buf = new StringBuffer(arg.length());
    		for(int i = 0; i < arg.length(); i++) {
    			ch = arg.charAt(i);
    			p = mapStr.indexOf(ch);
    			if(p == Constants.STRING_NOT_FOUND)
                    buf.append(ch);
                else {
    				if (p < transStr.length())
    					buf.append(transStr.charAt(p));
    			}    				
    		}
            result = new StringValue(buf.toString());
        }

        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result);        
        
        return result; 
	}
}
