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
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.AnyNodeTest;
import org.exist.xquery.Constants;
import org.exist.xquery.Expression;
import org.exist.xquery.LocalVariable;
import org.exist.xquery.LocationStep;
import org.exist.xquery.TextConstructor;
import org.exist.xquery.XPathException;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.ValueSequence;
import org.exist.xslt.StAXSequenceIterator;
import org.exist.xslt.XSLContext;
import org.exist.xslt.XSLStylesheet;
import org.exist.xslt.pattern.Pattern;
import org.w3c.dom.Attr;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

/**
 * <!-- Category: instruction -->
 * <xsl:apply-templates
 *   select? = expression
 *   mode? = token>
 *   <!-- Content: (xsl:sort | xsl:with-param)* -->
 * </xsl:apply-templates>
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class ApplyTemplates extends SimpleConstructor {

	private String attr_select = null; 
	
	private XSLPathExpr select = null;
	private String mode = null;

	private LocationStep anyChild;

	public ApplyTemplates(XSLContext context) {
		super(context);

		anyChild = new LocationStep(getContext(), Constants.CHILD_AXIS, new AnyNodeTest());
	}
	
	public void setToDefaults() {
		select = null;
		mode = null;
	}

	public void prepareAttribute(ContextAtExist context, Attr attr) throws XPathException {
		String attr_name = attr.getNodeName();
		if (attr_name.equals(SELECT)) {
			attr_select = attr.getValue();
		} else if (attr_name.equals(MODE)) {
			mode = attr.getValue();
		}
	}
	
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
    	super.analyze(contextInfo);

    	if (attr_select != null) {
    		select = new XSLPathExpr(getXSLContext());
    		Pattern.parse(contextInfo.getContext(), attr_select, select);
    		
    		_check_(select);
    	}
    }
	
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {

		Sequence result = new ValueSequence();
    	
        context.pushDocumentContext();

        LocalVariable mark = context.markLocalVariables(false);
        try {
			Sequence selected;
	    	if (select == null) {
	    		selected = anyChild.eval(contextSequence, contextItem);
	    	} else {
	    		selected = select.eval(contextSequence, contextItem);
	    	}
	    	
	    	for (Expression expr : steps) {
	    		if (expr instanceof Sort) {
					Sort sort = (Sort) expr;
					selected = sort.eval(selected, null);
	    		} else if (expr instanceof TextConstructor) {
	    			//ignore text elements
	    		} else if (expr instanceof WithParam) {
	    			WithParam param = (WithParam)expr;
	    			context.declareVariable(param.getName(), param);
				} else {
					throw new XPathException("not suported "+expr);//TODO: error?
				}
	    	}
	    		
	    	XSLStylesheet xslt = getXSLContext().getXSLStylesheet();
	
			int pos = 0;
	//    	for (Item item : selected) {
	        for (SequenceIterator iterInner = selected.iterate(); iterInner.hasNext();) {
	            Item item = iterInner.nextItem();   
	    		
	        	context.setContextSequencePosition(pos, selected);
	    		Sequence res = xslt.templates(selected, item);
	    		if (res == null) {
	            	if (item instanceof Text) {
	                    MemTreeBuilder builder = context.getDocumentBuilder();
	            		builder.characters(item.getStringValue());
	            		result.add(item);
	            	} else if (item instanceof Node || item instanceof NodeValue) {
	            		res = eval(selected, item); //item.toSequence();//
	            	} else
	            		throw new XPathException("not supported item type");
	    		}
	    		result.addAll(res);
	    		pos++;
	    	}
	    	
	    	return result;
        } finally {
            context.popLocalVariables(mark);
        	context.popDocumentContext();
        }
	}
	
//	public Sequence builtInTemplateRule(Item contextItem) throws XPathException {
//		Sequence result = new ValueSequence();
//		
//		Sequence contextSequence = anyChild.eval(contextItem.toSequence(), contextItem);
//		
//		context.pushInScopeNamespaces();
//        try {
//            MemTreeBuilder builder = context.getDocumentBuilder();
//            for (Item item : contextSequence) {
//            	if (item instanceof Text) {
//            		builder.characters(item.getStringValue());
//            		result.add(item);
//				} else if (item instanceof Node) {
//					Sequence res = eval(item.toSequence(), item);
//					result.addAll(res);
//				}
//            }
//        } finally {
//            context.popInScopeNamespaces();
//        }
//		
//		return result;
//	}

	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("<xsl:apply-templates");
        
        if (select != null) {
        	dumper.display(" select = ");
        	select.dump(dumper);
        }
        if (mode != null)
        	dumper.display(" mode = "+mode);

        dumper.display("> ");

        super.dump(dumper);

        dumper.display("</xsl:apply-templates>");
    }
    
    public String toString() {
    	StringBuilder result = new StringBuilder();
    	result.append("<xsl:apply-templates");
        
    	if (select != null)
        	result.append(" select = "+select.toString());    
        if (mode != null)
        	result.append(" mode = "+mode.toString());    

        result.append("> ");    

        result.append(super.toString());    

        result.append("</xsl:apply-templates>");
        return result.toString();
    }

	public static void searchAndProcess(SequenceIterator sequenceIterator, XSLContext context) {
//
//		if (sequenceIterator.hasNext()) {
//		}
	}    
}
