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

import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.xpath.Expression;
import org.exist.xpath.StaticContext;
import org.exist.xpath.XPathException;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.StringValue;
import org.exist.xpath.value.Type;

/**
 * @author wolf
 */
public class FunDocumentURI extends Function {

	/**
	 * @param name
	 */
	public FunDocumentURI(String name) {
		super(name);
	}

	/**
	 * 
	 */
	public FunDocumentURI() {
		super();
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#eval(org.exist.xpath.StaticContext, org.exist.dom.DocumentSet, org.exist.xpath.value.Sequence, org.exist.xpath.value.Item)
	 */
	public Sequence eval(
		StaticContext context,
		DocumentSet docs,
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		Expression arg = getArgument(0);
		Sequence s = arg.eval(context, docs, contextSequence, contextItem);
		if(s.getLength() == 0)
			throw new XPathException("argument to document-uri() returned empty sequence");
		Item item = s.itemAt(0);
		if(!Type.isNode(item.getType()))
			throw new XPathException("argument to document-uri() is not a node");
		NodeProxy node = (NodeProxy)item;
		return new StringValue(node.doc.getFileName()); 
	}

}
