/*
 *  eXist Open Source Native XML Database
 * 
 *  Copyright (C) 2000-03, Wolfgang M. Meier (meier@ifs. tu- darmstadt. de)
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
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 * $Id$
 */
package org.exist.xquery;

import java.text.Collator;
import java.util.Iterator;

import org.exist.EXistException;
import org.exist.dom.ContextItem;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.VirtualNodeSet;
import org.exist.storage.DBBroker;
import org.exist.storage.FulltextIndexSpec;
import org.exist.storage.IndexSpec;
import org.exist.storage.Indexable;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;

/**
 * A general XQuery/XPath2 comparison expression.
 * 
 * @author wolf
 */
public class GeneralComparison extends BinaryOp {

	/**
	 * The type of operator used for the comparison, i.e. =, !=, &lt;, &gt; ...
	 * One of the constants declared in class {@link Constants}.
	 */
	protected int relation = Constants.EQ;
	
	/**
	 * Truncation flags: when comparing with a string value, the search
	 * string may be truncated with a single * wildcard. See the constants declared
	 * in class {@link Constants}.
	 * 
	 * The standard functions starts-with, ends-with and contains are
	 * transformed into a general comparison with wildcard. Hence the need
	 * to consider wildcards here.
	 */
	protected int truncation = Constants.TRUNC_NONE;
	
	/**
	 * The class might cache the entire results of a previous execution.
	 */
	protected CachedResult cached = null;

	/**
	 * Extra argument (to standard functions starts-with/contains etc.)
	 * to indicate the collation to be used for string comparisons.
	 */
	protected Expression collationArg = null;
	
	/**
	 * Set to true if this expression is called within the where clause
	 * of a FLWOR expression.
	 */
	protected boolean inWhereClause = false;
    
    protected boolean invalidNodeEvaluation = false;

    protected int rightOpDeps;
    
	public GeneralComparison(XQueryContext context, int relation) {
		this(context, relation, Constants.TRUNC_NONE);
	}
	
	public GeneralComparison(XQueryContext context, int relation, int truncation) {
		super(context);
		this.relation = relation;
	}

	public GeneralComparison(XQueryContext context, Expression left, Expression right, int relation) {
		this(context, left, right, relation, Constants.TRUNC_NONE);
	}
	
	public GeneralComparison(XQueryContext context,	Expression left, Expression right, int relation, 
            int truncation) {        
		super(context);
        boolean didLeftSimplification = false;
        boolean didRightSimplification = false;
		this.relation = relation;
		this.truncation = truncation;		
		if (left instanceof PathExpr && ((PathExpr) left).getLength() == 1) {
			left = ((PathExpr) left).getExpression(0);
            didLeftSimplification = true;  
		}
		add(left);		
		if (right instanceof PathExpr && ((PathExpr) right).getLength() == 1) {
            right = ((PathExpr) right).getExpression(0);            
            didRightSimplification = true;
		}
		add(right);
        //TODO : should we also use simplify() here ? -pb
		if (didLeftSimplification)
            context.getProfiler().message(this, Profiler.OPTIMIZATIONS, "OPTIMIZATION",  
            "Marked left argument as a child expression");
        if (didRightSimplification)
            context.getProfiler().message(this, Profiler.OPTIMIZATIONS, "OPTIMIZATION",  
            "Marked right argument as a child expression"); 
	}

    /* (non-Javadoc)
     * @see org.exist.xquery.BinaryOp#analyze(org.exist.xquery.AnalyzeContextInfo)
     */
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
    	contextInfo.setParent(this);
        super.analyze(contextInfo);
        inWhereClause = (contextInfo.getFlags() & IN_WHERE_CLAUSE) != 0;

        //Ugly workaround for the polysemy of "." which is expanded as self::node() even when it is not relevant
        // (1)[.= 1] works...
        invalidNodeEvaluation = false;
        if (!Type.subTypeOf(contextInfo.getStaticType(), Type.NODE))
    		invalidNodeEvaluation = getLeft() instanceof LocationStep && ((LocationStep)getLeft()).axis == Constants.SELF_AXIS;
        
        //Unfortunately, we lose the possibility to make a nodeset optimization 
        //(we still don't know anything about the contextSequence that will be processed)

        // check if the right-hand operand is a simple cast expression
        // if yes, use the dependencies of the casted expression to compute
        // optimizations
        rightOpDeps = getRight().getDependencies();
        getRight().accept(new BasicExpressionVisitor() {
        	public void visitCastExpr(CastExpression expression) {
        		if (LOG.isTraceEnabled())
        			LOG.debug("Right operand is a cast expression");
        		rightOpDeps = expression.getInnerExpression().getDependencies();
        	}
        });
    }
    
	/* (non-Javadoc)
	 * @see org.exist.xquery.BinaryOp#returnsType()
	 */
	public int returnsType() {
		if (inPredicate && !invalidNodeEvaluation && (!Dependency.dependsOn(this, Dependency.CONTEXT_ITEM))) {
			/* If one argument is a node set we directly
			 * return the matching nodes from the context set. This works
			 * only inside predicates.
			 */
			return Type.NODE;
		}
		// In all other cases, we return boolean
		return Type.BOOLEAN;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.AbstractExpression#getDependencies()
	 */
	public int getDependencies() {
		// left expression returns node set
		if (Type.subTypeOf(getLeft().returnsType(), Type.NODE) &&
			//	and does not depend on the context item
			!Dependency.dependsOn(getLeft(), Dependency.CONTEXT_ITEM) &&
			(!inWhereClause || !Dependency.dependsOn(getLeft(), Dependency.CONTEXT_VARS)))
		{
			return Dependency.CONTEXT_SET;
		} else { 
			return Dependency.CONTEXT_SET + Dependency.CONTEXT_ITEM;
		}
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.xquery.StaticContext, org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        
		//Profiling for eval
		if (context.getProfiler().isEnabled())
        {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            if (contextItem != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
        }

        Sequence result = null;

		/* 
		 * If we are inside a predicate and one of the arguments is a node set, 
		 * we try to speed up the query by returning nodes from the context set.
		 * This works only inside a predicate. The node set will always be the left 
		 * operand.
		 */     
		if (inPredicate && !invalidNodeEvaluation) {
			
			if (!(Dependency.dependsOn(this, Dependency.CONTEXT_ITEM)) &&
					Type.subTypeOf(getLeft().returnsType(), Type.NODE)) {
                
                if(contextItem != null)
                    contextSequence = contextItem.toSequence();                                
                
                if ((rightOpDeps & Dependency.CONTEXT_ITEM) == 0) 
                	// &&
                    //    Type.subTypeOf(getRight().returnsType(), Type.NODE))
				{
					result = quickNodeSetCompare(contextSequence);
				}
				else
				{      
					result = nodeSetCompare(contextSequence);
				}
            }            
		}
		
        //TODO : better design. Should a (buggy) null previous result be returned, we would evaluate this !
        if(result == null) {
            result = genericCompare(contextSequence, contextItem);
        }
        
        if (context.getProfiler().isEnabled())           
            context.getProfiler().end(this, "", result);
        
        return result;
	}

	/**
	 * Generic, slow implementation. Applied if none of the possible 
	 * optimizations can be used.
	 * 
	 * @param contextSequence
	 * @param contextItem
	 * @return
	 * @throws XPathException
	 */
	protected Sequence genericCompare(Sequence contextSequence,	Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled())
            context.getProfiler().message(this, Profiler.OPTIMIZATION_FLAGS, 
                    "OPTIMIZATION CHOICE", "genericCompare");
		Sequence ls = getLeft().eval(contextSequence, contextItem);
		Sequence rs = getRight().eval(contextSequence, contextItem);
		Collator collator = getCollator(contextSequence);
		AtomicValue lv, rv;
		if (ls.hasOne() && rs.hasOne()) {
			lv = ls.itemAt(0).atomize();
			rv = rs.itemAt(0).atomize();
			return BooleanValue.valueOf(compareValues(collator, lv, rv));
		} else {
			for (SequenceIterator i1 = ls.iterate(); i1.hasNext();) {
				lv = i1.nextItem().atomize();				
				if (rs.hasOne()	&& 
                    compareValues(collator, lv, rs.itemAt(0).atomize()))
					return BooleanValue.TRUE;
				else {
					for (SequenceIterator i2 = rs.iterate(); i2.hasNext();) {
						rv = i2.nextItem().atomize();
						if (compareValues(collator, lv, rv))
							return BooleanValue.TRUE;
					}
				}
			}
		}
		return BooleanValue.FALSE;
	}

	/**
	 * Optimized implementation, which can be applied if the left operand
	 * returns a node set. In this case, the left expression is executed first.
	 * All matching context nodes are then passed to the right expression.
	 */
	protected Sequence nodeSetCompare(Sequence contextSequence)	throws XPathException {		
		NodeSet nodes = (NodeSet) getLeft().eval(contextSequence);
		return nodeSetCompare(nodes, contextSequence);
	}

	protected Sequence nodeSetCompare(NodeSet nodes, Sequence contextSequence) throws XPathException {
        if (context.getProfiler().isEnabled())
            context.getProfiler().message(this, Profiler.OPTIMIZATION_FLAGS, "OPTIMIZATION CHOICE", "nodeSetCompare");
        if (LOG.isTraceEnabled())
        	LOG.trace("No index: fall back to nodeSetCompare");
		NodeSet result = new ExtArrayNodeSet();
		Collator collator = getCollator(contextSequence);
		if(contextSequence != null && !contextSequence.isEmpty())
		{
			for (Iterator i = nodes.iterator(); i.hasNext();)
			{
				NodeProxy current = (NodeProxy) i.next();
				ContextItem context = current.getContext();
				if (context == null)
					throw new XPathException(getASTNode(), "Internal error: context node missing");
				AtomicValue lv = current.atomize();
                //TODO : review to consider transverse context
				do
				{					
					Sequence rs = getRight().eval(context.getNode().toSequence());
					for (SequenceIterator si = rs.iterate(); si.hasNext();)
					{                        
						AtomicValue rv = si.nextItem().atomize();
						if (compareValues(collator, lv, rv))
						{
							result.add(current);
						}
					}
				}while ((context = context.getNextDirect()) != null);
			}
		}
		else
		{
		    for (Iterator i = nodes.iterator(); i.hasNext();)
		    {
		    	NodeProxy current = (NodeProxy) i.next();	
				AtomicValue lv = current.atomize();
				Sequence rs = getRight().eval(null);
				for (SequenceIterator si = rs.iterate(); si.hasNext();)
				{
					AtomicValue rv = si.nextItem().atomize();
					if (compareValues(collator, lv, rv))
					{
						result.add(current);
					}
				}
		    }
		}
		return result;
	}

	/**
	 * Optimized implementation: first checks if a range index is defined
	 * on the nodes in the left argument. If that fails, check if we can use
	 * the fulltext index to speed up the search. Otherwise, fall back to
	 * {@link #nodeSetCompare(NodeSet, Sequence)}.
	 */
	protected Sequence quickNodeSetCompare(Sequence contextSequence) throws XPathException {
		
		/* TODO think about optimising fallback to NodeSetCompare() in the for loop!!!
		 * At the moment when we fallback to NodeSetCompare() we are in effect throwing away any nodes
		 * we have already processed in quickNodeSetCompare() and reprocessing all the nodes in NodeSetCompare().
		 * Instead - Could we create a NodeCompare() (based on NodeSetCompare() code) to only compare a single node and then union the result?
		 * - deliriumsky 
		 */
		
		/* TODO think about caching of results in this function...
		 * also examine and check if correct (line near the end) -
		 * 	 boolean canCache = contextSequence instanceof NodeSet && (getRight().getDependencies() & Dependency.VARS) == 0 && (getLeft().getDependencies() & Dependency.VARS) == 0;
		 *  - deliriumsky 
		 */
		
		if (context.getProfiler().isEnabled())
			context.getProfiler().message(this, Profiler.OPTIMIZATION_FLAGS, "OPTIMIZATION CHOICE", "quickNodeSetCompare");

		// if the context sequence hasn't changed we can return a cached result
		if(cached != null && cached.isValid(contextSequence))
		{
            if(context.getProfiler().isEnabled())
            {
                context.getProfiler().message(this, Profiler.OPTIMIZATIONS, "OPTIMIZATION", "Returned cached result");
            }
            
			return(cached.getResult());
		}

		//get the NodeSet on the left
		NodeSet nodes = (NodeSet) getLeft().eval(contextSequence);		
        if(!(nodes instanceof VirtualNodeSet) && nodes.isEmpty()) //nothing on the left, so nothing to do
        {
            return(Sequence.EMPTY_SEQUENCE);
        }

        //get the Sequence on the right
		Sequence rightSeq = getRight().eval(contextSequence);
		if(rightSeq.isEmpty())	//nothing on the right, so nothing to do
		{
            return(Sequence.EMPTY_SEQUENCE);
		}
		
		//Holds the result
		NodeSet result = null;
		
		//get the type of a possible index
		int indexType = nodes.getIndexType();
		//See if we have a range index defined on the nodes in this sequence
        //TODO : use isSubType ??? -pb
	    if(indexType != Type.ITEM)
	    {
	    	if (LOG.isTraceEnabled())
	    		LOG.trace("found an index of type: " + Type.getTypeName(indexType));
	    	//Get the documents from the node set
			DocumentSet docs = nodes.getDocumentSet();
	
			//Iterate through the right hand sequence
			for(SequenceIterator itRightSeq = rightSeq.iterate(); itRightSeq.hasNext();)
	    	{
				//Get the index key
				Item key = itRightSeq.nextItem().atomize();
				
				//if key has truncation, convert it to string
		        if(truncation != Constants.TRUNC_NONE)
		        {
		        	//TODO : log this conversion ? -pb
		        	//truncation is only possible on strings
		        	key = key.convertTo(Type.STRING);
		        }
		        //else if key is not the same type as the index
                //TODO : use isSubType ??? -pb
		        else if(key.getType() != indexType)
		        {
		        	//try and convert the key to the index type 
	            	try
					{
	            		key = key.convertTo(indexType);
					}
	            	catch(XPathException xpe)
					{
	            		//TODO : rethorw the exception ? -pb
	            		
			        	//Could not convert the key to a suitable type for the index, fallback to nodeSetCompare()
		                if(context.getProfiler().isEnabled())
		                {
		                    context.getProfiler().message(this, Profiler.OPTIMIZATION_FLAGS, "OPTIMIZATION FALLBACK", "Falling back to nodeSetCompare (" + xpe.getMessage() + ")");
		                }
		                if (LOG.isTraceEnabled())
		                	LOG.trace("Cannot convert key: " + Type.getTypeName(key.getType()) + " to required index type: " + Type.getTypeName(indexType));
		                
			            return nodeSetCompare(nodes, contextSequence);
					}
		        }
		        

		        if(key instanceof Indexable) {
		        	if (LOG.isTraceEnabled())
		        		LOG.trace("Checking if range index can be used for key: " + key.getStringValue());
			        // If key implements org.exist.storage.Indexable, we can use the index
		        	if (Type.subTypeOf(key.getType(), indexType)) {
			        	if(truncation == Constants.TRUNC_NONE) {
			        		if (LOG.isTraceEnabled())
			        			LOG.trace("Using range index for key: " + key.getStringValue());
				        	//key without truncation, find key
		                    context.getProfiler().message(this, Profiler.OPTIMIZATIONS, "OPTIMIZATION", "Using value index '" + context.getBroker().getValueIndex().toString() + 
		                    		"' to find key '" + Type.getTypeName(key.getType()) + "(" + key.getStringValue() + ")'");
		                    
		                    NodeSet ns = context.getBroker().getValueIndex().find(relation, docs, nodes, (Indexable)key);
							if (result == null)
								result = ns;
							else
								result = result.union(ns);							
		                }
			        	else
			        	{
				        	//key with truncation, match key
                            if (LOG.isTraceEnabled())
                                context.getProfiler().message(this, Profiler.OPTIMIZATIONS, "OPTIMIZATION", "Using value index '" + context.getBroker().getValueIndex().toString() +
		                    		"' to match key '" + Type.getTypeName(key.getType()) + "(" + key.getStringValue() + ")'");
							try
							{
								if (LOG.isTraceEnabled())
				        			LOG.trace("Using range index for key: " + key.getStringValue());
								NodeSet ns = context.getBroker().getValueIndex().match(docs, nodes,
                                        getRegexp(key.getStringValue()).toString(), DBBroker.MATCH_REGEXP);
								if (result == null)
									result = ns;
								else
									result = result.union(ns);
							} catch (EXistException e) {
								throw new XPathException(getASTNode(), e.getMessage(), e);
							}
						}
			        } else {
			        	//the datatype of our key does not
			        	//implement org.exist.storage.Indexable or is not of the correct type
		                if(context.getProfiler().isEnabled())
		                {
		                    context.getProfiler().message(this, Profiler.OPTIMIZATION_FLAGS, "OPTIMIZATION FALLBACK", "Falling back to nodeSetCompare (key is of type: " + 
		                    		Type.getTypeName(key.getType()) + ") whereas index is of type '" + Type.getTypeName(indexType) + "'");
		                }
		                if (LOG.isTraceEnabled())
		                	LOG.trace("Cannot use range index: key is of type: " + Type.getTypeName(key.getType()) + ") whereas index is of type '" + 
		                			Type.getTypeName(indexType));
	                    return(nodeSetCompare(nodes, contextSequence));
			        }
		        } else {
		        	//the datatype of our key does not implement org.exist.storage.Indexable
	                if(context.getProfiler().isEnabled())
	                {
	                    context.getProfiler().message(this, Profiler.OPTIMIZATION_FLAGS, "OPTIMIZATION FALLBACK", "Falling back to nodeSetCompare (key is not an indexable type: " + 
	                    		key.getClass().getName());
	                }
                    return(nodeSetCompare(nodes, contextSequence));
		        	
		        }

            }

		}
	    else
	    {
	    	if (LOG.isTraceEnabled())
	    		LOG.trace("No suitable index found for key: " + rightSeq.getStringValue());
	    	//no range index defined on the nodes in this sequence, so fallback to nodeSetCompare
            if(context.getProfiler().isEnabled())
            {
                context.getProfiler().message(this, Profiler.OPTIMIZATION_FLAGS, "OPTIMIZATION FALLBACK", "falling back to nodeSetCompare (no index available)");
            }

            return(nodeSetCompare(nodes, contextSequence));
		}
        
		// can this result be cached? Don't cache if the result depends on local variables.
	    boolean canCache = contextSequence instanceof NodeSet && 
	    	!Dependency.dependsOnVar(getLeft()) && 
	    	!Dependency.dependsOnVar(getRight());
		if(canCache)
		{
			cached = new CachedResult((NodeSet)contextSequence, result);
		}
		
		//return the result of the range index lookup(s) :-)
		return result;
	}

    private CharSequence getRegexp(String expr) {
        switch (truncation) {
            case Constants.TRUNC_LEFT :
                return new StringBuffer().append(expr).append('$');
            case Constants.TRUNC_RIGHT :
                return new StringBuffer().append('^').append(expr);
            default :
                return expr;
        }
    }
	
	/**
	 * Cast the atomic operands into a comparable type
	 * and compare them.
	 */
	protected boolean compareValues(Collator collator, AtomicValue lv, AtomicValue rv) throws XPathException {
		try {
			return compareAtomic(collator, lv, rv, context.isBackwardsCompatible(), truncation, relation);
		} catch (XPathException e) {
			e.setASTNode(getASTNode());
			throw e;
		}
	}

	public static boolean compareAtomic(Collator collator, AtomicValue lv, AtomicValue rv, 
            boolean backwardsCompatible, int truncation, int relation) throws XPathException{
		int ltype = lv.getType();
		int rtype = rv.getType();
		if (ltype == Type.UNTYPED_ATOMIC) {
			//If one of the atomic values is an instance of xdt:untypedAtomic 
			//and the other is an instance of a numeric type, 
			//then the xdt:untypedAtomic value is cast to the type xs:double.			
			if (Type.subTypeOf(rtype, Type.NUMBER)) {
			    if(isEmptyString(lv))
			        return false;
				lv = lv.convertTo(Type.DOUBLE);
			//If one of the atomic values is an instance of xdt:untypedAtomic 
			//and the other is an instance of xdt:untypedAtomic or xs:string, 
			//then the xdt:untypedAtomic value (or values) is (are) cast to the type xs:string.
			} else if (rtype == Type.UNTYPED_ATOMIC || rtype == Type.STRING) {
				lv = lv.convertTo(Type.STRING);
				if (rtype == Type.UNTYPED_ATOMIC)
					rv = rv.convertTo(Type.STRING);
				//If one of the atomic values is an instance of xdt:untypedAtomic 
				//and the other is not an instance of xs:string, xdt:untypedAtomic, or any numeric type, 
				//then the xdt:untypedAtomic value is cast to the dynamic type of the other value.
			} else
				lv = lv.convertTo(rv.getType());
		} else if (rtype == Type.UNTYPED_ATOMIC) {
			//If one of the atomic values is an instance of xdt:untypedAtomic 
			//and the other is an instance of a numeric type, 
			//then the xdt:untypedAtomic value is cast to the type xs:double.			
			if (Type.subTypeOf(ltype, Type.NUMBER)) {
			    if(isEmptyString(lv))
			        return false;
				rv = rv.convertTo(Type.DOUBLE);
			//If one of the atomic values is an instance of xdt:untypedAtomic 
			//and the other is an instance of xdt:untypedAtomic or xs:string, 
			//then the xdt:untypedAtomic value (or values) is (are) cast to the type xs:string.
			} else if (ltype == Type.UNTYPED_ATOMIC || ltype == Type.STRING) {
				rv = rv.convertTo(Type.STRING);
				if (ltype == Type.UNTYPED_ATOMIC)
					lv = lv.convertTo(Type.STRING);
			//If one of the atomic values is an instance of xdt:untypedAtomic 
			//and the other is not an instance of xs:string, xdt:untypedAtomic, or any numeric type, 
			//then the xdt:untypedAtomic value is cast to the dynamic type of the other value.
			} else
				rv = rv.convertTo(lv.getType());
		}
		if (backwardsCompatible) {
			if (!"".equals(lv.getStringValue()) && !"".equals(rv.getStringValue())) {
				// in XPath 1.0 compatible mode, if one of the operands is a number, cast
				// both operands to xs:double
				if (Type.subTypeOf(ltype, Type.NUMBER)
					|| Type.subTypeOf(rtype, Type.NUMBER)) {
						lv = lv.convertTo(Type.DOUBLE);
						rv = rv.convertTo(Type.DOUBLE);
				}
			}
		}
        // if truncation is set, we always do a string comparison
        if (truncation != Constants.TRUNC_NONE) {
            lv = lv.convertTo(Type.STRING);
        }
//			System.out.println(
//				lv.getStringValue() + Constants.OPS[relation] + rv.getStringValue());
		switch(truncation) {
			case Constants.TRUNC_RIGHT:
				return lv.startsWith(collator, rv);
			case Constants.TRUNC_LEFT:
				return lv.endsWith(collator, rv);
			case Constants.TRUNC_BOTH:
				return lv.contains(collator, rv);
			default:
				return lv.compareTo(collator, relation, rv);
		}
	}
	
	/**
     * @param lv
     * @return
	 * @throws XPathException
     */
    private static boolean isEmptyString(AtomicValue lv) throws XPathException {
        if(Type.subTypeOf(lv.getType(), Type.STRING) || lv.getType() == Type.ATOMIC) {           
            if(lv.getStringValue().length() == 0)
                return true;
        }
        return false;
    }

    private boolean checkArgumentTypes(XQueryContext context, DocumentSet docs)	throws XPathException {			 
		for (Iterator i = docs.iterator(); i.hasNext();) {
            DocumentImpl doc = (DocumentImpl) i.next();
            IndexSpec idxSpec = doc.getCollection().getIdxConf(context.getBroker());
			if(idxSpec != null) {
                FulltextIndexSpec idx = idxSpec.getFulltextIndexSpec();
                if (idx != null) {
    			    if(idx.isSelective())
    			        return true;
    			    if(!idx.getIncludeAlphaNum())
    			        return true;
                }
			}
		}
		return false;
	}

	/* (non-Javadoc)
     * @see org.exist.xquery.PathExpr#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {        
        if (truncation == Constants.TRUNC_BOTH) {
        	dumper.display("contains").display('(');
        	getLeft().dump(dumper);
        	dumper.display(", ");
        	getRight().dump(dumper);
        	dumper.display(")");
        } else {
        	getLeft().dump(dumper);        
        	dumper.display(' ').display(Constants.OPS[relation]).display(' ');        	
        	getRight().dump(dumper);
        }
    }
    
    public String toString() {
    	StringBuffer result = new StringBuffer();    	
    	if (truncation == Constants.TRUNC_BOTH) {    		
    		result.append("contains").append('(');
    		result.append(getLeft().toString());
    		result.append(", ");
    		result.append(getRight().toString());
    		result.append(")");
    	} else {
    		result.append(getLeft().toString());
    		result.append(' ').append(Constants.OPS[relation]).append(' ');
    		result.append(getRight().toString());
    	}    	
    	return result.toString();
    }    
    
	protected void switchOperands() {
        context.getProfiler().message(this, Profiler.OPTIMIZATIONS,  "OPTIMIZATION", "Switching operands");
        //Invert relation
		switch (relation) {
			case Constants.GT :
				relation = Constants.LT;
				break;
			case Constants.LT :
				relation = Constants.GT;
				break;
			case Constants.LTEQ :
				relation = Constants.GTEQ;
				break;
			case Constants.GTEQ :
				relation = Constants.LTEQ;
				break;
		}
		Expression right = getRight();
		setRight(getLeft());
		setLeft(right);
	}

	/**
	 * Possibly switch operands to simplify execution
	 */
	protected void simplify() {        
		//Prefer nodes at the left hand
		if ((!Type.subTypeOf(getLeft().returnsType(), Type.NODE)) && 
              Type.subTypeOf(getRight().returnsType(), Type.NODE))
			switchOperands();
        //Prefer fewer items at the left hand
		else if ((getLeft().getCardinality() & Cardinality.MANY) != 0 && 
                 (getRight().getCardinality() & Cardinality.MANY) == 0)
			switchOperands();
	}
	
	protected Collator getCollator(Sequence contextSequence) throws XPathException {
		if(collationArg == null)
			return context.getDefaultCollator();
		String collationURI = collationArg.eval(contextSequence).getStringValue();
		return context.getCollator(collationURI);
	}
	
	public void setCollation(Expression collationArg) {
		this.collationArg = collationArg;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.PathExpr#resetState()
	 */
	public void resetState() {
		super.resetState();
		getLeft().resetState();
		getRight().resetState();
		cached = null;        
	}
}
