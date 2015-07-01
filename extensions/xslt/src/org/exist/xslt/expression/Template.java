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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.Expression;
import org.exist.xquery.LocationStep;
import org.exist.xquery.NodeTest;
import org.exist.xquery.TextConstructor;
import org.exist.xquery.TypeTest;
import org.exist.xquery.Variable;
import org.exist.xquery.XPathException;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xslt.ErrorCodes;
import org.exist.xslt.XSLContext;
import org.exist.dom.QName;
import org.exist.interpreter.ContextAtExist;
import org.exist.xslt.expression.i.Parameted;
import org.exist.xslt.pattern.Pattern;
import org.exist.xslt.XSLExceptions;
import org.w3c.dom.Attr;

/**
 * <!-- Category: declaration -->
 * <xsl:template
 * 	match? = pattern
 * 	name? = qname
 * 	priority? = number
 * 	mode? = tokens
 * 	as? = sequence-type>
 * 	<!-- Content: (xsl:param*, sequence-constructor) -->
 * </xsl:template>
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Template extends Declaration implements Parameted, Comparable<Template> {

    private String attr_match = null;
    private String attr_priority = null;

    private XSLPathExpr match = null;
    private QName name = null;
    private QName[] mode = null;
    private Double priority = null;
    private String as = null;
    
    private Map<QName, org.exist.xquery.Variable> params = null; 

    public Template(XSLContext context) {
		super(context);
	}

	public void setToDefaults() {
	    attr_match = null;
	    attr_priority = null;
	    
	    match = null;
	    name = null;
	    mode = null;
	    priority = 0.5;//UNDERSTAND: what should be default
	    as = "item()*";
	}

	public void prepareAttribute(ContextAtExist context, Attr attr) throws XPathException {
		String attr_name = attr.getLocalName();
			
		if (attr_name.equals(MATCH)) {
			attr_match = attr.getValue();
		} else if (attr_name.equals(NAME)) {
		    name = new QName(attr.getValue());
		} else if (attr_name.equals(PRIORITY)) {
			attr_priority = attr.getValue();
		} else if (attr_name.equals(MODE)) {
//			mode = attr.getValue();//TODO: write
		} else if (attr_name.equals(AS)) {
		    as = attr.getValue();
		}
	}
	
//    public void add(SimpleConstructor s) {
//    	if (s instanceof Text) {
//			return; //ignore text nodes
//		}
//        steps.add(s);
//    }
//

	public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
		if (isRootMatch())
			contextInfo.addFlag(DOT_TEST);
		
		super.analyze(contextInfo);

    	if (attr_match != null) {
		    match = new XSLPathExpr(getXSLContext());
		    Pattern.parse(contextInfo.getContext(), attr_match, match);

			_check_(match);
    	}
    	if (attr_priority != null)
    		try {
    			priority = Double.valueOf(attr_priority);
    		} catch (NumberFormatException e) {
    			compileError(XSLExceptions.ERR_XTSE0530);
    		}
    	else 
    		priority = computedPriority();
		
		setUseStaticContext(true);
	}

	public void validate() throws XPathException {
		boolean canBeParam = true;
		for (int pos = 0; pos < this.getLength(); pos++) {
			Expression expr = this.getExpression(pos);
			
			if (expr instanceof TextConstructor) {
				continue;//ignore text elements
			}

			//validate instruction order
			if (expr instanceof Param) {
				if (!canBeParam) {
					compileError(ErrorCodes.XTSE0010, "<xsl:param> must be before any other tag");
				}
			} else 
				canBeParam = false;
			
			//validate sub-instructions
			if (expr instanceof XSLPathExpr) {
				XSLPathExpr xsl = (XSLPathExpr) expr;
				xsl.validate();
			}
		}
	}
	
	private double computedPriority() {
		double priority = 0.5;
		if (match != null)
			if (match.getLength() > 0) {
				Expression expr = match.getExpression(0);
				if (expr instanceof LocationStep) {
					LocationStep locationStep = (LocationStep) expr;
					NodeTest test = locationStep.getTest();
					if ((test.getName() == null) 
							|| (test.getName().getLocalPart() == null))
						priority = -0.5;
					else if (locationStep.getPredicates().size() > 0)
						priority = 0.25;
					else
						priority = 0;
					//TODO: else (element(E,T) 0.25 (matches by name and type) ...)
				}
			}
		return priority;
	}
	
	public boolean isSmallWildcard() {
		if (match != null)
			if (match.getLength() > 0) {
				Expression expr = match.getExpression(0);
				if (expr instanceof LocationStep) {
					LocationStep locationStep = (LocationStep) expr;
					NodeTest test = locationStep.getTest();
					if (test instanceof TypeTest) {
						if (test.getName() == null)
						return true;
					}
				}
			}
		return false;
	}
	
//	private void _check_(PathExpr path) {
//		for (int pos = 0; pos < path.getLength(); pos++) {
//			Expression expr = path.getExpression(pos);
//			if ((pos == 0) && (expr instanceof LocationStep)) {
//				LocationStep location = (LocationStep) expr;
//				if (location.getAxis() == Constants.CHILD_AXIS) {
//					location.setAxis(Constants.SELF_AXIS);
//				}
//			} else if (expr instanceof PathExpr) {
//				_check_((PathExpr) expr);
//			}
//		}
//	}

	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
    	context.pushDocumentContext();
    	try {
			return super.eval(contextSequence, contextItem);
    	} finally {
    		context.popDocumentContext();
    	}
	}
//	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
//		Sequence result = new ValueSequence();
//		
//		if ((contextItem == null) && (isSmallWildcard()))
//			return result; //UNDERSTAND: is it ok??? maybe better null or check at XSLComp 
//		
////		if ((contextSequence == null) && (isBigWildcard()))
////			return result; //UNDERSTAND: is it ok??? maybe better null or check at XSLComp 
//
//		Sequence matched = match.eval(contextSequence, contextItem);
//		for (Item item : matched) {
//			Sequence answer = super.eval(item.toSequence(), item);//item
//			result.addAll(answer);
//		}
//		
//		return result;
//	}
	
	public int compareTo(Template template) {
		if (priority == null)
			throw new RuntimeException("Priority can't be null.");
		if (template.priority == null)
			throw new RuntimeException("Priority can't be null.");

		//-compareTo  to make order from high to low
		int compared = priority.compareTo(template.priority);
		if (compared == 0) {
			int thisVal = getExpressionId();
			int anotherVal = template.getExpressionId();
			return (thisVal<anotherVal ? +1 : (thisVal==anotherVal ? 0 : -1));
		}
		return -compared;
	}

	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("<xsl:template");
        if (match != null) {
        	dumper.display(" match = ");
        	match.dump(dumper);
        }
        if (name != null)
        	dumper.display(" name = "+name);
        if (mode != null)
        	dumper.display(" mode = " + Arrays.toString(mode));
        if (attr_priority != null)
        	dumper.display(" priority = "+attr_priority);
        if (as != null)
        	dumper.display(" as = "+as);

        dumper.display("> ");
        
        super.dump(dumper);

        dumper.display("</xsl:template>");
    }
    
    public String toString() {
    	StringBuilder result = new StringBuilder();
    	result.append("<xsl:template");
        
    	if (match != null)
        	result.append(" match = "+match.toString());    
        if (name != null)
        	result.append(" name = "+name.getStringValue());    
        if (mode != null)
        	result.append(" mode = " + Arrays.toString(mode));
        if (attr_priority != null)
        	result.append(" priority = "+attr_priority);    
        if (as != null)
        	result.append(" as = "+as);    

        result.append("> ");
        
//        result.append(super.toString());

//        result.append("</xsl:template> ");
        return result.toString();
    }

	public boolean matched(Sequence contextSequence, Item item) throws XPathException {
		if (match == null)
			return false;
		
		boolean matched = false;
		for (int i = match.getLength()-1; i >= 0; i--) {
			Expression expr = match.getExpression(i);
			if (!expr.match(contextSequence, item))
				return false;
			if (expr instanceof LocationStep) {
				
				item = (Item)((NodeValue)item).getNode().getParentNode();
			}
			matched = true;
		}
		return matched;
	}
	
	public boolean isRootMatch() {
		return ("/".equals(attr_match)); 
	}
	
	public boolean isPrioritySet() {
		return attr_priority != null;
	}

	public double getPriority() {
		return priority;
	}

    public Map<QName, org.exist.xquery.Variable> getXSLParams() {
    	if (params == null)
    		params = new HashMap<QName, org.exist.xquery.Variable>();
    	
    	return params;
    }

    /* (non-Javadoc)
	 * @see org.exist.xslt.expression.i.Parameted#addXSLParam(org.exist.xslt.expression.Param)
	 */
	public void addXSLParam(Param param) throws XPathException {
		Map<QName, org.exist.xquery.Variable> params = getXSLParams();
		
		if (params.containsKey(param.getName()))
			compileError(XSLExceptions.ERR_XTSE0580);
		
		 Variable variable = context.declareVariable(param.getName(), param);
		 params.put(param.getName(), variable);
	}
	
	public QName getName() {
		return name;
	}
	
	/**
	 * @deprecated Use {@link #process(XSLContext,SequenceIterator)} instead
	 */
	public void process(SequenceIterator sequenceIterator, XSLContext context) {
		process(context, sequenceIterator);
	}

	public void process(XSLContext context, SequenceIterator sequenceIterator) {
		super.process(context, sequenceIterator);
	}
}
