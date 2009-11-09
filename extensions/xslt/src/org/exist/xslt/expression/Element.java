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
import org.exist.xquery.ElementConstructor;
import org.exist.xquery.LiteralValue;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xslt.XSLContext;
import org.w3c.dom.Attr;

/**
 * <!-- Category: instruction -->
 * <xsl:element
 *   name = { qname }
 *   namespace? = { uri-reference }
 *   inherit-namespaces? = "yes" | "no"
 *   use-attribute-sets? = qnames
 *   type? = qname
 *   validation? = "strict" | "lax" | "preserve" | "strip">
 *   <!-- Content: sequence-constructor -->
 * </xsl:element>
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Element extends SimpleConstructor {

    private String name = null;
    private String namespace = null;
    private Boolean inherit_namespaces = null;
    private String use_attribute_sets = null;
    private String type = null;
    private String validation = null;
    
    private ElementConstructor constructor;

    public Element(XSLContext context) {
		super(context);
		
		constructor = new ElementConstructor(getContext());
		constructor.setContent(this);
	}

	public void setToDefaults() {
		name = null;
		namespace = null;
		inherit_namespaces = null;
		use_attribute_sets = null;
	    type = null;
	    validation = null;
	}

	public void prepareAttribute(ContextAtExist context, Attr attr) throws XPathException {
		String attr_name = attr.getLocalName();
			
		if (attr_name.equals(NAME)) {
			name = attr.getValue();
			constructor.setNameExpr(new LiteralValue((XQueryContext) context, new StringValue(name)));
		} else if (attr_name.equals(NAMESPACE)) {
			namespace = attr.getValue();
		} else if (attr_name.equals(INHERIT_NAMESPACES)) {
			inherit_namespaces = getBoolean(attr.getValue());
		} else if (attr_name.equals(USE_ATTRIBUTE_SETS)) {
			use_attribute_sets = attr.getValue();
		} else if (attr_name.equals(TYPE)) {
			type = attr.getValue();
		} else if (attr_name.equals(VALIDATION)) {
			validation = attr.getValue();
		}
	}
	
	private boolean internalCall = false;
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
		if (!internalCall) {
			internalCall = true;
			return constructor.eval(contextSequence, contextItem);
		}
		internalCall = false;
		return super.eval(contextSequence, contextItem); 
	}

	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("<xsl:element");

        if (name != null) {
        	dumper.display(" name = ");
        	dumper.display(name);
        }
        if (namespace != null) {
        	dumper.display(" namespace = ");
        	dumper.display(namespace);
        }
        if (inherit_namespaces != null) {
        	dumper.display(" inherit_namespaces = ");
        	dumper.display(inherit_namespaces);
        }
        if (use_attribute_sets != null) {
        	dumper.display(" use_attribute_sets = ");
        	dumper.display(use_attribute_sets);
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

        dumper.display("</xsl:element>");
    }
    
    public String toString() {
    	StringBuffer result = new StringBuffer();
    	result.append("<xsl:element");
        
    	if (name != null)
        	result.append(" name = "+name.toString());    
    	if (namespace != null)
        	result.append(" namespace = "+namespace.toString());    
    	if (inherit_namespaces != null)
        	result.append(" inherit_namespaces = "+inherit_namespaces.toString());    
    	if (use_attribute_sets != null)
        	result.append(" use_attribute_sets = "+use_attribute_sets.toString());    
    	if (type != null)
        	result.append(" type = "+type.toString());    
    	if (validation != null)
        	result.append(" validation = "+validation.toString());    

        result.append("> ");
        
        result.append(super.toString());

        result.append("</xsl:element> ");
        return result.toString();
    }    
}
