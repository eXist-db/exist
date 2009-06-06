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

import org.apache.log4j.Logger;
import org.exist.dom.ContextItem;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.StoredNode;
import org.exist.dom.VirtualNodeSet;
import org.exist.numbering.NodeId;
import org.exist.storage.UpdateListener;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.GroupedValueSequenceTable;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

import java.util.Iterator;


/**
 * Abstract superclass for the variable binding expressions "for" and "let".
 * 
 * @author Wolfgang Meier <wolfgang@exist-db.org>
 */
public abstract class BindingExpression extends AbstractExpression {

	protected final static Logger LOG =
		Logger.getLogger(BindingExpression.class);

    protected final static SequenceType POSITIONAL_VAR_TYPE = 
        new SequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE);
    
	protected String varName;
	protected SequenceType sequenceType = null;
	protected Expression inputSequence;
	protected Expression returnExpr;
	protected Expression whereExpr;
	protected OrderSpec orderSpecs[] = null;
	protected int actualReturnType = Type.ITEM;

	/* bv : variables for group by 
	    group toGroupVarName as groupVarName as groupSpecs... return groupReturnExpr */ 
	protected GroupSpec groupSpecs[] = null; 
	protected Expression groupReturnExpr; 
	protected String groupVarName; 
	protected String toGroupVarName;   
	private ExprUpdateListener listener;


    public BindingExpression(XQueryContext context) {
		super(context);
	}

	public void setVariable(String qname) {
		varName = qname;
	}

    public String getVariable() {
        return this.varName;
    }
    
    /**
	 * Set the sequence type of the variable (as specified in the "as" clause).
	 * 
	 * @param type
	 */
	public void setSequenceType(SequenceType type) {
		this.sequenceType = type;
	}

	public void setInputSequence(Expression sequence) {
		this.inputSequence = sequence;
	}

    public Expression getInputSequence() {
        return this.inputSequence;
    }
    
    public void setReturnExpression(Expression expr) {
		this.returnExpr = expr;
	}

    public Expression getReturnExpression() {
        return this.returnExpr;
    }

    public void setWhereExpression(Expression expr) {
		this.whereExpr = expr;
	}

    public Expression getWhereExpression() {
        return this.whereExpr;
    }
    
    public void setOrderSpecs(OrderSpec specs[]) {
		this.orderSpecs = specs;
	}

	public void setGroupSpecs(GroupSpec specs[]) {
		this.groupSpecs = specs;
	}
	
	public void setGroupReturnExpr(Expression expr) {
		this.groupReturnExpr = expr;
	}	
	
	public void setGroupVariable(String qname) {
		groupVarName = qname;
	}

	public void setToGroupVariable(String qname) {
		toGroupVarName = qname;
	}

	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#analyze(org.exist.xquery.Expression, int)
     */
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
    	analyze(contextInfo, orderSpecs, groupSpecs); 

    }
    

    public abstract void analyze(AnalyzeContextInfo contextInfo, OrderSpec orderBy[], GroupSpec groupBy[]) throws XPathException;
    
	/* (non-Javadoc)
	 * @see org.exist.xquery.AbstractExpression#eval(org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
		return eval(contextSequence, contextItem, null, null); 

	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.xquery.StaticContext, org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public abstract Sequence eval(Sequence contextSequence,    Item contextItem, Sequence resultSequence, GroupedValueSequenceTable groupedSequence) 
		throws XPathException;

	protected Sequence applyWhereExpression(Sequence contextSequence) throws XPathException {
		if (contextSequence != null &&
				Type.subTypeOf(contextSequence.getItemType(), Type.NODE) &&
                contextSequence.isPersistentSet() &&
                //We might not be sure of the return type at this level
				Type.subTypeOf(whereExpr.returnsType(), Type.ITEM)) {
			Sequence seq = whereExpr.eval(contextSequence);
			//But *now*, we are ;-)
			if (Type.subTypeOf(whereExpr.returnsType(), Type.NODE)) {
				NodeSet nodes = seq.toNodeSet(); 
				// if the where expression returns a node set, check the context
				// node of each node in the set           
				NodeSet contextSet = contextSequence.toNodeSet();
				boolean contextIsVirtual = contextSet instanceof VirtualNodeSet;            
				NodeSet result = new ExtArrayNodeSet();
				DocumentImpl lastDoc = null;
				int count = 0;
				for (Iterator i = nodes.iterator(); i.hasNext(); count++) {
					NodeProxy current = (NodeProxy) i.next();                
					int sizeHint = Constants.NO_SIZE_HINT;
					if(lastDoc == null || current.getDocument() != lastDoc) {
						lastDoc = current.getDocument();
						sizeHint = nodes.getSizeHint(lastDoc);
					}
					ContextItem	context = current.getContext();                
					if (context == null) {               
						throw new XPathException(this, "Internal evaluation error: context node is missing for node " +
								current.getNodeId() + "!");
					}
	//				LOG.debug(current.debugContext());
					while (context != null) {
	                    //TODO : Is this the context we want ? Not sure... would have prefered the LetExpr.
						if (context.getContextId() == whereExpr.getContextId()) {
							NodeProxy contextNode = context.getNode();                    
							if(contextIsVirtual || contextSet.contains(contextNode)) {
	                            contextNode.addMatches(current);
								result.add(contextNode, sizeHint);
							}
						}
	                    context = context.getNextDirect();
					}
				}
				return result;
			}
		}
		
		if (contextSequence == null) {
			Sequence innerSeq = whereExpr.eval(null);
			return innerSeq.effectiveBooleanValue() ? BooleanValue.TRUE : BooleanValue.FALSE;
		} else {
			// general where clause: just check the effective boolean value
			ValueSequence result = new ValueSequence();
			int p = 0;			
			for (SequenceIterator i = contextSequence.iterate(); i.hasNext(); p++) {
				Item item = i.nextItem();
				context.setContextPosition(p);
                Sequence innerSeq = whereExpr.eval(contextSequence, item);                
				if (innerSeq.effectiveBooleanValue())
					result.add(item);                    
			}
			return result;
		}
	}

	/**
	 * Check all order specs to see if we can process them in
	 * one single step. In general, this is possible if all order 
	 * expressions return nodes.
	 * 
	 * @return Whether or not the order specs can be processed in one signle step.
	 */
	protected boolean checkOrderSpecs(Sequence in) {
		if (orderSpecs == null)
			return false;
		if (!Type.subTypeOf(in.getItemType(), Type.NODE))
			return false;
		for (int i = 0; i < orderSpecs.length; i++) {
			Expression expr = orderSpecs[i].getSortExpression();
			if(!Type.subTypeOf(expr.returnsType(), Type.NODE) ||
					Dependency.dependsOn(expr, Dependency.CONTEXT_ITEM ))
				return false;
		}
		return true;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#preselect(org.exist.dom.DocumentSet, org.exist.xquery.StaticContext)
	 */
	public DocumentSet preselect(DocumentSet in_docs) throws XPathException {
		return in_docs;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.AbstractExpression#resetState()
	 */
	public void resetState(boolean postOptimization) {
		super.resetState(postOptimization);
		inputSequence.resetState(postOptimization);
		if(whereExpr != null) whereExpr.resetState(postOptimization);
		returnExpr.resetState(postOptimization);
		if(orderSpecs != null) {
		    for(int i = 0; i < orderSpecs.length; i++) {
		        orderSpecs[i].resetState(postOptimization);
		    }
		}
	}
	
	protected final static void setContext(int contextId, Sequence seq) throws XPathException {
		if (seq instanceof VirtualNodeSet) {
			((VirtualNodeSet)seq).setInPredicate(true);
            ((VirtualNodeSet)seq).setSelfIsContext();
		} else {
			Item next;
			for (SequenceIterator i = seq.unorderedIterator(); i.hasNext();) {
				next = i.nextItem(); 
				if (next instanceof NodeProxy)
					 ((NodeProxy) next).addContextNode(contextId, (NodeProxy) next);
			}
		}
	}
	
	protected final static void clearContext(int contextId, Sequence seq) throws XPathException {
		if (seq != null && !(seq instanceof VirtualNodeSet)) {
            seq.clearContext(contextId);
		}
	}

    protected void registerUpdateListener(final Sequence sequence) {
        if (listener == null) {
            listener = new ExprUpdateListener(sequence);
            context.registerUpdateListener(listener);
        } else
            listener.setSequence(sequence);
    }

    private class ExprUpdateListener implements UpdateListener {
        private Sequence sequence;

        public ExprUpdateListener(Sequence sequence) {
            this.sequence = sequence;
        }

        public void setSequence(Sequence sequence) {
            this.sequence = sequence;
        }
        
        public void documentUpdated(DocumentImpl document, int event) {
        }

        public void nodeMoved(NodeId oldNodeId, StoredNode newNode) {
            sequence.nodeMoved(oldNodeId, newNode);
        }

        public void unsubscribe() {
            BindingExpression.this.listener = null;
        }

        public void debug() {
        }
    }
    
	public int getDependencies() {
		return returnExpr.getDependencies();
	}
}
