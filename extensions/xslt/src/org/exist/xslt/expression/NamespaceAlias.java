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
 * <xsl:namespace-alias
 *   stylesheet-prefix = prefix | "#default"
 *   result-prefix = prefix | "#default" />
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class NamespaceAlias extends Declaration {

    private String stylesheet_prefix = null;
    private String result_prefix = null;

    public NamespaceAlias(XSLContext context) {
		super(context);
	}

	public void setToDefaults() {
		stylesheet_prefix = null;
		result_prefix = null;
	}

	public void prepareAttribute(ContextAtExist context, Attr attr) throws XPathException {
		String attr_name = attr.getLocalName();
			
		if (attr_name.equals(STYLESHEET_PREFIX)) {
			stylesheet_prefix = attr.getValue();
		} else if (attr_name.equals(RESULT_PREFIX)) {
			result_prefix = attr.getValue();
		}
	}
	
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
    	throw new RuntimeException("eval(Sequence contextSequence, Item contextItem) at "+this.getClass());
	}

	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("<xsl:namespace-alias");

        if (stylesheet_prefix != null) {
        	dumper.display(" stylesheet-prefix = ");
        	dumper.display(stylesheet_prefix);
        }
        if (result_prefix != null) {
        	dumper.display(" result-prefix = ");
        	dumper.display(result_prefix);
        }

        super.dump(dumper);

        dumper.display("</xsl:namespace-alias>");
    }
    
    public String toString() {
    	StringBuilder result = new StringBuilder();
    	result.append("<xsl:namespace-alias");
        
    	if (stylesheet_prefix != null)
        	result.append(" stylesheet-prefix = "+stylesheet_prefix.toString());    
    	if (result_prefix != null)
        	result.append(" result-prefix = "+result_prefix.toString());    

        result.append("> ");
        
        result.append(super.toString());

        result.append("</xsl:namespace-alias> ");
        return result.toString();
    }    
}
