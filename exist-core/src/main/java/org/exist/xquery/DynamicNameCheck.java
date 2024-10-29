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

import org.exist.dom.INode;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.QName;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Check element or attribute name to match sequence type.
 * 
 * @author wolf
 */
public class DynamicNameCheck extends AbstractExpression {

    final private NameTest test;
    final private Expression expression;

    public DynamicNameCheck(XQueryContext context, NameTest test) {
        this(context, test, null);
    }

    public DynamicNameCheck(XQueryContext context, NameTest test, final Expression expression) {
        super(context);
        this.test = test;
        this.expression = expression;
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
        try {
            final Sequence seq = expression.eval(contextSequence, contextItem);
            for (final SequenceIterator i = seq.iterate(); i.hasNext(); ) {
                final Item item = i.nextItem();
                int itemType = item.getType();
                //If item type is "unknown", try to get it from the sequence type
                //Should we get a kind of Type.UNKNOWN rather than Type.NODE ?
                if (itemType == Type.NODE) 
                    {itemType = seq.getItemType();}
                //Last chance...
                if (item instanceof NodeProxy) {
                    itemType = item.getType();
                    if (itemType == NodeProxy.UNKNOWN_NODE_TYPE) {
                        //Retrieve the actual node
                        itemType = Type.fromDomNodeType(((NodeProxy) item).getNode().getNodeType());
                    }
                }
                if (!Type.subTypeOf(itemType, test.getType())) {
                    throw new XPathException(expression, ErrorCodes.XPTY0004, "Type error in expression" +
                        ": required type is " + Type.getTypeName(test.getType()) +
                        "; got: " + Type.getTypeName(item.getType()) + ": " + item.getStringValue());
                }
                final Node node = ((NodeValue) item).getNode();
                if (!test.matchesName(node)) {
                    throw new XPathException(expression, ErrorCodes.XPTY0004, "Type error in expression: " +
                        "required node name is " + getPrefixedNodeName(test) +
                        "; got: " + getPrefixedNodeName((INode) node));
                }
            }
            if (context.getProfiler().isEnabled()) {
                context.getProfiler().end(this, "", seq);
            }
            return seq;
        } catch(final IllegalArgumentException iae) {
            throw new XPathException(expression, iae);
        }
    }

    private String getPrefixedNodeName(final INode iNode) {
        if (iNode instanceof Document) {
            final Element documentElement = ((Document) iNode).getDocumentElement();
            if (documentElement != null) {
                return getPrefixedNodeName(true, ((INode) documentElement).getQName());
            }
        }
        return getPrefixedNodeName(false, iNode.getQName());
    }

    private String getPrefixedNodeName(final NameTest nameTest) {
        return getPrefixedNodeName(nameTest.isOfType(Node.DOCUMENT_NODE), nameTest.getName());
    }

    private String getPrefixedNodeName(final boolean wasDocumentNodeWithNamedElementTest, final QName name) {
        final String prefixedName;
        if (name.getPrefix() == null && name.hasNamespace()) {
            prefixedName = name.toURIQualifiedName();
        } else {
            prefixedName = name.getStringValue();
        }

        if (wasDocumentNodeWithNamedElementTest) {
            return "document-node(" + prefixedName + ")";
        } else {
            return prefixedName;
        }
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#returnsType()
     */
    public int returnsType() {
        return test.nodeType;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#resetState()
     */
    public void resetState(boolean postOptimization) {
        super.resetState(postOptimization);
        expression.resetState(postOptimization);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#analyze(org.exist.xquery.AnalyzeContextInfo)
     */
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        contextInfo.setParent(this);
        expression.analyze(contextInfo);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        if(dumper.verbosity() > 1) {
            dumper.display("dynamic-name-check"); 
            dumper.display("["); 
            dumper.display(Type.getTypeName(test.nodeType));
            dumper.display(", "); 
        }
        expression.dump(dumper);
        if(dumper.verbosity() > 1)
            {dumper.display("]");}
    }

    public String toString() {
        return expression.toString();
    }

    public void setContextDocSet(DocumentSet contextSet) {
        super.setContextDocSet(contextSet);
        expression.setContextDocSet(contextSet);
    }

    public void accept(ExpressionVisitor visitor) {
        expression.accept(visitor);
    }
}
