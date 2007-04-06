/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2006 The eXist team
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
package org.exist.xquery.functions;

import java.text.Collator;
import java.util.Comparator;
import java.util.TreeSet;

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Constants;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NumericValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 * Implements the fn:distinct-values standard library function.
 * 
 * @author wolf
 */
public class FunDistinctValues extends CollatingFunction {

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName("distinct-values", Function.BUILTIN_FUNCTION_NS, ModuleImpl.PREFIX),
			"Returns a sequence where duplicate values of $a, based on value equality, " + 
			"have been deleted.",
			new SequenceType[] { new SequenceType(Type.ATOMIC, Cardinality.ZERO_OR_MORE)},
			new SequenceType(Type.ATOMIC, Cardinality.ZERO_OR_MORE)
		),
		new FunctionSignature(
				new QName("distinct-values", Function.BUILTIN_FUNCTION_NS, ModuleImpl.PREFIX),
				"Returns a sequence where duplicate values of $a, based on value equality specified by collation $b, " + 
				"have been deleted.",
				new SequenceType[] { 
					new SequenceType(Type.ATOMIC, Cardinality.ZERO_OR_MORE),
					new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
				},
				new SequenceType(Type.ATOMIC, Cardinality.ZERO_OR_MORE)
		)		
	};

	public FunDistinctValues(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.PathExpr#returnsType()
	 */
	public int returnsType() {
		return Type.ATOMIC;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.functions.Function#getDependencies()
	 */
	public int getDependencies() {
		int deps = Dependency.CONTEXT_SET;
		if (getArgumentCount() == 1)
			deps |= getArgument(0).getDependencies();
		return deps;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.xquery.StaticContext, org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
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
        
        if (contextItem != null)
			contextSequence = contextItem.toSequence();

		Sequence values = getArgument(0).eval(contextSequence);
		Collator collator = getCollator(contextSequence, contextItem, 2);		
		TreeSet set = new TreeSet(new ValueComparator(collator));
		ValueSequence result = new ValueSequence();
		Item item;
		AtomicValue value;
		boolean hasAlreadyNaN = false;
		for (SequenceIterator i = values.iterate(); i.hasNext();) {
			item = i.nextItem();
			value = item.atomize();
			if (!set.contains(value)) {
				if (Type.subTypeOf(value.getType(), Type.NUMBER)) {
					if (((NumericValue)value).isNaN()) {
						//although NaN does not equal itself, if $arg contains multiple NaN values a single NaN is returned.
						if (!hasAlreadyNaN) {
							set.add(value);
							result.add(value);	
							hasAlreadyNaN = true;
						}
					} else {
						set.add(value);
						result.add(value);
					}
				} else {
					set.add(value);
					result.add(value);
				}
			}			
		}

        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result); 
        
        return result;
	}

	private final static class ValueComparator implements Comparator {
		
		Collator collator;
		
		public ValueComparator(Collator collator) {
			this.collator = collator;
		}
		
		/* (non-Javadoc)
		 * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
		 */
		public int compare(Object o1, Object o2) {
			try {
				return ((AtomicValue) o1).compareTo(collator, (AtomicValue) o2);
			} catch (XPathException e) {
				//throw new IllegalArgumentException("cannot compare values");
                //Values that cannot be compared, i.e. the eq operator is not defined for their types, are considered to be distinct
                return Constants.INFERIOR;
			}
		}
	}
}
