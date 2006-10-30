package org.exist.client.xacml;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

/**
 * XACML (exactly one)
 * |
 * --Top-level PolicySet (zero or more)
 *   |
 *   --Target (exactly one, may be empty)
 *   |
 *   --Policy (zero or more)
 *     |
 *     --Target (exactly one, may be empty)
 *     |
 *     --Rule (zero or more)
 *       |
 *       --Target (exactly one, may be empty)
 *       |
 *       --Condition (exactly one, may be empty)
 * 
 */
public class XACMLTreeModel implements NodeChangeListener, TreeModel
{
	private List listeners = new ArrayList(2);

	private RootNode root;
	
	private XACMLTreeModel() {}
	public XACMLTreeModel(RootNode root)
	{
		if(root == null)
			throw new NullPointerException("Root node cannot be null");
		this.root = root;
		root.addNodeChangeListener(this);
	}
	public Object getRoot()
	{
		return root;
	}

	public int getChildCount(Object parent)
	{
		if(parent instanceof NodeContainer)
			return ((NodeContainer)parent).getChildCount();
		return 0;
	}

	public boolean isLeaf(Object parent)
	{
		return !(parent == root || parent instanceof PolicyElementNode);
	}

	public Object getChild(Object parent, int index)
	{
		if(parent instanceof NodeContainer)
			return ((NodeContainer)parent).getChild(index);
		return null;
	}

	public int getIndexOfChild(Object parent, Object child)
	{
		if(parent instanceof NodeContainer)
			return ((NodeContainer)parent).indexOfChild(child);
		return -1;
	}

	public void valueForPathChanged(TreePath path, Object newValue)
	{
		//do nothing
	}


	public void addTreeModelListener(TreeModelListener listener)
	{
		if(listener != null)
			listeners.add(listener);
	}

	public void removeTreeModelListener(TreeModelListener listener)
	{
		if(listener != null)
			listeners.remove(listener);
	}
	public boolean hasUnsavedChanges()
	{
		return root.isModified(true);
	}
	public void revert()
	{
		root.revert(true);
	}
	public void commit()
	{
		root.commit(true);
	}
	public void nodeChanged(XACMLTreeNode node)
	{
		TreePath path = getPathToNode(node);
		TreeModelEvent event = new TreeModelEvent(this, path);
		for(Iterator it = listeners.iterator(); it.hasNext();)
			((TreeModelListener)it.next()).treeNodesChanged(event);
	}
	public void nodeAdded(XACMLTreeNode node, int newIndex)
	{
		TreeModelEvent event = getEvent(node, newIndex);
		for(Iterator it = listeners.iterator(); it.hasNext();)
			((TreeModelListener)it.next()).treeNodesInserted(event);
	}
	public void nodeRemoved(XACMLTreeNode removedNode, int oldChildIndex)
	{
		TreeModelEvent event = getEvent(removedNode, oldChildIndex);
		for(Iterator it = listeners.iterator(); it.hasNext();)
			((TreeModelListener)it.next()).treeNodesRemoved(event);
	}
	private TreeModelEvent getEvent(XACMLTreeNode node, int index)
	{
		TreePath path = getPathToNode(node.getParent());
		int[] childIndices = { index };
		Object[] child = { node };
		TreeModelEvent event = new TreeModelEvent(this, path, childIndices, child);
		return event;
	}
	public static TreePath getPathToNode(XACMLTreeNode node)
	{
		NodeContainer parent = node.getParent();
		if(parent == null)
			return new TreePath(node);
		return getPathToNode(parent).pathByAddingChild(node);
	}
}
