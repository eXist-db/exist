/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
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
package org.exist.xquery;

import org.exist.memtree.MemTreeBuilder;
import org.exist.memtree.NodeImpl;
import org.exist.memtree.Receiver;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;
import org.xml.sax.SAXException;


/**
 * Implements a dynamic document constructor. Creates a new
 * document node with its own node identity.
 * 
 * @author wolf
 */
public class DocumentConstructor extends NodeConstructor {

    private Expression content;
    
    /**
     * @param context
     */
    public DocumentConstructor(XQueryContext context, Expression contentExpr) {
        super(context);
        this.content = contentExpr;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#eval(org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
     */
    public Sequence eval(Sequence contextSequence, Item contextItem)
            throws XPathException {
        System.out.println(content.pprint());
        Sequence contentSeq = content.eval(contextSequence, contextItem);
        context.pushDocumentContext();
        MemTreeBuilder builder = context.getDocumentBuilder();
        Receiver receiver = new Receiver(builder);
        
        if(contentSeq.getLength() == 0)
            return builder.getDocument();
        try {
	        StringBuffer buf = null;
	        SequenceIterator i = contentSeq.iterate();
	        Item next = i.nextItem();
	        while(next != null) {
	            context.proceed(this, builder);
	            if(next.getType() == Type.ATTRIBUTE || next.getType() == Type.NAMESPACE ||
	                    next.getType() == Type.DOCUMENT)
	                throw new XPathException(getASTNode(), "Found a node of type " + Type.getTypeName(next.getType()) +
	                        " inside a document constructor");
	            // if item is an atomic value, collect the string values of all
				// following atomic values and seperate them by a space. 
				if (Type.subTypeOf(next.getType(), Type.ATOMIC)) {
				    if(buf == null)
				        buf = new StringBuffer();
					else if (buf.length() > 0)
						buf.append(' ');
					buf.append(next.getStringValue());
					next = i.nextItem();
				// if item is a node, flush any collected character data and
				//	copy the node to the target doc. 
				} else if (Type.subTypeOf(next.getType(), Type.NODE)) {
					if (buf != null && buf.length() > 0) {
						receiver.characters(buf);
						buf.setLength(0);
					}
					next.copyTo(context.getBroker(), receiver);
					next = i.nextItem();
				}
	        }
	        // flush remaining character data
			if (buf != null && buf.length() > 0)
				receiver.characters(buf);
        } catch(SAXException e) {
			throw new XPathException(getASTNode(),
				"Encountered SAX exception while processing document constructor: "
					+ pprint());
        }
        NodeImpl node =  builder.getDocument();
        context.popDocumentContext();
        return node;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#pprint()
     */
    public String pprint() {
        StringBuffer buf = new StringBuffer();
        buf.append("document { ").append(content.pprint()).append(" }");
        return buf.toString();
    }

}
