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
import org.exist.xquery.PathExpr;
import org.exist.xquery.XPathException;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
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

	private PathExpr select = null;
	private PathExpr group_by = null;
	private PathExpr group_adjacent = null;
	private PathExpr group_starting_with = null;
	private PathExpr group_ending_with = null;
	private String collation = null;
	
	public ForEachGroup(XSLContext context) {
		super(context);
	}
	
	public void setToDefaults() {
		select = null;
		group_by = null;
		group_adjacent = null;
		group_starting_with = null;
		group_ending_with = null;
		collation = null;
	}

	public void prepareAttribute(ContextAtExist context, Attr attr) throws XPathException {
		String attr_name = attr.getNodeName();
		if (attr_name.equals(SELECT)) {
			select = new PathExpr(getContext());
			Pattern.parse(context, attr.getValue(), select);
			
		} else if (attr_name.equals(GROUP_BY)) {
			group_by = new PathExpr(getContext());
			Pattern.parse(context, attr.getValue(), group_by);
			
		} else if (attr_name.equals(GROUP_ADJACENT)) {
			group_adjacent = new PathExpr(getContext());
			Pattern.parse(context, attr.getValue(), group_adjacent);
			
		} else if (attr_name.equals(GROUP_STARTING_WITH)) {
			group_starting_with = new PathExpr(getContext());
			Pattern.parse(context, attr.getValue(), group_starting_with);
			
		} else if (attr_name.equals(GROUP_ENDING_WITH)) {
			group_ending_with = new PathExpr(getContext());
			Pattern.parse(context, attr.getValue(), group_ending_with);
			
		} else if (attr_name.equals(COLLATION)) {
			collation = attr.getValue();
		}
	}
	
    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
    	throw new RuntimeException("eval(Sequence contextSequence, Item contextItem) at "+this.getClass());
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
        if (collation != null)
        	dumper.display(" collation = "+collation);

        dumper.display("> ");

        super.dump(dumper);

        dumper.display("</xsl:for-each-group>");
    }
    
    public String toString() {
    	StringBuffer result = new StringBuffer();
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
    	if (collation != null)
        	result.append(" collation = "+collation.toString());    

        result.append("> ");    

        result.append(super.toString());    

        result.append("</xsl:for-each-group>");
        return result.toString();
    }    
}
