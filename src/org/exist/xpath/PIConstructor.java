/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001 Wolfgang M. Meier
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
package org.exist.xpath;

import org.exist.memtree.DocumentImpl;
import org.exist.memtree.MemTreeBuilder;
import org.exist.memtree.NodeImpl;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;

/**
 * Constructor for processing-instruction nodes.
 * 
 * @author wolf
 */
public class PIConstructor extends NodeConstructor {

	private String target;
	private String data = null;
	
	public PIConstructor(StaticContext context, String pi) throws XPathException {
		super(context);
		int p = pi.indexOf(' ');
		if(p < 0)
			throw new XPathException("Syntax error in processing instruction");
		target = pi.substring(0, p);
		if(++p < pi.length())
			data = pi.substring(p);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#eval(org.exist.xpath.StaticContext, org.exist.dom.DocumentSet, org.exist.xpath.value.Sequence, org.exist.xpath.value.Item)
	 */
	public Sequence eval(
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		MemTreeBuilder builder = context.getDocumentBuilder();
		int nodeNr = builder.processingInstruction(target, data);
		NodeImpl node = ((DocumentImpl)builder.getDocument()).getNode(nodeNr);
		return node;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#pprint()
	 */
	public String pprint() {
		StringBuffer buf = new StringBuffer();
		buf.append("<?");
		buf.append(target);
		buf.append(' ');
		buf.append(data);
		buf.append("?>");
		return buf.toString();
	}

}
