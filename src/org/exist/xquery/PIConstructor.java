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

import org.exist.memtree.DocumentImpl;
import org.exist.memtree.MemTreeBuilder;
import org.exist.memtree.NodeImpl;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;

/**
 * Constructor for processing-instruction nodes.
 * 
 * @author wolf
 */
public class PIConstructor extends NodeConstructor {

	private final String target;
	private String data = null;
	
	public PIConstructor(XQueryContext context, String pi) throws XPathException {
		super(context);
        //TODO : handle this from the parser -pb
		int p = pi.indexOf(" ");
		if(p == Constants.STRING_NOT_FOUND)
			throw new XPathException("Syntax error in processing instruction");
		target = pi.substring(0, p);
		if(++p < pi.length())
			data = pi.substring(p);
	}
	
    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#analyze(org.exist.xquery.AnalyzeContextInfo)
     */
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
    }
    
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.xquery.StaticContext, org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
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
    	StringBuffer result = new StringBuffer();
    	result.append("processing-instruction {");
    	result.append(target.toString());
    	result.append("} {");        
    	result.append(data.toString());
    	result.append("}");
    	return result.toString();
    }    
}
