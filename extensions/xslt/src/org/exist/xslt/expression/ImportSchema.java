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
 * <xsl:import-schema
 *   namespace? = uri-reference
 *   schema-location? = uri-reference>
 *   <!-- Content: xs:schema? -->
 * </xsl:import-schema>

 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class ImportSchema extends Declaration {

    private String namespace = null;
    private String schema_location = null;

    public ImportSchema(XSLContext context) {
		super(context);
	}

	public void setToDefaults() {
		namespace = null;
		schema_location = null;
	}

	public void prepareAttribute(ContextAtExist context, Attr attr) throws XPathException {
		String attr_name = attr.getLocalName();
			
		if (attr_name.equals(NAMESPACE)) {
			namespace = attr.getValue();
		} else if (attr_name.equals(SCHEMA_LOCATION)) {
			schema_location = attr.getValue();
		}
	}
	
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
    	throw new RuntimeException("eval(Sequence contextSequence, Item contextItem) at "+this.getClass());
	}

	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("<xsl:import-schema");

        if (namespace != null) {
        	dumper.display(" namespace = ");
        	dumper.display(namespace);
        }
        if (schema_location != null) {
        	dumper.display(" schema-location = ");
        	dumper.display(schema_location);
        }

        super.dump(dumper);

        dumper.display("</xsl:import-schema>");
    }
    
    public String toString() {
    	StringBuilder result = new StringBuilder();
    	result.append("<xsl:import-schema");
        
    	if (namespace != null)
        	result.append(" namespace = "+namespace.toString());    
    	if (schema_location != null)
        	result.append(" schema-location = "+schema_location.toString());    

        result.append("> ");
        
        result.append(super.toString());

        result.append("</xsl:import-schema> ");
        return result.toString();
    }    
}
