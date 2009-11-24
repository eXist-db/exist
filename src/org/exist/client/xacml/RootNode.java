package org.exist.client.xacml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.sun.xacml.Policy;
import com.sun.xacml.PolicySet;
import com.sun.xacml.PolicyTreeElement;

public class RootNode extends AbstractTreeNode implements PolicyElementContainer
{
	private List<PolicyElementNode> children;
	private List<PolicyElementNode> originalChildren;
	private List<NodeChangeListener> listeners = new ArrayList<NodeChangeListener>(2);
	
	public RootNode() {
		super(null);
		children = new ArrayList<PolicyElementNode>();
		originalChildren = Collections.emptyList();
	}
	
	public void add(PolicyTreeElement element)
	{
		add(-1, element);
	}
	public void add(int index, PolicyTreeElement element)
	{
		if(element == null)
			return;
		if(element instanceof Policy)
			add(index, new PolicyNode(this, (Policy)element));
		else if(element instanceof PolicySet)
			add(index, new PolicySetNode(this, (PolicySet)element));
		else
			throw new IllegalArgumentException("Only Policies and PolicySets can be top level elements.");
	}
	public void add(PolicyElementNode node)
	{
		add(-1, node);
	}
	public void add(int index, PolicyElementNode node)
	{
		if(node == null)
			return;
		if(node instanceof AbstractPolicyNode)
		{
			if(index < 0)
				index = children.size();
			children.add(index, node);
			setModified(true);
			nodeAdded(node, index);
		}
		else
			throw new IllegalArgumentException("Only PolicyNodes and PolicySetNodes can be top level elements.");
	}
	public void remove(PolicyElementNode node)
	{
		if(node == null)
			return;
		int index = children.indexOf(node);
		if(index < 0)
			return;
		children.remove(index);
		setModified(true);
		nodeRemoved(node, index);
	}
	
	public boolean containsId(String id) {
		for(PolicyElementNode child : children) {
			if( child.getId().toString().equals(id) )
				return true;
		}
		return false;
	}
	
	public int getChildCount()
	{
		return children.size();
	}

	public XACMLTreeNode getChild(int index)
	{
		return (XACMLTreeNode)children.get(index);
	}
	public int indexOfChild(Object child)
	{
		return children.indexOf(child);
	}
	
	public boolean isModified(boolean deep) {
		if(super.isModified(deep))
			return true;
		
		if(deep) {
			for(PolicyElementNode child : children) {
				if(child.isModified(true))
					return true;
			}
		}
		return false;
	}

	public void revert(boolean deep) {
		children = originalChildren;
		
		if(deep) {
			for(PolicyElementNode child : children)
				child.revert(true);
		}
		super.revert(deep);
	}
	
	public void commit(boolean deep) {
		originalChildren = children;
		
		if(deep) {
			for(PolicyElementNode node : children)
				node.commit(true);
		}
		super.commit(deep);
	}
	
	public Set<String> getRemovedDocumentNames() {
		Set<String> ret = new HashSet<String>();
		for(Iterator originalIt = originalChildren.iterator(); originalIt.hasNext();) {
			AbstractPolicyNode originalChild = (AbstractPolicyNode)originalIt.next();
			String documentName = originalChild.getDocumentName();
			if(!documentNameExists(documentName))
				ret.add(documentName);
		}
		return ret;
	}
	
	private boolean documentNameExists(String documentName) {
		for(Iterator currentIt = children.iterator(); currentIt.hasNext();) {
			AbstractPolicyNode currentChild = (AbstractPolicyNode)currentIt.next();
			String currentDocName = currentChild.getDocumentName();
			if(currentDocName != null && currentDocName.equals(documentName))
				return true;
		}
		return false;
	}

	public void addNodeChangeListener(NodeChangeListener listener) {
		if(listener != null)
			listeners.add(listener);
	}
	
	public void removeNodeChangeListener(NodeChangeListener listener) {
		if(listener != null)
			listeners.remove(listener);
	}
	
	public void nodeChanged(XACMLTreeNode node) {
		for(NodeChangeListener listener : listeners)
			listener.nodeChanged(node);
	}
	
	public void nodeAdded(XACMLTreeNode node, int newIndex) {
		for(NodeChangeListener listener : listeners)
			listener.nodeAdded(node, newIndex);
	}
	
	public void nodeRemoved(XACMLTreeNode removedNode, int oldChildIndex) {
		for(NodeChangeListener listener : listeners)
			listener.nodeRemoved(removedNode, oldChildIndex);
	}
	
	public String serialize(boolean indent) {
		throw new UnsupportedOperationException("Cannot serialize the root node");
	}
}
