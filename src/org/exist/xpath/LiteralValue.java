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

import org.exist.dom.DocumentSet;
import org.exist.xpath.value.AtomicValue;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;

/**
 * @author wolf
 */
public class LiteralValue extends AbstractExpression {

	private AtomicValue value;
	
	public LiteralValue(StaticContext context, AtomicValue value) {
		super(context);
		this.value = value;
	}
	
	public AtomicValue getValue() {
		return value;
	}
	
	public void setValue(AtomicValue value) {
		this.value = value;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#eval(org.exist.xpath.StaticContext, org.exist.dom.DocumentSet, org.exist.xpath.value.Sequence, org.exist.xpath.value.Item)
	 */
	public Sequence eval(
		DocumentSet docs,
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		return value;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#preselect(org.exist.dom.DocumentSet, org.exist.xpath.StaticContext)
	 */
	public DocumentSet preselect(DocumentSet in_docs)
		throws XPathException {
		return in_docs;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#pprint()
	 */
	public String pprint() {
		return value.pprint();
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#returnsType()
	 */
	public int returnsType() {
		return value.getType();
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.AbstractExpression#getDependencies()
	 */
	public int getDependencies() {
		return Dependency.NO_DEPENDENCY;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.AbstractExpression#getCardinality()
	 */
	public int getCardinality() {
		return Cardinality.EXACTLY_ONE;
	}
}
