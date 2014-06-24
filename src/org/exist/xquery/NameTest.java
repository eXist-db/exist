package org.exist.xquery;

import org.exist.dom.NodeProxy;
import org.exist.dom.QName;
import org.exist.memtree.NodeImpl;
import org.exist.memtree.ReferenceNode;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Type;
import org.w3c.dom.Node;

import javax.xml.stream.XMLStreamReader;

public class NameTest extends TypeTest {

	protected final QName nodeName;

	public NameTest(int type, QName name) throws XPathException {
		super(type);
		name.isValid();
		nodeName = name;
	}

	public QName getName() {
		return nodeName;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.NodeTest#matches(org.exist.dom.NodeProxy)
	 */
	public boolean matches(NodeProxy proxy) {
		Node node = null;
		short type = proxy.getNodeType();
		if(proxy.getType() == Type.ITEM) {
			node = proxy.getNode();
			type = node.getNodeType();
		}
		if (!isOfType(type))
			{return false;}
		if(node == null)
			{node = proxy.getNode();}
        return matchesName(node);
	}

	public boolean matches(Node other) {
        if (other.getNodeType() == NodeImpl.REFERENCE_NODE)
            {return matches(((ReferenceNode)other).getReference());}
        if(!isOfType(other.getNodeType()))
			{return false;}
		return matchesName(other);
	}

	public boolean matches(QName name) {
		if (nodeName.getNamespaceURI() != null) {
            if (!nodeName.getNamespaceURI().equals(name.getNamespaceURI()))
				{return false;}
		}
		if (nodeName.getLocalName() != null) {
			return nodeName.getLocalName().equals(name.getLocalName());
		}
		return true;
	}
	
    public boolean matchesName(Node other) {
        if (other.getNodeType() == NodeImpl.REFERENCE_NODE)
            {return matchesName(((ReferenceNode)other).getReference().getNode());}
        if (nodeName.getNamespaceURI() != null) {
            if (!nodeName.getNamespaceURI().equals(other.getNamespaceURI()))
				{return false;}
		}
		if (nodeName.getLocalName() != null) {
			return nodeName.getLocalName().equals(other.getLocalName());
		}
		return true;
	}

    public boolean matches(XMLStreamReader reader) {
        final int ev = reader.getEventType();
        if (!isOfEventType(ev))
            {return false;}
        switch (ev) {
            case XMLStreamReader.START_ELEMENT :
                if (nodeName.getNamespaceURI() != null) {
                    if (!nodeName.getNamespaceURI().equals(reader.getNamespaceURI()))
                        {return false;}
                }
                if (nodeName.getLocalName() != null) {
                    return nodeName.getLocalName().equals(reader.getLocalName());
                }
                break;
            case XMLStreamReader.PROCESSING_INSTRUCTION :
                if (nodeName.getLocalName() != null) {
                    return nodeName.getLocalName().equals(reader.getPITarget());
                }
                break;
        }
        return true;
    }

    /* (non-Javadoc)
	 * @see org.exist.xquery.NodeTest#isWildcardTest()
	 */
	public boolean isWildcardTest() {
		return nodeName.getLocalName() == null || nodeName.getNamespaceURI() == null;
	}

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof NameTest) {
            final NameTest other = (NameTest) obj;
            return other.nodeType == nodeType && other.nodeName.equalsSimple(nodeName);
        }
        return false;
    }

    public void dump(ExpressionDumper dumper) {
        if(nodeName.getLocalName() == null)
            {dumper.display(nodeName.getPrefix() + ":*");}
        else
            {dumper.display(nodeName.getStringValue());}        
    }    

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
    public String toString() {
        final StringBuilder result = new StringBuilder();
        
        if(nodeName.getPrefix() != null) {
            result.append(nodeName.getPrefix());
            result.append(":");
        } else if(nodeName.getNamespaceURI() != null) {
            result.append("{");
            result.append(nodeName.getNamespaceURI());
            result.append("}");
        }
        
        if(nodeName.getLocalName() == null) {
            result.append("*");
        } else {
            result.append(nodeName.getLocalName());
        }
        
        return result.toString();
    }

}
