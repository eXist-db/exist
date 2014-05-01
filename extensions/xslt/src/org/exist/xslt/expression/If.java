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
import org.exist.xslt.pattern.Pattern;
import org.w3c.dom.Attr;

/**
 * <!-- Category: instruction -->
 * <xsl:if
 *   test = expression>
 *   <!-- Content: sequence-constructor -->
 * </xsl:if>
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class If extends SimpleConstructor {
	
	private String attr_test = null;
	
	private XSLPathExpr test = null;

	public If(XSLContext context) {
		super(context);
	}
	
	public void setToDefaults() {
		attr_test = null;

		test = null;
	}

	public void prepareAttribute(ContextAtExist context, Attr attr) throws XPathException {
		String attr_name = attr.getNodeName();
		if (attr_name.equals(TEST)) {
			attr_test = attr.getValue();
		}
	}

	public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
		super.analyze(contextInfo);
		
		if (attr_test == null)
			throw new XPathException("error, no test at xsl:if");//TODO: error???
		
	    test = new XSLPathExpr(getXSLContext());
	    Pattern.parse(contextInfo.getContext(), attr_test, test);
	}
	
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
		if (test.eval(contextSequence, contextItem).effectiveBooleanValue())
			return super.eval(contextSequence, contextItem);
		
		return Sequence.EMPTY_SEQUENCE;

	}

	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("<xsl:if");
        
        if (attr_test != null)
        	dumper.display(" test = "+attr_test);
        
        dumper.display("> ");

        super.dump(dumper);

        dumper.display("</xsl:if>");
    }
    
    public String toString() {
    	StringBuilder result = new StringBuilder();
    	result.append("<xsl:if");

        if (attr_test != null)
        	result.append(" test = "+attr_test.toString());    
        
        result.append("> ");    

        result.append(super.toString());    

        result.append("</xsl:if>");
        return result.toString();
    }

}
