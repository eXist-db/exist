/* eXist Native XML Database
 * Copyright (C) 2000-03,  Wolfgang M. Meier (wolfgang@exist-db.org)
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
package org.exist.xpath;

import org.exist.dom.DocumentSet;
import org.exist.memtree.MemTreeBuilder;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.Type;

/**
 * Abstract base class for all node constructors.
 * 
 * @author wolf
 */
public abstract class NodeConstructor extends AbstractExpression {

	protected MemTreeBuilder builder = null;
	
	public NodeConstructor(StaticContext context) {
		super(context);
	}
	
	public void setDocumentBuilder(MemTreeBuilder builder) {
		this.builder = builder;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#eval(org.exist.xpath.StaticContext, org.exist.dom.DocumentSet, org.exist.xpath.value.Sequence, org.exist.xpath.value.Item)
	 */
	public abstract Sequence eval(
		Sequence contextSequence,
		Item contextItem)
		throws XPathException;

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
	public abstract String pprint();

	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#returnsType()
	 */
	public int returnsType() {
		return Type.NODE;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.AbstractExpression#resetState()
	 */
	public void resetState() {
	}
}
