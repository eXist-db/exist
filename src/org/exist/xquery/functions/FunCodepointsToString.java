/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Team
 *
 *  http://exist-db.org
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
import org.exist.util.XMLChar;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.NumericValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

public class FunCodepointsToString extends BasicFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
				new QName("codepoints-to-string", Function.BUILTIN_FUNCTION_NS, ModuleImpl.PREFIX),
				"Creates an xs:string from a sequence of code points. Returns the zero-length string if " +
				"$a is the empty sequence. If any of the code points in $a is not a legal XML character, " +
				"an error is raised",
				new SequenceType[] {
						new SequenceType(Type.INTEGER, Cardinality.ZERO_OR_MORE),
				},
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE));
	
	public FunCodepointsToString(XQueryContext context) {
		super(context, signature);
	}

	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
        }
        
        Sequence result;
		if (args[0].isEmpty())
			result = StringValue.EMPTY_STRING;
        else {
    		StringBuffer buf = new StringBuffer();    		
    		for (SequenceIterator i = args[0].iterate(); i.hasNext(); ) {
                long next = ((NumericValue)i.nextItem()).getLong();
    			if (next < 0 || next > Integer.MAX_VALUE || !XMLChar.isValid((int)next)) {
    				throw new XPathException(getASTNode(), "err:FOCH0001: Codepoint " + next + 
                            " is not a valid character.");
    			}
    			if (next < 65536) {
                    buf.append((char)next);
                }
                else {  // output a surrogate pair
                	buf.append(XMLChar.highSurrogate((int)next));
                    buf.append(XMLChar.lowSurrogate((int)next));
                }
    		}
    		result = new StringValue(buf.toString());
        }
        
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result);        
        
        return result;
	}
}
