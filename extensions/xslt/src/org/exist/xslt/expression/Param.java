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

import org.exist.dom.QName;
import org.exist.interpreter.ContextAtExist;
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.PathExpr;
import org.exist.xquery.Variable;
import org.exist.xquery.XPathException;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xslt.XSLContext;
import org.exist.xslt.expression.i.Parameted;
import org.exist.xslt.pattern.Pattern;
import org.w3c.dom.Attr;

/**
 * <!-- Category: declaration -->
 * <xsl:param
 *   name = qname
 *   select? = expression
 *   as? = sequence-type
 *   required? = "yes" | "no"
 *   tunnel? = "yes" | "no">
 *   <!-- Content: sequence-constructor -->
 * </xsl:param>
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Param extends Declaration {

    private String attr_select = null;

	private QName name = null;
    private PathExpr select = null;
    private String as = null;
    private Boolean required = null;
    private Boolean tunnel = null;

    public Param(XSLContext context) {
		super(context);
	}

	public void setToDefaults() {
		attr_select = null;
		
	    name = null;
	    select = null;
	    as = null;
	    required = null;
	    tunnel = null;
	}

	public void prepareAttribute(ContextAtExist context, Attr attr) throws XPathException {
		String attr_name = attr.getLocalName();
			
		if (attr_name.equals(NAME)) {
			name = new QName(attr.getValue());
		} else if (attr_name.equals(SELECT)) {
			attr_select = attr.getValue();
		} else if (attr_name.equals(AS)) {
			as = attr.getValue();
		} else if (attr_name.equals(REQUIRED)) {
			required = getBoolean(attr.getValue());
		} else if (attr_name.equals(TUNNEL)) {
			tunnel = getBoolean(attr.getValue());
		}
	}

	public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
		if (contextInfo.getParent() instanceof Parameted) {
			Parameted parameted = (Parameted) contextInfo.getParent();
			parameted.addXSLParam(this);
		} else {
			throw new XPathException("wrong parent");//XXX: error
		}
		
    	super.analyze(contextInfo);

    	if (attr_select != null) {
    		select = new PathExpr(getContext());
		    Pattern.parse(getContext(), attr_select, select);

			_check_(select);
			
    	}
    }
	
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
		if (name != null) {
			Variable var = getXSLContext().resolveVariable(name);
			return var.getValue();
		} else if (select != null) {
			return select.eval(contextSequence, contextItem);
		}
		throw new XPathException("param can't calculated");//TODO: error?
	}

	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("<xsl:param");

        if (getName() != null) {
        	dumper.display(" name = ");
        	dumper.display(getName());
        }
        if (select != null) {
        	dumper.display(" select = ");
        	dumper.display(select);
        }
        if (as != null) {
        	dumper.display(" as = ");
        	dumper.display(as);
        }
        if (required != null) {
        	dumper.display(" required = ");
        	dumper.display(required);
        }
        if (tunnel != null) {
        	dumper.display(" tunnel = ");
        	dumper.display(tunnel);
        }

        super.dump(dumper);

        dumper.display("</xsl:param>");
    }
    
    public String toString() {
    	StringBuffer result = new StringBuffer();
    	result.append("<xsl:param");
        
    	if (getName() != null)
        	result.append(" name = "+getName().toString());    
    	if (select != null)
        	result.append(" select = "+select.toString());    
    	if (as != null)
        	result.append(" as = "+as.toString());    
    	if (required != null)
        	result.append(" required = "+required.toString());    
    	if (tunnel != null)
        	result.append(" tunnel = "+tunnel.toString());    

        result.append("> ");
        
        result.append(super.toString());

        result.append("</xsl:param> ");
        return result.toString();
    }

	/**
	 * @return the name
	 */
	public QName getName() {
		return name;
	}    
}
