
/*
 *  eXist Native XML Database
 *  Copyright (C) 2001-03,  Wolfgang M. Meier (wolfgang@exist-db.org)
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 * $Id$
 */

package org.exist.xpath;

import org.exist.memtree.MemTreeBuilder;
import org.exist.memtree.Receiver;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.SequenceIterator;
import org.exist.xpath.value.Type;
import org.xml.sax.SAXException;

/**
 * Represents an enclosed expression <code>{expr}</code> inside element
 * content. Enclosed expressions within attribute values are processed by
 * {@link org.exist.xpath.AttributeConstructor}.
 *  
 * @author Wolfgang Meier <wolfgang@exist-db.org>
 */
public class EnclosedExpr extends PathExpr {

	/**
	 * 
	 */
	public EnclosedExpr(XQueryContext context) {
		super(context);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.AbstractExpression#eval(org.exist.xpath.StaticContext, org.exist.dom.DocumentSet, org.exist.xpath.value.Sequence)
	 */
	public Sequence eval(Sequence contextSequence, Item contextItem)
		throws XPathException {
		long start = System.currentTimeMillis();
		context.pushDocumentContext();
		Sequence result = super.eval(null, null);
		context.popDocumentContext();
		MemTreeBuilder builder = context.getDocumentBuilder();
		Receiver receiver = new Receiver(builder);
		start = System.currentTimeMillis();
		try {
			SequenceIterator i = result.iterate();
			Item next = i.nextItem();
			boolean readNext = true;
			StringBuffer buf = new StringBuffer();
			while (next != null) {
				// if item is an atomic value, collect the string values of all
				// following atomic values and seperate them by a space. 
				if (Type.subTypeOf(next.getType(), Type.ATOMIC)) {
					if (buf.length() > 0)
						buf.append(' ');
					buf.append(next.getStringValue());
					next = i.nextItem();
				} else if (Type.subTypeOf(next.getType(), Type.NODE)) {
					if (buf.length() > 0) {
						receiver.characters(buf);
						buf.setLength(0);
					}
					next.copyTo(context.getBroker(), receiver);
					next = i.nextItem();
				}
			}
			if (buf.length() > 0)
				receiver.characters(buf);
		} catch (SAXException e) {
			throw new XPathException(
				"Encountered SAX exception while serializing enclosed expression: "
					+ pprint());
		}
		return result;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.PathExpr#pprint()
	 */
	public String pprint() {
		return '{' + super.pprint() + '}';
	}
}
