
/* eXist Open Source Native XML Database
 * Copyright (C) 2000-03,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Library General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * $Id$
 */
package org.exist.xquery;

import org.exist.dom.persistent.NodeSet;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;

/**
 * Boolean operator "or".
 * 
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang</a>
 */
public class OpOr extends LogicalOp {

    public OpOr(XQueryContext context) {
        super(context);
    }

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
        if (getLength() == 0)
            {return Sequence.EMPTY_SEQUENCE;}
        if (contextItem != null)
            {contextSequence = contextItem.toSequence();}
        boolean doOptimize = optimize;
        if (contextSequence != null && !contextSequence.isPersistentSet())
            {doOptimize = false;}
        Sequence result;
        final Expression left = getLeft();
        final Expression right = getRight();
        final Sequence ls = left.eval(contextSequence, null);
        // first check if left operand is a persistent set
        doOptimize = doOptimize && (ls.isPersistentSet() || ls.isEmpty());
        if (doOptimize) {
            final Sequence rs = right.eval(contextSequence, null);
            if (inPredicate) {
                NodeSet lr = ls.toNodeSet();
                lr = lr.getContextNodes(getContextId());

                NodeSet rr = rs.toNodeSet();
                rr = rr.getContextNodes(contextId);
                result = lr.union(rr);
            } else {
                // fall back
                final boolean rl = ls.effectiveBooleanValue();
                if (rl) {
                    result = BooleanValue.TRUE;
                } else {
                    final boolean rr = rs.effectiveBooleanValue();
                    result = rl || rr ? BooleanValue.TRUE : BooleanValue.FALSE;
                }
            }
        } else {
            // no: default evaluation based on boolean value
            final boolean rl = ls.effectiveBooleanValue();
            if (rl) {
                result = BooleanValue.TRUE;
            } else {
                final Sequence rs = right.eval(contextSequence, null);
                final boolean rr = rs.effectiveBooleanValue();
                result = rl || rr ? BooleanValue.TRUE : BooleanValue.FALSE;
            }
        }
        if (context.getProfiler().isEnabled()) 
            {context.getProfiler().end(this, "", result);}
        return result;
    }

    public void accept(ExpressionVisitor visitor) {
        visitor.visitOrExpr(this);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.PathExpr#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        if (getLength() == 0)
            {return;}
        dumper.display("(");
        getExpression(0).dump(dumper);
        for (int i = 1; i < getLength(); i++) {
            dumper.display(") or (");
            getExpression(i).dump(dumper);
        }
        dumper.display(")");
    }

    public String toString() {
        if (getLength() == 0)
            {return "";}
        final StringBuilder result = new StringBuilder("(");
        result.append(getExpression(0).toString());
        for (int i = 1; i < getLength(); i++) {
            result.append(") or (");
            result.append(getExpression(i).toString());
        }
        result.append(")");
        return result.toString();
    }
}
