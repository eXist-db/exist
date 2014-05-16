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

import org.exist.interpreter.ContextAtExist;
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.XPathException;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xslt.XSLContext;
import org.w3c.dom.Attr;

/**
 * <!-- Category: instruction -->
 * <xsl:document
 *   validation? = "strict" | "lax" | "preserve" | "strip"
 *   type? = qname>
 *   <!-- Content: sequence-constructor -->
 * </xsl:document>
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Document extends Declaration {

    private String validation = null;
    private String type = null;

    public Document(XSLContext context) {
		super(context);
	}

	public void setToDefaults() {
	    type = null;
	    validation = null;
	}

	public void prepareAttribute(ContextAtExist context, Attr attr) throws XPathException {
		String attr_name = attr.getLocalName();
			
		if (attr_name.equals(VALIDATION)) {
			validation = attr.getValue();
		} else if (attr_name.equals(TYPE)) {
			type = attr.getValue();
		}
	}
	
	public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
		super.analyze(contextInfo);
	}
	
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {

		Sequence contentSeq;
		
        context.pushDocumentContext();
        try {
            contentSeq = super.eval(contextSequence, contextItem);
            
            return contentSeq;
        } finally {
        	context.popDocumentContext();
        }
	}

	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("<xsl:document");

        if (validation != null) {
        	dumper.display(" validation = ");
        	dumper.display(validation);
        }
        if (type != null) {
        	dumper.display(" type = ");
        	dumper.display(type);
        }

        super.dump(dumper);

        dumper.display("</xsl:document>");
    }
    
    public String toString() {
    	StringBuilder result = new StringBuilder();
    	result.append("<xsl:document");
        
    	if (validation != null)
        	result.append(" validation = "+validation.toString());    
    	if (type != null)
        	result.append(" type = "+type.toString());    

        result.append("> ");
        
        result.append(super.toString());

        result.append("</xsl:document> ");
        return result.toString();
    }    
}
