/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 */
package org.exist.xquery.functions;

import org.exist.dom.QName;
import org.exist.util.Collations;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Constants;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

public class FunCodepointEqual extends BasicFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
				new QName("codepoint-equal", Function.BUILTIN_FUNCTION_NS, ModuleImpl.PREFIX),
				"Returns true or false depending on whether the value of $a is equal to the value of $b, " +
				"according to the Unicode code point collation",
				new SequenceType[] {
						new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE),
						new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE),
				},
				new SequenceType(Type.BOOLEAN, Cardinality.ZERO_OR_ONE));
	
	public FunCodepointEqual(XQueryContext context) {
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
			result = Sequence.EMPTY_SEQUENCE;
		else if (args[1].isEmpty())
			result =  Sequence.EMPTY_SEQUENCE;
        else {
    		result = new BooleanValue(Collations.compare(
    				//TODO : how ugly ! WE should be able to use Collations.CODEPOINT here ! -pb
    					context.getDefaultCollator(),
    					getArgument(0).eval(contextSequence).getStringValue(), 
    					getArgument(1).eval(contextSequence).getStringValue()
    				) == Constants.EQUAL);
        }
        
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result);        
        
        return result;
	}
}
