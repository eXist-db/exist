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
import org.exist.xquery.DynamicAttributeConstructor;
import org.exist.xquery.Expression;
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
 * <xsl:attribute
 *   name = { qname }
 *   namespace? = { uri-reference }
 *   select? = expression
 *   separator? = { string }
 *   type? = qname
 *   validation? = "strict" | "lax" | "preserve" | "strip">
 *   <!-- Content: sequence-constructor -->
 * </xsl:attribute>
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Attribute extends SimpleConstructor {

    private String name = null;
    private String namespace = null;
    private String select = null;
    private String separator = null;
    private String type = null;
    private String validation = null;
    
    private Expression value = null;

    private DynamicAttributeConstructor constructor;

    public Attribute(XSLContext context) {
		super(context);
		
		constructor = new DynamicAttributeConstructor(getContext());
	}

	public void setToDefaults() {
	    name = null;
	    namespace = null;
	    select = null;
	    separator = null;
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
		} else if (attr_name.equals(SELECT)) {
			select = attr.getValue();
		} else if (attr_name.equals(SEPARATOR)) {
			separator = attr.getValue();
		} else if (attr_name.equals(TYPE)) {
			type = attr.getValue();
		} else if (attr_name.equals(VALIDATION)) {
			validation = attr.getValue();
		}
	}
	
	public void validate() throws XPathException {
		for (int pos = 0; pos < this.getLength(); pos++) {
			Expression expr = this.getExpression(pos);
			if (expr instanceof ValueOf) {
				ValueOf valueOf = (ValueOf) expr;
				valueOf.validate();
				valueOf.sequenceItSelf = true;
				constructor.setContentExpr(valueOf);
			} else if (expr instanceof Text) {
				Text text = (Text) expr;
				text.validate();
				text.sequenceItSelf = true;
				constructor.setContentExpr(text);
			} else {
				compileError("unsupported subelement");
			}
		}
	}

	public void addText(String text) {
    	value = new LiteralValue(context, new StringValue(text));
		constructor.setContentExpr(value);
    }
	
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
		return constructor.eval(contextSequence, contextItem);
	}

	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("<xsl:attribute");

        if (name != null) {
        	dumper.display(" name = ");
        	dumper.display(name);
        }
        if (namespace != null) {
        	dumper.display(" namespace = ");
        	dumper.display(namespace);
        }
        if (select != null) {
        	dumper.display(" select = ");
        	dumper.display(select);
        }
        if (separator != null) {
        	dumper.display(" separator = ");
        	dumper.display(separator);
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

        dumper.display("</xsl:attribute>");
    }
    
    public String toString() {
    	StringBuffer result = new StringBuffer();
    	result.append("<xsl:attribute");
        
    	if (name != null)
        	result.append(" name = "+name.toString());    
    	if (namespace != null)
        	result.append(" namespace = "+namespace.toString());    
    	if (select != null)
        	result.append(" select = "+select.toString());    
    	if (separator != null)
        	result.append(" separator = "+separator.toString());    
    	if (type != null)
        	result.append(" type = "+type.toString());    
    	if (validation != null)
        	result.append(" validation = "+validation.toString());    

        result.append("> ");
        
        result.append(super.toString());

        result.append("</xsl:attribute> ");
        return result.toString();
    }

	
}
