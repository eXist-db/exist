package org.exist.xpath;

import org.exist.dom.NodeProxy;
import org.w3c.dom.Node;

public class TypeTest extends NodeTest {

	protected int nodeType = 0;

	public static TypeTest ANY_TYPE = new TypeTest(Constants.NODE_TYPE);

	public TypeTest(int nodeType) {
		super(NodeTest.TYPE_TEST);
		this.nodeType = nodeType;
	}

	public int getNodeType() {
		return nodeType;
	}

	public boolean isOfType(NodeProxy proxy, short type) {
		if (getNodeType() == Constants.NODE_TYPE)
			return true;
		if (type == Constants.TYPE_UNKNOWN) {
			Node node = proxy.doc.getNode(proxy);
			if (node == null)
				return false;
			type = node.getNodeType();
		}
		return isOfType(type);
	}

	public boolean isOfType(short type) {
		int domType;
		switch (getNodeType()) {
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
}
