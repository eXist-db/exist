/*
 *  eXist Open Source Native XML Database
 * 
 *  Copyright (C) 2000, Wolfgang M. Meier (meier@ifs. tu- darmstadt. de)
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
 */
package org.exist.xpath;

import java.util.Iterator;
import java.util.Map;

import org.apache.log4j.Category;
import org.exist.EXistException;
import org.exist.dom.ArraySet;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.IndexPaths;
import org.exist.storage.analysis.SimpleTokenizer;
import org.exist.storage.analysis.TextToken;
import org.exist.storage.analysis.Token;
import org.exist.util.Configuration;

/**
 *  compare two operands by =, <, > etc..
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    31. August 2002
 */
public class OpEquals extends BinaryOp {

	private static Category LOG = Category.getInstance(OpEquals.class.getName());

	protected int relation = Constants.EQ;
	protected NodeSet temp = null;

	// in some cases, we use a fulltext expression to preselect nodes
	protected FunContains containsExpr = null;

	/**
	 *  Constructor for the OpEquals object
	 *
	 *@param  relation  Description of the Parameter
	 */
	public OpEquals(BrokerPool pool, int relation) {
		super(pool);
		this.relation = relation;
	}

	/**
	 *  Constructor for the OpEquals object
	 *
	 *@param  left      Description of the Parameter
	 *@param  right     Description of the Parameter
	 *@param  relation  Description of the Parameter
	 */
	public OpEquals(BrokerPool pool, Expression left, Expression right, int relation) {
		super(pool);
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
	protected Value booleanCompare(
		Expression left,
		Expression right,
		DocumentSet docs,
		NodeSet context,
		NodeProxy node) {
		ArraySet result = new ArraySet(100);
		NodeProxy n;
		boolean lvalue;
		boolean rvalue;
		ArraySet set;
		DocumentSet dset;
		for (Iterator i = context.iterator(); i.hasNext();) {
			n = (NodeProxy) i.next();
			set = new ArraySet(1);
			set.add(n);
			dset = new DocumentSet();
			dset.add(n.doc);
			rvalue = left.eval(dset, set, n).getBooleanValue();
			lvalue = right.eval(dset, set, n).getBooleanValue();
			if (lvalue == rvalue)
				result.add(n);
		}
		return new ValueNodeSet(result);
	}

	/**
	 *  Description of the Method
	 *
	 *@param  left   Description of the Parameter
	 *@param  right  Description of the Parameter
	 *@return        Description of the Return Value
	 */
	protected boolean cmpBooleans(boolean left, boolean right) {
		switch (relation) {
			case Constants.EQ :
				return (left == right);
			case Constants.NEQ :
				return (left != right);
		}
		return false;
	}

	/**
	 *  Description of the Method
	 *
	 *@param  left   Description of the Parameter
	 *@param  right  Description of the Parameter
	 *@return        Description of the Return Value
	 */
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

	/**
	 *  Description of the Method
	 *
	 *@param  left   Description of the Parameter
	 *@param  right  Description of the Parameter
	 *@return        Description of the Return Value
	 */
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
	public Value eval(DocumentSet docs, NodeSet context, NodeProxy node) {
		if (getLeft().returnsType() == Constants.TYPE_NODELIST)
			return nodeSetCompare(getLeft(), getRight(), docs, context, node);
		else if (getRight().returnsType() == Constants.TYPE_NODELIST) {
			switchOperands();
			return nodeSetCompare(getRight(), getLeft(), docs, context, node);
		} else if (getLeft().returnsType() == Constants.TYPE_NUM)
			return numberCompare(getLeft(), getRight(), docs, context, node);
		else if (getRight().returnsType() == Constants.TYPE_NUM)
			return numberCompare(getRight(), getLeft(), docs, context, node);
		else if (getLeft().returnsType() == Constants.TYPE_STRING)
			return stringCompare(getLeft(), getRight(), docs, context, node);
		else if (getLeft().returnsType() == Constants.TYPE_BOOL)
			return booleanCompare(getLeft(), getRight(), docs, context, node);
		else if (getRight().returnsType() == Constants.TYPE_BOOL)
			return booleanCompare(getRight(), getLeft(), docs, context, node);
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
	protected Value nodeSetCompare(
		Expression left,
		Expression right,
		DocumentSet docs,
		NodeSet context,
		NodeProxy node) {
		NodeSet result = new ArraySet(100);
		if (right.returnsType() == Constants.TYPE_STRING ||
			right.returnsType() == Constants.TYPE_NODELIST) {
			// evaluate left expression
			NodeSet nodes = (NodeSet) left.eval(docs, context, null).getNodeList();
			String cmp = right.eval(docs, context, null).getStringValue();
			if (getLeft().returnsType() == Constants.TYPE_NODELIST && relation == Constants.EQ &&
				nodes.hasIndex() && cmp.length() > 0) {
				// try to use a fulltext search expression to reduce the number
				// of potential nodes to scan through
				SimpleTokenizer tokenizer = new SimpleTokenizer();
				tokenizer.setText(cmp);
				TextToken token;
				String term;
				boolean foundNumeric = false;
				cmp = cmp.replace('%', '*');
				// setup up an &= expression using the fulltext index
				containsExpr = new FunContains(pool, Constants.FULLTEXT_AND);
				for (int i = 0; i < 5 && (token = tokenizer.nextToken(true)) != null; i++) {
					// remember if we find an alphanumeric token
					if(token.getType() == TextToken.ALPHANUM)
						foundNumeric = true;
					containsExpr.addTerm(token.getText());
				} 
				// check if all elements are indexed. If not, we can't use the
				// fulltext index.
				if(foundNumeric)
					foundNumeric = checkArgumentTypes(docs);
				if(!foundNumeric) {
					// all elements are indexed: use the fulltext index
					Value temp = containsExpr.eval(docs, nodes, null);
					nodes = (NodeSet) temp.getNodeList();
				}
				cmp = cmp.replace('*', '%');
			}
			// now compare the input node set to the search expression
			DBBroker broker = null;
			try {
				broker = pool.get();
				result = broker.getNodesEqualTo(nodes, docs, relation, cmp);
			} catch (EXistException e) {
				e.printStackTrace();
			} finally {
				pool.release(broker);
			}
		} else if (right.returnsType() == Constants.TYPE_NUM) {
			double rvalue;
			double lvalue;
			NodeProxy ln;
			NodeSet temp;
			NodeSet lset = (NodeSet) left.eval(docs, context, null).getNodeList();
			for (Iterator i = lset.iterator(); i.hasNext();) {
				ln = (NodeProxy) i.next();
				try {
					lvalue = Double.parseDouble(ln.getNodeValue());
				} catch (NumberFormatException nfe) {
					continue;
				}
				temp = new ArraySet(1);
				temp.add(ln);
				rvalue = right.eval(docs, temp, ln).getNumericValue();
				if (cmpNumbers(lvalue, rvalue))
					result.add(ln);
			}
		} else if (right.returnsType() == Constants.TYPE_BOOL) {
			NodeProxy n;
			NodeProxy parent;
			boolean rvalue;
			boolean lvalue;
			long pid;
			ArraySet leftNodeSet;
			ArraySet temp;
			// get left arguments node set
			leftNodeSet = (ArraySet) left.eval(docs, context, null).getNodeList();
			temp = new ArraySet(10);
			// get that part of context for which left argument's node set would
			// be > 0
			for (Iterator i = leftNodeSet.iterator(); i.hasNext();) {
				n = (NodeProxy) i.next();
				parent = context.parentWithChild(n, false, true);
				if (parent != null)
					temp.add(parent);
			}
			// now compare every node of context with the temporary set
			for (Iterator i = context.iterator(); i.hasNext();) {
				n = (NodeProxy) i.next();
				lvalue = temp.contains(n);
				rvalue = right.eval(docs, context, n).getBooleanValue();
				if (cmpBooleans(lvalue, rvalue))
					result.add(n);
			}
		}
		return new ValueNodeSet(result);
	}

	private boolean checkArgumentTypes(DocumentSet docs) {
		DBBroker broker = null;
		try {
			broker = pool.get();
			Configuration config = broker.getConfiguration();
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
		} catch(EXistException e) {
			LOG.warn("Exception while processing expression", e);
		} finally {
			pool.release(broker);
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
	protected Value numberCompare(
		Expression left,
		Expression right,
		DocumentSet docs,
		NodeSet context,
		NodeProxy node) {
		ArraySet result = new ArraySet(100);
		ArraySet currentSet;
		NodeProxy current;
		double rvalue;
		double lvalue;
		for (Iterator i = context.iterator(); i.hasNext();) {
			current = (NodeProxy) i.next();
			currentSet = new ArraySet(1);
			currentSet.add(current);
			rvalue = right.eval(docs, currentSet, current).getNumericValue();
			lvalue = left.eval(docs, currentSet, current).getNumericValue();
			if (cmpNumbers(lvalue, rvalue)) {
				result.add(current);
				current.addContextNode(current);
			}
			
		}
		return new ValueNodeSet(result);
	}

	/**
	 *  Description of the Method
	 *
	 *@return    Description of the Return Value
	 */
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
	public DocumentSet preselect(DocumentSet in_docs) {
		return in_docs;
	}

	/**
	 *  Description of the Method
	 *
	 *@return    Description of the Return Value
	 */
	public int returnsType() {
		return Constants.TYPE_NODELIST;
	}

	protected Value stringCompare(
		Expression left,
		Expression right,
		DocumentSet docs,
		NodeSet context,
		NodeProxy node) {
		ArraySet result = new ArraySet(100);
		NodeProxy n;
		String lvalue;
		String rvalue;
		int cmp;
		for (Iterator i = context.iterator(); i.hasNext();) {
			n = (NodeProxy) i.next();
			rvalue = left.eval(docs, context, n).getStringValue();
			lvalue = right.eval(docs, context, n).getStringValue();
			if (compareStrings(rvalue, lvalue))
				result.add(n);
		}
		return new ValueNodeSet(result);
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
