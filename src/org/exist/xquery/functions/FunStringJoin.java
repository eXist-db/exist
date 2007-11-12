/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  wolfgang@exist-db.org
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
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 * 
 * @author wolf
 *
 */
public class FunStringJoin extends BasicFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
				new QName("string-join", Function.BUILTIN_FUNCTION_NS),
				"Returns a xs:string created by concatenating the members of the " +
				"$a sequence using $b as a separator. If the value of $b is the zero-length " +
				"string, then the members of $a are concatenated without a separator.",
				new SequenceType[] {
						new SequenceType(Type.STRING, Cardinality.ZERO_OR_MORE),
						new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
				},
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE));
	
	/**
	 *
	 */

	public FunStringJoin(XQueryContext context) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
         }      
        
		String sep = args[1].getStringValue();
		if(sep.length() == 0)
			sep = null;
		StringBuffer out = new StringBuffer();
		Item next;
		boolean gotOne = false;
		for(SequenceIterator i = args[0].iterate(); i.hasNext(); ) {
			next = i.nextItem();
			if(gotOne && sep != null)
				out.append(sep);
			out.append(next.getStringValue());
			gotOne = true;
		}
		Sequence result = new StringValue(out.toString());
        
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result); 
        
        return result;         
	}

}
