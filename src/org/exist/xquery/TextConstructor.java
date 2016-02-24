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
package org.exist.xquery;

import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.dom.memtree.NodeImpl;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;

/**
 * Direct constructor for text nodes.
 * 
 * @author wolf
 */
public class TextConstructor extends NodeConstructor {

    private final String text;
    private boolean isWhitespaceOnly = true;

    public TextConstructor(XQueryContext context, String text) throws XPathException {
        super(context);
        this.text = StringValue.expand(text);
        for (int i = 0; i < text.length(); i++) {
            if (!isWhiteSpace(text.charAt(i))) {
                isWhitespaceOnly = false;
                break;
            }
        }
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#eval(org.exist.xquery.StaticContext, org.exist.dom.persistent.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
     */
    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (isWhitespaceOnly && context.stripWhitespace())
            {return Sequence.EMPTY_SEQUENCE;}
        if (newDocumentContext)
            {context.pushDocumentContext();}
        try {
            final MemTreeBuilder builder = context.getDocumentBuilder();
            context.proceed(this, builder);
            final int nodeNr = builder.characters(text);
            final NodeImpl node = builder.getDocument().getNode(nodeNr);
            return node;
        } finally {
            if (newDocumentContext)
                {context.popDocumentContext();}
        }
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("text {");
        dumper.startIndent();
        dumper.display(text);
        dumper.endIndent();
        dumper.nl().display("}");
    }

    public String toString() {
        return "text {" + text + "}";
    }

    protected final static boolean isWhiteSpace(char ch) {
        return (ch == 0x20) || (ch == 0x09) || (ch == 0xD) || (ch == 0xA);
    }

    public String getText() {
        return text;
    }

    @Override
    public boolean allowMixedNodesInReturn() {
        return true;
    }
}
