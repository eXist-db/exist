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

import java.util.ArrayList;
import java.util.List;

import org.exist.interpreter.ContextAtExist;
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.Expression;
import org.exist.xquery.TextConstructor;
import org.exist.xquery.XPathException;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xslt.XSLContext;
import org.w3c.dom.Attr;

/**
 * <!-- Category: instruction -->
 * <xsl:choose>
 *   <!-- Content: (xsl:when+, xsl:otherwise?) -->
 * </xsl:choose>
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Choose extends SimpleConstructor {
	
	private List<When> whens = new ArrayList<When>();
	private Otherwise otherwise = null;

	public Choose(XSLContext context) {
		super(context);
	}
	
	public void setToDefaults() {
		whens = new ArrayList<When>();
		otherwise = null;
	}    

	public void prepareAttribute(ContextAtExist context, Attr attr) throws XPathException {
	}
	
	public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
    	super.analyze(contextInfo);

		for (Expression expr : steps) {
			if (expr instanceof TextConstructor) {
				;//ignore text elements
			} else if (expr instanceof When) {
				whens.add((When) expr);
			} else if (expr instanceof Otherwise) {
				otherwise = (Otherwise) expr;//TODO: check for double
			} else {
				throw new XPathException("not permited element."); //TODO: error?
			} 
		}
    }

	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
		for (When when : whens) {
			if (when.test(contextSequence, contextItem)) {
				return when.eval(contextSequence, contextItem);
			}
		}
		if (otherwise != null)
			return otherwise.eval(contextSequence, contextItem);
			
    	return Sequence.EMPTY_SEQUENCE;
	}

	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("<xsl:choose");
        dumper.display("> ");

        super.dump(dumper);

        dumper.display("</xsl:choose>");
    }
    
    public String toString() {
    	StringBuilder result = new StringBuilder();
    	result.append("<xsl:choose");
        result.append("> ");    

        result.append(super.toString());    

        result.append("</xsl:choose>");
        return result.toString();
    }

}
