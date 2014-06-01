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
import org.exist.xquery.value.Type;
import org.exist.xquery.value.BooleanValue;
import org.exist.xslt.XSLContext;
import org.exist.xslt.pattern.Pattern;
import org.w3c.dom.Attr;

/**
 * <!-- Category: instruction -->
 * <xsl:when
 *   test = expression>
 *   <!-- Content: sequence-constructor -->
 * </xsl:when>
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class When extends SimpleConstructor {
	
	private String attr_test = null;
	
	private XSLPathExpr test = null;

	public When(XSLContext context) {
		super(context);
	}
	
	public void setToDefaults() {
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

    	if (attr_test != null) {
    		test = new XSLPathExpr(getXSLContext());
		    Pattern.parse(contextInfo.getContext(), attr_test, test);

			_check_(test);
    	}
    }

	public Boolean test(Sequence contextSequence, Item contextItem) throws XPathException {
		Sequence result = test.eval(contextSequence, contextItem);
		if (result.getItemCount() == 0) //UNDERSTAND: is it correct
			return false;
		
		if (result.getItemCount() != 1)
			throw new XPathException("problem at test");//UNDERSTAND: what to do?
    	return ((BooleanValue) result.itemAt(0).convertTo(Type.BOOLEAN)).getValue();
	}

	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
		return super.eval(contextSequence, contextItem);
	}

	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("<xsl:when");
        
        if (test != null)
        	dumper.display(" test = "+test);
        
        dumper.display("> ");

        super.dump(dumper);

        dumper.display("</xsl:when>");
    }
    
    public String toString() {
    	StringBuilder result = new StringBuilder();
    	result.append("<xsl:when");

        if (test != null) {
        	result.append(" test = ");
        	result.append(test.toString());
        }
        
        result.append("> ");    

        result.append(super.toString());    

        result.append("</xsl:when>");
        return result.toString();
    }

}
