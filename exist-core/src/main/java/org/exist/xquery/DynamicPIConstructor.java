/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery;

import org.exist.dom.memtree.DocumentImpl;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.util.XMLNames;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;


/**
 * Dynamic constructor for processing instruction nodes.
 * 
 * @author wolf
 */
public class DynamicPIConstructor extends NodeConstructor {

    private Expression name;
    private Expression content;

    public DynamicPIConstructor(XQueryContext context) {
        super(context);
    }

    public void setNameExpr(Expression nameExpr) {
        this.name = new Atomize(context, nameExpr);
    }

    public void setContentExpr(Expression contentExpr) {
        this.content = contentExpr;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#analyze(org.exist.xquery.AnalyzeContextInfo)
     */
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        super.analyze(contextInfo);
        contextInfo.setParent(this);
        name.analyze(contextInfo);
        content.analyze(contextInfo);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#eval(org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
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
        if (newDocumentContext)
            {context.pushDocumentContext();}
        try {
            final MemTreeBuilder builder = context.getDocumentBuilder();
            context.proceed(this, builder);
            final Sequence nameSeq = name.eval(contextSequence, contextItem);
            //TODO : get rid of getLength()
            if (!nameSeq.hasOne())
                {throw new XPathException(this, ErrorCodes.XPTY0004,
                    "The name expression should evaluate to a single value");}
            final Item nameItem = nameSeq.itemAt(0);
            if (!(nameItem.getType() == Type.STRING || nameItem.getType() == Type.NCNAME ||
                    nameItem.getType() == Type.UNTYPED_ATOMIC))
                {throw new XPathException(this, ErrorCodes.XPTY0004,
                        "The name expression should evaluate to a " + Type.getTypeName(Type.STRING) +
                        " or a " + Type.getTypeName(Type.NCNAME) +
                        " or a " + Type.getTypeName(Type.UNTYPED_ATOMIC) +
                        ". Got: " + Type.getTypeName(nameItem.getType()));}
            if(!XMLNames.isNCName(nameSeq.getStringValue()))
                {throw new XPathException(this, ErrorCodes.XQDY0041,
                    nameSeq.getStringValue() + "' is not a valid processing instruction name", nameSeq);}
            if ("XML".equalsIgnoreCase(nameSeq.getStringValue()))
                {throw new XPathException(this, ErrorCodes.XQDY0064,
                    nameSeq.getStringValue() + "' is not a valid processing instruction name", nameSeq);}
            String contentString;
            final Sequence contentSeq = content.eval(contextSequence, contextItem);
            if (contentSeq.isEmpty())
        	{contentString = "";}
            else {
                final StringBuilder buf = new StringBuilder();
                for(final SequenceIterator i = Atomize.atomize(contentSeq).iterate(); i.hasNext(); ) {
                    context.proceed(this, builder);
                    final Item next = i.nextItem();
                    if(!buf.isEmpty())
                        {buf.append(' ');}
                    buf.append(next.getStringValue());
                }
                while (!buf.isEmpty() && Character.isWhitespace(buf.charAt(0)))
                    buf.deleteCharAt(0);
                contentString = buf.toString();
            }
            if (contentString.contains("?>"))
                {throw new XPathException(this, ErrorCodes.XQDY0026, contentString + "' is not a valid processing intruction content", contentSeq);}
            final int nodeNo = builder.processingInstruction(nameSeq.getStringValue(), contentString);
            final Sequence result = ((DocumentImpl)builder.getDocument()).getNode(nodeNo);
            if (context.getProfiler().isEnabled())
                {context.getProfiler().end(this, "", result);}
            return result;
        } finally {
            if (newDocumentContext)
                {context.popDocumentContext();}
        }
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("processing-instruction {");
        name.dump(dumper);
        dumper.display("} {");
        dumper.startIndent();
        content.dump(dumper);
        dumper.endIndent().nl().display("}");
    }

    public String toString() {
        return "processing-instruction {" + name.toString() + "} {" + content.toString() + "} ";
    }

    public void resetState(boolean postOptimization) {
        super.resetState(postOptimization);
        name.resetState(postOptimization);
        content.resetState(postOptimization);
    }
}
