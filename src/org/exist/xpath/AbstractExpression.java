/*
 * AbstractExpression.java - Aug 27, 2003
 * 
 * @author wolf
 */
package org.exist.xpath;

import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;

/**
 * @author wolf
 *
 * To change this generated comment go to 
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public abstract class AbstractExpression implements Expression {

	public Value eval(StaticContext context, DocumentSet docs, NodeSet contextSet) 
		throws XPathException {
		return eval(context, docs, contextSet, null); 
	}
	
	public abstract Value eval(StaticContext context, DocumentSet docs, NodeSet contextSet,
		NodeProxy contextNode) throws XPathException;
		
	public abstract DocumentSet preselect(DocumentSet in_docs, StaticContext context) throws XPathException;
	
	public abstract String pprint();
	
	public abstract int returnsType();
	
	public void setInPredicate(boolean inPredicate) {
		// has no effect by default
	}
}
