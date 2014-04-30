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

import org.exist.dom.QName;
import org.exist.interpreter.ContextAtExist;
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.XPathException;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.exist.xslt.XSLContext;
import org.exist.xslt.pattern.Pattern;
import org.w3c.dom.Attr;

/**
 * <xsl:with-param
 *   name = qname
 *   select? = expression
 *   as? = sequence-type
 *   tunnel? = "yes" | "no">
 *   <!-- Content: sequence-constructor -->
 * </xsl:with-param>
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class WithParam extends Declaration {

	private String attr_name = null;
	private String attr_select = null;
    
	private QName name = null;
    private XSLPathExpr select = null;
    private String as = null;
    private Boolean tunnel = null;

    public WithParam(XSLContext context) {
		super(context);
	}

	public void setToDefaults() {
	    attr_select = null;
		
		name = null;
	    select = null;
	    as = null;
	    tunnel = null;
	}

	public void prepareAttribute(ContextAtExist context, Attr attr) throws XPathException {
		String _attr_name = attr.getLocalName();
			
		if (_attr_name.equals(NAME)) {
			attr_name = attr.getValue();
		} else if (_attr_name.equals(SELECT)) {
			attr_select = attr.getValue();
		} else if (_attr_name.equals(AS)) {
			as = attr.getValue();
		} else if (_attr_name.equals(TUNNEL)) {
			tunnel = getBoolean(attr.getValue());
		}
	}

	public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
    	super.analyze(contextInfo);

    	if (attr_name != null) {
    		name = QName.parse(contextInfo.getContext(), attr_name);
    	}
    	
    	if (attr_select != null) {
    		select = new XSLPathExpr(getXSLContext());
		    Pattern.parse(contextInfo.getContext(), attr_select, select);

			_check_(select, true);
    	}
    }
	
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
		Sequence result;
		if (select != null) {
			result = select.eval(contextSequence, contextItem);
		} else {
			result = super.eval(contextSequence, contextItem);
		}
		
		if (result.getItemCount() != 1)
			throw new XPathException("only one value for param posible.");//TODO: error?
		
		return result.convertTo(Type.UNTYPED_ATOMIC);
	}

	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("<xsl:with-param");

        if (name != null) {
        	dumper.display(" name = ");
        	dumper.display(name);
        }
        if (select != null) {
        	dumper.display(" select = ");
        	select.dump(dumper);
        }
        if (as != null) {
        	dumper.display(" as = ");
        	dumper.display(as);
        }
        if (tunnel != null) {
        	dumper.display(" tunnel = ");
        	dumper.display(tunnel);
        }

        super.dump(dumper);

        dumper.display("</xsl:with-param>");
    }
    
    public String toString() {
    	StringBuilder result = new StringBuilder();
    	result.append("<xsl:with-param");
        
    	if (name != null) {
        	result.append(" name = ");
        	result.append(name.toString());
    	}
    	if (select != null) {
        	result.append(" select = ");
        	result.append(select.toString());
    	}
    	if (as != null) {
        	result.append(" as = ");
        	result.append(as);
    	}
    	if (tunnel != null) {
        	result.append(" tunnel = ");
        	result.append(tunnel);
    	}

        result.append("> ");
        
        result.append(super.toString());

        result.append("</xsl:with-param> ");
        return result.toString();
    }

	public QName getName() {
		return name;
	}    
}
