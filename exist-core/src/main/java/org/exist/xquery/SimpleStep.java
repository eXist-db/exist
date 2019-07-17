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

import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.NodeSet;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.MemoryNodeSet;

/**
 * Represents a primary expression in a simple path step like
 * foo//$x. The class is mainly used to wrap variable references inside
 * a path expression.
 * 
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public class SimpleStep extends Step {

	private final Expression expression;

	public SimpleStep(XQueryContext context, int axis, Expression expression) {
		super(context, axis);
		this.expression = expression;
		this.expression.setPrimaryAxis(axis);
	}

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#analyze(org.exist.xquery.AnalyzeContextInfo)
     */
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
    	contextInfo.setParent(this);
        expression.analyze(contextInfo);
        super.analyze(contextInfo);
    }
    
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.persistent.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
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
        
		if (contextItem != null)
			{contextSequence = contextItem.toSequence();}
        
        Sequence result = Sequence.EMPTY_SEQUENCE;
	    final Sequence set = expression.eval(contextSequence);

        if (!set.isEmpty()) {
            if (set.isPersistentSet()) {
                final NodeSet nodeSet = set.toNodeSet();
                switch(axis) {
                    case Constants.DESCENDANT_SELF_AXIS:
                        result = nodeSet.selectAncestorDescendant(contextSequence.toNodeSet(), NodeSet.DESCENDANT,
                            true, contextId, true);
                        break;
                    case Constants.CHILD_AXIS:
                        result = nodeSet.selectParentChild(contextSequence.toNodeSet(), NodeSet.DESCENDANT, contextId);
                        break;
                    default:
                        throw new XPathException(this, "Wrong axis specified");
                }
            } else {
                final MemoryNodeSet ctxNodes = contextSequence.toMemNodeSet();
                final MemoryNodeSet nodes = set.toMemNodeSet();
                switch(axis) {
                    case Constants.DESCENDANT_SELF_AXIS:
                        result = ctxNodes.selectDescendants(nodes);
                        break;
                    case Constants.CHILD_AXIS:
                        result = ctxNodes.selectChildren(nodes);
                        break;
                }

            }
        }

        if (context.getProfiler().isEnabled()) 
            {context.getProfiler().end(this, "", result);}
        
        //actualReturnType = result.getItemType();
        
        return result;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Step#resetState()
	 */
	public void resetState(boolean postOptimization) {
		super.resetState(postOptimization);
		expression.resetState(postOptimization);
	}
	
	public void setContextDocSet(DocumentSet contextSet) {
		super.setContextDocSet(contextSet);
		expression.setContextDocSet(contextSet);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.AbstractExpression#setPrimaryAxis(int)
	 */
	public void setPrimaryAxis(int axis) {
		expression.setPrimaryAxis(axis);
	}

    public int getPrimaryAxis() {
        return expression.getPrimaryAxis();
    }
}
