/*
 * FunContains.java - Aug 29, 2003
 * 
 * @author wolf
 */
package org.exist.xpath;

import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.SingleNodeSet;

public class FunContains extends Function {

	public FunContains() {
		super("contains");
	}
	
	public int returnsType() {
		return Constants.TYPE_BOOL;
	}
	
	public Value eval(StaticContext context, DocumentSet docs, NodeSet contextSet, 
		NodeProxy contextNode) throws XPathException {
		if (getArgumentCount() != 2)
			throw new IllegalArgumentException("starts-with expects two arguments");
		if(contextNode != null)
			contextSet = new SingleNodeSet(contextNode);
		String s1 = getArgument(0).eval(context, docs, contextSet).getStringValue();
		String s2 = getArgument(1).eval(context, docs, contextSet).getStringValue();
		if(s1.indexOf(s2) > -1)
			return new ValueBoolean(true);
		else
			return new ValueBoolean(false);
	}
}
