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

import org.exist.dom.QName;
import org.exist.memtree.DocumentImpl;
import org.exist.memtree.MemTreeBuilder;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;


/**
 * Dynamic constructor for processing instruction nodes.
 * 
 * @author wolf
 */
public class DynamicPIConstructor extends NodeConstructor {

    private Expression name;
    private Expression content;
    
    /**
     * @param context
     */
    public DynamicPIConstructor(XQueryContext context) {
        super(context);
    }

    public void setNameExpr(Expression nameExpr) {
        this.name = nameExpr;
    }
    
    public void setContentExpr(Expression contentExpr) {
        this.content = contentExpr;
    }
    
    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#eval(org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
     */
    public Sequence eval(Sequence contextSequence, Item contextItem)
            throws XPathException {
        MemTreeBuilder builder = context.getDocumentBuilder();
        context.proceed(this, builder);
        Sequence nameSeq = name.eval(contextSequence, contextItem);
        if(nameSeq.getLength() != 1)
            throw new XPathException(getASTNode(), "The name expression should evaluate to a single value");
        Item nameItem = nameSeq.itemAt(0);
        if(!(nameItem.getType() == Type.STRING || nameItem.getType() == Type.QNAME))
            throw new XPathException(getASTNode(), "The name expression should evaluate to a string or qname");
        
        QName qn = QName.parse(context, nameSeq.getStringValue());
        
        String value;
        Sequence contentSeq = content.eval(contextSequence, contextItem);
        if(contentSeq.getLength() == 0)
            value = "";
        else {
	        StringBuffer buf = new StringBuffer();
	        for(SequenceIterator i = contentSeq.iterate(); i.hasNext(); ) {
	            context.proceed(this, builder);
	            Item next = i.nextItem();
	            if(buf.length() > 0)
	                buf.append(' ');
	            buf.append(next.toString());
	        }
	        value = buf.toString();
        }
        int nodeNr = builder.processingInstruction(qn.getLocalName(), value);
        return ((DocumentImpl)builder.getDocument()).getNode(nodeNr);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#pprint()
     */
    public String pprint() {
        StringBuffer buf = new StringBuffer();
        buf.append("processing-instruction { ").append(name.pprint());
        buf.append(" } { ").append(content.pprint()).append(" }");
        return buf.toString();
    }

}
