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
package org.exist.xpath;

import java.util.Iterator;
import java.util.Map;

import org.exist.dom.ArraySet;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.SingleNodeSet;
import org.exist.storage.IndexPaths;
import org.exist.storage.analysis.SimpleTokenizer;
import org.exist.storage.analysis.TextToken;
import org.exist.util.Configuration;
import org.exist.xpath.value.BooleanValue;
import org.exist.xpath.value.DecimalValue;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.SequenceIterator;
import org.exist.xpath.value.Type;
import org.exist.xpath.value.ValueSequence;

/**
 *  compare two operands by =, <, > etc..
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    31. August 2002
 */
public class OpEquals extends BinaryOp {

	protected int relation = Constants.EQ;
	protected NodeSet temp = null;

	// in some cases, we use a fulltext expression to preselect nodes
	protected ExtFulltext containsExpr = null;

	public OpEquals(int relation) {
		super();
		this.relation = relation;
	}

	public OpEquals(Expression left, Expression right, int relation) {
		super();
		this.relation = relation;
		if (left instanceof PathExpr && ((PathExpr) left).getLength() == 1)
			add(((PathExpr) left).getExpression(0));
		else
			add(left);
		if (right instanceof PathExpr && ((PathExpr) right).getLength() == 1)
			add(((PathExpr) right).getExpression(0));
		else
			add(right);
	}

	/**
	 *  Left argument is boolean: Convert right argument to a bool.
	 *
	 *@param  left     Description of the Parameter
	 *@param  right    Description of the Parameter
	 *@param  docs     Description of the Parameter
	 *@param  context  Description of the Parameter
	 *@param  node     Description of the Parameter
	 *@return          Description of the Return Value
	 */
	protected Sequence booleanCompare(
		Expression left,
		Expression right,
		StaticContext context,
		DocumentSet docs,
		Sequence contextSequence) throws XPathException {
		boolean lvalue;
		boolean rvalue;
		if(contextSequence.getItemType() == Type.NODE) {
			ArraySet result = new ArraySet(100);
			NodeProxy n;
			SingleNodeSet set = new SingleNodeSet();
			for(SequenceIterator i = contextSequence.iterate(); i.hasNext(); ) {
				n = (NodeProxy)i.nextItem();
				set.add(n);
				lvalue = ((BooleanValue)left.eval(context, docs, set).convertTo(Type.BOOLEAN)).getValue();
				rvalue = ((BooleanValue)right.eval(context, docs, set).convertTo(Type.BOOLEAN)).getValue();
				if(cmpBooleans(lvalue, rvalue)) {
					result.add(n);
					n.addContextNode(n);
				}
			}
			return result;
		} else {
			ValueSequence result = new ValueSequence();
			Item item;
			for(SequenceIterator i = contextSequence.iterate(); i.hasNext(); ) {
				item = i.nextItem();
				lvalue = ((BooleanValue)left.eval(context, docs, contextSequence, item).convertTo(Type.BOOLEAN)).getValue();
				rvalue = ((BooleanValue)right.eval(context, docs, contextSequence, item).convertTo(Type.BOOLEAN)).getValue();
				if(cmpBooleans(lvalue, rvalue))
					result.add(item);
			}
			return result;
		}
	}

	protected boolean cmpBooleans(boolean left, boolean right) {
		switch (relation) {
			case Constants.EQ :
				return (left == right);
			case Constants.NEQ :
				return (left != right);
		}
		return false;
	}

	protected boolean cmpNumbers(double left, double right) {
		switch (relation) {
			case Constants.EQ :
				return (left == right);
			case Constants.NEQ :
				return (left != right);
			case Constants.GT :
				return (left > right);
			case Constants.LT :
				return (left < right);
			case Constants.GTEQ :
				return (left >= right);
			case Constants.LTEQ :
				return (left <= right);
		}
		return false;
	}

	protected boolean compareStrings(String left, String right) {
		int cmp = left.compareTo(right);
		switch (relation) {
			case Constants.EQ :
				return (cmp == 0);
			case Constants.NEQ :
				return (cmp != 0);
			case Constants.GT :
				return (cmp > 0);
			case Constants.LT :
				return (cmp < 0);
			case Constants.GTEQ :
				return (cmp >= 0);
			case Constants.LTEQ :
				return (cmp <= 0);
		}
		return false;
	}

	/**
	 *  Compare left and right statement. Comparison is done like described in
	 *  the spec. If one argument returns a node set, we handle that first.
	 *  Otherwise if one argument is a number, process that. Third follows
	 *  string, boolean is last. If necessary move right to left and left to
	 *  right.
	 *
	 *@param  docs     Description of the Parameter
	 *@param  context  Description of the Parameter
	 *@param  node     Description of the Parameter
	 *@return          Description of the Return Value
	 */
	public Sequence eval(StaticContext context, DocumentSet docs, Sequence contextSequence,
		Item contextItem) throws XPathException {
		if (Type.subTypeOf(getLeft().returnsType(), Type.NODE))
			return nodeSetCompare(getLeft(), getRight(), context, docs, contextSequence);
		else if (Type.subTypeOf(getRight().returnsType(), Type.NODE)) {
			switchOperands();
			return nodeSetCompare(getRight(), getLeft(), context, docs, contextSequence);
		} else if (Type.subTypeOf(getLeft().returnsType(), Type.NUMBER))
			return numberCompare(getLeft(), getRight(), context, docs, contextSequence);
		else if (Type.subTypeOf(getRight().returnsType(), Type.NUMBER))
			return numberCompare(getRight(), getLeft(), context, docs, contextSequence);
		else if (Type.subTypeOf(getLeft().returnsType(), Type.STRING))
			return stringCompare(getLeft(), getRight(), context, docs, contextSequence);
		else if (Type.subTypeOf(getLeft().returnsType(), Type.BOOLEAN))
			return booleanCompare(getLeft(), getRight(), context, docs, contextSequence);
		else if (Type.subTypeOf(getRight().returnsType(), Type.BOOLEAN))
			return booleanCompare(getRight(), getLeft(), context, docs, contextSequence);
		throw new RuntimeException("syntax error");
	}

	/**
	 *  Left argument is a node set. If right arg is a string-literal, call
	 *  broker.getNodesEqualTo - which is fast. If it is a number, convert it.
	 *  If it is a boolean, get the part of context which matches the left
	 *  expression, get the right value for every node of context and compare it
	 *  with the left-part.
	 *
	 *@param  left     Description of the Parameter
	 *@param  right    Description of the Parameter
	 *@param  docs     Description of the Parameter
	 *@param  context  Description of the Parameter
	 *@param  node     Description of the Parameter
	 *@return          Description of the Return Value
	 */
	protected Sequence nodeSetCompare(
		Expression left,
		Expression right,
		StaticContext context,
		DocumentSet docs,
		Sequence contextSequence) throws XPathException {
		NodeSet result = new ArraySet(100);
		if(!(contextSequence.getItemType() == Type.NODE))
			throw new XPathException("context is not a node sequence");
		NodeSet contextSet = (NodeSet)contextSequence;
		// TODO: not correct: should test if right is a string literal
		if (Type.subTypeOf(right.returnsType(), Type.STRING) ||
			Type.subTypeOf(right.returnsType(), Type.NODE)) {
			// evaluate left expression
			NodeSet nodes = (NodeSet) left.eval(context, docs, contextSequence);
			String cmp = right.eval(context, docs, contextSequence).getStringValue();
			if (getLeft().returnsType() == Type.NODE && relation == Constants.EQ &&
				nodes.hasIndex() && cmp.length() > 0) {
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
				containsExpr = new ExtFulltext(Constants.FULLTEXT_AND);
				for (int i = 0; i < 5 && (token = tokenizer.nextToken(true)) != null; i++) {
					// remember if we find an alphanumeric token
					if(token.getType() == TextToken.ALPHANUM)
						foundNumeric = true;
					containsExpr.addTerm(token.getText());
				} 
				// check if all elements are indexed. If not, we can't use the
				// fulltext index.
				if(foundNumeric)
					foundNumeric = checkArgumentTypes(context, docs);
				if((!foundNumeric) && containsExpr.countTerms() > 0) {
					// all elements are indexed: use the fulltext index
					nodes = (NodeSet)containsExpr.eval(context, docs, nodes, null);
				}
				cmp = cmpCopy;
			}
			// now compare the input node set to the search expression
				result = context.getBroker().getNodesEqualTo(nodes, docs, relation, cmp);
		} else if (Type.subTypeOf(right.returnsType(), Type.NUMBER)) {
			DecimalValue rvalue;
			DecimalValue lvalue;
			NodeProxy ln;
			NodeSet temp = new SingleNodeSet();
			NodeSet lset = (NodeSet) left.eval(context, docs, contextSequence);
			for (Iterator i = lset.iterator(); i.hasNext();) {
				ln = (NodeProxy) i.next();
				lvalue = (DecimalValue)ln.convertTo(Type.DECIMAL);
				temp.add(ln);
				rvalue = (DecimalValue)right.eval(context, docs, temp).convertTo(Type.DECIMAL);
				if (cmpNumbers(lvalue.getDouble(), rvalue.getDouble()))
					result.add(ln);
			}
		} else if (Type.subTypeOf(right.returnsType(), Type.BOOLEAN)) {
			NodeProxy n;
			NodeProxy parent;
			boolean rvalue;
			boolean lvalue;
			long pid;
			NodeSet lset = (NodeSet) left.eval(context, docs, contextSequence);
			// get left arguments node set
			NodeSet temp = new SingleNodeSet();
			// get that part of context for which left argument's node set would
			// be > 0
			for (Iterator i = lset.iterator(); i.hasNext();) {
				n = (NodeProxy) i.next();
				parent = contextSet.parentWithChild(n, false, true, -1);
				if (parent != null)
					temp.add(parent);
			}
			SingleNodeSet ltemp = new SingleNodeSet();
			// now compare every node of context with the temporary set
			for (Iterator i = contextSet.iterator(); i.hasNext();) {
				n = (NodeProxy) i.next();
				ltemp.add(n);
				lvalue = temp.contains(n);
				rvalue = ((BooleanValue)right.eval(context, docs, ltemp).convertTo(Type.BOOLEAN)).getValue();
				if (cmpBooleans(lvalue, rvalue))
					result.add(n);
			}
		}
		return result;
	}

	private String maskWildcards(String expr) {
		StringBuffer buf = new StringBuffer();
		char ch;
		for(int i = 0; i < expr.length(); i++) {
			ch = expr.charAt(i);
			switch(ch) {
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
	
	private boolean checkArgumentTypes(StaticContext context, DocumentSet docs) throws XPathException {
			Configuration config = context.getBroker().getConfiguration();
			Map idxPathMap = (Map) config.getProperty("indexer.map");
			DocumentImpl doc;
			IndexPaths idx;
			for(Iterator i = docs.iterator(); i.hasNext(); ) {
				doc = (DocumentImpl)i.next();
				idx = (IndexPaths) idxPathMap.get(doc.getDoctype().getName());
				if(idx != null && idx.isSelective())
					return true;
				if(idx != null && (!idx.getIncludeAlphaNum()))
					return true;
			}
		return false;
	}
	
	/**
	 *  Left argument is a number: Convert right argument to a number for every
	 *  node in context.
	 *
	 *@param  left     Description of the Parameter
	 *@param  right    Description of the Parameter
	 *@param  docs     Description of the Parameter
	 *@param  context  Description of the Parameter
	 *@param  node     Description of the Parameter
	 *@return          Description of the Return Value
	 */
	protected Sequence numberCompare(
		Expression left,
		Expression right,
		StaticContext context,
		DocumentSet docs,
		Sequence contextSequence) throws XPathException {
		System.out.println("comparing numbers");
		Sequence result;
		if(contextSequence.getItemType() == Type.NODE) 
			result = new ArraySet(10);
		else
			result = new ValueSequence();
		Item current;
		double rvalue;
		double lvalue;
		for(SequenceIterator i = contextSequence.iterate(); i.hasNext(); ) {
			current = i.nextItem();
			rvalue = ((DecimalValue)right.eval(context, docs, contextSequence, current).convertTo(Type.DECIMAL)).getDouble();
			lvalue = ((DecimalValue)left.eval(context, docs, contextSequence, current).convertTo(Type.DECIMAL)).getDouble();
			System.out.println(rvalue + " = " + lvalue);
			if (cmpNumbers(lvalue, rvalue)) {
				result.add(current);
				if(Type.subTypeOf(current.getType(), Type.NODE))
					((NodeProxy)current).addContextNode((NodeProxy)current);
			}
			
		}
		return result;
	}

	public String pprint() {
		StringBuffer buf = new StringBuffer();
		buf.append(getLeft().pprint());
		buf.append(Constants.OPS[relation]);
		buf.append(getRight().pprint());
		return buf.toString();
	}

	/**
	 *  check relevant documents. Does nothing here.
	 *
	 *@param  in_docs  Description of the Parameter
	 *@return          Description of the Return Value
	 */
	public DocumentSet preselect(DocumentSet in_docs, StaticContext context) {
		return in_docs;
	}

	public int returnsType() {
		return Type.NODE;
	}

	protected Sequence stringCompare(
		Expression left,
		Expression right,
		StaticContext context,
		DocumentSet docs,
		Sequence contextSequence) throws XPathException {
		LOG.debug("comparing " + docs.getLength());
		ValueSequence result = new ValueSequence();
		Item current;
		String lvalue;
		String rvalue;
		int cmp;
		for(SequenceIterator i = contextSequence.iterate(); i.hasNext(); ) {
			current = i.nextItem();
			rvalue = left.eval(context, docs, contextSequence, current).getStringValue();
			lvalue = right.eval(context, docs, contextSequence, current).getStringValue();
			if (compareStrings(rvalue, lvalue)) {
				result.add(current);
				if(current.getType() == Type.NODE)
					((NodeProxy)current).addContextNode((NodeProxy)current);
			}
		}
		return result;
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
	}
}
