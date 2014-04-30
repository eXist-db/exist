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
 * <xsl:decimal-format
 *   name? = qname
 *   decimal-separator? = char
 *   grouping-separator? = char
 *   infinity? = string
 *   minus-sign? = char
 *   NaN? = string
 *   percent? = char
 *   per-mille? = char
 *   zero-digit? = char
 *   digit? = char
 *   pattern-separator? = char />
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class DecimalFormat extends Declaration {

    private String name = null;
    private String decimal_separator = null;
    private String grouping_separator = null;
    private String infinity = null;
    private String minus_sign = null;
    private String NaN = null;
    private String percent = null;
    private String per_mille = null;
    private String zero_digit = null;
    private String digit = null;
    private String pattern_separator = null;

    public DecimalFormat(XSLContext context) {
		super(context);
	}

	public void setToDefaults() {
	    name = null;
	    decimal_separator = null;
	    grouping_separator = null;
	    infinity = null;
	    minus_sign = null;
	    NaN = null;
	    percent = null;
	    per_mille = null;
	    zero_digit = null;
	    digit = null;
	    pattern_separator = null;
	}

	public void prepareAttribute(ContextAtExist context, Attr attr) throws XPathException {
		String attr_name = attr.getLocalName();
			
		if (attr_name.equals(NAME)) {
			name = attr.getValue();
		} else if (attr_name.equals(DECIMAL_SEPARATOR)) {
			decimal_separator = attr.getValue();
		} else if (attr_name.equals(GROUPING_SEPARATOR)) {
			grouping_separator = attr.getValue();
		} else if (attr_name.equals(INFINITY)) {
			infinity = attr.getValue();
		} else if (attr_name.equals(MINUS_SIGN)) {
			minus_sign = attr.getValue();
		} else if (attr_name.equals(NAN)) {
			NaN = attr.getValue();
		} else if (attr_name.equals(PERCENT)) {
			percent = attr.getValue();
		} else if (attr_name.equals(PER_MILLE)) {
			per_mille = attr.getValue();
		} else if (attr_name.equals(ZERO_DIGIT)) {
			zero_digit = attr.getValue();
		} else if (attr_name.equals(DIGIT)) {
			digit = attr.getValue();
		} else if (attr_name.equals(PATTERN_SEPARATOR)) {
			pattern_separator = attr.getValue();
		}
	}
	
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
    	throw new RuntimeException("eval(Sequence contextSequence, Item contextItem) at "+this.getClass());
	}

	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("<xsl:decimal-format");
        if (name != null) {
        	dumper.display(" name = ");
        	dumper.display(name);
        }
        if (decimal_separator != null) {
        	dumper.display(" decimal_separator = ");
        	dumper.display(decimal_separator);
        }
        if (grouping_separator != null) {
        	dumper.display(" grouping_separator = ");
        	dumper.display(grouping_separator);
        }
        if (infinity != null) {
        	dumper.display(" infinity = ");
        	dumper.display(infinity);
        }
        if (minus_sign != null) {
        	dumper.display(" minus_sign = ");
        	dumper.display(minus_sign);
        }
        if (NaN != null) {
        	dumper.display(" NaN = ");
        	dumper.display(NaN);
        }
        if (percent != null) {
        	dumper.display(" percent = ");
        	dumper.display(percent);
        }
        if (per_mille != null) {
        	dumper.display(" per_mille = ");
        	dumper.display(per_mille);
        }
        if (zero_digit != null) {
        	dumper.display(" zero_digit = ");
        	dumper.display(zero_digit);
        }
        if (digit != null) {
        	dumper.display(" digit = ");
        	dumper.display(digit);
        }
        if (pattern_separator != null) {
        	dumper.display(" pattern_separator = ");
        	dumper.display(pattern_separator);
        }

        super.dump(dumper);

        dumper.display("</xsl:decimal-format>");
    }
    
    public String toString() {
    	StringBuilder result = new StringBuilder();
    	result.append("<xsl:decimal-format");
        if (name != null) 
        	result.append(" name = "+name.toString());    
        if (decimal_separator != null)
        	result.append(" decimal-separator = "+decimal_separator.toString());    
        if (grouping_separator != null)
        	result.append(" grouping-separator = "+grouping_separator.toString());    
        if (infinity != null)
        	result.append(" infinity = "+infinity.toString());    
        if (minus_sign != null)
        	result.append(" minus-sign = "+minus_sign.toString());    
        if (NaN != null)
        	result.append(" NaN = "+NaN.toString());    
        if (percent != null)
        	result.append(" percent = "+percent.toString());    
        if (per_mille != null)
        	result.append(" per-mille = "+per_mille.toString());    
        if (zero_digit != null)
        	result.append(" zero-digit = "+zero_digit.toString());    
        if (digit != null)
        	result.append(" digit = "+digit.toString());    
        if (pattern_separator != null)
        	result.append(" pattern-separator = "+pattern_separator.toString());    
        result.append("> ");
        
        result.append(super.toString());

        result.append("</xsl:decimal-format> ");
        return result.toString();
    }    
}
