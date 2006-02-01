package org.exist.client.xacml;

import java.awt.Color;
import java.awt.Component;
import java.net.URL;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.tree.DefaultTreeCellRenderer;

import org.exist.client.ClientFrame;

public class CustomRenderer extends DefaultTreeCellRenderer
{
	private static final Icon targetIcon = getIcon("icons/Target.png");
	private static final Icon qmarkIcon = getIcon("icons/Condition.png");
	private static final Icon policyIcon = getIcon("icons/Policy.png");
	private static final Icon policySetIcon = getIcon("icons/PolicySet.png");
	private static final Icon ruleIcon = getIcon("icons/Rule.png");
	
	private static final Color BORDER_COLOR = UIManager.getColor("Tree.selectionBorderColor");
	
	private static Icon getIcon(String location)
	{
		URL url = ClientFrame.class.getResource(location);
		return (url == null) ? null : new ImageIcon(url);
	}
	
	private boolean currentModified = false;
	private TreeMutator mutator;
	
	private CustomRenderer() {}
	public CustomRenderer(TreeMutator mutator)
	{
		this.mutator = mutator;
	}
	
	public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus)
	{
		XACMLTreeNode node = (XACMLTreeNode)value;
		currentModified = node.isModified(false);
		
		JLabel comp = (JLabel)super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
		Border border = BorderFactory.createEmptyBorder(1,1,1,1);
		
		int bias = mutator.getDestinationBias(node);
		if(selected || bias == TreeMutator.BIAS_CURRENT)
			border = BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER_COLOR), border);
		else if(bias == TreeMutator.BIAS_AFTER)
			border = BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0,0,1,0,BORDER_COLOR), border);
		else if(bias == TreeMutator.BIAS_BEFORE)
			border = BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1,0,0,0,BORDER_COLOR), border);
		comp.setBorder(border);
		
		if(value instanceof RuleNode)
			comp.setIcon(ruleIcon);
		else if(value instanceof TargetNode)
			comp.setIcon(targetIcon);
		else if(value instanceof ConditionNode)
			comp.setIcon(qmarkIcon);
		else if(value instanceof PolicyNode)
			comp.setIcon(policyIcon);
		else if(value instanceof PolicySetNode)
			comp.setIcon(policySetIcon);
		return comp;
	}
	private Color getModifiedColor(Color color)
	{
		if(!currentModified)
			return color;
		if(color == null)
			return null;
		float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
		return (hsb[2] < 0.5f) ? color.brighter() : color.darker();
	}
	
	public Color getBackgroundNonSelectionColor()
	{
		return getModifiedColor(super.getBackgroundNonSelectionColor());
		
	}
	public Color getBackgroundSelectionColor()
	{
		return getModifiedColor(super.getBackgroundSelectionColor());
	}
}
