/*
 *  eXist Open Source Native XML Database
 * Copyright (C) 2001, Wolfgang M. Meier (meier@ifs. tu- darmstadt. de)
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
 *  You should have received a copy of the GNU Library General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.exist.xpath;

import java.util.Iterator;
import java.util.ArrayList;
import org.exist.*;
import org.exist.storage.*;
import org.exist.dom.*;
import org.apache.log4j.Category;

/**
 *  Description of the Class
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    July 31, 2002
 */
public class FunContains extends Function {

	private static Category LOG =
		Category.getInstance(FunContains.class.getName());
	protected ArrayList containsExpr = new ArrayList(2);
	protected NodeSet[][] hits = null;
	protected PathExpr path;
	protected String terms[] = null;
	protected int type = Constants.FULLTEXT_AND;

	/**
	 *  Constructor for the FunContains object
	 *
	 *@param  type    Description of the Parameter
	 */
	public FunContains(BrokerPool pool, int type) {
		super(pool, "contains");
		this.type = type;
	}

	/**
	 *  Constructor for the FunContains object
	 *
	 *@param  path    Description of the Parameter
	 *@param  arg     Description of the Parameter
	 */
	public FunContains(BrokerPool pool, PathExpr path, String arg) {
		super(pool, "contains");
		this.path = path;
		this.containsExpr.add(arg);
	}

	/**
	 *  Adds a feature to the Term attribute of the FunContains object
	 *
	 *@param  arg  The feature to be added to the Term attribute
	 */
	public void addTerm(String arg) {
		this.containsExpr.add(arg);
	}

	public Value eval(DocumentSet docs, NodeSet context, NodeProxy node) {
		NodeSet nodes = (NodeSet) path.eval(docs, context, null).getNodeList();
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
						if(temp.contains(parent)) {
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
					(type == Constants.FULLTEXT_AND)
						? t0.intersection(t1)
						: t0.union(t1);

		}
		if (t0 == null)
			t0 = NodeSet.EMPTY_SET;
		return new ValueNodeSet(t0);
	}

	/**
	 *  Description of the Method
	 *
	 *@return    Description of the Return Value
	 */
	public String pprint() {
		StringBuffer buf = new StringBuffer();
		buf.append(path.pprint());
		buf.append(" &= ");
		for (Iterator i = containsExpr.iterator(); i.hasNext();) {
			buf.append('\'');
			buf.append(i.next());
			buf.append('\'');
			if (i.hasNext())
				buf.append(", ");

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

		processQuery(in_docs);
		NodeProxy p;
		DocumentSet ndocs = new DocumentSet();
		Iterator i;
		for (int j = 0; j < hits.length; j++)
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

	/**
	 *  Description of the Method
	 *
	 *@param  in_docs  Description of the Parameter
	 */
	protected void processQuery(DocumentSet in_docs) {
		terms = new String[containsExpr.size()];
		boolean skip_preselect = false;
		int j = 0;
		for (Iterator i = containsExpr.iterator(); i.hasNext(); j++) {
			terms[j] = (String) i.next();
		}
		DBBroker broker = null;
		try {
			broker = pool.get();
			if (terms == null)
				throw new RuntimeException("no search terms");
			hits = new NodeSet[terms.length][];

			for (int k = 0; k < terms.length; k++) {
				String t[] = { terms[k] };
				hits[k] = broker.getNodesContaining(in_docs, t);
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

	/**
	 *  Sets the path attribute of the FunContains object
	 *
	 *@param  path  The new path value
	 */
	public void setPath(PathExpr path) {
		this.path = path;
	}
}
