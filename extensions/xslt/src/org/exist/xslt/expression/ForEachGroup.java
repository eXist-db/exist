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

import java.text.Collator;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.TreeSet;

import org.exist.interpreter.ContextAtExist;
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.Expression;
import org.exist.xquery.PathExpr;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.fn.FunDistinctValues.ValueComparator;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NumericValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;
import org.exist.xslt.ErrorCodes;
import org.exist.xslt.XSLContext;
import org.exist.xslt.pattern.Pattern;
import org.w3c.dom.Attr;

/**
 * <!-- Category: instruction -->
 * <xsl:for-each-group
 *   select = expression
 *   group-by? = expression
 *   group-adjacent? = expression
 *   group-starting-with? = pattern
 *   group-ending-with? = pattern
 *   collation? = { uri }>
 *   <!-- Content: (xsl:sort*, sequence-constructor) -->
 * </xsl:for-each-group>
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class ForEachGroup extends SimpleConstructor {

	private String attr_select = null;
	private String attr_group_by = null;
	private String attr_group_adjacent = null;
	private String attr_group_starting_with = null;
	private String attr_group_ending_with = null;
	private String attr_collation = null;

	private XSLPathExpr select = null;
	private XSLPathExpr group_by = null;
	private PathExpr group_adjacent = null;
	private PathExpr group_starting_with = null;
	private PathExpr group_ending_with = null;
	private XSLPathExpr collator = null;
	
	public ForEachGroup(XSLContext context) {
		super(context);
	}
	
	public void setToDefaults() {
		attr_select = null;
		attr_group_by = null;
		attr_group_adjacent = null;
		attr_group_starting_with = null;
		attr_group_ending_with = null;
		attr_collation = null;
		
		select = null;
		group_by = null;
		group_adjacent = null;
		group_starting_with = null;
		group_ending_with = null;
		collator = null;
	}

	public void prepareAttribute(ContextAtExist context, Attr attr) throws XPathException {
		String attr_name = attr.getNodeName();
		if (attr_name.equals(SELECT)) {
			attr_select = attr.getValue();
			
		} else if (attr_name.equals(GROUP_BY)) {
			attr_group_by = attr.getValue();
			
		} else if (attr_name.equals(GROUP_ADJACENT)) {
			attr_group_adjacent = attr.getValue();
			
		} else if (attr_name.equals(GROUP_STARTING_WITH)) {
			attr_group_starting_with = attr.getValue();
			
		} else if (attr_name.equals(GROUP_ENDING_WITH)) {
			attr_group_ending_with = attr.getValue();
			
		} else if (attr_name.equals(COLLATION)) {
			attr_collation = attr.getValue();
		}
	}

    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
    	boolean atRootCall = false;
    	
    	if (attr_collation != null) {
    		if (attr_group_by == null && attr_group_adjacent == null)
    			throw new XPathException(this, ErrorCodes.XTSE1090, "");
    		
    		if (attr_collation.startsWith("{") && attr_collation.endsWith("}")) {
    			collator = new XSLPathExpr(getXSLContext());
    			Pattern.parse(contextInfo.getContext(), 
    					attr_collation.substring(1, attr_collation.length() - 1), 
    					collator);
    		}
    	}
    	
    	if (attr_select != null) {
			select = new XSLPathExpr(getXSLContext());
			Pattern.parse(contextInfo.getContext(), attr_select, select);
			
			if ((contextInfo.getFlags() & DOT_TEST) != 0) {
				atRootCall = true;
				_check_(select);
				contextInfo.removeFlag(DOT_TEST);
			}
    	}

    	if (attr_group_by != null) {
    		group_by = new XSLPathExpr(getXSLContext());
			Pattern.parse(contextInfo.getContext(), attr_group_by, group_by);
			
			if ((contextInfo.getFlags() & DOT_TEST) != 0) {
				atRootCall = true;
				_check_(group_by);
				contextInfo.removeFlag(DOT_TEST);
			}
    	}
    	
    	super.analyze(contextInfo);
    	
    	if (atRootCall)
    		contextInfo.addFlag(DOT_TEST);
    }
	
    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
		Sequence result = new ValueSequence();
    	
    	Sequence selected = select.eval(contextSequence, contextItem);
    	
		Collator collator = getCollator(contextSequence, contextItem, 2);		
		TreeMap<AtomicValue, Sequence> map = new TreeMap<AtomicValue, Sequence>(new ValueComparator(collator));

		Item item;
		AtomicValue value;
		NumericValue firstNaN = null;
		for (SequenceIterator i = selected.iterate(); i.hasNext();) {
			item = i.nextItem();
			value = group_by.eval(selected, item).itemAt(0).atomize(); //UNDERSTAND: is it correct?
			if (!map.containsKey(value)) {
				if (Type.subTypeOf(value.getType(), Type.NUMBER)) {
					if (((NumericValue)value).isNaN()) {
						//although NaN does not equal itself, if $arg contains multiple NaN values a single NaN is returned.
						if (firstNaN == null) {
							Sequence seq = new ValueSequence();
							seq.add(item);
							map.put(value, seq);
							firstNaN = (NumericValue)value;
						} else {
							Sequence seq = map.get(firstNaN);
							seq.add(item);
						}
						continue;
					}
				}
				Sequence seq = new ValueSequence();
				seq.add(item);
				map.put(value, seq);
			} else {
				Sequence seq = map.get(value);
				seq.add(item);
			}
		}

		for (Entry<AtomicValue, Sequence> entry : map.entrySet()) {
			for (SequenceIterator iterInner = entry.getValue().iterate(); iterInner.hasNext();) {
	            Item each = iterInner.nextItem();   

	            //Sequence seq = childNodes.eval(contextSequence, each);
	    		Sequence answer = super.eval(contextSequence, each);
	    		result.addAll(answer);
	    	}
		}
    	
    	return result;
	}

	protected Collator getCollator(Sequence contextSequence, Item contextItem, int arg) throws XPathException {
		if(attr_collation != null) {
			String collationURI = attr_collation;

			if (collator != null)
				collationURI = collator.eval(contextSequence, contextItem).getStringValue();

			return context.getCollator(collationURI);
		} else
			return context.getDefaultCollator();
	}

	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("<xsl:for-each-group");
        
        if (select != null) {
        	dumper.display(" select = ");
        	select.dump(dumper);
        }
        if (group_by != null) {
        	dumper.display(" group_by = ");
        	group_by.dump(dumper);
        }
        if (group_adjacent != null) {
        	dumper.display(" group_adjacent = ");
        	group_adjacent.dump(dumper);
        }
        if (group_starting_with != null) {
        	dumper.display(" group_starting_with = ");
        	group_starting_with.dump(dumper);
        }
        if (group_ending_with != null) {
        	dumper.display(" group_ending_with = ");
        	group_ending_with.dump(dumper);
        }
        if (attr_collation != null)
        	dumper.display(" collation = "+attr_collation);

        dumper.display("> ");

        super.dump(dumper);

        dumper.display("</xsl:for-each-group>");
    }
    
    public String toString() {
    	StringBuilder result = new StringBuilder();
    	result.append("<xsl:for-each-group");
        
    	if (select != null)
        	result.append(" select = "+select.toString());    
    	if (group_by != null)
        	result.append(" group_by = "+group_by.toString());    
    	if (group_adjacent != null)
        	result.append(" group_adjacent = "+group_adjacent.toString());    
    	if (group_starting_with != null)
        	result.append(" group_starting_with = "+group_starting_with.toString());    
    	if (group_ending_with != null)
        	result.append(" group_ending_with = "+group_ending_with.toString());    
    	if (attr_collation != null)
        	result.append(" collation = "+attr_collation.toString());    

        result.append("> ");    

        result.append(super.toString());    

        result.append("</xsl:for-each-group>");
        return result.toString();
    }    
}
