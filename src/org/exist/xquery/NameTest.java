package org.exist.xquery;

import org.exist.dom.NodeProxy;
import org.exist.dom.QName;
import org.exist.xquery.value.Type;
import org.w3c.dom.Node;

public class NameTest extends TypeTest {

	protected QName nodeName;

	public NameTest(int type, QName name) {
		super(type);
		nodeName = name;
	}

	public QName getName() {
		return nodeName;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.NodeTest#matches(org.exist.dom.NodeProxy)
	 */
	public boolean matches(NodeProxy proxy) {
		Node node = null;
		short type = proxy.nodeType;
		if(type == Type.ITEM) {
			node = proxy.getNode();
			type = node.getNodeType();
		}
		if (!isOfType(type))
			return false;
		if(node == null)
			node = proxy.getNode();
		return matchesInternal(node);
	}

	public boolean matches(Node other) {
		if(!isOfType(other.getNodeType()))
			return false;
		return matchesInternal(other);
	}
	
	private boolean matchesInternal(Node other) {
		if (nodeName.getNamespaceURI() != null) {
			if (!nodeName.getNamespaceURI().equals(other.getNamespaceURI()))
				return false;
		}
		if (nodeName.getLocalName() != null) {
			return nodeName.getLocalName().equals(other.getLocalName());
		}
		return true;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.NodeTest#isWildcardTest()
	 */
	public boolean isWildcardTest() {
		return nodeName.getLocalName() == null || nodeName.getNamespaceURI() == null;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		return nodeName.toString();
	}

}
