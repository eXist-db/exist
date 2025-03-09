/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery;

import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.dom.memtree.NodeImpl;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Constructor for processing-instruction nodes.
 * 
 * @author wolf
 */
public class PIConstructor extends NodeConstructor {

	private static Pattern wsContentStart = Pattern.compile("^(\\s)*(.*)");
	private final String target;
	private String data = null;
	
	public PIConstructor(XQueryContext context, String pi) throws XPathException {
		super(context);
        //TODO : handle this from the parser -pb
		int p = pi.indexOf(' ');
		if(p == Constants.STRING_NOT_FOUND) {
            target = pi;
        } else {
            target = pi.substring(0, p);
            if(++p < pi.length()) {
                data = pi.substring(p);

                final Matcher m = wsContentStart.matcher(data);
                if (m.matches()) {
                    data = m.group(2);
                }
            }
        }
        if ("xml".equalsIgnoreCase(target)) {
            throw new XPathException(this, ErrorCodes.XPST0003, "The target 'xml' is not allowed in XML processing instructions.");
        }
	}
    
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.xquery.StaticContext, org.exist.dom.persistent.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
        if (newDocumentContext)
            {context.pushDocumentContext();}
        try {
            final MemTreeBuilder builder = context.getDocumentBuilder();
            final int nodeNr = builder.processingInstruction(target, data);
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
        dumper.display("processing-instruction {");
        dumper.display(target);
        dumper.display("} {");
        dumper.startIndent();
        dumper.display(data);
        dumper.endIndent().nl().display("}");
    }
    
    public String toString() {
        return "processing-instruction {" + target + "} {" + data + "}";
    }
}
