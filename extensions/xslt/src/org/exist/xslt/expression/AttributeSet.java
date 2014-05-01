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

import java.util.Set;

import org.exist.interpreter.ContextAtExist;
import org.exist.xquery.XPathException;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.ValueSequence;
import org.exist.xslt.XSLContext;
import org.w3c.dom.Attr;

/**
 * <!-- Category: declaration -->
 * <xsl:attribute-set
 *   name = qname
 *   use-attribute-sets? = qnames>
 *   <!-- Content: xsl:attribute* -->
 * </xsl:attribute-set>
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class AttributeSet extends Declaration {

    private String name = null;
    private String use_attribute_sets = null;
    
    private String delay_use_attribute_sets = null;

    public AttributeSet(XSLContext context) {
		super(context);
	}

	public AttributeSet(XSLContext context, String name) {
		super(context);
		
		delay_use_attribute_sets = name;
	}

	public void setToDefaults() {
		name = null;
		use_attribute_sets = null;
	}

	public void prepareAttribute(ContextAtExist context, Attr attr) throws XPathException {
		String attr_name = attr.getLocalName();
			
		if (attr_name.equals(NAME)) {
			name = attr.getValue();
		} else if (attr_name.equals(USE_ATTRIBUTE_SETS)) {
			use_attribute_sets = attr.getValue();
		}
	}
	
	public String getName() {
		return name;
	}    

	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
		Sequence result = new ValueSequence();
		
		if (use_attribute_sets != null) {
			result.addAll(
					((XSLContext)context).getXSLStylesheet()
						.attributeSet(use_attribute_sets, contextSequence, contextItem)
        	);
		} else if (delay_use_attribute_sets != null) {
			return getXSLContext().getXSLStylesheet().attributeSet(delay_use_attribute_sets, contextSequence, contextItem);
		}
			
		result.addAll( super.eval(contextSequence, contextItem) );
		
		return result;
	}

	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("<xsl:attribute-set");
        if (name != null) {
        	dumper.display(" name = ");
        	dumper.display(name);
        }
        if (use_attribute_sets != null) {
        	dumper.display(" use-attribute-sets = ");
        	dumper.display(use_attribute_sets);
        }
        dumper.display(">");

        super.dump(dumper);

        dumper.display("</xsl:attribute-set>");
    }
    
    public String toString() {
    	StringBuilder result = new StringBuilder();
    	result.append("<xsl:attribute-set");
        
    	if (name != null)
        	result.append(" name = "+name.toString());    
        if (use_attribute_sets != null)
        	result.append(" use-attribute-sets = "+use_attribute_sets.toString());    
        
        result.append("> ");
        
        result.append(super.toString());

        result.append("</xsl:attribute-set> ");
        return result.toString();
    }

}
