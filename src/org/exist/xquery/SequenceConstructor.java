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
package org.exist.xquery;

import java.util.Iterator;

import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 * An XQuery sequence constructor ",". For example, ($a, $b) constructs a new
 * sequence containing items $a and $b.
 * 
 * @author wolf
 */
public class SequenceConstructor extends PathExpr {
	
	/**
	 * @param context
	 */
	public SequenceConstructor(XQueryContext context) {
		super(context);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		ValueSequence result = new ValueSequence();
		Sequence temp;
		for(Iterator i = steps.iterator(); i.hasNext(); ) {
			temp = ((Expression)i.next()).eval(contextSequence, contextItem);
			result.addAll(temp);
		}
		return result;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#pprint()
	 */
	public String pprint() {
		StringBuffer buf = new StringBuffer();
		for(Iterator i = steps.iterator(); i.hasNext(); ) {
			if(buf.length() > 0)
				buf.append(", ");
			buf.append(((Expression)i.next()).pprint());
		}
		return buf.toString();
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#returnsType()
	 */
	public int returnsType() {
		return Type.ITEM;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.AbstractExpression#getCardinality()
	 */
	public int getCardinality() {
		return Cardinality.ZERO_OR_MORE;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.AbstractExpression#resetState()
	 */
	public void resetState() {
		for (Iterator i = steps.iterator(); i.hasNext();) {
			((Expression) i.next()).resetState();
		}
	}
}
