/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
 *  meier@ifs.tu-darmstadt.de
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
 */
package org.exist.xpath;

import java.util.Iterator;

import org.exist.EXistException;
import org.exist.dom.ArraySet;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;

/**
 *  xpath-library function: match-keywords(XPATH, arg1, arg2 ...)
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    7. Oktober 2002
 */
public class FunKeywordMatchAll extends Function {

	protected String terms[] = null;
	protected NodeSet[][] hits = null;

	/**  Constructor for the FunKeywordMatchAll object */
	public FunKeywordMatchAll(BrokerPool pool) {
		super(pool, "match-all");
	}

	/**
	 *  Constructor for the FunKeywordMatchAll object
	 *
	 *@param  name  Description of the Parameter
	 */
	public FunKeywordMatchAll(BrokerPool pool, String name) {
		super(pool, name);
	}

	/**
	 *  Description of the Method
	 *
	 *@param  docs     Description of the Parameter
	 *@param  context  Description of the Parameter
	 *@param  node     Description of the Parameter
	 *@return          Description of the Return Value
	 */
	public Value eval(StaticContext context, DocumentSet docs, NodeSet contextSet,
		NodeProxy contextNode) {
		Expression path = getArgument(0);
		NodeSet nodes = (NodeSet) path.eval(context, docs, contextSet, contextNode).getNodeList();

		if (hits == null)
			processQuery(docs);
		long pid;
		NodeProxy current;
		NodeProxy parent;
		NodeSet temp = null;
		long start = System.currentTimeMillis();
		for (int j = 0; j < hits.length; j++) {
			temp = new ArraySet(100);
			for (int k = 0; k < hits[j].length; k++) {
				if (hits[j][k] == null)
					continue;
				((ArraySet) hits[j][k]).sort();
				for (Iterator i = hits[j][k].iterator(); i.hasNext();) {
					current = (NodeProxy) i.next();
					parent = nodes.parentWithChild(current, false, true);
					if (parent != null) {
						if (temp.contains(parent)) {
							parent = temp.get(parent);
							parent.addMatches(current.matches);
						} else {
							parent.addMatches(current.matches);
							temp.add(parent);
						}
					}
				}
			}
			hits[j][0] = (temp == null) ? new ArraySet(1) : temp;
		}
		NodeSet t0 = null;
		NodeSet t1;
		for (int j = 0; j < hits.length; j++) {
			t1 = hits[j][0];
			if (t0 == null)
				t0 = t1;
			else
				t0 =
					(getOperatorType() == Constants.FULLTEXT_AND)
						? t0.intersection(t1)
						: t0.union(t1);
		}
		if (t0 == null)
			t0 = new ArraySet(1);
		return new ValueNodeSet(t0);
	}

	/**
	 *  Gets the operatorType attribute of the FunKeywordMatchAll object
	 *
	 *@return    The operatorType value
	 */
	protected int getOperatorType() {
		return Constants.FULLTEXT_AND;
	}

	/**
	 *  Description of the Method
	 *
	 *@return    Description of the Return Value
	 */
	public String pprint() {
		StringBuffer buf = new StringBuffer();
		buf.append(name);
		buf.append(getArgument(0).pprint());
		for (int i = 1; i < getArgumentCount(); i++) {
			buf.append(", ");
			buf.append(getArgument(i).pprint());
		}
		buf.append(')');
		return buf.toString();
	}

	/**
	 *  Description of the Method
	 *
	 *@param  in_docs  Description of the Parameter
	 *@return          Description of the Return Value
	 */
	public DocumentSet preselect(DocumentSet in_docs) {
		int j = 0;
		processQuery(in_docs);
		NodeProxy p;
		DocumentSet ndocs = new DocumentSet();
		Iterator i;
		for (j = 0; j < hits.length; j++)
			for (int k = 0; k < hits[j].length; k++) {
				if (hits[j][k] == null)
					break;
				for (i = hits[j][k].iterator(); i.hasNext();) {
					p = (NodeProxy) i.next();
					if (!ndocs.contains(p.doc.getDocId()))
						ndocs.add(p.doc);
				}
			}

		return ndocs;
	}

	private Literal getLiteral(Expression expr) {
		if (expr instanceof PathExpr && ((PathExpr) expr).getLength() == 1)
			expr = ((PathExpr) expr).getExpression(0);
		if (expr instanceof Literal)
			return (Literal) expr;
		return null;
	}

	protected void processQuery(DocumentSet in_docs) {
		terms = new String[getArgumentCount() - 1];
		for (int i = 1; i < getArgumentCount(); i++)
			terms[i - 1] = getLiteral(getArgument(i)).literalValue;
		DBBroker broker = null;
		try {
			broker = pool.get();
			if (terms == null)
				throw new RuntimeException("no search terms");
			//in_docs = path.preselect(in_docs);
			hits = new NodeSet[terms.length][];

			for (int j = 0; j < terms.length; j++) {
				String t[] = { terms[j] };
				hits[j] =
					broker.getNodesContaining(
						in_docs,
						t,
						DBBroker.MATCH_REGEXP);
			}
		} catch (EXistException e) {
			e.printStackTrace();
		} finally {
			pool.release(broker);
		}
	}

	/**
	 *  Description of the Method
	 *
	 *@return    Description of the Return Value
	 */
	public int returnsType() {
		return Constants.TYPE_NODELIST;
	}
}
