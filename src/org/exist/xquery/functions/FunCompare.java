/* eXist Open Source Native XML Database
 * Copyright (C) 2000-03,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Library General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 * $Id$
 */

package org.exist.xquery.functions;

import java.text.Collator;

import org.exist.dom.QName;
import org.exist.util.Collations;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Constants;
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
import org.exist.xquery.value.Type;

public class FunCompare extends CollatingFunction {

	public final static FunctionSignature signatures[] = {
		new FunctionSignature (
			new QName("compare", Function.BUILTIN_FUNCTION_NS),
			new SequenceType[] {
				 new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE),
				 new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE)
			},
			new SequenceType(Type.INTEGER, Cardinality.ZERO_OR_ONE)),
		new FunctionSignature (
			new QName("compare", Function.BUILTIN_FUNCTION_NS),
			new SequenceType[] {
				 new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE),
				 new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE),
				 new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.INTEGER, Cardinality.ZERO_OR_ONE))
	};
					
	public FunCompare(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}
	
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            if (contextItem != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
        }
        
		Sequence seq1 = getArgument(0).eval(contextSequence, contextItem);
		Sequence seq2 =	getArgument(1).eval(contextSequence, contextItem);
		
		Sequence result;		
		if (seq1.isEmpty() || seq2.isEmpty())
			result = Sequence.EMPTY_SEQUENCE;
		else {
			Collator collator = getCollator(contextSequence, contextItem, 3);		
			int comparison = Collations.compare(collator, seq1.getStringValue(), seq2.getStringValue());
			if (comparison == Constants.EQUAL) 
				result = new IntegerValue(Constants.EQUAL);	        
			else if (comparison < 0)
				result = new IntegerValue(Constants.INFERIOR);
			else 
				result = new IntegerValue(Constants.SUPERIOR);
		}
        
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result);        
        
        return result;        
	}
}
