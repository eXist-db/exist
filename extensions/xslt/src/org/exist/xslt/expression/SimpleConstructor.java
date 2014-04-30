/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2008-2010 The eXist Project
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
package org.exist.xslt.expression;

import java.util.Iterator;

import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;
import org.exist.xslt.XSLContext;

/**
 * The xsl:attribute, xsl:comment, xsl:processing-instruction, xsl:namespace, 
 * and xsl:value-of elements create nodes that cannot have children. 
 * Specifically, the 
 * xsl:attribute instruction creates an attribute node, 
 * xsl:comment creates a comment node, 
 * xsl:processing-instruction creates a processing instruction node, 
 * xsl:namespace creates a namespace node, 
 * and xsl:value-of creates a text node.
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public abstract class SimpleConstructor extends XSLPathExpr {

	protected boolean newDocumentContext = false;
	protected boolean sequenceItSelf = false;

	public SimpleConstructor(XSLContext context) {
		super(context);
	}
	
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        newDocumentContext = (contextInfo.getFlags() & IN_NODE_CONSTRUCTOR) == 0;
        
        sequenceItSelf = (contextInfo.getFlags() & NON_STREAMABLE) == 0;

//        if (!newDocumentContext) {
//        	for (Iterator<Expression> i = steps.iterator(); i.hasNext();) {
//        		if (i.next() instanceof Text) {
//					i.remove();
//				}
//        	}
//        }
        
        super.analyze(contextInfo);
    }
}
