/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
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
package org.exist.xpath.functions;

import java.util.Comparator;
import java.util.Iterator;
import java.util.TreeSet;

import org.exist.dom.DocumentSet;
import org.exist.dom.QName;
import org.exist.xpath.Cardinality;
import org.exist.xpath.Dependency;
import org.exist.xpath.StaticContext;
import org.exist.xpath.XPathException;
import org.exist.xpath.value.AtomicValue;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.SequenceIterator;
import org.exist.xpath.value.SequenceType;
import org.exist.xpath.value.Type;
import org.exist.xpath.value.ValueSequence;

/**
 * @author wolf
 */
public class FunDistinctValues extends Function {

	private final static FunctionSignature signature =
		new FunctionSignature(
			new QName("distinct-values", BUILTIN_FUNCTION_NS),
			new SequenceType[] { new SequenceType(Type.ATOMIC, Cardinality.ZERO_OR_MORE) },
			new SequenceType(Type.ATOMIC, Cardinality.ONE)
		);
		
	public FunDistinctValues(StaticContext context) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.PathExpr#returnsType()
	 */
	public int returnsType() {
		return Type.ATOMIC;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.functions.Function#getDependencies()
	 */
	public int getDependencies() {
		int deps = Dependency.CONTEXT_SET;
		if(getArgumentCount() == 1)
			deps |= getArgument(0).getDependencies();
		return deps; 
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#eval(org.exist.xpath.StaticContext, org.exist.dom.DocumentSet, org.exist.xpath.value.Sequence, org.exist.xpath.value.Item)
	 */
	public Sequence eval(
		DocumentSet docs,
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		if(contextItem != null)
			contextSequence = contextItem.toSequence();
		long start = System.currentTimeMillis();
		Sequence values = getArgument(0).eval(docs, contextSequence);
		TreeSet set = new TreeSet(new Comparator() {
			public int compare(Object o1, Object o2) {
				try {
					return ((AtomicValue)o1).compareTo((AtomicValue)o2);
				} catch (XPathException e) {
					throw new IllegalArgumentException("cannot compare values");
				}
			}
		});
		AtomicValue value;
		for(SequenceIterator i = values.iterate(); i.hasNext(); ) {
			value = (AtomicValue)i.nextItem();
			if(!set.contains(value))
				set.add(value);
		}
		ValueSequence result = new ValueSequence();
		for(Iterator i = set.iterator(); i.hasNext(); ) {
			value = (AtomicValue)i.next();
			//LOG.debug("value: " + value.getStringValue());
			result.add(value);
		}
		LOG.debug("distinct-values found " + result.getLength() + 
			" in " + (System.currentTimeMillis() - start));
		return result;
	}

}
