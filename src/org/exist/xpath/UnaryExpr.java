/*
 * UnaryExpr.java - Aug 30, 2003
 * 
 * @author wolf
 */
package org.exist.xpath;

import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.SingleNodeSet;
import org.exist.storage.BrokerPool;

public class UnaryExpr extends PathExpr {

	private int mode;
	
	public UnaryExpr(BrokerPool pool, int mode) {
		super(pool);
		this.mode = mode;
	}

	public int returnsType() {
		return Constants.TYPE_NUM;
	}
	
	public Value eval(StaticContext context, DocumentSet docs, NodeSet contextSet, NodeProxy node) {
		if(node != null)
			contextSet = new SingleNodeSet(node);
		if(getLength() == 0)
			throw new IllegalArgumentException("unary expression requires an operand");
		double value = getExpression(0).eval(context, docs, contextSet).getNumericValue();
		switch(mode) {
			case Constants.MINUS :
				value = -value;
				break;
			case Constants.PLUS :
				value = +value;
		}
		return new ValueNumber(value);
	}
}
