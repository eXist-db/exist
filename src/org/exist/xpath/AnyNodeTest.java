/*
 * AnyNodeTest.java - Aug 30, 2003
 * 
 * @author wolf
 */
package org.exist.xpath;

import org.exist.dom.NodeProxy;
import org.exist.dom.QName;
import org.w3c.dom.Node;

public class AnyNodeTest implements NodeTest {
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.NodeTest#getName()
	 */
	public QName getName() {
		return null;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.NodeTest#isWildcardTest()
	 */
	public boolean isWildcardTest() {
		return true;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.NodeTest#matches(org.w3c.dom.Node)
	 */
	public boolean matches(Node node) {
		return true;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.NodeTest#matches(org.exist.dom.NodeProxy)
	 */
	public boolean matches(NodeProxy proxy) {
		return true;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		return "node()";
	}

}
