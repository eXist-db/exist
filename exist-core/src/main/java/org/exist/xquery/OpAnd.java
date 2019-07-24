
/* eXist Open Source Native XML Database
 * Copyright (C) 2001-06,  Wolfgang M. Meier (wolfgang@exist-db.org)
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
 * Boolean operator "and".
 * 
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang</a>
 */
public class OpAnd extends LogicalOp {

    public OpAnd(XQueryContext context) {
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
        Sequence result;
        if (getLength() == 0) {
            result = Sequence.EMPTY_SEQUENCE;
        } else {
            if (contextItem != null)
                {contextSequence = contextItem.toSequence();}
            boolean doOptimize = optimize;
            if (contextSequence != null && !contextSequence.isPersistentSet())
                {doOptimize = false;}
            final Expression left = getLeft();
            final Expression right = getRight();
            
//            setContextId(getExpressionId());
            if (doOptimize && contextSequence != null)
            	{contextSequence.setSelfAsContext(getContextId());}
            
            final Sequence ls = left.eval(contextSequence, null);
            doOptimize = doOptimize && (ls.isPersistentSet() || ls.isEmpty());
            if (doOptimize) {
            	
            	if (inPredicate) {
                    NodeSet lr = ls.toNodeSet();
                    lr = lr.getContextNodes(getContextId()); 

                    if (lr.isEmpty())
                        {return NodeSet.EMPTY_SET;}

                    final Sequence rs = right.eval(lr, null);
                    
                    final NodeSet rr = rs.toNodeSet();
                    result = rr.getContextNodes(getContextId()); 

            	} else {
            		final Sequence rs = right.eval(contextSequence, null);
                    final boolean rl = ls.effectiveBooleanValue();
                    if (!rl) {
                        result = BooleanValue.FALSE;
                    } else {
                        final boolean rr = rs.effectiveBooleanValue();
                        result = (rl && rr) ? BooleanValue.TRUE : BooleanValue.FALSE;
                    }
            	}
            } else {
                boolean rl = ls.effectiveBooleanValue();
                //Immediately return false if the left operand is false
                if (!rl) {
                    result = BooleanValue.FALSE;
                } else {
                    final Sequence rs = right.eval(contextSequence, null);
                    final boolean rr = rs.effectiveBooleanValue();
                    result = (rl && rr) ? BooleanValue.TRUE : BooleanValue.FALSE;
                }
            }
        }
        if (context.getProfiler().isEnabled()) 
            {context.getProfiler().end(this, "", result);}
        return result;
    }

    public void accept(ExpressionVisitor visitor) {
        visitor.visitAndExpr(this);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.PathExpr#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        if (getLength() == 0)
            {return;}
        getExpression(0).dump(dumper);
        for (int i = 1; i < getLength(); i++) {
            dumper.display(") and (");
            getExpression(i).dump(dumper);
        }
    }

    public String toString() {
        if (getLength() == 0)
            {return "";}
        final StringBuilder result = new StringBuilder("(");
        result.append(getExpression(0).toString());
        for (int i = 1; i < getLength(); i++) {
            result.append(") and (");
            result.append(getExpression(i).toString());
        }
        result.append(")");
        return result.toString();
    }
}
