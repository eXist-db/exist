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
import org.exist.xquery.Constants;
import org.exist.xquery.Dependency;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xslt.ErrorCodes;
import org.exist.xslt.XSLContext;
import org.exist.xslt.pattern.Pattern;
import org.w3c.dom.Attr;

/**
 * <!-- Category: instruction -->
 * <xsl:comment
 *   select? = expression>
 *   <!-- Content: sequence-constructor -->
 * </xsl:comment>
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Comment extends SimpleConstructor {
	
	private String attr_select = null;

	private XSLPathExpr select = null;
	
    public Comment(XSLContext context) {
		super(context);
	}
	
	public void setToDefaults() {
		attr_select = null;
		
		select = null;
	}

	public void prepareAttribute(ContextAtExist context, Attr attr) throws XPathException {
		String attr_name = attr.getNodeName();
		if (attr_name.equals(SELECT)) {
			attr_select = attr.getValue();
		}
	}
	
	
	public void validate() throws XPathException {
		if (attr_select != null && this.getLength() > 0)
			compileError(ErrorCodes.XTSE0940, "");
		
		super.validate();
	}
	
	public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
		if (attr_select != null) {
			select = new XSLPathExpr(getXSLContext());
			Pattern.parse(contextInfo.getContext(), attr_select, select);
		}
			
		super.analyze(contextInfo);
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

//        if (newDocumentContext)
//            context.pushDocumentContext();
        
        Sequence result;
        try {
        	Sequence contentSeq;
        	if (select != null)
            	contentSeq = select.eval(contextSequence, contextItem);
        	else
        		contentSeq = super.eval(contextSequence, contextItem);

            if(contentSeq.isEmpty())
            result = Sequence.EMPTY_SEQUENCE;
            else {
                MemTreeBuilder builder = context.getDocumentBuilder();
                context.proceed(this, builder);

                StringBuilder buf = new StringBuilder();
                for(SequenceIterator i = contentSeq.iterate(); i.hasNext(); ) {
                    context.proceed(this, builder);
                    Item next = i.nextItem();
                    if(buf.length() > 0)
                        buf.append(' ');
                    buf.append(next.getStringValue());
                }

                if (buf.indexOf("--") != Constants.STRING_NOT_FOUND|| buf.toString().endsWith("-")) {
                    throw new XPathException(this, ErrorCodes.XQDY0072, "'" + buf.toString() + "' is not a valid comment");
                }

                int nodeNr = builder.comment(buf.toString());
                result = builder.getDocument().getNode(nodeNr);
            }
        } finally {
//            if (newDocumentContext)
//                context.popDocumentContext();
        }

        if (context.getProfiler().isEnabled())           
            context.getProfiler().end(this, "", result);  
        
        return result;
	}

	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("<xsl:comment");
        
        if (select != null) {
        	dumper.display(" select = ");
        	select.dump(dumper);
        }

        dumper.display("> ");

        super.dump(dumper);

        dumper.display("</xsl:comment>");
    }
    
    public String toString() {
    	StringBuilder result = new StringBuilder();
    	result.append("<xsl:comment");
        
    	if (select != null)
        	result.append(" select = "+select.toString());    

        result.append("> ");    

        result.append(super.toString());    

        result.append("</xsl:comment>");
        return result.toString();
    }    
}
