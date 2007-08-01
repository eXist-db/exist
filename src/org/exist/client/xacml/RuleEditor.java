package org.exist.client.xacml;

import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;

import com.sun.xacml.ctx.Result;

public class RuleEditor extends PolicyElementEditor
{
	public static final String PERMIT = "Permit";
	public static final String DENY = "Deny";

	public RuleEditor() {}
	
	protected String getComboLabel()
	{
		return "Rule Effect:";
	}
	protected ComboBoxModel getComboModel()
	{
		DefaultComboBoxModel model = new DefaultComboBoxModel();
		model.addElement(PERMIT);
		model.addElement(DENY);
		return model;
	}
	protected Object getComboPrototype()
	{
		return PERMIT;
	}
	public int getEffect()
	{
		String effect = (String)getSelectedItem();
		if(DENY.equals(effect))
			return Result.DECISION_DENY;
		if(PERMIT.equals(effect))
			return Result.DECISION_PERMIT;
		throw new IllegalStateException("Invalid effect: '" + effect + "'");
	}
	public void setNode(XACMLTreeNode treeNode)
	{
		if(!(treeNode instanceof RuleNode))
			throw new IllegalArgumentException("RuleEditor can only edit RuleNodes");
		
		RuleNode node = (RuleNode)treeNode;
		super.setNode(node);
		int effect = node.getEffect();
		if(effect == Result.DECISION_DENY)
			setSelectedItem(DENY);
		else if(effect == Result.DECISION_PERMIT)
			setSelectedItem(PERMIT);
		else
			throw new IllegalArgumentException("Unknown effect for rule '" + getId() + "'");
	}

	public void pushChanges()
	{
		super.pushChanges();
		int effect = getEffect();
		((RuleNode)node).setEffect(effect);
	}
}
