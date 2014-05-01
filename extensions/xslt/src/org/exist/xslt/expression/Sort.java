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
import org.exist.util.FastQSort;
import org.exist.xquery.AnyNodeTest;
import org.exist.xquery.Constants;
import org.exist.xquery.LocationStep;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.ValueSequence;
import org.exist.xslt.XSLContext;
import org.exist.xslt.pattern.Pattern;
import org.w3c.dom.Attr;

/**
 * <xsl:sort
 *   select? = expression
 *   lang? = { nmtoken }
 *   order? = { "ascending" | "descending" }
 *   collation? = { uri }
 *   stable? = { "yes" | "no" }
 *   case-order? = { "upper-first" | "lower-first" }
 *   data-type? = { "text" | "number" | qname-but-not-ncname }>
 *   <!-- Content: sequence-constructor -->
 * </xsl:sort>
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Sort extends Declaration {

	class SortItem implements Comparable<SortItem> {
		
		private Item item;
		private String value;
		private int pos;
		
		public SortItem(Item item, int pos) throws XPathException {
			this.item = item;
			value = select.eval(item.toSequence(), item).getStringValue();
			this.pos = pos;
		}
		
		public Item getItem() {
			return item;
		}

		public int compareTo(SortItem o) {
			int compare = value.compareTo(o.value);
			if (compare == 0)
				return order * (pos<o.pos ? -1 : (pos==o.pos ? 0 : 1));
			return order * compare;
		}
		
	}
	
    private String attr_order = null;

    private XSLPathExpr select = null;
    private String lang = null;
    private int order = 1;//ascending
    private String collation = null;
    private String stable = null;
    private String case_order = null;
    private String data_type = null;

    public Sort(XSLContext context) {
		super(context);
	}

	public void setToDefaults() {
	    attr_order = null;

	    select = null;
	    lang = null;
	    order = 1;
	    collation = null;
	    stable = null;
	    case_order = null;
	    data_type = null;
	}

	public void prepareAttribute(ContextAtExist context, Attr attr) throws XPathException {
		String attr_name = attr.getLocalName();
			
		if (attr_name.equals(SELECT)) {
			select = new XSLPathExpr(getXSLContext());
			Pattern.parse((XQueryContext) context, attr.getValue(), select);
			
			_check_(select);
		} else if (attr_name.equals(LANG)) {
			lang = attr.getValue();
		} else if (attr_name.equals(ORDER)) {
			attr_order = attr.getValue();
			
			if (attr.getValue().equals("ascending")) 
				order = 1;
			else if (attr.getValue().equals("descending"))
				order = -1;
			else
				throw new XPathException("wrong order");//TODO: error?
		} else if (attr_name.equals(COLLATION)) {
			collation = attr.getValue();
		} else if (attr_name.equals(STABLE)) {
			stable = attr.getValue();
		} else if (attr_name.equals(CASE_ORDER)) {
			case_order = attr.getValue();
		} else if (attr_name.equals(DATA_TYPE)) {
			data_type = attr.getValue();
		}
	}
	
	public void validate() throws XPathException {
		if (select == null) {
			select = new XSLPathExpr(getXSLContext());
			select.add(new LocationStep(getContext(), Constants.SELF_AXIS, new AnyNodeTest()));
		}
		
	}
	
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
		Sequence result = null;
		
		SortItem[] items = new SortItem[contextSequence.getItemCount()];
		int i = 0;
//		for (Item item : contextSequence) {
        for (SequenceIterator iterInner = contextSequence.iterate(); iterInner.hasNext();) {
            Item item = iterInner.nextItem();   

            items[i] = new SortItem(item, i);
			i++;
		}
		
		FastQSort.sort(items, 0, contextSequence.getItemCount()-1);
		
		result = new ValueSequence();
		for (i = 0; i < items.length; i++)
			result.add(items[i].getItem());
		
		return result;
	}

	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("<xsl:sort");

        if (select != null) {
        	dumper.display(" select = ");
        	dumper.display(select);
        }
        if (lang != null) {
        	dumper.display(" lang = ");
        	dumper.display(lang);
        }
        if (attr_order != null) {
        	dumper.display(" order = ");
        	dumper.display(attr_order);
        }
        if (collation != null) {
        	dumper.display(" collation = ");
        	dumper.display(collation);
        }
        if (stable != null) {
        	dumper.display(" stable = ");
        	dumper.display(stable);
        }
        if (case_order != null) {
        	dumper.display(" case_order = ");
        	dumper.display(case_order);
        }
        if (data_type != null) {
        	dumper.display(" data_type = ");
        	dumper.display(data_type);
        }

        super.dump(dumper);

        dumper.display("</xsl:sort>");
    }
    
    public String toString() {
    	StringBuilder result = new StringBuilder();
    	result.append("<xsl:sort");
        
    	if (select != null)
        	result.append(" select = "+select.toString());    
    	if (lang != null)
        	result.append(" lang = "+lang.toString());    
    	if (attr_order != null)
        	result.append(" order = "+attr_order.toString());    
    	if (collation != null)
        	result.append(" collation = "+collation.toString());    
    	if (stable != null)
        	result.append(" stable = "+stable.toString());    
    	if (case_order != null)
        	result.append(" case_order = "+case_order.toString());    
    	if (data_type != null)
        	result.append(" data_type = "+data_type.toString());    

        result.append("> ");
        
        result.append(super.toString());

        result.append("</xsl:sort> ");
        return result.toString();
    }    
}
