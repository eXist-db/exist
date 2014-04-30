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

import org.exist.dom.QName;
import org.exist.interpreter.ContextAtExist;
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.Dependency;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xslt.XSLContext;
import org.exist.xslt.pattern.Pattern;
import org.w3c.dom.Attr;

/**
 * <!-- Category: declaration -->
 * <!-- Category: instruction -->
 * <xsl:variable
 *   name = qname
 *   select? = expression
 *   as? = sequence-type>
 *   <!-- Content: sequence-constructor -->
 * </xsl:variable>
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Variable extends XSLPathExpr {

	private String attr_select = null;

	private QName name = null;
	private XSLPathExpr select = null;
	private String as = null;
	
	private org.exist.xquery.Variable variable = null;
	
	public Variable(XSLContext context) {
		super(context);
	}
	
	public void setToDefaults() {
		attr_select = null;
		
		name = null;
		select = null;
		as = null;
		
		variable = null;
	}

	public void prepareAttribute(ContextAtExist context, Attr attr) throws XPathException {
		String attr_name = attr.getNodeName();
		if (attr_name.equals(NAME)) {
			name = new QName(attr.getValue());
		} else if (attr_name.equals(SELECT)) {
			attr_select = attr.getValue();
		} else if (attr_name.equals(as)) {
			as = attr.getValue();
		}
	}

	public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
		variable = context.declareVariable(name, this);
		
    	super.analyze(contextInfo);

    	if (attr_select != null) {
    		select = new XSLPathExpr(getXSLContext());
		    Pattern.parse(contextInfo.getContext(), attr_select, select);

			_check_(select);
			
    	}
    }
	
    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
    	if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            if (contextItem != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
        }

    	Sequence in = null;
    	
        // evaluate the expression
		context.pushDocumentContext();
        try {
//        	System.out.println("=================================================================");
//        	System.out.println("select = "+select);
//        	System.out.println("contextSequence = "+contextSequence);
//        	System.out.println("contextItem     = "+contextItem);
        	if (select != null)
        		in = select.eval(contextSequence, contextItem);
        	else {
        		in = super.eval(contextSequence, contextItem);
        	}

        	variable.setValue(in);
        	variable.checkType();

        } finally {
            context.popDocumentContext();
        }
        
        
		if (context.getProfiler().isEnabled())           
			context.getProfiler().end(this, "", in);              
           
		return Sequence.EMPTY_SEQUENCE;
	}

	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("<xsl:variable");
        
        if (name != null)
        	dumper.display(" name = "+name);
        if (select != null) {
        	dumper.display(" select = ");
        	select.dump(dumper);
        }
        if (as != null)
        	dumper.display(" as = "+as);

        dumper.display("> ");

        super.dump(dumper);

        dumper.display("</xsl:variable>");
    }
    
    public String toString() {
    	StringBuilder result = new StringBuilder();
    	result.append("<xsl:variable");
        
        if (name != null)
        	result.append(" name = "+name.getStringValue());    
    	if (select != null)
        	result.append(" select = "+select.toString());    
        if (as != null)
        	result.append(" as = "+as);    

        result.append("> ");    

        result.append(super.toString());    

        result.append("</xsl:variable>");
        return result.toString();
    }    
}
