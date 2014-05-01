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
import org.exist.xquery.XQueryContext;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xslt.XSLContext;
import org.exist.xslt.pattern.Pattern;
import org.w3c.dom.Attr;

/**
 * <!-- Category: instruction -->
 * <xsl:message
 *   select? = expression
 *   terminate? = { "yes" | "no" }>
 *   <!-- Content: sequence-constructor -->
 * </xsl:message>
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Message extends SimpleConstructor {

	private XSLPathExpr select = null;
	private Boolean terminate = null;
	
	public Message(XSLContext context) {
		super(context);
	}
	
	public void setToDefaults() {
		select = null;
		terminate = null;
	}

	public void prepareAttribute(ContextAtExist context, Attr attr) throws XPathException {
		String attr_name = attr.getNodeName();
		if (attr_name.equals(SELECT)) {
			select = new XSLPathExpr(getXSLContext());
			Pattern.parse((XQueryContext) context, attr.getValue(), select);
			
		} else if (attr_name.equals(TERMINATE)) {
			terminate = getBoolean(attr.getValue());
			
		}
	}
	
    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
    	//ignore it for now. may be, send it to log or console
    	return Sequence.EMPTY_SEQUENCE;
	}

	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("<xsl:message");
        
        if (select != null) {
        	dumper.display(" select = ");
        	select.dump(dumper);
        }
        if (terminate != null)
        	dumper.display(" terminate = "+terminate);

        dumper.display("> ");

        super.dump(dumper);

        dumper.display("</xsl:message>");
    }
    
    public String toString() {
    	StringBuilder result = new StringBuilder();
    	result.append("<xsl:message");
        
    	if (select != null)
        	result.append(" select = "+select.toString());    
        if (terminate != null)
        	result.append(" terminate = "+terminate.toString());    

        result.append("> ");    

        result.append(super.toString());    

        result.append("</xsl:message>");
        return result.toString();
    }    
}
