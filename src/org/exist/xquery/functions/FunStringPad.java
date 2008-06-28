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
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class FunStringPad extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("string-pad", Function.BUILTIN_FUNCTION_NS),
			"Returns an xs:string consisting of a number copies of the first argument " +
			"concatenated together without any separators. The number of copies is specified " +
			"by the second argument.",
			new SequenceType[] {
				 new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE),
				 new SequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE));
			
	/**
	 * @param context
	 */
	public FunStringPad(XQueryContext context) {
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
        
        Sequence result;
		Sequence seq = getArgument(0).eval(contextSequence, contextItem);
		if(seq.isEmpty())
            result = Sequence.EMPTY_SEQUENCE;
        else {        
    		String str = seq.getStringValue();
    		int count = ((IntegerValue)getArgument(1).eval(contextSequence, contextItem).convertTo(Type.INTEGER)).getInt();
    		if(count < 0)
    			throw new XPathException(getASTNode(), "Invalid string-pad count");
            if(count == 0)
                result = StringValue.EMPTY_STRING;
            else {
        		StringBuffer buf = new StringBuffer(str.length() * count);
        		for(int i = 0; i < count; i++)
        			buf.append(str);
                result = new StringValue(buf.toString());
            }
        }

        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result); 
        
        return result;                        
            
	}

}
