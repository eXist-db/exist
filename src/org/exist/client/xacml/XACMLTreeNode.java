package org.exist.client.xacml;

public interface XACMLTreeNode
{
	/**
	 * Returns true if this element has been modified.  If 
	 * deep is true, then this takes into account the status
	 * of any descendants.
	 * 
	 * @param deep if descendants should be included
	 * @return if this element has been modified
	 */
	boolean isModified(boolean deep);
	
	/**
	 * Sets the modification status of this node only.
	 * 
	 * @param flag Whether this node's state is different
	 *  from its state immediately after the last commit.
	 * 
	 */
	void setModified(boolean flag);
	
	/**
	 * Reverts the state of this element to the last commit.
	 * If deep is true, then this includes any descendants.
	 * 
	 * @param deep
	 */
	void revert(boolean deep);

	/**
	 * Commits the state of this element.  This state
	 * will be the state reverted to when revert is called.
	 * If deep is true, then this includes any descendants.
	 * 
	 * @param deep
	 */
	void commit(boolean deep);
	
	/**
	 * Returns the parent of this node, or null if this is
	 * the root node.
	 * 
	 * @return This node's parent
	 */
	NodeContainer getParent();
	
	/**
	 * Serializes this node to a <code>String</code>
	 * 
	 * @param indent Whether or not the XML should be indented
	 * @return The string representation of this node
	 */
	String serialize(boolean indent);
}
