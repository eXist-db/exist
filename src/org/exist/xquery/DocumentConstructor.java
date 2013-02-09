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

import org.exist.memtree.DocumentBuilderReceiver;
import org.exist.memtree.MemTreeBuilder;
import org.exist.memtree.NodeImpl;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;
import org.xml.sax.SAXException;

/**
 * Implements a dynamic document constructor. Creates a new
 * document node with its own node identity.
 * 
 * @author wolf
 */
public class DocumentConstructor extends NodeConstructor {

    private final Expression content;

    /**
     * @param context
     */
    public DocumentConstructor(XQueryContext context, Expression contentExpr) {
        super(context);
        this.content = contentExpr;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#analyze(org.exist.xquery.Expression)
     */
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        final AnalyzeContextInfo newContextInfo = new AnalyzeContextInfo(contextInfo);
        newContextInfo.setParent(this);
        newContextInfo.addFlag(IN_NODE_CONSTRUCTOR);
        content.analyze(newContextInfo);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#eval(org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
     */
    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES",
                Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES,
                    "CONTEXT SEQUENCE", contextSequence);}
            if (contextItem != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES,
                    "CONTEXT ITEM", contextItem.toSequence());}
        }

        context.pushDocumentContext();

        final Sequence contentSeq = content.eval(contextSequence, contextItem);

        context.popDocumentContext();
        context.pushDocumentContext();

        final MemTreeBuilder builder = context.getDocumentBuilder(true);
        final DocumentBuilderReceiver receiver = new DocumentBuilderReceiver(builder);

        try {
            if(!contentSeq.isEmpty()) {
                StringBuilder buf = null;
                final SequenceIterator i = contentSeq.iterate();
                Item next = i.nextItem();
                while(next != null) {
                    context.proceed(this, builder);
                    if (next.getType() == Type.ATTRIBUTE || next.getType() == Type.NAMESPACE)
                        {throw new XPathException(this, "Found a node of type " +
                            Type.getTypeName(next.getType()) + " inside a document constructor");}
                    // if item is an atomic value, collect the string values of all
                    // following atomic values and seperate them by a space. 
                    if (Type.subTypeOf(next.getType(), Type.ATOMIC)) {
                        if(buf == null)
                            {buf = new StringBuilder();}
                        else if (buf.length() > 0)
                            {buf.append(' ');}
                        buf.append(next.getStringValue());
                        next = i.nextItem();
                    // if item is a node, flush any collected character data and
                    // copy the node to the target doc. 
                    } else if (next.getType() == Type.DOCUMENT) {
                        if (buf != null && buf.length() > 0) {
                            receiver.characters(buf);
                            buf.setLength(0);
                        }
                        next.copyTo(context.getBroker(), receiver);
                        next = i.nextItem();
                    } else if (Type.subTypeOf(next.getType(), Type.NODE)) {
                        if (buf != null && buf.length() > 0) {
                            receiver.characters(buf);
                            buf.setLength(0);
                        }
                        next.copyTo(context.getBroker(), receiver);
                        next = i.nextItem();
                    }
                }
                // flush remaining character data
                if (buf != null && buf.length() > 0) {
                    receiver.characters(buf);
                    buf.setLength(0);
                }
            }
        } catch(final SAXException e) {
            throw new XPathException(this,
                "Encountered SAX exception while processing document constructor: " +
                ExpressionDumper.dump(this));
        }
        context.popDocumentContext();
        final NodeImpl node =  builder.getDocument();
        if (context.getProfiler().isEnabled())
            {context.getProfiler().end(this, "", node);}
        return node;
    }

    public Expression getContent() {
    	return content;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("document {");
        dumper.startIndent();
        //TODO : is this the required syntax ?
        content.dump(dumper);
        dumper.endIndent();
        dumper.nl().display("}");
    }

    public String toString() {
        final StringBuilder result = new StringBuilder();
        result.append("document {");
        //TODO : is this the required syntax ?
        result.append(content.toString());
        result.append("} ");
        return result.toString();
    }

    public void resetState(boolean postOptimization) {
        super.resetState(postOptimization);
        content.resetState(postOptimization);
    }

    @Override
    public void accept(ExpressionVisitor visitor) {
        visitor.visitDocumentConstructor(this);
    }
}
