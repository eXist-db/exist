package org.exist.client.xacml;

import org.exist.security.xacml.XACMLUtil;

import com.sun.xacml.Target;

public class TargetNode extends AbstractTreeNode
{
	private Target originalTarget;
	private Target target;

	private TargetNode()
	{
		this(null, null);
	}
	public TargetNode(PolicyElementNode parent)
	{
		this(parent, null);
	}
	public TargetNode(PolicyElementNode parent, Target target)
	{
		super(parent);
		this.originalTarget = target;
		this.target = target;
	}
	
	public String toString()
	{
		return "Target";
	}
	public Target getTarget()
	{
		return target;
	}
	public void setTarget(Target target)
	{
		this.target = target;
		fireChanged();
	}
	
	public boolean isModified(boolean deep)
	{
		return super.isModified(deep) || isTargetModified();
	}
	public boolean isTargetModified()
	{
		return target != originalTarget;
	}
	public void commit(boolean deep)
	{
		originalTarget = target;
		super.commit(deep);
	}
	public void revert(boolean deep)
	{
		target = originalTarget;
		super.revert(deep);
	}
	public String serialize(boolean indent)
	{
		return XACMLUtil.serialize(target, indent);
	}
}
