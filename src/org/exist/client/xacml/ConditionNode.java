package org.exist.client.xacml;

import org.exist.security.xacml.XACMLUtil;

import com.sun.xacml.cond.Apply;

public class ConditionNode extends AbstractTreeNode 
{
	private Apply condition;
	private Apply originalCondition;
	
	public ConditionNode(NodeContainer parent)
	{
		this(parent, null);
	}
	public ConditionNode(NodeContainer parent, Apply condition)
	{
		super(parent);
		this.condition = condition;
		originalCondition = condition;
	}
	
	public Apply getCondition()
	{
		return condition;
	}
	public void setCondition(Apply condition)
	{
		this.condition = condition;
		fireChanged();
	}
	
	public boolean isModified(boolean deep)
	{
		return super.isModified(deep) || isConditionModified();
	}
	public boolean isConditionModified()
	{
		//simple check, ?replace with something more exact?
		return condition != originalCondition;
	}

	public void revert(boolean deep)
	{
		condition = originalCondition;
		super.revert(deep);
	}
	public void commit(boolean deep)
	{
		originalCondition = condition;
		super.commit(deep);
	}
	public String serialize(boolean indent)
	{
		return XACMLUtil.serialize(condition, indent);
	}
	
	public String toString()
	{
		return "Condition";
	}
}
