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
import org.exist.memtree.DocumentBuilderReceiver;
import org.exist.memtree.MemTreeBuilder;
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.Dependency;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;
import org.exist.xslt.XSLContext;
import org.exist.xslt.pattern.Pattern;
import org.w3c.dom.Attr;
import org.xml.sax.SAXException;

/**
 * <!-- Category: instruction -->
 * <xsl:sequence
 *   select = expression>
 *   <!-- Content: xsl:fallback* -->
 * </xsl:sequence>
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class SequenceConstructor extends SimpleConstructor {
	
	private String attr_select = null;

	private XSLPathExpr select = null;
	
	public SequenceConstructor(XSLContext context) {
		super(context);
	}
	
	public void setToDefaults() {
		attr_select = null;
	}

	public void prepareAttribute(ContextAtExist context, Attr attr) throws XPathException {
		String attr_name = attr.getNodeName();
		if (attr_name.equals(SELECT)) {
			attr_select = attr.getValue();
		}
	}
	
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
//    	boolean atRootCall = false;//XXX: rewrite

    	if (attr_select != null) {
    		select = new XSLPathExpr(getXSLContext());
    		Pattern.parse(contextInfo.getContext(), attr_select, select);

//			//UNDERSTAND: <node>text<node>  step = "." -> SELF:node(), but need CHILD:node()
//			if ((contextInfo.getFlags() & DOT_TEST) != 0) {
//				atRootCall = true;
//				_check_(select);
//				contextInfo.removeFlag(DOT_TEST);
//			}

//			_check_childNodes_(select);
    	}

    	super.analyze(contextInfo);

//    	if (atRootCall)
//    		contextInfo.addFlag(DOT_TEST);
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

        // evaluate the expression
		context.pushDocumentContext();
        Sequence result;
        try {
            result = select.eval(contextSequence, contextItem);
        } finally {
            context.popDocumentContext();
        }
        
		// create the output
		MemTreeBuilder builder = context.getDocumentBuilder();
		DocumentBuilderReceiver receiver = new DocumentBuilderReceiver(builder);		
		try {
			SequenceIterator i = result.iterate();
			Item next = i.nextItem();		
			StringBuilder buf = null;
            boolean allowAttribs = true;
            while (next != null) {
			    context.proceed(this, builder);
				// if item is an atomic value, collect the string values of all
				// following atomic values and seperate them by a space. 
				if (Type.subTypeOf(next.getType(), Type.ATOMIC)) {
				    if(buf == null)
				        buf = new StringBuilder();
					else if (buf.length() > 0)
						buf.append(' ');
					buf.append(next.getStringValue());
                    allowAttribs = false;
                    next = i.nextItem();            // if item is a node, flush any collected character data and
				//	copy the node to the target doc. 
				} else if (Type.subTypeOf(next.getType(), Type.NODE)) {
                    if (buf != null && buf.length() > 0) {
						receiver.characters(buf);
						buf.setLength(0);
					}
                    if (next.getType() == Type.ATTRIBUTE && !allowAttribs)
                        throw new XPathException(this, "XQTY0024: An attribute may not appear after " +
                            "another child node.");
                    next.copyTo(context.getBroker(), receiver);
                    allowAttribs = next.getType() == Type.ATTRIBUTE;
                    next = i.nextItem();
				}
            }
			// flush remaining character data
			if (buf != null && buf.length() > 0)
				receiver.characters(buf);
		} catch (SAXException e) {
		    LOG.warn("SAXException during serialization: " + e.getMessage(), e);
            throw new XPathException(this, e.getMessage());
			//throw new XPathException(getASTNode(),
			//	"Encountered SAX exception while serializing enclosed expression: "
			//		+ ExpressionDumper.dump(this));
		}
        
       if (context.getProfiler().isEnabled())           
            context.getProfiler().end(this, "", result);              
           
		return result;
	}

	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("<xsl:sequence");
        
        if (attr_select != null)
        	dumper.display(" select = " + select.toString());
        
        dumper.display("> ");

        super.dump(dumper);

        dumper.display("</xsl:sequence>");
    }
    
    public String toString() {
    	StringBuilder result = new StringBuilder();
    	result.append("<xsl:sequence");

        if (attr_select != null)
        	result.append(" select = "+attr_select.toString());    
        
        result.append("> ");    

        result.append(super.toString());    

        result.append("</xsl:sequence>");
        return result.toString();
    }

}
