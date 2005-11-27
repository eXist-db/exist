package org.exist.xquery;

import org.exist.dom.NodeProxy;
import org.exist.dom.QName;
import org.exist.xquery.value.Type;
import org.w3c.dom.Node;

/**
 * Tests if a node is of a given node type.
 * 
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class TypeTest implements NodeTest {

	protected int nodeType = 0;
	
	public TypeTest(int nodeType) {
		setType(nodeType);
	}

	public void setType(int nodeType) {
	    this.nodeType = nodeType;
	}
	
	public int getType() {
		return nodeType;
	}
	
	public QName getName() {
		return null;
	}
	
	protected boolean isOfType(short type) {
		int domType;
		switch (nodeType) {
			case Type.ELEMENT :
				domType = Node.ELEMENT_NODE;
				break;
			case Type.TEXT :
				domType = Node.TEXT_NODE;
				break;
			case Type.ATTRIBUTE :
				domType = Node.ATTRIBUTE_NODE;
				break;
			case Type.COMMENT :
				domType = Node.COMMENT_NODE;
				break;
			case Type.PROCESSING_INSTRUCTION :
				domType = Node.PROCESSING_INSTRUCTION_NODE;
				break;
			case Type.NODE :
			default :
				return true;
		}
		return (type == domType);
	}

	public String toString() {
		return nodeType == Type.NODE ? "node()" : Type.NODETYPES[nodeType] + "()";
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.NodeTest#matches(org.exist.dom.NodeProxy)
	 */
	public boolean matches(NodeProxy proxy) {        
		if(proxy.getNodeType() == Type.ITEM || proxy.getNodeType() == Type.NODE) {
			if (this.nodeType == Type.NODE)
				return true;			
			Node node = proxy.getNode();
			return matches(node);
		} else
			return isOfType(proxy.getNodeType());
	}
	
	public boolean matches(Node other) {
		if(other == null)
			return false;
		return isOfType(other.getNodeType());
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.NodeTest#isWildcardTest()
	 */
	public boolean isWildcardTest() {
		return true;
	}

}
