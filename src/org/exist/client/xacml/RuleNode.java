package org.exist.client.xacml;

import java.net.URI;

import com.sun.xacml.PolicyTreeElement;
import com.sun.xacml.Rule;
import com.sun.xacml.ctx.Result;

public class RuleNode extends PolicyElementNode
{
	private int effect;
	private int originalEffect;
	private ConditionNode condition;
	
	public RuleNode(NodeContainer parent, Rule rule)
	{
		super(parent, rule);
		
		if(rule == null)
		{
			effect = Result.DECISION_DENY;
			condition = new ConditionNode(this);
		}
		else
		{
			effect = rule.getEffect();
			condition = new ConditionNode(this, rule.getCondition());
		}
		originalEffect = effect;
	}
	public int getEffect()
	{
		return effect;
	}
	public void setEffect(int effect)
	{
		if(effect == Result.DECISION_DENY || effect == Result.DECISION_PERMIT)
			this.effect = effect;
		else
			throw new IllegalArgumentException("Invalid effect value: " + effect);
		fireChanged();
	}
	
	public ConditionNode getCondition()
	{
		return condition;
	}

	public boolean isModified(boolean deep)
	{
		if(super.isModified(deep) || isEffectModified())
			return true;
		if(deep)
		{
			if(condition.isModified(true))
				return true;
		}
		return false;
	}
	public boolean isEffectModified()
	{
		return effect != originalEffect;
	}
	
	public void revert(boolean deep)
	{
		effect = originalEffect;
		if(deep)
			condition.revert(deep);
		super.revert(deep);
	}
	public void commit(boolean deep)
	{
		originalEffect = effect;
		if(deep)
			condition.commit(deep);
		super.commit(deep);
	}

	public PolicyTreeElement create()
	{
		return createRule();
	}
	public PolicyTreeElement create(URI id)
	{
		return createRule(id);
	}
	public Rule createRule()
	{
		return createRule(null);
	}
	public Rule createRule(URI id)
	{
		URI useId = (id == null) ? getId() : id;
		return new Rule(useId, effect, getDescription(), getTarget().getTarget(), condition.getCondition());
	}
	
	public int getChildCount()
	{
		return 2;
	}
	public XACMLTreeNode getChild(int index)
	{
		if(index == 0)
			return getTarget();
		if(index == 1)
			return getCondition();
		return null;
	}
	public int indexOfChild(Object child)
	{
		if(getTarget() == child)
			return 0;
		if(getCondition() == child)
			return 1;
		return -1;
	}
}
