/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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

import org.exist.xquery.Constants.NodeComparisonOperator;
import org.exist.xquery.util.Error;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

/**
 * Implements node comparisons: is, isnot, &lt;&lt;, &gt;&gt;.
 * 
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public class NodeComparison extends BinaryOp {

    private final NodeComparisonOperator relation;

    public NodeComparison(XQueryContext context, Expression left, Expression right, NodeComparisonOperator relation) {
        super(context);
        this.relation = relation;
        add(new DynamicCardinalityCheck(context, Cardinality.ZERO_OR_ONE, left, 
                new Error(Error.NODE_COMP_TYPE_MISMATCH)));
        add(right);
        //add(new DynamicCardinalityCheck(context, Cardinality.ZERO_OR_ONE, right,
        //        new Error(Error.NODE_COMP_TYPE_MISMATCH)));
        //add(left);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.PathExpr#getDependencies()
     */
    public int getDependencies() {
        return Dependency.CONTEXT_SET | Dependency.CONTEXT_ITEM;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.AbstractExpression#getCardinality()
     */
    public int getCardinality() {
        return Cardinality.ZERO_OR_ONE;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.BinaryOp#returnsType()
     */
    public int returnsType() {
        return Type.BOOLEAN;
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
        if(contextItem != null)
            {contextSequence = contextItem.toSequence();}
        Sequence result;
        final Sequence ls = getLeft().eval(contextSequence, contextItem);
        final Sequence rs = getRight().eval(contextSequence, contextItem);
        if (!ls.isEmpty() && !rs.isEmpty()) {
            if (!Type.subTypeOf(ls.itemAt(0).getType(), Type.NODE))
                {throw new XPathException(this, ErrorCodes.XPTY0004,
                    "left item is not a node; got '" +
                    Type.getTypeName(ls.itemAt(0).getType()) + "'");}
            if (!Type.subTypeOf(rs.itemAt(0).getType(), Type.NODE))
                {throw new XPathException(this, ErrorCodes.XPTY0004,
                    "right item is not a node; got '" +
                    Type.getTypeName(rs.itemAt(0).getType()) + "'");}
            final NodeValue lv = (NodeValue)ls.itemAt(0);
            final NodeValue rv = (NodeValue)rs.itemAt(0);
            if (lv.getImplementationType() != rv.getImplementationType()) {
                // different implementations : can't be the same nodes
                result =  BooleanValue.FALSE;
            } else {
                switch(relation) {
                case IS:
                    result = lv.equals(rv) ? BooleanValue.TRUE : BooleanValue.FALSE;
                    break;
                case BEFORE:
                    result = lv.before(rv, false) ? BooleanValue.TRUE : BooleanValue.FALSE;
                    break;
                case AFTER:
                    result = lv.after(rv, false) ? BooleanValue.TRUE : BooleanValue.FALSE;
                    break;
                default:
                    throw new XPathException(this, "Illegal argument: unknown relation");
                }
            }
        } else {
            if (ls.isEmpty() && !rs.isEmpty()) {
                if (!Type.subTypeOf(rs.getItemType(), Type.NODE))
                    {throw new XPathException(this, ErrorCodes.XPTY0004,
                    "The empty sequence cant be an atomic value");}
            }
            if (!ls.isEmpty() && rs.isEmpty()) {
                if (!Type.subTypeOf(ls.getItemType(), Type.NODE))
                    {throw new XPathException(this, ErrorCodes.XPTY0004,
                    "The empty sequence cant be an atomic value");}
            }
            result = BooleanValue.EMPTY_SEQUENCE;
        }
        if (context.getProfiler().isEnabled())
            {context.getProfiler().end(this, "", result);}
        return result;
    }

    @Override
    public void dump(ExpressionDumper dumper) {
        getLeft().dump(dumper);
        dumper.display(' ').display(relation.symbol).display(' ');
        getRight().dump(dumper);
    }

    @Override
    public String toString() {
        return getLeft().toString() + ' ' + relation.symbol + ' ' + getRight().toString();
    }
}
