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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.dom.persistent.*;
import org.exist.numbering.NodeId;
import org.exist.storage.UpdateListener;
import org.exist.xquery.value.*;

/**
 * Abstract superclass for the variable binding expressions "for", "let", and "count".
 * 
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public abstract class BindingExpression extends AbstractFLWORClause implements RewritableExpression {

	protected final static Logger LOG =
		LogManager.getLogger(BindingExpression.class);

    protected final static SequenceType POSITIONAL_VAR_TYPE = 
        new SequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE);
    
	protected QName varName;
	protected SequenceType sequenceType = null;
	protected Expression inputSequence;
	private ExprUpdateListener listener;

    public BindingExpression(final XQueryContext context) {
		super(context);
	}

	public void setVariable(final QName varName) {
		this.varName = varName;
	}

    public QName getVariable() {
        return this.varName;
    }

    /**
	 * Set the sequence type of the variable (as specified in the "as" clause).
	 * 
	 * @param type the {@link SequenceType} to set
	 */
	public void setSequenceType(SequenceType type) {
		this.sequenceType = type;
	}

	public void setInputSequence(Expression sequence) {
		this.inputSequence = sequence.simplify();
	}

    public Expression getInputSequence() {
        return this.inputSequence;
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        unordered = (contextInfo.getFlags() & UNORDERED) > 0;
    }

    @Override
    public Sequence postEval(Sequence seq) throws XPathException {
        if (returnExpr instanceof FLWORClause) {
            seq = ((FLWORClause)returnExpr).postEval(seq);
        }
        return super.postEval(seq);
    }

	public DocumentSet preselect(final DocumentSet docs) throws XPathException {
		return docs;
	}

	@Override
	public void resetState(final boolean postOptimization) {
		super.resetState(postOptimization);
		inputSequence.resetState(postOptimization);
		returnExpr.resetState(postOptimization);
	}
	
	public static void setContext(final int contextId, final Sequence seq) throws XPathException {
		if (seq instanceof VirtualNodeSet) {
			((VirtualNodeSet)seq).setInPredicate(true);
            ((VirtualNodeSet)seq).setSelfIsContext();
		} else {
			for (final SequenceIterator i = seq.unorderedIterator(); i.hasNext(); ) {
				final Item next = i.nextItem();
				if (next instanceof NodeProxy) {
					((NodeProxy) next).addContextNode(contextId, (NodeProxy) next);
				}
			}
		}
	}
	
	public final static void clearContext(final int contextId, final Sequence seq) throws XPathException {
		if (seq != null && !(seq instanceof VirtualNodeSet)) {
            seq.clearContext(contextId);
		}
	}

    protected void registerUpdateListener(final Sequence sequence) {
        if (listener == null) {
            listener = new ExprUpdateListener(sequence);
            context.registerUpdateListener(listener);
        } else {
			listener.setSequence(sequence);
		}
    }

    private class ExprUpdateListener implements UpdateListener {
        private Sequence sequence;

        public ExprUpdateListener(final Sequence sequence) {
            this.sequence = sequence;
        }

        public void setSequence(final Sequence sequence) {
            this.sequence = sequence;
        }
        
        @Override
        public void documentUpdated(final DocumentImpl document, final int event) {
        }

        @Override
        public void nodeMoved(final NodeId oldNodeId, final NodeHandle newNode) {
            sequence.nodeMoved(oldNodeId, newNode);
        }

        @Override
        public void unsubscribe() {
            BindingExpression.this.listener = null;
        }

        @Override
        public void debug() {
        }
    }

    @Override
    public int returnsType() {
        //TODO: let must return "return expression type"
        if (sequenceType != null) {
            return sequenceType.getPrimaryType();
        }
        return super.returnsType();
    }

	/* RewritableExpression API */
	
	@Override
	public void replace(final Expression oldExpr, final Expression newExpr) {
		if (inputSequence == oldExpr) {
			inputSequence = newExpr;
		} else if (returnExpr == oldExpr) {
			returnExpr = newExpr;
		}
	}
	
	@Override
	public Expression getPrevious(Expression current) {
		return null;
	}
	
	@Override
	public Expression getFirst() {
		return null;
	}
	
	@Override
	public void remove(final Expression oldExpr) throws XPathException {
	}
	
	/* END RewritableExpression API */
}
