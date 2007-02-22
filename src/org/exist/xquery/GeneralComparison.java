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
import org.exist.dom.DocumentSet;
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.dom.VirtualNodeSet;
import org.exist.storage.DBBroker;
import org.exist.storage.ElementValue;
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
public class GeneralComparison extends BinaryOp implements Optimizable {

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
    
    private boolean hasUsedIndex = false;

    private LocationStep contextStep = null;
    private QName contextQName = null;
    private NodeSet preselectResult = null;

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
        contextInfo.addFlag(NEED_INDEX_INFO);
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
        if (contextInfo.getContextStep() != null && contextInfo.getContextStep() instanceof LocationStep) {
            ((LocationStep)contextInfo.getContextStep()).setUseDirectAttrSelect(false);
        }
        contextInfo.removeFlag(NEED_INDEX_INFO);

        LocationStep step = BasicExpressionVisitor.findFirstStep(getLeft());
        if (step != null) {
            NodeTest test = step.getTest();
            if (!test.isWildcardTest() && test.getName() != null) {
                contextQName = new QName(test.getName());
                if (step.getAxis() == Constants.ATTRIBUTE_AXIS || step.getAxis() == Constants.DESCENDANT_ATTRIBUTE_AXIS)
                    contextQName.setNameType(ElementValue.ATTRIBUTE);
                contextStep = step;
            }
        }
    }

    public boolean canOptimize(Sequence contextSequence) {
        if (contextQName == null)
            return false;
        return Optimize.getQNameIndexType(context, contextSequence, contextQName) != Type.ITEM;
    }

    public boolean optimizeOnSelf() {
        return false;
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

    public int getRelation() {
        return this.relation;
    }
    
    public NodeSet preSelect(Sequence contextSequence, boolean useContext) throws XPathException {
        int indexType = Optimize.getQNameIndexType(context, contextSequence, contextQName);
        if (LOG.isTraceEnabled())
            LOG.trace("Using QName index on type " + Type.getTypeName(indexType));
        Sequence rightSeq = getRight().eval(contextSequence);

        for (SequenceIterator itRightSeq = rightSeq.iterate(); itRightSeq.hasNext();) {
            //Get the index key
            Item key = itRightSeq.nextItem().atomize();

            //if key has truncation, convert it to string
            if(truncation != Constants.TRUNC_NONE) {
                //TODO : log this conversion ? -pb
                //truncation is only possible on strings
                key = key.convertTo(Type.STRING);
            }
            //else if key is not the same type as the index
            //TODO : use isSubType ??? -pb
            else if (key.getType() != indexType) {
                //try and convert the key to the index type
                try	{
                    key = key.convertTo(indexType);
                } catch(XPathException xpe)	{
                    if (LOG.isTraceEnabled())
                        LOG.trace("Cannot convert key: " + Type.getTypeName(key.getType()) + " to required index type: " + Type.getTypeName(indexType));

                    throw new XPathException(getASTNode(), "Cannot convert key to required index type");
                }
            }

            // If key implements org.exist.storage.Indexable, we can use the index
            if (key instanceof Indexable) {
                if (LOG.isTraceEnabled())
                    LOG.trace("Using QName range index for key: " + key.getStringValue());
                NodeSet temp;
                NodeSet contextSet = useContext ? contextSequence.toNodeSet() : null;
                if(truncation == Constants.TRUNC_NONE) {
                    temp =
                        context.getBroker().getValueIndex().find(relation, contextSequence.getDocumentSet(),
                                contextSet, NodeSet.DESCENDANT, contextQName, (Indexable)key);
                } else {
                    try {
                        temp = context.getBroker().getValueIndex().match(contextSequence.getDocumentSet(), contextSet,
                                NodeSet.DESCENDANT, getRegexp(key.getStringValue()).toString(),
                                contextQName, DBBroker.MATCH_REGEXP);
                    } catch (EXistException e) {
                        throw new XPathException(getASTNode(), "Error during index lookup: " + e.getMessage(), e);
                    }
                }
                if (preselectResult == null)
                    preselectResult = temp;
                else
                    preselectResult = preselectResult.union(temp);
            }
        }
        return preselectResult;
    }

    /* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.xquery.StaticContext, org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
		if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            if (contextItem != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
        }

        // if we were optimizing and the preselect did not return anything,
        // we won't have any matches and can return
        if (preselectResult != null && preselectResult.isEmpty())
            return Sequence.EMPTY_SEQUENCE;

        Sequence result;

        if (contextStep == null || preselectResult == null) {
            /*
             * If we are inside a predicate and one of the arguments is a node set,
             * we try to speed up the query by returning nodes from the context set.
             * This works only inside a predicate. The node set will always be the left
             * operand.
             */
            if (inPredicate && !invalidNodeEvaluation &&
                    !Dependency.dependsOn(this, Dependency.CONTEXT_ITEM) &&
                    Type.subTypeOf(getLeft().returnsType(), Type.NODE)) {

                if(contextItem != null)
                    contextSequence = contextItem.toSequence();

                if ((!Dependency.dependsOn(rightOpDeps, Dependency.CONTEXT_ITEM))) {
                    result = quickNodeSetCompare(contextSequence);
                } else {
                    result = nodeSetCompare(contextSequence);
                }
            } else {
                result = genericCompare(contextSequence, contextItem);
            }
        } else {
            contextStep.setPreloadNodeSets(true);
            contextStep.setPreloadedData(preselectResult.getDocumentSet(), preselectResult);

            result = getLeft().eval(contextSequence).toNodeSet();
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
	 * @return The Sequence resulting from the comparison
	 * @throws XPathException
	 */
	protected Sequence genericCompare(Sequence contextSequence,	Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled())
            context.getProfiler().message(this, Profiler.OPTIMIZATION_FLAGS,
                    "OPTIMIZATION CHOICE", "genericCompare");
		final Sequence ls = getLeft().eval(contextSequence, contextItem);
		final Sequence rs = getRight().eval(contextSequence, contextItem);
		final Collator collator = getCollator(contextSequence);
		if (ls.isEmpty() && rs.isEmpty()) {
			return BooleanValue.valueOf(compareValues(collator, AtomicValue.EMPTY_VALUE, AtomicValue.EMPTY_VALUE));
		} else if (!ls.isEmpty()&& rs.isEmpty()) {
			for (SequenceIterator i1 = ls.iterate(); i1.hasNext();) {
				AtomicValue lv = i1.nextItem().atomize();
				if (compareValues(collator, lv, AtomicValue.EMPTY_VALUE))
					return BooleanValue.TRUE;
			}			
		} else if (ls.hasOne() && rs.hasOne()) {
			return BooleanValue.valueOf(compareValues(collator, ls.itemAt(0).atomize(), rs.itemAt(0).atomize()));
		} else {
			for (SequenceIterator i1 = ls.iterate(); i1.hasNext();) {
				AtomicValue lv = i1.nextItem().atomize();
				if (rs.isEmpty()) {
					if (compareValues(collator, lv, AtomicValue.EMPTY_VALUE))
						return BooleanValue.TRUE;
				} else if (rs.hasOne()) {
					if (compareValues(collator, lv, rs.itemAt(0).atomize()))
						//return early if we are successful, continue otherwise
						return BooleanValue.TRUE;
				} else {
					for (SequenceIterator i2 = rs.iterate(); i2.hasNext();) {
						if (compareValues(collator, lv, i2.nextItem().atomize()))
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
		final Collator collator = getCollator(contextSequence);
		if (contextSequence != null && !contextSequence.isEmpty() && !contextSequence.getDocumentSet().contains(nodes.getDocumentSet()))
		{
			for (Iterator i1 = nodes.iterator(); i1.hasNext();) {
				NodeProxy item = (NodeProxy) i1.next();
				ContextItem context = item.getContext();
				if (context == null)
					throw new XPathException(getASTNode(), "Internal error: context node missing");
				AtomicValue lv = item.atomize();
				do
				{
					Sequence rs = getRight().eval(context.getNode().toSequence());
					for (SequenceIterator i2 = rs.iterate(); i2.hasNext();)
					{
						AtomicValue rv = i2.nextItem().atomize();
						if (compareValues(collator, lv, rv))
							result.add(item);
					}
				} while ((context = context.getNextDirect()) != null);
			}
		} else { 
			for (Iterator i1 = nodes.iterator(); i1.hasNext();) {
		    	NodeProxy current = (NodeProxy) i1.next();
				AtomicValue lv = current.atomize();
				Sequence rs = getRight().eval(contextSequence);				
				for (SequenceIterator i2 = rs.iterate(); i2.hasNext();)	{
					AtomicValue rv = i2.nextItem().atomize();
					if (compareValues(collator, lv, rv))
						result.add(current);
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
		if(cached != null && cached.isValid(contextSequence)) {
			LOG.debug("Using cached results");
            if(context.getProfiler().isEnabled())
                context.getProfiler().message(this, Profiler.OPTIMIZATIONS, "OPTIMIZATION", "Returned cached result");
			return(cached.getResult());
		}

		//get the NodeSet on the left
		NodeSet nodes = (NodeSet) getLeft().eval(contextSequence);
		if(!(nodes instanceof VirtualNodeSet) && nodes.isEmpty()) //nothing on the left, so nothing to do
            return(Sequence.EMPTY_SEQUENCE);

        //get the Sequence on the right
		Sequence rightSeq = getRight().eval(contextSequence);
		if(rightSeq.isEmpty())	//nothing on the right, so nothing to do
            return(Sequence.EMPTY_SEQUENCE);

		//Holds the result
		NodeSet result = null;

		//get the type of a possible index
		int indexType = nodes.getIndexType();
        
        //See if we have a range index defined on the nodes in this sequence
        //TODO : use isSubType ??? -pb
		//rememeber that Type.ITEM means... no index ;-)
	    if(indexType != Type.ITEM) {
	    	if (LOG.isTraceEnabled())
	    		LOG.trace("found an index of type: " + Type.getTypeName(indexType));

	    	//Get the documents from the node set
			DocumentSet docs = nodes.getDocumentSet();

			//Iterate through the right hand sequence
			for (SequenceIterator itRightSeq = rightSeq.iterate(); itRightSeq.hasNext();) {
				//Get the index key
				Item key = itRightSeq.nextItem().atomize();

				//if key has truncation, convert it to string
		        if(truncation != Constants.TRUNC_NONE) {
		        	//TODO : log this conversion ? -pb
		        	//truncation is only possible on strings
		        	key = key.convertTo(Type.STRING);
		        }
		        //else if key is not the same type as the index
                //TODO : use isSubType ??? -pb
		        else if (key.getType() != indexType) {
		        	//try and convert the key to the index type
	            	try	{
	            		key = key.convertTo(indexType);
					} catch(XPathException xpe)	{
	            		//TODO : rethrow the exception ? -pb

			        	//Could not convert the key to a suitable type for the index, fallback to nodeSetCompare()
		                if(context.getProfiler().isEnabled())
		                    context.getProfiler().message(this, Profiler.OPTIMIZATION_FLAGS, "OPTIMIZATION FALLBACK", "Falling back to nodeSetCompare (" + xpe.getMessage() + ")");

		                if (LOG.isTraceEnabled())
		                	LOG.trace("Cannot convert key: " + Type.getTypeName(key.getType()) + " to required index type: " + Type.getTypeName(indexType));

			            return nodeSetCompare(nodes, contextSequence);
					}
		        }

		        // If key implements org.exist.storage.Indexable, we can use the index
		        if (key instanceof Indexable) {
		        	if (LOG.isTraceEnabled())
		        		LOG.trace("Checking if range index can be used for key: " + key.getStringValue());

		        	if (Type.subTypeOf(key.getType(), indexType)) {
			        	if(truncation == Constants.TRUNC_NONE) {
			        		if (LOG.isTraceEnabled())
			        			LOG.trace("Using range index for key: " + key.getStringValue());

			        		//key without truncation, find key
		                    context.getProfiler().message(this, Profiler.OPTIMIZATIONS, "OPTIMIZATION", "Using value index '" + context.getBroker().getValueIndex().toString() +
		                    		"' to find key '" + Type.getTypeName(key.getType()) + "(" + key.getStringValue() + ")'");

		                    NodeSet ns = context.getBroker().getValueIndex().find(relation, docs, nodes, NodeSet.ANCESTOR, null, (Indexable)key);
		                    hasUsedIndex = true;

		                    if (result == null)
								result = ns;
							else
								result = result.union(ns);

		                } else {
				        	//key with truncation, match key
                            if (LOG.isTraceEnabled())
                                context.getProfiler().message(this, Profiler.OPTIMIZATIONS, "OPTIMIZATION", "Using value index '" + context.getBroker().getValueIndex().toString() +
		                    		"' to match key '" + Type.getTypeName(key.getType()) + "(" + key.getStringValue() + ")'");

                            if (LOG.isTraceEnabled())
			        			LOG.trace("Using range index for key: " + key.getStringValue());

                            try {
								NodeSet ns = context.getBroker().getValueIndex().match(docs, nodes, NodeSet.ANCESTOR,
                                        getRegexp(key.getStringValue()).toString(), null, DBBroker.MATCH_REGEXP);
								hasUsedIndex = true;

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
		                    context.getProfiler().message(this, Profiler.OPTIMIZATION_FLAGS, "OPTIMIZATION FALLBACK", "Falling back to nodeSetCompare (key is of type: " +
		                    		Type.getTypeName(key.getType()) + ") whereas index is of type '" + Type.getTypeName(indexType) + "'");

		                if (LOG.isTraceEnabled())
		                	LOG.trace("Cannot use range index: key is of type: " + Type.getTypeName(key.getType()) + ") whereas index is of type '" +
		                			Type.getTypeName(indexType));

		                return(nodeSetCompare(nodes, contextSequence));
			        }
		        } else {
		        	//the datatype of our key does not implement org.exist.storage.Indexable
	                if(context.getProfiler().isEnabled())
	                    context.getProfiler().message(this, Profiler.OPTIMIZATION_FLAGS, "OPTIMIZATION FALLBACK", "Falling back to nodeSetCompare (key is not an indexable type: " +
	                    		key.getClass().getName());

	                return(nodeSetCompare(nodes, contextSequence));

		        }
            }
		} else {
	    	if (LOG.isTraceEnabled())
	    		LOG.trace("No suitable index found for key: " + rightSeq.getStringValue());

	    	//no range index defined on the nodes in this sequence, so fallback to nodeSetCompare
            if(context.getProfiler().isEnabled())
                context.getProfiler().message(this, Profiler.OPTIMIZATION_FLAGS, "OPTIMIZATION FALLBACK", "falling back to nodeSetCompare (no index available)");

            return(nodeSetCompare(nodes, contextSequence));
		}

		// can this result be cached? Don't cache if the result depends on local variables.
	    boolean canCache = contextSequence instanceof NodeSet &&
	    	!Dependency.dependsOnVar(getLeft()) &&
	    	!Dependency.dependsOnVar(getRight());

	    if(canCache)
			cached = new CachedResult((NodeSet)contextSequence, result);

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
     * @return Whether or not <code>lv</code> is an empty string
	 * @throws XPathException
     */
    private static boolean isEmptyString(AtomicValue lv) throws XPathException {
        if(Type.subTypeOf(lv.getType(), Type.STRING) || lv.getType() == Type.ATOMIC) {
            if(lv.getStringValue().length() == 0)
                return true;
        }
        return false;
    }

    public boolean hasUsedIndex() {
        return hasUsedIndex;
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
		else if ((Cardinality.checkCardinality(Cardinality.MANY, getLeft().getCardinality())) && 
                 !(Cardinality.checkCardinality(Cardinality.MANY, getRight().getCardinality())))
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
		hasUsedIndex = false;
	}

    public void accept(ExpressionVisitor visitor) {
        visitor.visitGeneralComparison(this);
    }
}
