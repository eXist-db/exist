/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
 *  wolfgang@exist-db.org
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
package org.exist.xquery;

import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.memtree.MemTreeBuilder;
import org.exist.memtree.NodeImpl;
import org.exist.util.XMLChar;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.QNameValue;
import org.w3c.dom.DOMException;

/**
 * Represents a dynamic attribute constructor. The implementation differs from
 * AttributeConstructor as the evaluation is not controlled by the surrounding 
 * element. The attribute name as well as its value are only determined at evaluation time,
 * not at compile time.
 *  
 * @author wolf
 */
public class DynamicAttributeConstructor extends NodeConstructor {

    private Expression qnameExpr;
    private Expression valueExpr;
    
    private boolean replaceAttribute = false;
    
    /**
     * @param context
     */
    public DynamicAttributeConstructor(XQueryContext context) {
        super(context);
    }

    public void setNameExpr(Expression expr) {
        this.qnameExpr = new Atomize(context, expr);
    }

    public Expression getNameExpr() {
        return this.qnameExpr;
    }

    public void setContentExpr(Expression expr) {
        this.valueExpr  = new Atomize(context, expr);
    }

    public Expression getContentExpr() {
        return this.valueExpr;
    }
    
    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#analyze(org.exist.xquery.Expression)
     */
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        super.analyze(contextInfo);
        contextInfo.setParent(this);
        qnameExpr.analyze(contextInfo);
        valueExpr.analyze(contextInfo);
    }
    
    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#eval(org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
     */
    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);}
            if (contextItem != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());}
        }

        if (newDocumentContext)
            {context.pushDocumentContext();}
        
        NodeImpl node;
        try {
            final MemTreeBuilder builder = context.getDocumentBuilder();
            builder.setReplaceAttributeFlag(replaceAttribute);
            context.proceed(this, builder);

            final Sequence nameSeq = qnameExpr.eval(contextSequence, contextItem);
            if(!nameSeq.hasOne())
            	{throw new XPathException(this, "The name expression should evaluate to a single value");}

            final Item qnItem = nameSeq.itemAt(0);
            QName qn;
            if (qnItem.getType() == Type.QNAME)
                {qn = ((QNameValue) qnItem).getQName();}
            else
            	try {
            		qn = QName.parse(context, nameSeq.getStringValue(), null);
		    	} catch (final IllegalArgumentException e) {
					throw new XPathException(this, ErrorCodes.XPTY0004, "'" + nameSeq.getStringValue() + "' is not a valid attribute name");
				}

            //Not in the specs but... makes sense
            if(!XMLChar.isValidName(qn.getLocalName()))
            	{throw new XPathException(this, ErrorCodes.XPTY0004, "'" + qn.getLocalName() + "' is not a valid attribute name");}
            
            if ("xmlns".equals(qn.getLocalName()) && qn.getNamespaceURI().isEmpty())
            	{throw new XPathException(this, ErrorCodes.XQDY0044, "'" + qn.getLocalName() + "' is not a valid attribute name");}

            String value;
            final Sequence valueSeq = valueExpr.eval(contextSequence, contextItem);
            if(valueSeq.isEmpty())
            	{value = "";}
            else {
                final StringBuilder buf = new StringBuilder();
                for(final SequenceIterator i = valueSeq.iterate(); i.hasNext(); ) {
                    final Item next = i.nextItem();
                    buf.append(next.getStringValue());
                    if(i.hasNext())
                        {buf.append(' ');}
                }
                value = buf.toString();
            }
            
            value = DynamicAttributeConstructor.normalize(this, qn, value);
            
            node = null;
            try {
                final int nodeNr = builder.addAttribute(qn, value);
                node = builder.getDocument().getAttribute(nodeNr);
            } catch (final DOMException e) {
                throw new XPathException(this, ErrorCodes.XQDY0025, "element has more than one attribute '" + qn + "'");
            } 
        } finally {
            if (newDocumentContext)
                {context.popDocumentContext();}
        }

        if (context.getProfiler().isEnabled())           
            {context.getProfiler().end(this, "", node);}          
        
        return node;
    }
    
    public static String normalize(Expression expr, QName qn, String value) throws XPathException {
        //normalize xml:id
    	if (qn.equalsSimple(Namespaces.XML_ID_QNAME)) {
    		value = StringValue.trimWhitespace(StringValue.collapseWhitespace(value));
            if (!XMLChar.isValidNCName(value))
                {throw new XPathException(expr, ErrorCodes.XQDY0091, "Value of xml:id attribute is not a valid NCName: " + value);}
    	}
    	return value;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("attribute ");
        //TODO : remove curly braces if Qname
        dumper.display("{");
        qnameExpr.dump(dumper);
        dumper.display("} ");
        //TODO : handle empty value
        dumper.display("{");
        dumper.startIndent();
        valueExpr.dump(dumper);
        dumper.endIndent();
        dumper.nl().display("}");
    }
    
    public String toString() {
    	final StringBuilder result = new StringBuilder();
    	result.append("attribute ");
        //TODO : remove curly braces if Qname
        result.append("{");  
    	result.append(qnameExpr.toString());
        result.append("} ");
        //TODO : handle empty value
        result.append("{");        
    	result.append(valueExpr.toString());        
        result.append("} ");
    	return result.toString();
    } 
    
    public void resetState(boolean postOptimization) {
        super.resetState(postOptimization);
        qnameExpr.resetState(postOptimization);
        valueExpr.resetState(postOptimization);
    }

    public void accept(ExpressionVisitor visitor) {
        visitor.visitAttribConstructor(this);
    }

	public void setReplaceAttributeFlag(boolean flag) {
		replaceAttribute = flag;
	}
}
