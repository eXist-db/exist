package org.exist.xquery;

import org.exist.dom.NodeProxy;
import org.exist.dom.QName;
import org.w3c.dom.Node;

public interface NodeTest {

    public void setType(int nodeType);
    
    public int getType();
    
	public boolean matches(NodeProxy proxy);

	public boolean matches(Node node);
	
	public boolean isWildcardTest();
	
	public QName getName();
}
