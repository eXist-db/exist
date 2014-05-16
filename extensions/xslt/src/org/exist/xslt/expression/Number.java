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
 * <!-- Category: instruction -->
 * <xsl:number
 *   value? = expression
 *   select? = expression
 *   level? = "single" | "multiple" | "any"
 *   count? = pattern
 *   from? = pattern
 *   format? = { string }
 *   lang? = { nmtoken }
 *   letter-value? = { "alphabetic" | "traditional" }
 *   ordinal? = { string }
 *   grouping-separator? = { char }
 *   grouping-size? = { number } />
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Number extends Declaration {

    private String value = null;
    private String select = null;
    private String level = null;
    private String count = null;
    private String from = null;
    private String format = null;
    private String lang = null;
    private String letter_value = null;
    private String ordinal = null;
    private String grouping_separator = null;
    private String grouping_size = null;

    public Number(XSLContext context) {
		super(context);
	}

	public void setToDefaults() {
	    value = null;
	    select = null;
	    level = null;
	    count = null;
	    from = null;
	    format = null;
	    lang = null;
	    letter_value = null;
	    ordinal = null;
	    grouping_separator = null;
	    grouping_size = null;
	}

	public void prepareAttribute(ContextAtExist context, Attr attr) throws XPathException {
		String attr_name = attr.getLocalName();
			
		if (attr_name.equals(VALUE)) {
			value = attr.getValue();
		} else if (attr_name.equals(SELECT)) {
			select = attr.getValue();
		} else if (attr_name.equals(LEVEL)) {
			level = attr.getValue();
		} else if (attr_name.equals(COUNT)) {
			count = attr.getValue();
		} else if (attr_name.equals(FROM)) {
			from = attr.getValue();
		} else if (attr_name.equals(FORMAT)) {
			format = attr.getValue();
		} else if (attr_name.equals(LANG)) {
			lang = attr.getValue();
		} else if (attr_name.equals(LETTER_VALUE)) {
			letter_value = attr.getValue();
		} else if (attr_name.equals(ORDINAL)) {
			ordinal = attr.getValue();
		} else if (attr_name.equals(GROUPING_SEPARATOR)) {
			grouping_separator = attr.getValue();
		} else if (attr_name.equals(GROUPING_SIZE)) {
			grouping_size = attr.getValue();
		}
	}
	
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
    	throw new RuntimeException("eval(Sequence contextSequence, Item contextItem) at "+this.getClass());
	}

	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("<xsl:number");

        if (value != null) {
        	dumper.display(" value = ");
        	dumper.display(value);
        }
        if (select != null) {
        	dumper.display(" select = ");
        	dumper.display(select);
        }
        if (level != null) {
        	dumper.display(" level = ");
        	dumper.display(level);
        }
        if (count != null) {
        	dumper.display(" count = ");
        	dumper.display(count);
        }
        if (from != null) {
        	dumper.display(" from = ");
        	dumper.display(from);
        }
        if (format != null) {
        	dumper.display(" format = ");
        	dumper.display(format);
        }
        if (lang != null) {
        	dumper.display(" lang = ");
        	dumper.display(lang);
        }
        if (letter_value != null) {
        	dumper.display(" letter_value = ");
        	dumper.display(letter_value);
        }
        if (ordinal != null) {
        	dumper.display(" ordinal = ");
        	dumper.display(ordinal);
        }
        if (grouping_separator != null) {
        	dumper.display(" grouping_separator = ");
        	dumper.display(grouping_separator);
        }
        if (grouping_size != null) {
        	dumper.display(" grouping_size = ");
        	dumper.display(grouping_size);
        }

        super.dump(dumper);

        dumper.display("</xsl:number>");
    }
    
    public String toString() {
    	StringBuilder result = new StringBuilder();
    	result.append("<xsl:number");
        
    	if (value != null)
        	result.append(" value = "+value.toString());    
    	if (select != null)
        	result.append(" select = "+select.toString());    
    	if (level != null)
        	result.append(" level = "+level.toString());    
    	if (count != null)
        	result.append(" count = "+count.toString());    
    	if (from != null)
        	result.append(" from = "+from.toString());    
    	if (format != null)
        	result.append(" format = "+format.toString());    
    	if (lang != null)
        	result.append(" lang = "+lang.toString());    
    	if (letter_value != null)
        	result.append(" letter_value = "+letter_value.toString());    
    	if (ordinal != null)
        	result.append(" ordinal = "+ordinal.toString());    
    	if (grouping_separator != null)
        	result.append(" grouping_separator = "+grouping_separator.toString());    
    	if (grouping_size != null)
        	result.append(" grouping_size = "+grouping_size.toString());    

        result.append("> ");
        
        result.append(super.toString());

        result.append("</xsl:number> ");
        return result.toString();
    }    
}
