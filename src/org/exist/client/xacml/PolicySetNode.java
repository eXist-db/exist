package org.exist.client.xacml;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import com.sun.xacml.Policy;
import com.sun.xacml.PolicySet;
import com.sun.xacml.PolicyTreeElement;
import com.sun.xacml.Target;
import com.sun.xacml.combine.CombiningAlgorithm;
import com.sun.xacml.combine.PolicyCombiningAlgorithm;

public class PolicySetNode extends AbstractPolicyNode
{
	private List<AbstractPolicyNode> children;
	private List<AbstractPolicyNode> originalChildren;
	
	public PolicySetNode(NodeContainer parent, PolicySet policySet)
	{
		this(parent, null, policySet);
	}
	public PolicySetNode(NodeContainer parent, String documentName, PolicySet policySet)
	{
		super(parent, documentName, policySet);

		final List<PolicyTreeElement> toCopy = policySet.getChildren();
		children = new ArrayList<AbstractPolicyNode>(toCopy.size());
		for(final PolicyTreeElement elem : toCopy)
			add(elem);

		originalChildren = new ArrayList<AbstractPolicyNode>(children);
	}

	public PolicyTreeElement create()
	{
		return createPolicySet();
	}
	public PolicyTreeElement create(URI id)
	{
		return createPolicySet(id);
	}
	public PolicySet createPolicySet()
	{
		return createPolicySet(null);
	}
	public PolicySet createPolicySet(URI id)
	{
		final CombiningAlgorithm alg = getCombiningAlgorithm();
		if(!(alg instanceof PolicyCombiningAlgorithm))
			{throw new IllegalStateException("Combining algorithm must be a policy combining algorithm");}
		final PolicyCombiningAlgorithm algorithm = (PolicyCombiningAlgorithm)alg;
		final Target target = getTarget().getTarget();
		final List<PolicyTreeElement> copy = new ArrayList<PolicyTreeElement>(children.size());
		for(final PolicyElementNode child : children)
			copy.add(child.create());
		final URI useId = (id == null) ? getId() : id;
		return new PolicySet(useId, algorithm, getDescription(), target, copy);
	}


	public void add(PolicyTreeElement element)
	{
		add(-1, element);
	}
	public void add(int index, PolicyTreeElement element)
	{
		if(element == null)
			{return;}
		if(element instanceof Policy)
			{add(index, new PolicyNode(this, (Policy)element));}
		else if(element instanceof PolicySet)
			{add(index, new PolicySetNode(this, (PolicySet)element));}
		else
			{throw new IllegalArgumentException("Only Policies and PolicySets can be top level elements.");}
	}
	public void add(PolicyElementNode node)
	{
		add(-1, node);
	}
	public void add(int index, PolicyElementNode node)
	{
		if(node == null)
			{return;}
		if(node.getParent() != this)
			{throw new IllegalArgumentException("Cannot add a PolicyElementNode to a parent other than its declared parent.");}
		if(node instanceof AbstractPolicyNode)
		{
			if(index < 0)
				{index = children.size()+1;}
			if(index == 0)
				{throw new IllegalArgumentException("Cannot insert AbstractPolicy before Target");}
			children.add(index-1, (AbstractPolicyNode)node);
			setModified(true);
			nodeAdded(node, index);
		}
		else
			{throw new IllegalArgumentException("Only PolicyNodes and PolicySetNodes can be top level elements.");}
	}
	public void remove(PolicyElementNode node)
	{
		if(node == null)
			{return;}
		final int index = children.indexOf(node);
		if(index < 0)
			{return;}
		children.remove(index);
		setModified(true);
		nodeRemoved(node, index+1);
	}

	public boolean containsId(String id)
	{
		for(final AbstractPolicyNode child : children)
		{
			if(child.getId().toString().equals(id))
				{return true;}
		}
		return false;
	}
	public int getChildCount()
	{
		return children.size() + 1; //+1 for the target
	}

	public XACMLTreeNode getChild(int index)
	{
		return (index == 0) ? getTarget() : (XACMLTreeNode)children.get(index-1);
	}

	public int indexOfChild(Object child)
	{
		if(child == getTarget())
			{return 0;}
		final int ret = children.indexOf(child);
		return (ret >= 0) ? ret+1 : -1;
	}

	public boolean isModified(boolean deep)
	{
		if(super.isModified(deep))
			{return true;}
		if(deep)
		{
			for(final PolicyElementNode child : children)
			{
				if(child.isModified(true))
					{return true;}
			}
		}
		return false;
	}
	public void revert(boolean deep)
	{
		children = originalChildren;
		if(deep)
		{
			for(final PolicyElementNode child : children)
				child.revert(true);
		}
		super.revert(deep);
	}
	public void commit(boolean deep)
	{
		originalChildren = children;
		if(deep)
		{
			for(final PolicyElementNode child : children)
				child.commit(true);
		}
		super.commit(deep);
	}

}
