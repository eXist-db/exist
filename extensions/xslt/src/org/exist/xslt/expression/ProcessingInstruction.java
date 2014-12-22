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
import org.exist.dom.memtree.DocumentImpl;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.util.XMLChar;
import org.exist.xquery.Constants;
import org.exist.xquery.Dependency;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.PathExpr;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xslt.XSLContext;
import org.w3c.dom.Attr;

/**
 * <!-- Category: instruction -->
 * <xsl:processing-instruction
 *   name = { ncname }
 *   select? = expression>
 *   <!-- Content: sequence-constructor -->
 * </xsl:processing-instruction>
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class ProcessingInstruction extends Declaration {

    private String attr_name = null;
    private String attr_select = null;

	private PathExpr select = null;

	public ProcessingInstruction(XSLContext context) {
		super(context);
	}

	public void setToDefaults() {
	    attr_name = null;
	    attr_select = null;

	    select = null;
	}

	public void prepareAttribute(ContextAtExist context, Attr attr) throws XPathException {
		String localName = attr.getLocalName();
			
		if (localName.equals(NAME)) {
			attr_name = attr.getValue();
		} else if (localName.equals(SELECT)) {
			attr_select = attr.getValue();
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

//        if (newDocumentContext)
//            context.pushDocumentContext();
        try {
            MemTreeBuilder builder = context.getDocumentBuilder();
            context.proceed(this, builder);

//            Sequence nameSeq = name.eval(contextSequence, contextItem);
//            if(!nameSeq.hasOne())
//            	throw new XPathException(this, "The name expression should evaluate to a single value");

//            Item nameItem = nameSeq.itemAt(0);
//            if(!(nameItem.getType() == Type.STRING || nameItem.getType() == Type.NCNAME ||
//                    nameItem.getType() == Type.UNTYPED_ATOMIC))
//                throw new XPathException(this, "The name expression should evaluate to a " +
//                        Type.getTypeName(Type.STRING) + " or a " + Type.getTypeName(Type.NCNAME) +
//                        " or a " + Type.getTypeName(Type.UNTYPED_ATOMIC) + ". Got: " +
//                        Type.getTypeName(nameItem.getType()));

//            if(!XMLChar.isValidNCName(nameSeq.getStringValue()))
//			throw new XPathException(this, ErrorCodes.XQDY0041, nameSeq.getStringValue() + "' is not a valid processing instruction name", nameSeq);

//            if (nameSeq.getStringValue().equalsIgnoreCase("XML"))
//                throw new XPathException(this, ErrorCodes.XQDY0064, nameSeq.getStringValue() + "' is not a valid processing instruction name", nameSeq);

            if(!XMLChar.isValidNCName(attr_name))
            	throw new XPathException(this, ErrorCodes.XQDY0041, "'"+attr_name + "' is not a valid processing instruction name");

            if (attr_name.equalsIgnoreCase("XML"))
                throw new XPathException(this, ErrorCodes.XQDY0064, "'"+attr_name+"' is not a valid processing instruction name");

            String contentString;
            Sequence contentSeq;
            if (select != null)
            	contentSeq = select.eval(contextSequence, contextItem);
            else
            	contentSeq = super.eval(contextSequence, contextItem);
            
            if(contentSeq.isEmpty())
        	contentString = "";
            else {
                StringBuilder buf = new StringBuilder();
                for(SequenceIterator i = contentSeq.iterate(); i.hasNext(); ) {
                    context.proceed(this, builder);
                    Item next = i.nextItem();
                    if(buf.length() > 0)
                        buf.append(' ');
                    buf.append(next.getStringValue());
                }
                while (buf.length() > 0 && Character.isWhitespace(buf.charAt(0)))
                    buf.deleteCharAt(0);
                contentString = buf.toString();
            }

            if (contentString.indexOf("?>") != Constants.STRING_NOT_FOUND)
                throw new XPathException(this, ErrorCodes.XQDY0026, contentString + "' is not a valid processing intruction content", contentSeq);

            int nodeNr = builder.processingInstruction(attr_name, contentString);

            Sequence result = ((DocumentImpl)builder.getDocument()).getNode(nodeNr);

            if (context.getProfiler().isEnabled())
                context.getProfiler().end(this, "", result);

            return result;
        } finally {
//            if (newDocumentContext)
//                context.popDocumentContext();
        }
	}

	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("<xsl:processing-instruction");

        if (attr_name != null) {
        	dumper.display(" name = ");
        	dumper.display(attr_name);
        }
        if (attr_select != null) {
        	dumper.display(" select = ");
        	dumper.display(attr_select);
        }

        super.dump(dumper);

        dumper.display("</xsl:processing-instruction>");
    }
    
    public String toString() {
    	StringBuilder result = new StringBuilder();
    	result.append("<xsl:processing-instruction");
        
    	if (attr_name != null)
        	result.append(" name = "+attr_name.toString());    
    	if (attr_select != null)
        	result.append(" select = "+attr_select.toString());    

        result.append("> ");
        
        result.append(super.toString());

        result.append("</xsl:processing-instruction> ");
        return result.toString();
    }    
}
