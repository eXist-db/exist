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

import org.exist.memtree.DocumentImpl;
import org.exist.memtree.MemTreeBuilder;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;


/**
 * Implements a dynamic namespace constructor.
 * 
 * @author wolf
 */
public class NamespaceConstructor extends NodeConstructor {

    private String prefix = null;
    private Expression uri = null;
    
    /**
     * @param context
     */
    public NamespaceConstructor(XQueryContext context, String prefix) {
        super(context);
        this.prefix = prefix;
    }

    public void setURIExpression(Expression uriExpr) {
        this.uri = new Atomize(context, uriExpr);
    }
    
    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#eval(org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
     */
    public Sequence eval(Sequence contextSequence, Item contextItem)
            throws XPathException {
        MemTreeBuilder builder = context.getDocumentBuilder();
		context.proceed(this, builder);
        Sequence uriSeq = uri.eval(contextSequence, contextItem);
        String value = null;
        if(uriSeq.getLength() == 0)
            value = "";
        else {
            StringBuffer buf = new StringBuffer();
            for(SequenceIterator i = uriSeq.iterate(); i.hasNext(); ) {
                context.proceed(this, builder);
                Item next = i.nextItem();
                if(buf.length() > 0)
                    buf.append(' ');
                buf.append(next.toString());
            }
            value = buf.toString();
        }
        context.declareInScopeNamespace(prefix, value);
        int nodeNr = builder.namespaceNode(prefix, value);
        return ((DocumentImpl)builder.getDocument()).getNode(nodeNr);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#pprint()
     */
    public String pprint() {
        StringBuffer buf = new StringBuffer();
        buf.append("namespace ").append(prefix);
        buf.append("{ ").append(uri.pprint()).append(" }");
        return buf.toString();
    }

}
