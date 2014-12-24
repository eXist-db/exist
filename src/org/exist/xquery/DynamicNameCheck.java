/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Team
 *
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

import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.QName;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.*;
import org.w3c.dom.Node;

/**
 * Check element or attribute name to match sequence type.
 * 
 * @author wolf
 */
public class DynamicNameCheck extends AbstractExpression {

    final private NameTest test;
    final private Expression expression;

    public DynamicNameCheck(XQueryContext context, NameTest test, Expression expression) {
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
                        itemType = NodeProxy.nodeType2XQuery(((NodeProxy) item).getNode().getNodeType());
                    }
                }
                if (!Type.subTypeOf(itemType, test.getType())) {
                    throw new XPathException(expression, "Type error in expression" +
                        ": required type is " + Type.getTypeName(test.getType()) +
                        "; got: " + Type.getTypeName(item.getType()) + ": " + item.getStringValue());
                }
                final Node node = ((NodeValue) item).getNode();
                if (!test.matchesName(node))
                    {throw new XPathException(expression, "Type error in expression: " +
                        "required node name is " + getPrefixedNodeName(test.getName()) +
                        "; got: " + getPrefixedNodeName(node));}
                }
                if (context.getProfiler().isEnabled()) {
                    context.getProfiler().end(this, "", seq);
                }
                return seq;
            } catch(final IllegalArgumentException iae) {
                throw new XPathException(expression, iae);
            }
    }

    private String getPrefixedNodeName(Node node) {
        final String prefix = node.getPrefix();
        if (prefix == null) {
            final String nameSpace = node.getNamespaceURI();
            if (nameSpace == null) {
                return node.getNodeName();
            } else {
                return "{'" + nameSpace + "'}:" + node.getNodeName();
            }
        } else if (prefix.isEmpty()) {
            return "{''}:" + node.getNodeName();
        } else {
            return prefix + ":" + node.getLocalName();
        }
    }

    // TODO should be moved to QName
    private String getPrefixedNodeName(QName name) {
        final String prefix = name.getPrefix();
        final String localName = name.getLocalPart();
        if (prefix == null) {
            final String namespaceURI = name.getNamespaceURI();
            if (namespaceURI == null) {
                return localName;
            } else {
                return "{'" + namespaceURI + "'}:" + localName;
            }
        } else if (prefix.isEmpty()) {
            return "{''}:" + localName;
        } else {
            return prefix + ":" + localName;
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
