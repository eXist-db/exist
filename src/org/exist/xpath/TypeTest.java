package org.exist.xpath;

import org.exist.dom.NodeProxy;
import org.exist.dom.QName;
import org.w3c.dom.Node;

public class TypeTest implements NodeTest {

	protected int nodeType = 0;
	
	public TypeTest(int nodeType) {
		this.nodeType = nodeType;
	}

	public QName getName() {
		return null;
	}
	
	protected boolean isOfType(short type) {
		int domType;
		switch (nodeType) {
			case Constants.ELEMENT_NODE :
				domType = Node.ELEMENT_NODE;
				break;
			case Constants.TEXT_NODE :
				domType = Node.TEXT_NODE;
				break;
			case Constants.ATTRIBUTE_NODE :
				domType = Node.ATTRIBUTE_NODE;
				break;
			case Constants.NODE_TYPE :
			default :
				return true;
		}
		return (type == domType);
	}

	public String toString() {
		return Constants.NODETYPES[nodeType] + "()";
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.NodeTest#matches(org.exist.dom.NodeProxy)
	 */
	public boolean matches(NodeProxy proxy) {
		if(proxy.nodeType == Constants.TYPE_UNKNOWN) {
			Node node = proxy.getNode();
			return matches(node);
		} else
			return isOfType(proxy.nodeType);
	}
	
	public boolean matches(Node other) {
		if(other == null)
			return false;
		return isOfType(other.getNodeType());
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.NodeTest#isWildcardTest()
	 */
	public boolean isWildcardTest() {
		return true;
	}

}
