
/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU Library General Public
 *  License along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 *  $Id$
 */
package org.exist.xquery;

import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import org.exist.dom.ContextItem;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.VirtualNodeSet;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NumericValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 *  Handles predicate expressions.
 *
 *@author     Wolfgang Meier
 */
public class Predicate extends PathExpr {

	private final static int UNKNOWN = -1;
	private final static int NODE = 0;
    private final static int BOOLEAN = 1;
    private final static int POSITIONAL = 2;
    private final static int POTENTIALLY_POSITIONAL = 3;
    
	private CachedResult cached = null;
	
	private int executionMode = UNKNOWN;
	
    private int outerContextId;
    
    private boolean innerExpressionDot = false; 

    private Expression parent;

    public Predicate(XQueryContext context) {
		super(context);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.PathExpr#getDependencies()
	 */
	public int getDependencies() {
		int deps = 0;
		if(getLength() == 1) {
			deps = getExpression(0).getDependencies();
		} else {
			deps = super.getDependencies();
        }
		if (executionMode == POSITIONAL)
			// in a positional predicate, remove the dependency on the context item
			deps = deps & ~Dependency.CONTEXT_ITEM;
		return deps;
	}

    /* (non-Javadoc)
     * @see org.exist.xquery.PathExpr#analyze(org.exist.xquery.AnalyzeContextInfo)
     */
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        parent = contextInfo.getParent();
        AnalyzeContextInfo newContextInfo = new AnalyzeContextInfo(contextInfo);
    	newContextInfo.addFlag(IN_PREDICATE); // set flag to signal subexpression that we are in a predicate
    	newContextInfo.removeFlag(IN_WHERE_CLAUSE);	// remove where clause flag
    	newContextInfo.removeFlag(DOT_TEST);
        outerContextId = newContextInfo.getContextId();
        newContextInfo.setContextId(getExpressionId());
        
        newContextInfo.setStaticType(contextInfo.getStaticType());
    	newContextInfo.setParent(this);
        super.analyze(newContextInfo);
      
        //TODO : (0, 1, 2)[if(. eq 1) then 0 else position()]
        if ((newContextInfo.getFlags() & DOT_TEST) == DOT_TEST)
        	innerExpressionDot = true;
        
        Expression inner = getExpression(0);        

        // Case 1: predicate expression returns a node set. 
        // Check the returned node set against the context set 
        // and return all nodes from the context, for which the
		// predicate expression returns a non-empty sequence.
        final int innerType = inner.returnsType();
        if (Type.subTypeOf(innerType, Type.NODE)) {
            if(!Dependency.dependsOn(inner, Dependency.CONTEXT_ITEM))
                executionMode = NODE;
            else
                executionMode = BOOLEAN;
            
        // Case 2: predicate expression returns a number.
        } else if (Type.subTypeOf(innerType, Type.NUMBER)) {
        	if (inner.getCardinality() == Cardinality.EXACTLY_ONE
        		/*|| inner.getCardinality() == Cardinality.ZERO_OR_ONE)*/) 
        		//Just a hint : inner's cardinality may still be potential
        		executionMode = POSITIONAL;
        	else
        		executionMode = POTENTIALLY_POSITIONAL;
            
        // Case 3: predicate expression evaluates to a boolean.
        } else
            executionMode = BOOLEAN;
        
		if(executionMode == BOOLEAN) {
			newContextInfo.addFlag(SINGLE_STEP_EXECUTION);
		    // need to re-analyze:
		    super.analyze(newContextInfo);
		}
    }
    
	public Sequence evalPredicate(Sequence outerSequence, Sequence contextSequence,	int mode)
		    throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
        }            
        
        Sequence result;
		Expression inner = getExpression(0);
		if (inner == null)
			result = Sequence.EMPTY_SEQUENCE;
        else {
            int recomputedExecutionMode = executionMode; 
            
            //Try to promote a boolean evaluation to a positionnal one  
            if (executionMode == BOOLEAN) {
            	//Atomic sequences will keep evaluating "." in BOOLEAN mode
            	if (!innerExpressionDot && !Type.subTypeOf(contextSequence.getItemType(), Type.NODE)) {  
            		try {
	            		Sequence innerSeq = inner.eval(contextSequence); 
		                //Only if we have an actual *singleton* of numeric items
		                if (innerSeq.hasOne() && Type.subTypeOf(innerSeq.getItemType(), Type.NUMBER)) { 
		                    recomputedExecutionMode = POSITIONAL;
		            	}
            		} catch (XPathException e) {
            			//Keep in boolean mide : How ugly !!! 
            			//innerExpressionDot is false for (1,2,3)[. lt 3]
            		}          
            	}
            }  
            
            //Try to promote a boolean evaluation to a positionnal one  (second case)
            if (executionMode == BOOLEAN && !innerExpressionDot && !Dependency.dependsOn(inner, Dependency.CONTEXT_ITEM)) {
		        try {
		        	Sequence innerSeq = inner.eval(contextSequence); 
		            //Only if we have an actual *singleton* of numeric items
		            if (innerSeq.hasOne() && Type.subTypeOf(innerSeq.getItemType(), Type.NUMBER)) { 
	                    recomputedExecutionMode = POSITIONAL;
		        	}
	    		} catch (XPathException e) {
	    			//Keep in boolean mode : How ugly !!! 
	    			//innerExpressionDot is false for (1,2,3)[. lt 3]
	    		}		            
            }

            if (executionMode == NODE && Type.subTypeOf(contextSequence.getItemType(), Type.ATOMIC)
                    && !(contextSequence instanceof VirtualNodeSet)) {
            	//(1,2,2,4)[.]
	            if (innerExpressionDot && Type.subTypeOf(contextSequence.getItemType(), Type.NUMBER)) { 
                    recomputedExecutionMode = POSITIONAL;
	        	} else {
	        		recomputedExecutionMode = BOOLEAN;
	        	}
            }

            //(1,2,3)[xs:decimal(.)]
            //TODO : think and find *the* triggering condition that leads to boolean evaluation
            //ideally determine it at analysis time
            if (executionMode == POSITIONAL && innerExpressionDot && 
            		Type.subTypeOf(contextSequence.getItemType(), Type.ATOMIC)) {
                recomputedExecutionMode = BOOLEAN;
            }
            
            if (executionMode == POTENTIALLY_POSITIONAL) {
            	if (Type.subTypeOf(inner.returnsType(), Type.NUMBER) && !Dependency.dependsOn(inner, Dependency.CONTEXT_ITEM)) {
            		//Can't do anything else...
            		//TODO : pass this to selectByPosition() ?
            		Sequence innerSeq = inner.eval(contextSequence);
            		if (innerSeq.getItemCount() == 1)
            			recomputedExecutionMode = POSITIONAL;
            		else
            			recomputedExecutionMode = BOOLEAN;
            	} else {
            		boolean gotMany = false;
            		if (Type.subTypeOf(inner.returnsType(), Type.NUMBER)) {
            			boolean gotOne = false;
            			NumericValue nv = null;
	        			for (SequenceIterator i = contextSequence.iterate(); i.hasNext();) {
	        				Item item = i.nextItem();            
	        	            Sequence innerSeq = inner.eval(item.toSequence(), null);
	        	            if (innerSeq.getItemCount() > 1) {
	        	            	gotMany = true;
	        	            	break;
	        	            }
	        	            if (nv == null)
	        	            	nv = (NumericValue)innerSeq.itemAt(0);
	        	            else if (!nv.equals((NumericValue)innerSeq.itemAt(0))) {
	        	            	gotMany = true;
	        	            	break;
	        	            }	
	        			}
	        			
            		}
    				if (!gotMany) 
    					recomputedExecutionMode = POSITIONAL;
    				else
            			recomputedExecutionMode = BOOLEAN;
            	}
            }

    		switch(recomputedExecutionMode) {
    			case NODE: 
                    if (context.getProfiler().isEnabled())
                        context.getProfiler().message(this, Profiler.OPTIMIZATION_FLAGS, 
                                "OPTIMIZATION CHOICE", "Node selection");                    
                    result = selectByNodeSet(contextSequence);                    
                    break;
                case BOOLEAN: 
                    if (context.getProfiler().isEnabled())
                        context.getProfiler().message(this, Profiler.OPTIMIZATION_FLAGS, 
                                "OPTIMIZATION CHOICE", "Boolean evaluation");                    
                    result = evalBoolean(contextSequence, inner);
                    break;
    			case POSITIONAL: 
                    if (context.getProfiler().isEnabled())
                        context.getProfiler().message(this, Profiler.OPTIMIZATION_FLAGS, 
                                "OPTIMIZATION CHOICE", "Positional evaluation");
                    result = selectByPosition(outerSequence, contextSequence, mode, inner);
                    break;
    			default:
                    throw new IllegalArgumentException("Unsupported execution mode: '" + recomputedExecutionMode + "'");			    
    		}            
        }
        
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result);
        
        return result;

	}

	/**
	 * @param contextSequence
	 * @param inner
	 * @return The result of the boolean evaluation of the predicate.
	 * @throws XPathException
	 */
	private Sequence evalBoolean(Sequence contextSequence, Expression inner) throws XPathException {
		Sequence result = new ValueSequence();
		int p;
		if (contextSequence instanceof NodeSet && ((NodeSet)contextSequence).getProcessInReverseOrder()) {
			//This one may be expensive...
			p = contextSequence.getItemCount();
			for (SequenceIterator i = contextSequence.iterate(); i.hasNext(); p--) {
				//0-based
	            context.setContextPosition(p - 1); 
				Item item = i.nextItem();            
	            Sequence innerSeq = inner.eval(item.toSequence(), null);
				if(innerSeq.effectiveBooleanValue())
					result.add(item);
			}
		} else {
			//0-based
			p = 0;
			for (SequenceIterator i = contextSequence.iterate(); i.hasNext(); p++) {
				context.setContextPosition(p); 
				Item item = i.nextItem();            
	            Sequence innerSeq = inner.eval(contextSequence, item);
				if(innerSeq.effectiveBooleanValue())
					result.add(item);				
			}
		}
		return result;
	}

	/**
	 * @param contextSequence
	 * @return The result of the node set evaluation of the predicate.
	 * @throws XPathException
	 */
	private Sequence selectByNodeSet(Sequence contextSequence) throws XPathException {
		ExtArrayNodeSet result = new ExtArrayNodeSet();
		NodeSet contextSet = contextSequence.toNodeSet();
		boolean contextIsVirtual = contextSet instanceof VirtualNodeSet;
		
		/*
		//Uncomment the lines below which are intended to work around a VirtualNodeSet bug
		//No need to say that performance can suffer !
		NodeSet nodes;
		if (contextIsVirtual) {
			ArraySet copy = new ArraySet(contextSet.getLength());
			for (Iterator i = contextSet.iterator(); i.hasNext();) {
				copy.add((Item)i.next());
			}
			nodes =	super.eval(copy, null).toNodeSet();
		} else
			nodes =	super.eval(contextSet, null).toNodeSet();			
		//End of work-around
		*/
		
		//Comment the line below if you have uncommented the lines above :-)
		NodeSet nodes =	super.eval(contextSet, null).toNodeSet();
		
		/* if the predicate expression returns results from the cache
		 * we can also return the cached result. 
		 */
		if(cached != null && cached.isValid(contextSequence) && nodes.isCached()) {
			LOG.debug("Using cached results");
            if (context.getProfiler().isEnabled())                     
                context.getProfiler().message(this, Profiler.OPTIMIZATIONS, 
                        "Using cached results", result);
            return cached.getResult();
        }
       
		DocumentImpl lastDoc = null;      
		for (Iterator i = nodes.iterator(); i.hasNext();) {               
            NodeProxy currentNode = (NodeProxy) i.next();
            int sizeHint = Constants.NO_SIZE_HINT;
			if(lastDoc == null || currentNode.getDocument() != lastDoc) {
				lastDoc = currentNode.getDocument();
				sizeHint = nodes.getSizeHint(lastDoc);
			}
            ContextItem contextItem = currentNode.getContext();
			if (contextItem == null) {
				throw new XPathException("Internal evaluation error: context is missing for node " +
                        currentNode.getNodeId() + " !");
			}
           //TODO : review to consider transverse context
			while (contextItem != null) {
				if (contextItem.getContextId() == getExpressionId()) {
	                NodeProxy next = contextItem.getNode();
					if(contextIsVirtual || contextSet.contains(next)) {    
						next.addMatches(currentNode);
						result.add(next, sizeHint);
					}
				}
				contextItem = contextItem.getNextDirect();
			}
		}
        
		if (contextSequence instanceof NodeSet)
			cached = new CachedResult((NodeSet)contextSequence, result);
        
		return result;
	}

	/**
	 * @param outerSequence
	 * @param contextSequence
	 * @param mode
	 * @param inner
	 * @return The result of the positional evaluation of the predicate.
	 * @throws XPathException
	 */
	private Sequence selectByPosition(Sequence outerSequence, Sequence contextSequence, int mode, Expression inner) throws XPathException {
		if(outerSequence != null && !outerSequence.isEmpty() && 
				Type.subTypeOf(contextSequence.getItemType(), Type.NODE)) {
			Sequence result = new ExtArrayNodeSet(100);
			NodeSet contextSet = contextSequence.toNodeSet();
            switch(mode) {
            case Constants.CHILD_AXIS:
            case Constants.ATTRIBUTE_AXIS:
            case Constants.DESCENDANT_AXIS:
            case Constants.DESCENDANT_SELF_AXIS:
            case Constants.DESCENDANT_ATTRIBUTE_AXIS: 
            {
        		
        		NodeSet outerNodeSet;
        		
        		//Ugly and costly processing of VirtualNodeSets
        		//TODO : CORRECT THIS !!!
        		
        		if (outerSequence instanceof VirtualNodeSet) {

        			outerNodeSet = new ExtArrayNodeSet();
        			for (int i = 0 ; i < outerSequence.getItemCount() ; i++) {
        				outerNodeSet.add(outerSequence.itemAt(i));
        			}
        			
        		} else outerNodeSet = outerSequence.toNodeSet();       		
        		
        		//Comment the line below if you have uncommented the lines above :-)
            	
            	//TODO: in some cases, especially with in-memory nodes, 
            	//outerSequence.toNodeSet() will generate a document
            	//which will be different from the one(s) in contextSet
            	//ancestors will thus be empty :-(
            	if (outerSequence instanceof VirtualNodeSet)
            		((VirtualNodeSet)outerSequence).realize();
//                Sequence ancestors = outerNodeSet.selectAncestors(contextSet, true, getExpressionId());
                Sequence ancestors = contextSet.selectAncestorDescendant(outerNodeSet,
						NodeSet.ANCESTOR, true, getExpressionId());
				if (contextSet.getDocumentSet().intersection(outerNodeSet.getDocumentSet()).getLength() == 0)
					LOG.info("contextSet and outerNodeSet don't share any document");
				ExtArrayNodeSet temp = new ExtArrayNodeSet(100);
				for(SequenceIterator i = ancestors.iterate(); i.hasNext(); ) {
				    NodeProxy p = (NodeProxy)i.nextItem();
				    ContextItem contextNode = p.getContext();
				    temp.reset();
				    while (contextNode != null) {
				    	if (contextNode.getContextId() == getExpressionId())
				    		temp.add(contextNode.getNode());
				    	contextNode = contextNode.getNextDirect();
				    }
                    //TODO : understand why we sort here...
				    temp.sortInDocumentOrder();
				    
                    Sequence innerSeq = inner.eval(contextSequence);                    
				    for(SequenceIterator j = innerSeq.iterate(); j.hasNext(); ) {				      
				        NumericValue v = (NumericValue)j.nextItem();
				        //Non integers return... nothing, not even an error !
				        if (!v.hasFractionalPart()) {
					        //... whereas we don't want a sorted array here
	                        //TODO : rename this method as getInDocumentOrder ? -pb
					        p = temp.get(v.getInt() - 1);
					        if (p != null) {
					        	//Commented out : but this is probably more complicated (see test case in the same commit)
					        	//p.clearContext(Expression.IGNORE_CONTEXT);
					        	result.add(p);
					        }
	                        //TODO : does null make sense here ? Well... sometimes ;-)
				        }
				    }
				}
                break;
            }
            default:
				for (SequenceIterator i = outerSequence.iterate(); i.hasNext(); ) {				   
				    NodeProxy p = (NodeProxy)i.nextItem();
				    Sequence temp;
                    boolean reverseAxis = true;
                    switch(mode) {
                    case Constants.ANCESTOR_AXIS:                            
                        temp = contextSet.selectAncestors(p, false, Expression.IGNORE_CONTEXT);
                        break;
                    case Constants.ANCESTOR_SELF_AXIS:
                       temp = contextSet.selectAncestors(p, true, Expression.IGNORE_CONTEXT);
                        break;
                    case Constants.PARENT_AXIS:
                        //TODO : understand why the contextSet is not involved here
                        //NodeProxy.getParent returns a *theoretical* parent 
                        //which is *not* guaranteed to be in the context set !
                        temp = p.getParents(Expression.NO_CONTEXT_ID);
                        break;                            
                    case Constants.PRECEDING_AXIS:
                        temp = contextSet.selectPreceding(p);
                        break;
                    case Constants.PRECEDING_SIBLING_AXIS:
                        temp = contextSet.selectPrecedingSiblings(p, Expression.IGNORE_CONTEXT);
                        break;
                    case Constants.FOLLOWING_SIBLING_AXIS:
                        temp = contextSet.selectFollowingSiblings(p, Expression.IGNORE_CONTEXT);
                        reverseAxis = false;
                        break;    
                    case Constants.FOLLOWING_AXIS:
                        temp = contextSet.selectFollowing(p);
                        reverseAxis = false;
                        break;
                    case Constants.SELF_AXIS:
                        temp = p;
                        reverseAxis = false;
                        break;                            
                    default:                            
                        //temp = contextSet.selectAncestorDescendant(p, NodeSet.DESCENDANT, false, false);
                        //break;
                        throw new IllegalArgumentException("Tested unknown axis");
                    }
                    if (!temp.isEmpty()) {
                        //TODO : build a value sequence *one* time ? -pb
                        Sequence innerSeq = inner.eval(contextSequence);                        
                        for(SequenceIterator j = innerSeq.iterate(); j.hasNext(); ) {                    
                            NumericValue v = (NumericValue)j.nextItem();                             
            		        //Non integers return... nothing, not even an error !
            		        if (!v.hasFractionalPart()) {
	                            int pos = (reverseAxis ? temp.getItemCount() - v.getInt() : v.getInt() - 1);
	                            //Other positions are ignored
	                            if (pos >= 0 && pos < temp.getItemCount()) {
	                                NodeProxy t = (NodeProxy) temp.itemAt(pos);
	                                // for the current context: filter out those context items
	                                // not selected by the positional predicate
	                                ContextItem ctx = t.getContext();
	                                t.clearContext(Expression.IGNORE_CONTEXT);
	                                while (ctx != null) {
	                                    if (ctx.getContextId() == outerContextId) {
	                                        if (ctx.getNode().getNodeId().equals(p.getNodeId()))
	                                            t.addContextNode(outerContextId, ctx.getNode());
	                                    } else
	                                        t.addContextNode(ctx.getContextId(), ctx.getNode());
	                                    ctx = ctx.getNextDirect();
	                                }
	                                result.add(t);
	                            }
            		        }
                        }
                    }
    			}
			}
			return result;
		} else { 
			Set set = new TreeSet(); 
            ValueSequence result = new ValueSequence();
			Sequence innerSeq = inner.eval(contextSequence);
			for(SequenceIterator i = innerSeq.iterate(); i.hasNext();) {
				NumericValue v = (NumericValue)i.nextItem();
		        //Non integers return... nothing, not even an error !
		        if (!v.hasFractionalPart()) {
					int pos = v.getInt();
	                //Other positions are ignored    
					if (pos > 0 && pos <= contextSequence.getItemCount() && !set.contains(v)) {
						result.add(contextSequence.itemAt(pos - 1));						
						set.add(v);
					}
		        }
			}
			return result;
		}
	}

	public void setContextDocSet(DocumentSet contextSet) {
		super.setContextDocSet(contextSet);
		if (getLength() > 0)
			getExpression(0).setContextDocSet(contextSet);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.PathExpr#resetState()
	 */
	public void resetState() {       
		super.resetState();
		cached = null;
	}

    public Expression getParent() {
        return parent;
    }
    
    public void accept(ExpressionVisitor visitor) {
        visitor.visitPredicate(this);
    }
    
    public void dump(ExpressionDumper dumper) {
    	dumper.display("[");
  		super.dump(dumper);
  		dumper.display("]");
  }	
	
	public String toString() {
		return "[" + super.toString() + "]";
	}
}
