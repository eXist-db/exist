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

import org.exist.dom.ContextItem;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.storage.FulltextIndexSpec;
import org.exist.storage.IndexConfiguration;
import org.exist.storage.IndexSpec;
import org.exist.storage.Indexable;
import org.exist.storage.NativeTextEngine;
import org.exist.storage.analysis.SimpleTokenizer;
import org.exist.storage.analysis.TextToken;
import org.exist.storage.serializers.Serializer;
import org.exist.util.Configuration;
import org.exist.xquery.functions.ExtFulltext;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 * A general XQuery/XPath2 comparison expression.
 * 
 * @author wolf
 */
public class GeneralComparison extends BinaryOp {

	protected int relation = Constants.EQ;
	protected int truncation = Constants.TRUNC_NONE;
	
	protected CachedResult cached = null;

	protected Expression collationArg = null;
	
	public GeneralComparison(XQueryContext context, int relation) {
		this(context, relation, Constants.TRUNC_NONE);
	}
	
	public GeneralComparison(XQueryContext context, int relation, int truncation) {
		super(context);
		this.relation = relation;
	}

	public GeneralComparison(XQueryContext context, Expression left, Expression right,
		int relation) {
		this(context, left, right, relation, Constants.TRUNC_NONE);
	}
	
	public GeneralComparison(
		XQueryContext context,
		Expression left,
		Expression right,
		int relation,
		int truncation) {
		super(context);
		this.relation = relation;
		this.truncation = truncation;
		// simplify arguments
		if (left instanceof PathExpr && ((PathExpr) left).getLength() == 1) {
			left = ((PathExpr) left).getExpression(0);
		}
		add(left);
		if (right instanceof PathExpr && ((PathExpr) right).getLength() == 1) {
			right = ((PathExpr) right).getExpression(0);
		}
		add(right);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.BinaryOp#returnsType()
	 */
	public int returnsType() {
		// TODO: Assumes that context sequence is a node set
		if (inPredicate && (getDependencies() & Dependency.CONTEXT_ITEM) == 0) {
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
		int leftDeps = getLeft().getDependencies();
		int rightDeps = getRight().getDependencies();
		// left expression returns node set
		if (Type.subTypeOf(getLeft().returnsType(), Type.NODE)
			//	and does not depend on the context item
			&& (leftDeps & Dependency.CONTEXT_ITEM) == 0)
//			&& (rightDeps & Dependency.LOCAL_VARS) == 0)
		{
			return Dependency.CONTEXT_SET;
		} else {
			return Dependency.CONTEXT_SET + Dependency.CONTEXT_ITEM;
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.xquery.StaticContext, org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(Sequence contextSequence, Item contextItem)
		throws XPathException {
//        long start = System.currentTimeMillis();
		if(contextItem != null)
			contextSequence = contextItem.toSequence();
        Sequence result = null;
		/* 
		 * If we are inside a predicate and one of the arguments is a node set, 
		 * we try to speed up the query by returning nodes from the context set.
		 * This works only inside a predicate. The node set will always be the left 
		 * operand.
		 */
		if (inPredicate) {
			if((getDependencies() & Dependency.CONTEXT_ITEM) == 0 &&
			        Type.subTypeOf(getLeft().returnsType(), Type.NODE)) {
				// if the right operand is static, we can use the fulltext index
				if ((getRight().getDependencies() & Dependency.CONTEXT_ITEM) == 0
					&& (getRight().getCardinality() & Cardinality.MANY) == 0) {
					// lookup search terms in the fulltext index
					result = quickNodeSetCompare(contextSequence);
				} else {
					result = nodeSetCompare(contextSequence);
				}
			}
		}
        if(result == null)
            // Fall back to the generic compare process
            result = genericCompare(contextSequence, contextItem);
//        LOG.debug("comparison took " + (System.currentTimeMillis() - start));
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
	protected Sequence genericCompare(
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		Sequence ls = getLeft().eval(contextSequence, contextItem);
		Sequence rs = getRight().eval(contextSequence, contextItem);
		Collator collator = getCollator(contextSequence);
		AtomicValue lv, rv;
		if (ls.getLength() == 1 && rs.getLength() == 1) {
			lv = ls.itemAt(0).atomize();
			rv = rs.itemAt(0).atomize();
			return BooleanValue.valueOf(compareValues(collator, lv, rv));
		} else {
			for (SequenceIterator i1 = ls.iterate(); i1.hasNext();) {
				lv = i1.nextItem().atomize();
				if (rs.getLength() == 1
					&& compareValues(collator, lv, rs.itemAt(0).atomize()))
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
	protected Sequence nodeSetCompare(Sequence contextSequence)
		throws XPathException {
		// evaluate left expression (returning node set)
		NodeSet nodes = (NodeSet) getLeft().eval(contextSequence);
		return nodeSetCompare(nodes, contextSequence);
	}

	protected Sequence nodeSetCompare(
		NodeSet nodes,
		Sequence contextSequence)
		throws XPathException {
		NodeSet result = new ExtArrayNodeSet();
		NodeProxy current;
		ContextItem c;
		Sequence rs;
		AtomicValue lv, rv;
		Collator collator = getCollator(contextSequence);
		for (Iterator i = nodes.iterator(); i.hasNext();) {
			current = (NodeProxy) i.next();
			c = current.getContext();
			if(c == null)
				throw new XPathException(getASTNode(), "Internal error: context node missing");
			do {
				lv = current.atomize();
				rs = getRight().eval(c.getNode().toSequence());
				for (SequenceIterator si = rs.iterate(); si.hasNext();) {
					if (compareValues(collator, lv, si.nextItem().atomize())) {
						result.add(current);
					}
				}
			} while ((c = c.getNextItem()) != null);
		}
		return result;
	}

	/**
	 * Optimized implementation: first checks if a range index is defined
	 * on the nodes in the left argument. If that fails, check if we can use
	 * the fulltext index to speed up the search. Otherwise, fall back to
	 * {@link #nodeSetCompare(NodeSet, Sequence)}.
	 */
	protected Sequence quickNodeSetCompare(Sequence contextSequence)
		throws XPathException {
		// if the context sequence hasn't changed we can return a cached result
		if(cached != null && cached.isValid(contextSequence)) {
//			LOG.debug("Returning cached result for " + pprint());
			return cached.getResult();
		}
		//	evaluate left expression
		NodeSet nodes = (NodeSet) getLeft().eval(contextSequence);
		
		if(nodes.getLength() < 2)
			// fall back to nodeSetCompare if we just have to check a single node
			return nodeSetCompare(nodes, contextSequence);
		
		// evaluate right expression
		Sequence rightSeq = getRight().eval(contextSequence);

		if (rightSeq.getLength() > 1)
			// fall back to nodeSetCompare
			return nodeSetCompare(nodes, contextSequence);
		
		// get the type of a possible index
		int indexType = nodes.getIndexType();
		
		DocumentSet docs = nodes.getDocumentSet();
		NodeSet result = null;
		
	    if(indexType != Type.ITEM) {
	        // we have a range index defined on the nodes in this sequence
	        Item key = rightSeq.itemAt(0);
	        if(key.getType() != indexType) {
	            // index type doesn't match. If index and argument have a numeric type,
	            // we convert to the type of the index
	            if(Type.subTypeOf(indexType, Type.NUMBER) &&
	                    Type.subTypeOf(key.getType(), Type.NUMBER))
	                key = key.convertTo(indexType);
	        }
	        // if key does not implement Indexable, we can't use the index
	        if(key instanceof Indexable && Type.subTypeOf(key.getType(), indexType)) {
	            LOG.debug("Using value index for key: " + key.getStringValue());
	            result = context.getBroker().getValueIndex().find(relation, docs, nodes, (Indexable)key);
	        } else
	            return nodeSetCompare(nodes, contextSequence);
	        
	    } else if (relation == Constants.EQ
				&& nodes.hasTextIndex()) {
	        // we can use the fulltext index
	        String cmp = getComparisonString(rightSeq);
	        if(cmp.length() < NativeTextEngine.MAX_WORD_LENGTH)
	            nodes = useFulltextIndex(cmp, nodes, docs);
	        // now compare the input node set to the search expression
			Collator collator = getCollator(contextSequence);
			result =
				context.getBroker().getNodesEqualTo(nodes, docs, relation, cmp, collator);
		} else {
		    
		    // no usable index found. Fall back to nodeSetCompare()
		    return nodeSetCompare(nodes, contextSequence);
		}
		
		// can this result be cached? Don't cache if the result depends on local variables.
		boolean canCache = 
		    contextSequence instanceof NodeSet &&
	        (getRight().getDependencies() & Dependency.LOCAL_VARS) == 0 &&
	        (getLeft().getDependencies() & Dependency.LOCAL_VARS) == 0;
		if(canCache)
			cached = new CachedResult((NodeSet)contextSequence, result);
		return result;
	}

	/**
     * @param rightSeq
     * @return
     * @throws XPathException
     */
    private String getComparisonString(Sequence rightSeq) throws XPathException {
        String cmp = rightSeq.getStringValue();
        switch(truncation) {
			case Constants.TRUNC_RIGHT:
				cmp = cmp + '%';
				break;
			case Constants.TRUNC_LEFT:
				cmp = '%' + cmp;
				break;
			case Constants.TRUNC_BOTH:
				cmp = '%' + cmp + '%';
		}
        return cmp;
    }

    protected NodeSet useFulltextIndex(String cmp, NodeSet nodes, DocumentSet docs) throws XPathException {
//	    LOG.debug("Using fulltext index for expression " + ExpressionDumper.dump(this));
	    String cmpCopy = cmp;
		cmp = maskWildcards(cmp);
		// try to use a fulltext search expression to reduce the number
		// of potential nodes to scan through
		SimpleTokenizer tokenizer = new SimpleTokenizer();
		tokenizer.setText(cmp);
		TextToken token;
		String term;
		boolean foundNumeric = false;
		// setup up an &= expression using the fulltext index
		ExtFulltext containsExpr = new ExtFulltext(context, Constants.FULLTEXT_AND);
		containsExpr.setASTNode(getASTNode());
		// disable default match highlighting
		int oldFlags = context.getBroker().getTextEngine().getTrackMatches();
		context.getBroker().getTextEngine().setTrackMatches(Serializer.TAG_NONE);
		
		int i = 0;
		for (; i < 5 && (token = tokenizer.nextToken(true)) != null; i++) {
			// remember if we find an alphanumeric token
			if (token.getType() == TextToken.ALPHANUM)
				foundNumeric = true;
		}
		// check if all elements are indexed. If not, we can't use the
		// fulltext index.
		if (foundNumeric)
			foundNumeric = checkArgumentTypes(context, docs);
		if ((!foundNumeric) && i > 0) {
			// all elements are indexed: use the fulltext index
			containsExpr.addTerm(new LiteralValue(context, new StringValue(cmp)));
			nodes = (NodeSet) containsExpr.eval(nodes, null);
		}
		context.getBroker().getTextEngine().setTrackMatches(oldFlags);
		cmp = cmpCopy;
		return nodes;
	}
	
	/**
	 * Cast the atomic operands into a comparable type
	 * and compare them.
	 */
	protected boolean compareValues(
		Collator collator,
		AtomicValue lv,
		AtomicValue rv)
		throws XPathException {
		try {
			return compareAtomic(collator, lv, rv, context.isBackwardsCompatible(), truncation, relation);
		} catch (XPathException e) {
			e.setASTNode(getASTNode());
			throw e;
		}
	}

	public static boolean compareAtomic(Collator collator, AtomicValue lv,
			AtomicValue rv, boolean backwardsCompatible, int truncation,
			int relation) 
	throws XPathException{
		int ltype = lv.getType();
		int rtype = rv.getType();
		if (ltype == Type.ITEM || ltype == Type.ATOMIC) {
			if (Type.subTypeOf(rtype, Type.NUMBER)) {
				lv = lv.convertTo(Type.DOUBLE);
			} else if (rtype == Type.ITEM || rtype == Type.ATOMIC) {
				lv = lv.convertTo(Type.STRING);
				rv = rv.convertTo(Type.STRING);
			} else
				lv = lv.convertTo(rv.getType());
		} else if (rtype == Type.ITEM || rtype == Type.ATOMIC) {
			if (Type.subTypeOf(ltype, Type.NUMBER)) {
				rv = rv.convertTo(Type.DOUBLE);
			} else if (rtype == Type.ITEM || rtype == Type.ATOMIC) {
				lv = lv.convertTo(Type.STRING);
				rv = rv.convertTo(Type.STRING);
			} else
				rv = rv.convertTo(lv.getType());
		}
		if (backwardsCompatible) {
			// in XPath 1.0 compatible mode, if one of the operands is a number, cast
			// both operands to xs:double
			if (Type.subTypeOf(ltype, Type.NUMBER)
				|| Type.subTypeOf(rtype, Type.NUMBER)) {
				lv = lv.convertTo(Type.DOUBLE);
				rv = rv.convertTo(Type.DOUBLE);
			}
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
	
	private boolean checkArgumentTypes(XQueryContext context, DocumentSet docs)
		throws XPathException {
		Configuration config = context.getBroker().getConfiguration();
		IndexConfiguration idxConf = (IndexConfiguration) config.getProperty("indexer.map");
		DocumentImpl doc;
		IndexSpec idxSpec;
		FulltextIndexSpec idx;
		for (Iterator i = docs.iterator(); i.hasNext();) {
			doc = (DocumentImpl) i.next();
			idxSpec = idxConf.getByDoctype(doc.getDoctype().getName());
			if(idxSpec != null) {
			    idx = idxSpec.getFulltextIndexSpec();
			    if(idx.isSelective())
			        return true;
			    if(!idx.getIncludeAlphaNum())
			        return true;
			}
		}
		return false;
	}

	private String maskWildcards(String expr) {
		StringBuffer buf = new StringBuffer();
		char ch;
		for (int i = 0; i < expr.length(); i++) {
			ch = expr.charAt(i);
			switch (ch) {
				case '*' :
					buf.append("\\*");
					break;
				case '%' :
					buf.append('*');
					break;
				default :
					buf.append(ch);
			}
		}
		return buf.toString();
	}

	/* (non-Javadoc)
     * @see org.exist.xquery.PathExpr#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        getLeft().dump(dumper);
        dumper.display(' ').display(Constants.OPS[relation]).display(' ');
        getRight().dump(dumper);
    }
    
	protected void switchOperands() {
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

	protected void simplify() {
		// switch operands to simplify execution
		if ((!Type.subTypeOf(getLeft().returnsType(), Type.NODE))
			&& Type.subTypeOf(getRight().returnsType(), Type.NODE))
			switchOperands();
		else if (
			(getLeft().getCardinality() & Cardinality.MANY) != 0
				&& (getRight().getCardinality() & Cardinality.MANY) == 0)
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
		getLeft().resetState();
		getRight().resetState();
		cached = null;
	}
}
