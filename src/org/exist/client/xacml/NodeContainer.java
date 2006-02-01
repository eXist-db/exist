package org.exist.client.xacml;

public interface NodeContainer extends NodeChangeListener, XACMLTreeNode
{	
	/**
	 * Gets the number of children of this element.  Children
	 * should include the target of this element, the condition
	 * of a rule, child policies or policy sets of a policy set,
	 * or child rules of a policy.  
	 * 
	 * @return the number of children
	 */
	abstract int getChildCount();
	
	/**
	 * Gets the child at the specified index.  Children
	 * may include the target of this element, the condition of
	 * a rule, child policies or policy sets of a policy set,
	 * or child rules of a policy.
	 * 
	 * @param index The child's position
	 * @return the child
	 */
	abstract XACMLTreeNode getChild(int index);
	
	/**
	 * Gets the index of a particular child.  This operates on
	 * reference equality, not equals.
	 * 
	 * @param child The child to obtain the index of
	 * @return The child's index, or -1 if it is not a child of
	 *  this policy element
	 */
	abstract int indexOfChild(Object child);
}
