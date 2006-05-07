/*
 * AnyNodeTest.java - Aug 30, 2003
 * 
 * @author wolf
 */
package org.exist.xquery;

import org.exist.dom.NodeProxy;
import org.exist.dom.QName;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Type;
import org.w3c.dom.Node;

public class AnyNodeTest implements NodeTest {
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.NodeTest#getName()
	 */
	public QName getName() {
		return null;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.NodeTest#isWildcardTest()
	 */
	public boolean isWildcardTest() {
		return true;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.NodeTest#matches(org.w3c.dom.Node)
	 */
	public boolean matches(Node node) {
	    return (node.getNodeType() != Node.ATTRIBUTE_NODE);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.NodeTest#matches(org.exist.dom.NodeProxy)
	 */
	public boolean matches(NodeProxy proxy) {
	    int type = proxy.getType();
		if (type == Type.ITEM || type == Type.NODE) {		
			if (proxy.getNodeType() != NodeProxy.UNKNOWN_NODE_TYPE)
				return matches(proxy.getNode());
			return proxy.getNodeType() != Node.ATTRIBUTE_NODE;
		} else
			return type != Node.ATTRIBUTE_NODE;
	}
    
    public void dump(ExpressionDumper dumper) {
        if(dumper.verbosity() > 1) {            
            dumper.display("node()"); 
        }
    }    

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		return "node()";
	}

    /* (non-Javadoc)
     * @see org.exist.xquery.NodeTest#setType(int)
     */
    public void setType(int nodeType) {
    }
    
    public int getType() {
        return Type.NODE;
    }
}
