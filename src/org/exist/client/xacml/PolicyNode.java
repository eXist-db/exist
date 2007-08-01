package org.exist.client.xacml;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.sun.xacml.Policy;
import com.sun.xacml.PolicyTreeElement;
import com.sun.xacml.Rule;
import com.sun.xacml.Target;
import com.sun.xacml.combine.RuleCombiningAlgorithm;

public class PolicyNode extends AbstractPolicyNode
{
	private List rules;
	private List originalRules;
	
	public PolicyNode(NodeContainer parent, Policy policy)
	{
		this(parent, null, policy);
	}
	public PolicyNode(NodeContainer parent, String documentName, Policy policy)
	{
		super(parent, documentName, policy);
		
		List children = policy.getChildren();
		rules = new ArrayList(children.size());
		for(Iterator it = children.iterator(); it.hasNext();)
			rules.add(new RuleNode(this, (Rule)it.next()));
		originalRules = new ArrayList(rules);
	}

	public PolicyTreeElement create()
	{
		return createPolicy();
	}
	public PolicyTreeElement create(URI id)
	{
		return createPolicy(id);
	}
	public Policy createPolicy()
	{
		return createPolicy(null);
	}
	public Policy createPolicy(URI id)
	{
		Target target = getTarget().getTarget();
		RuleCombiningAlgorithm algorithm = (RuleCombiningAlgorithm)getCombiningAlgorithm();
		List rawRules = new ArrayList(rules.size());
		for(Iterator it = rules.iterator(); it.hasNext();)
			rawRules.add(((RuleNode)it.next()).createRule());
		URI useId = (id == null) ? getId() : id;
		return new Policy(useId, algorithm, getDescription(), target, rawRules);
	}

	public void add(PolicyTreeElement element)
	{
		add(-1, element);
	}
	public void add(int index, PolicyTreeElement element)
	{
		if(element == null)
			return;
		if(element instanceof Rule)
			add(index, new RuleNode(this, (Rule)element));
		else
			throw new IllegalArgumentException("Policies can only contain rules.");
	}
	public void add(PolicyElementNode node)
	{
		add(-1, node);
	}
	public void add(int index, PolicyElementNode node)
	{
		if(node == null)
			return;
		if(node instanceof RuleNode)
		{
			if(index < 0)
				index = rules.size()+1;
			if(index == 0)
				throw new IllegalArgumentException("Cannot insert Rule before Target");
			rules.add(index-1, node);
			setModified(true);
			nodeAdded(node, index);
		}
		else
			throw new IllegalArgumentException("PolicyNodes can only contain RuleNodes.");
	}
	public void remove(PolicyElementNode node)
	{
		if(node == null)
			return;
		int index = rules.indexOf(node);
		if(index < 0)
			return;
		rules.remove(index);
		setModified(true);
		nodeRemoved(node, index+1);
	}

	public boolean containsId(String id)
	{
		for(Iterator it = rules.iterator();it.hasNext();)
		{
			if(((RuleNode)it.next()).getId().toString().equals(id))
				return true;
		}
		return false;
	}
	public int getChildCount()
	{
		return rules.size() + 1; //+1 for the target
	}

	public XACMLTreeNode getChild(int index)
	{
		return (index == 0) ? getTarget() : (XACMLTreeNode)rules.get(index-1);
	}

	public int indexOfChild(Object child)
	{
		if(child == getTarget())
			return 0;
		int ret = rules.indexOf(child);
		return (ret >= 0) ? ret + 1 : -1;
	}
	public boolean isModified(boolean deep)
	{
		if(super.isModified(deep))
			return true;
		if(deep)
		{
			for(Iterator it = rules.iterator(); it.hasNext();)
			{
				if(((RuleNode)it.next()).isModified(true))
					return true;
			}
		}
		return false;
	}
	public void revert(boolean deep)
	{
		rules = new ArrayList(originalRules);
		if(deep)
		{
			for(Iterator it = rules.iterator(); it.hasNext();)
				((RuleNode)it.next()).revert(true);
		}
		super.revert(deep);
	}
	public void commit(boolean deep)
	{
		originalRules = new ArrayList(rules);
		if(deep)
		{
			for(Iterator it = rules.iterator(); it.hasNext();)
				((RuleNode)it.next()).commit(true);
		}
		super.commit(deep);
	}
}
