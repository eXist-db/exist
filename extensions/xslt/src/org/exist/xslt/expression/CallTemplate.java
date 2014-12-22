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

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.exist.dom.QName;
import org.exist.interpreter.ContextAtExist;
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.Expression;
import org.exist.xquery.LocalVariable;
import org.exist.xquery.TextConstructor;
import org.exist.xquery.XPathException;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xslt.ErrorCodes;
import org.exist.xslt.XSLContext;
import org.exist.xslt.XSLExceptions;
import org.exist.xslt.XSLStylesheet;
import org.w3c.dom.Attr;

/**
 * <!-- Category: instruction -->
 * <xsl:call-template
 *   name = qname>
 *   <!-- Content: xsl:with-param* -->
 * </xsl:call-template>
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class CallTemplate extends SimpleConstructor {

	private QName name = null;
	
	private Map<String, WithParam> params = new HashMap<String, WithParam>();  
	
	public CallTemplate(XSLContext context) {
		super(context);
	}
	
	public void setToDefaults() {
		name = null;
	}

	public void prepareAttribute(ContextAtExist context, Attr attr) throws XPathException {
		String attr_name = attr.getNodeName();
		if (attr_name.equals(NAME)) {
			name = new QName(attr.getValue());
		} else if (attr_name.equals(XML_SPACE)) {
			;//TODO: code
		} else {
			//XXX: is it correct error code?
			compileError(ErrorCodes.XTSE0090, "attribute '"+attr_name+"' not allowed");
		}
	}
	
	public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
		super.analyze(contextInfo);

		for (Expression expr : steps) {
			if (expr instanceof WithParam) {
				WithParam param = (WithParam) expr;
				
				QName name = param.getName();
				String key = name.getNamespaceURI() + name.getLocalPart();
				
				if (params.containsKey(key))
					compileError(XSLExceptions.ERR_XTSE0670);

				params.put(key, param);
			} else if (expr instanceof TextConstructor) {
				;//ignore text elements
			} else {
				throw new XPathException("not permited element '"+expr+"'."); //TODO: error?
			} 
		}
	}

	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
//		System.out.println("enter "+toString());//+" : contextItem = "+contextItem.toString());//TODO: remove
		
        // Save the local variable stack
        LocalVariable mark = context.markLocalVariables(false);

		Sequence value = null;
		for (Entry<String, WithParam> entry : params.entrySet()) {
			value = entry.getValue().eval(contextSequence, contextItem);

            // Declare the iteration variable
            LocalVariable var = new LocalVariable(entry.getValue().getName());
//            var.setSequenceType(sequenceType);
            context.declareVariableBinding(var);
            var.setValue(value);
		}
		
		XSLStylesheet xslt = getXSLContext().getXSLStylesheet();

		Sequence result = xslt.template(name, contextSequence, contextItem);

		context.popLocalVariables(mark);
		
		return result;
    }

	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("<xsl:call-template");
        
        if (name != null)
        	dumper.display(" name = "+name);

        dumper.display("> ");

        super.dump(dumper);

        dumper.display("</xsl:call-template>");
    }
    
    public String toString() {
    	StringBuilder result = new StringBuilder();
    	result.append("<xsl:call-template");
        
    	if (name != null)
        	result.append(" name = "+name.toString());    

        result.append("> ");    

        result.append(super.toString());    

        result.append("</xsl:call-template>");
        return result.toString();
    }    
}
