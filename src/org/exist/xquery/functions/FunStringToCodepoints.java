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
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

public class FunStringToCodepoints extends BasicFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
				new QName("string-to-codepoints", Function.BUILTIN_FUNCTION_NS),
				"Returns the sequence of code points that constitute an xs:string. If $a is a zero-length " +
				"string or the empty sequence, the empty sequence is returned.",
				new SequenceType[] {
						new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE),
				},
				new SequenceType(Type.INTEGER, Cardinality.ZERO_OR_MORE));
	
	public FunStringToCodepoints(XQueryContext context) {
		super(context, signature);
	}

	public Sequence eval(Sequence[] args, Sequence contextSequence)	throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
       }    
       
        Sequence result;
        if (args[0].isEmpty())
			result =  Sequence.EMPTY_SEQUENCE;
        else {
    		String s = args[0].getStringValue();
    		ValueSequence codepoints = new ValueSequence();
    		int ch;
    		IntegerValue next;
    		for (int i = 0; i < s.length(); i++) {
    			ch = s.charAt(i);
    			if (ch >= 55296 && ch <= 56319) {
                    // we'll trust the data to be sound
                    next = new IntegerValue(((ch - 55296) * 1024) + ((int) s.charAt(i++) - 56320) + 65536);
                } else {
                    next = new IntegerValue(ch);
                }
    			codepoints.add(next);
    		}
    		result = codepoints;
        }
        
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result); 
        
        return result;             
        
	}

}
