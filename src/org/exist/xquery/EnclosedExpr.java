/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2012 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.xquery;

import org.exist.memtree.DocumentBuilderReceiver;
import org.exist.memtree.MemTreeBuilder;
import org.exist.memtree.TextImpl;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;
import org.xml.sax.SAXException;

/**
 * Represents an enclosed expression <code>{expr}</code> inside element
 * content. Enclosed expressions within attribute values are processed by
 * {@link org.exist.xquery.AttributeConstructor}.
 *  
 * @author Wolfgang Meier <wolfgang@exist-db.org>
 */
public class EnclosedExpr extends PathExpr {

    /**
     * 
     */
    public EnclosedExpr(XQueryContext context) {
        super(context);
    }
    
	public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
		final AnalyzeContextInfo newContextInfo = new AnalyzeContextInfo(contextInfo);
		newContextInfo.removeFlag(IN_NODE_CONSTRUCTOR);
		super.analyze(newContextInfo);
	}


    /* (non-Javadoc)
     * @see org.exist.xquery.AbstractExpression#eval(org.exist.xquery.StaticContext,
     * org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence)
     */
    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES,
                "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES,
                "CONTEXT SEQUENCE", contextSequence);}
            if (contextItem != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES,
                "CONTEXT ITEM", contextItem.toSequence());}
        }
        if (contextItem != null) {
            contextSequence = contextItem.toSequence();
        }
        // evaluate the expression
        context.pushDocumentContext();
        Sequence result;
        try {
            result = super.eval(contextSequence, null);
        } finally {
            context.popDocumentContext();
        }
        // create the output
        final MemTreeBuilder builder = context.getDocumentBuilder();
        final DocumentBuilderReceiver receiver = new DocumentBuilderReceiver(builder);
        receiver.checkNS = true;
        try {
            final SequenceIterator i = result.iterate();
            Item next = i.nextItem();
            StringBuilder buf = null;
            boolean allowAttribs = true;
            while (next != null) {
                context.proceed(this, builder);
                // if item is an atomic value, collect the string values of all
                // following atomic values and separate them by a space.
                if (Type.subTypeOf(next.getType(), Type.ATOMIC)) {
                    if (buf == null)
                        {buf = new StringBuilder();}
                    else if (buf.length() > 0)
                        {buf.append(' ');}
                    buf.append(next.getStringValue());
                    allowAttribs = false;
                    next = i.nextItem();
                //If the item is a node, flush any collected character data and
                //copy the node to the target doc. 
                } else if (Type.subTypeOf(next.getType(), Type.NODE)) {
                	
                    //It is possible for a text node constructor to construct a text node containing a zero-length string.
                    //However, if used in the content of a constructed element or document node,
                    //such a text node will be deleted or merged with another text node.
                	if (next instanceof TextImpl && ((TextImpl)next).getStringValue().isEmpty()) {
                        next = i.nextItem();
						continue;
					}
                    if (buf != null && buf.length() > 0) {
                        receiver.characters(buf);
                        buf.setLength(0);
                    }
                    if (next.getType() == Type.ATTRIBUTE && !allowAttribs)
                        {throw new XPathException(this, ErrorCodes.XQTY0024,
                            "An attribute may not appear after another child node.");}
                    next.copyTo(context.getBroker(), receiver);
                    allowAttribs = next.getType() == Type.ATTRIBUTE || next.getType() == Type.NAMESPACE;
                    next = i.nextItem();
                }
            }
            // flush remaining character data
            if (buf != null && buf.length() > 0)
                {receiver.characters(buf);}
        } catch (final SAXException e) {
            LOG.warn("SAXException during serialization: " + e.getMessage(), e);
            throw new XPathException(this, e);
        }
       if (context.getProfiler().isEnabled())
            {context.getProfiler().end(this, "", result);}
       return result;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.PathExpr#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("{");
        dumper.startIndent();
        super.dump(dumper);
        dumper.endIndent();
        dumper.nl().display("}");
    }

    public String toString() {
        final StringBuilder result = new StringBuilder();
        result.append("{");
        result.append(super.toString());
        result.append("}");
        return result.toString();
    }

    public void accept(ExpressionVisitor visitor) {
        visitor.visitPathExpr(this);
    }

    @Override
    public boolean allowMixedNodesInReturn() {
        return true;
    }

    @Override
    public Expression simplify() {
        return this;
    }

}
