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
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */

//fn:string-pad() is not part of the W3C spec
@Deprecated
public class FunStringPad extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("string-pad", Function.BUILTIN_FUNCTION_NS),
			"Returns an xs:string consisting of a number copies of $arg " +
			"concatenated together without any separators. The number of copies is specified " +
			"by $count.",
			new SequenceType[] {
				 new FunctionParameterSequenceType("arg", Type.STRING, Cardinality.ZERO_OR_ONE, "The string to be duplicated"),
				 new FunctionParameterSequenceType("count", Type.INTEGER, Cardinality.EXACTLY_ONE, "The number of copies of $arg to be returned")
			},
			new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE, "the duplicated string"),
                        "fn:string-pad(...) is not part of the W3C specification.");
			
	/**
	 * @param context
	 */
	public FunStringPad(XQueryContext context) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.persistent.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
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
        
        Sequence result;
		final Sequence seq = getArgument(0).eval(contextSequence, contextItem);
		if(seq.isEmpty())
            {result = Sequence.EMPTY_SEQUENCE;}
        else {        
    		final String str = seq.getStringValue();
    		final int count = ((IntegerValue)getArgument(1).eval(contextSequence, contextItem).convertTo(Type.INTEGER)).getInt();
    		if(count < 0)
    			{throw new XPathException(this, "Invalid string-pad count");}
            if(count == 0)
                {result = StringValue.EMPTY_STRING;}
            else {
        		final StringBuilder buf = new StringBuilder(str.length() * count);
        		for(int i = 0; i < count; i++)
        			buf.append(str);
                result = new StringValue(buf.toString());
            }
        }

        if (context.getProfiler().isEnabled()) 
            {context.getProfiler().end(this, "", result);} 
        
        return result;                        
            
	}

}
