/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2008-2009 The eXist Project
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
import org.exist.xquery.XPathException;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xslt.XSLContext;
import org.w3c.dom.Attr;

/**
 * <!-- Category: instruction -->
 * <xsl:copy-of
 *   select = expression
 *   copy-namespaces? = "yes" | "no"
 *   type? = qname
 *   validation? = "strict" | "lax" | "preserve" | "strip" />
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class CopyOf extends Declaration {

    private String select = null;
    private Boolean copy_namespaces = null;
    private String type = null;
    private String validation = null;

    public CopyOf(XSLContext context) {
		super(context);
	}

	public void setToDefaults() {
		select = null;
		copy_namespaces = null;
	    type = null;
	    validation = null;
	}

	public void prepareAttribute(ContextAtExist context, Attr attr) throws XPathException {
		String attr_name = attr.getLocalName();
			
		if (attr_name.equals(SELECT)) {
			select = attr.getValue();
		} else if (attr_name.equals(COPY_NAMESPACES)) {
			copy_namespaces = getBoolean(attr.getValue());
		} else if (attr_name.equals(TYPE)) {
			type = attr.getValue();
		} else if (attr_name.equals(VALIDATION)) {
			validation = attr.getValue();
		}
	}
	
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
    	throw new RuntimeException("eval(Sequence contextSequence, Item contextItem) at "+this.getClass());
	}

	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("<xsl:copy-of");

        if (select != null) {
        	dumper.display(" select = ");
        	dumper.display(select);
        }
        if (copy_namespaces != null) {
        	dumper.display(" copy_namespaces = ");
        	dumper.display(copy_namespaces);
        }
        if (type != null) {
        	dumper.display(" type = ");
        	dumper.display(type);
        }
        if (validation != null) {
        	dumper.display(" validation = ");
        	dumper.display(validation);
        }

        super.dump(dumper);

        dumper.display("</xsl:copy-of>");
    }
    
    public String toString() {
    	StringBuffer result = new StringBuffer();
    	result.append("<xsl:copy-of");
        
    	if (select != null)
        	result.append(" select = "+select.toString());    
    	if (copy_namespaces != null)
        	result.append(" copy_namespaces = "+copy_namespaces.toString());    
    	if (type != null)
        	result.append(" type = "+type.toString());    
    	if (validation != null)
        	result.append(" validation = "+validation.toString());    

        result.append("> ");
        
        result.append(super.toString());

        result.append("</xsl:copy-of> ");
        return result.toString();
    }    
}
