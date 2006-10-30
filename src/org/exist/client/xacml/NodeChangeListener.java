package org.exist.client.xacml;

public interface NodeChangeListener
{
	void nodeChanged(XACMLTreeNode node);
	void nodeAdded(XACMLTreeNode node, int newIndex);
	void nodeRemoved(XACMLTreeNode removedNode, int oldChildIndex);
}
