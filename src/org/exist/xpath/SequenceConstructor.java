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
package org.exist.xpath;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.exist.dom.DocumentSet;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.Type;
import org.exist.xpath.value.ValueSequence;

/**
 * @author wolf
 */
public class SequenceConstructor extends AbstractExpression {

	List expressions = new ArrayList(5);
	
	/**
	 * @param context
	 */
	public SequenceConstructor(StaticContext context) {
		super(context);
	}

	public void addExpression(Expression expr) {
		expressions.add(expr);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#eval(org.exist.dom.DocumentSet, org.exist.xpath.value.Sequence, org.exist.xpath.value.Item)
	 */
	public Sequence eval(
		DocumentSet docs,
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		ValueSequence result = new ValueSequence();
		Sequence temp;
		for(Iterator i = expressions.iterator(); i.hasNext(); ) {
			temp = ((Expression)i.next()).eval(docs, contextSequence, contextItem);
			result.addAll(temp);
		}
		return result;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#preselect(org.exist.dom.DocumentSet)
	 */
	public DocumentSet preselect(DocumentSet in_docs) throws XPathException {
		for(Iterator i = expressions.iterator(); i.hasNext(); ) {
			in_docs = ((Expression)i.next()).preselect(in_docs);
		}
		return in_docs;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#pprint()
	 */
	public String pprint() {
		StringBuffer buf = new StringBuffer();
		for(Iterator i = expressions.iterator(); i.hasNext(); ) {
			if(buf.length() > 0)
				buf.append(", ");
			buf.append(((Expression)i.next()).pprint());
		}
		return buf.toString();
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#returnsType()
	 */
	public int returnsType() {
		return Type.ITEM;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.AbstractExpression#getCardinality()
	 */
	public int getCardinality() {
		return Cardinality.ZERO_OR_MORE;
	}
}
