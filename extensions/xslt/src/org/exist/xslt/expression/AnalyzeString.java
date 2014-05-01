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
import org.exist.xquery.XQueryContext;
import org.exist.xslt.XSLContext;
import org.exist.xslt.pattern.Pattern;
import org.w3c.dom.Attr;

/**
 * <!-- Category: instruction -->
 * <xsl:analyze-string
 *   select = expression
 *   regex = { string }
 *   flags? = { string }>
 *   <!-- Content: (xsl:matching-substring?, xsl:non-matching-substring?, xsl:fallback*) -->
 * </xsl:analyze-string>
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class AnalyzeString extends SimpleConstructor {

	private XSLPathExpr select = null;
	private String regex = null;
	private String flags = null;
	
	public AnalyzeString(XSLContext context) {
		super(context);
	}
	
	public void setToDefaults() {
		select = null;
		regex = null;
		flags = null;
	}

	public void prepareAttribute(ContextAtExist context, Attr attr) throws XPathException {
		String attr_name = attr.getNodeName();
		if (attr_name.equals(SELECT)) {
			select = new XSLPathExpr(getXSLContext());
			Pattern.parse((XQueryContext) context, attr.getValue(), select);
		} else if (attr_name.equals(REGEX)) {
			regex = attr.getValue();
		} else if (attr_name.equals(FLAGS)) {
			flags = attr.getValue();
		}
	}
	
    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
    	throw new RuntimeException("eval(Sequence contextSequence, Item contextItem) at "+this.getClass());
	}

	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("<xsl:analyze-string");
        
        if (select != null) {
        	dumper.display(" select = ");
        	select.dump(dumper);
        }
        if (regex != null)
        	dumper.display(" regex = "+regex);
        if (flags != null)
        	dumper.display(" disable-output-escaping = "+flags);

        dumper.display("> ");

        super.dump(dumper);

        dumper.display("</xsl:analyze-string>");
    }
    
    public String toString() {
    	StringBuilder result = new StringBuilder();
    	result.append("<xsl:analyze-string");
        
    	if (select != null)
        	result.append(" select = "+select.toString());    
        if (regex != null)
        	result.append(" regex = "+regex.toString());    
        if (flags != null)
        	result.append(" flags = "+flags);    

        result.append("> ");    

        result.append(super.toString());    

        result.append("</xsl:analyze-string>");
        return result.toString();
    }    
}
