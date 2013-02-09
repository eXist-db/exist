package org.exist.client.xacml;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import com.sun.xacml.Policy;
import com.sun.xacml.PolicyTreeElement;
import com.sun.xacml.Rule;
import com.sun.xacml.Target;
import com.sun.xacml.combine.RuleCombiningAlgorithm;

public class PolicyNode extends AbstractPolicyNode
{
	private List<RuleNode> rules;
	private List<RuleNode> originalRules;
	
	public PolicyNode(NodeContainer parent, Policy policy)
	{
		this(parent, null, policy);
	}
	public PolicyNode(NodeContainer parent, String documentName, Policy policy)
	{
		super(parent, documentName, policy);
		
		final List<Rule> children = policy.getChildren();
		rules = new ArrayList<RuleNode>(children.size());
		for(final Rule rule : children)
			rules.add(new RuleNode(this, rule));
		originalRules = new ArrayList<RuleNode>(rules);
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
		final Target target = getTarget().getTarget();
		final RuleCombiningAlgorithm algorithm = (RuleCombiningAlgorithm)getCombiningAlgorithm();
		final List<Rule> rawRules = new ArrayList<Rule>(rules.size());
		for(final RuleNode rule : rules)
			rawRules.add(rule.createRule());
		final URI useId = (id == null) ? getId() : id;
		return new Policy(useId, algorithm, getDescription(), target, rawRules);
	}

	public void add(PolicyTreeElement element)
	{
		add(-1, element);
	}
	public void add(int index, PolicyTreeElement element)
	{
		if(element == null)
			{return;}
		if(element instanceof Rule)
			{add(index, new RuleNode(this, (Rule)element));}
		else
			{throw new IllegalArgumentException("Policies can only contain rules.");}
	}
	public void add(PolicyElementNode node)
	{
		add(-1, node);
	}
	public void add(int index, PolicyElementNode node)
	{
		if(node == null)
			{return;}
		if(node instanceof RuleNode)
		{
			if(index < 0)
				{index = rules.size()+1;}
			if(index == 0)
				{throw new IllegalArgumentException("Cannot insert Rule before Target");}
			rules.add(index-1, (RuleNode)node);
			setModified(true);
			nodeAdded(node, index);
		}
		else
			{throw new IllegalArgumentException("PolicyNodes can only contain RuleNodes.");}
	}
	public void remove(PolicyElementNode node)
	{
		if(node == null)
			{return;}
		final int index = rules.indexOf(node);
		if(index < 0)
			{return;}
		rules.remove(index);
		setModified(true);
		nodeRemoved(node, index+1);
	}

	public boolean containsId(String id)
	{
		for(final RuleNode rule : rules)
		{
			if(rule.getId().toString().equals(id))
				{return true;}
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
			{return 0;}
		final int ret = rules.indexOf(child);
		return (ret >= 0) ? ret + 1 : -1;
	}
	public boolean isModified(boolean deep)
	{
		if(super.isModified(deep))
			{return true;}
		if(deep)
		{
			for(final RuleNode rule : rules)
			{
				if(rule.isModified(true))
					{return true;}
			}
		}
		return false;
	}
	public void revert(boolean deep)
	{
		rules = new ArrayList<RuleNode>(originalRules);
		if(deep)
		{
			for(final RuleNode rule : rules)
				rule.revert(true);
		}
		super.revert(deep);
	}
	public void commit(boolean deep)
	{
		originalRules = new ArrayList<RuleNode>(rules);
		if(deep)
		{
			for(final RuleNode rule : rules)
				rule.commit(true);
		}
		super.commit(deep);
	}
}
