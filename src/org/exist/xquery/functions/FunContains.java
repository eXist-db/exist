/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
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
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

public class FunContains extends CollatingFunction {

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName("contains", Function.BUILTIN_FUNCTION_NS),
			"Returns an xs:boolean indicating whether or not the value of $arg1 " +
			"contains (at the beginning, at the end, or anywhere within) at least " +
			"one sequence of collation units that provides a minimal match to the " +
			"collation units in the value of $arg2, according to the default collation.",
			new SequenceType[] {
				 new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE),
				 new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE)
			},
			new SequenceType(Type.BOOLEAN, Cardinality.ONE)),
		new FunctionSignature(
			new QName("contains", Function.BUILTIN_FUNCTION_NS),
			"Returns an xs:boolean indicating whether or not the value of $arg1 " +
			"contains (at the beginning, at the end, or anywhere within) at least " +
			"one sequence of collation units that provides a minimal match to the " +
			"collation units in the value of $arg2, according to the collation that is " +
			"specified in $arg3.",
			new SequenceType[] {
					new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE),
					new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE),
					new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
					
			},
			new SequenceType(Type.BOOLEAN, Cardinality.ONE))
	};

	public FunContains(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	public int returnsType() {
		return Type.BOOLEAN;
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
        
		Sequence result;
		//s2 takes precedence over s1
		String s2 = getArgument(1).eval(contextSequence, contextItem).getStringValue();
		if ("".equals(s2))
			result = BooleanValue.TRUE;
		else {
			String s1 = getArgument(0).eval(contextSequence, contextItem).getStringValue();
			if ("".equals(s1))
				result = BooleanValue.FALSE;
			else {
				Collator collator = getCollator(contextSequence, contextItem, 3);
				if (Collations.indexOf(collator, s1, s2) != Constants.STRING_NOT_FOUND)
					return BooleanValue.TRUE;
				else
					return BooleanValue.FALSE;
			}
		}

		if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result);        
        
        return result;        
	}
}
