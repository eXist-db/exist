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
import org.exist.xquery.XPathException;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xslt.XSLContext;
import org.w3c.dom.Attr;

/**
 * <!-- Category: declaration -->
 * <xsl:function
 *   name = qname
 *   as? = sequence-type
 *   override? = "yes" | "no">
 *   <!-- Content: (xsl:param*, sequence-constructor) -->
 * </xsl:function>
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Function extends Declaration {

    private String name = null;
    private String as = null;
    private Boolean override = null;

    public Function(XSLContext context) {
		super(context);
	}

	public void setToDefaults() {
		name = null;
		as = null;
		override = null;
	}

	public void prepareAttribute(ContextAtExist context, Attr attr) throws XPathException {
		String attr_name = attr.getLocalName();
			
		if (attr_name.equals(NAME)) {
			name = attr.getValue();
		} else if (attr_name.equals(AS)) {
			as = attr.getValue();
		} else if (attr_name.equals(OVERRIDE)) {
			override = getBoolean(attr.getValue());
		}
	}
	
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
    	throw new RuntimeException("eval(Sequence contextSequence, Item contextItem) at "+this.getClass());
	}

	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("<xsl:function");
        if (name != null) {
        	dumper.display(" name = ");
        	dumper.display(name);
        }
        if (as != null) {
        	dumper.display(" as = ");
        	dumper.display(as);
        }
        if (override != null) {
        	dumper.display(" override = ");
        	dumper.display(override);
        }

        super.dump(dumper);

        dumper.display("</xsl:function>");
    }
    
    public String toString() {
    	StringBuilder result = new StringBuilder();
    	result.append("<xsl:function");
        
    	if (name != null)
        	result.append(" name = "+name.toString());    
    	if (as != null)
        	result.append(" as = "+as.toString());    
    	if (override != null)
        	result.append(" override = "+override.toString());    

        result.append("> ");
        
        result.append(super.toString());

        result.append("</xsl:function> ");
        return result.toString();
    }    
}
