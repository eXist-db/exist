/* eXist Open Source Native XML Database
 * Copyright (C) 2000,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Library General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package org.exist.xpath;

import org.apache.log4j.Category;
import org.exist.dom.ArraySet;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.w3c.dom.NodeList;

/**
 * numeric operation on two operands by +, -, *, div, mod etc..
 *
 */
public class OpNumeric extends BinaryOp {

    private static Category LOG = Category.getInstance(OpEquals.class.getName());

    protected int operator = Constants.PLUS;
    protected NodeSet temp = null;
    protected DBBroker broker;

    public OpNumeric(BrokerPool pool, int operator) {
        super(pool);
        this.operator = operator;
    }

    public OpNumeric(BrokerPool pool, Expression left,
                    Expression right, int operator) {
		super(pool);
        this.operator = operator;
		add(left);
		add(right);
    }

    public int returnsType() {
		return Constants.TYPE_NUM;
	}

    public DocumentSet preselect(DocumentSet in_docs) {
		if(getLength() == 0)
			return in_docs;
		DocumentSet out_docs = getExpression(0).preselect(in_docs);
		for(int i = 1; i < getLength(); i++)
			out_docs = out_docs.union(getExpression(i).preselect(out_docs));
		return out_docs;
    }

	public Value eval(DocumentSet docs, NodeSet context, NodeProxy node) {
		if(getLeft().returnsType() == Constants.TYPE_NODELIST) {
			DocumentSet dset = new DocumentSet();
			NodeSet set = new ArraySet(1);
			set.add(node);
			dset.add(node.doc);
			ValueSet result = new ValueSet();
			double rvalue, val;
			NodeList args = getLeft().eval(dset, set, node).getNodeList();
			rvalue = getRight().eval(dset, set, node).getNumericValue();
			for(int i = 0; i < args.getLength(); i++) {
				try {
					val = applyOperator(Double.parseDouble(args.item(i).getNodeValue()), rvalue);
					if(val != Double.NaN)
						result.add(new ValueNumber(val));
				} catch(NumberFormatException nfe) {
				}
			}
			return result;
		}
		return new ValueNumber(Double.NaN);
	}

	public double applyOperator(double left, double right) {
		if(left == Double.NaN || right == Double.NaN)
			return Double.NaN;
		switch(operator) {
		case Constants.MINUS:
			return left - right;
		case Constants.PLUS:
			return left + right;
		case Constants.MULT:
			return left * right;
		case Constants.DIV:
			return left / right;
		default:
			return Double.NaN;
		}
	}
	 
    public String pprint() {
        StringBuffer buf = new StringBuffer();
        buf.append(getLeft().pprint());
        buf.append(Constants.OPS[operator]);
        buf.append(getRight().pprint());
        return buf.toString();
    }
}
