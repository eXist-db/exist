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
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.AnyNodeTest;
import org.exist.xquery.Constants;
import org.exist.xquery.Expression;
import org.exist.xquery.LocationStep;
import org.exist.xquery.PathExpr;
import org.exist.xquery.XPathException;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.ValueSequence;
import org.exist.xslt.XSLContext;
import org.exist.xslt.pattern.Pattern;
import org.w3c.dom.Attr;

/**
 * <!-- Category: instruction -->
 * <xsl:for-each
 *   select = expression>
 *   <!-- Content: (xsl:sort*, sequence-constructor) -->
 * </xsl:for-each>
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class ForEach extends SimpleConstructor {

	private String attr_select = null;

	private XSLPathExpr select = null;
	private PathExpr childNodes = null;
	
	private Sort sort = null;
	
	public ForEach(XSLContext context) {
		super(context);
		
		childNodes = new PathExpr(getContext());
		childNodes.add(new LocationStep(getContext(), Constants.CHILD_AXIS, new AnyNodeTest()));

	}
	
	public void setToDefaults() {
		attr_select = null;

		select = null;
		childNodes = null;
		
		sort = null;
	}

	public void prepareAttribute(ContextAtExist context, Attr attr) throws XPathException {
		String attr_name = attr.getNodeName();
		if (attr_name.equals(SELECT)) {
			attr_select = attr.getValue();
		}
	}
	
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
    	boolean atRootCall = false;
    	
    	if (attr_select != null) {
			select = new XSLPathExpr(getXSLContext());
			Pattern.parse(contextInfo.getContext(), attr_select, select);
			
			if ((contextInfo.getFlags() & DOT_TEST) != 0) {
				atRootCall = true;
				_check_(select);
				contextInfo.removeFlag(DOT_TEST);
			}
    	}

    	for (Expression expr : steps) {
    		if (expr instanceof Sort) {
    			if (sort != null)
    				throw new XPathException("double at sort"); //TODO: error?
    			
				sort = (Sort) expr;

				steps.remove(expr);
			}
    	}

    	super.analyze(contextInfo);
    	
    	if (atRootCall)
    		contextInfo.addFlag(DOT_TEST);
    }

//	protected void _check_(PathExpr path) {
////		for (int pos = 0; pos < select.getLength(); pos++) {
//			Expression expr = select.getExpression(0);//pos
//			if (expr instanceof LocationStep) {
//				LocationStep location = (LocationStep) expr;
//				if (location.getAxis() == Constants.CHILD_AXIS) {
//					location.setAxis(Constants.SELF_AXIS);
//				}
////			} else if (expr instanceof PathExpr) {
////				_check_((PathExpr) expr);
//			}
////		}
//	}
	
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
		Sequence result = new ValueSequence();
    	
    	Sequence selected = select.eval(contextSequence, contextItem);
    	
    	if (sort != null)
    		selected = sort.eval(selected, null);
    	
//    	for (Item each : selected) {
        for (SequenceIterator iterInner = selected.iterate(); iterInner.hasNext();) {
            Item each = iterInner.nextItem();   

            //Sequence seq = childNodes.eval(contextSequence, each);
    		Sequence answer = super.eval(contextSequence, each);
    		result.addAll(answer);
    	}
    	
    	return result;
	}

	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("<xsl:for-each");
        
        if (select != null) {
        	dumper.display(" select = ");
        	select.dump(dumper);
        }

        dumper.display("> ");

        super.dump(dumper);

        dumper.display("</xsl:for-each>");
    }
    
    public String toString() {
    	StringBuilder result = new StringBuilder();
    	result.append("<xsl:for-each");
        
    	if (select != null)
        	result.append(" select = "+select.toString());    

        result.append("> ");    

        result.append(super.toString());    

        result.append("</xsl:for-each>");
        return result.toString();
    }    
}
