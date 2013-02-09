package org.exist.client.xacml;

import java.net.URI;
import java.util.Set;

import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;

import com.sun.xacml.combine.CombiningAlgorithm;
import com.sun.xacml.combine.PolicyCombiningAlgorithm;
import com.sun.xacml.combine.RuleCombiningAlgorithm;
import com.sun.xacml.combine.StandardCombiningAlgFactory;

public class AbstractPolicyEditor extends PolicyElementEditor
{
	public AbstractPolicyEditor()
	{
		super();
	}
	
	protected String getComboLabel()
	{
		return ((node instanceof PolicySetNode) ? "Policy" : "Rule") + " Combining Algorithm:";
	}

	protected ComboBoxModel getComboModel()
	{
		final boolean isPolicy = node instanceof PolicyNode;
		final DefaultComboBoxModel model = new DefaultComboBoxModel();
		final Set<CombiningAlgorithm> algorithms = StandardCombiningAlgFactory.getFactory().getStandardAlgorithms();
		for(final CombiningAlgorithm algorithm : algorithms)
		{
			if(isPolicy)
			{
				if(algorithm instanceof RuleCombiningAlgorithm)
				{
					final String abbreviatedID = abbrev.getAbbreviatedCombiningID(algorithm.getIdentifier());
					model.addElement(abbreviatedID);
				}
			}
			else if(algorithm instanceof PolicyCombiningAlgorithm)
			{
				final String abbreviatedID = abbrev.getAbbreviatedCombiningID(algorithm.getIdentifier());
				model.addElement(abbreviatedID);
			}
		}
		return model;
	}
	protected Object getComboPrototype()
	{
		Object prototype = "";
		int maxLength = -1;
		final Set<CombiningAlgorithm> algorithms = StandardCombiningAlgFactory.getFactory().getStandardAlgorithms();
		for(final CombiningAlgorithm algorithm : algorithms)
		{
			final URI ID = algorithm.getIdentifier();
			String abbreviatedID = abbrev.getAbbreviatedCombiningID(ID);
			int length = abbreviatedID.length(); 
			if(length > maxLength)
			{
				maxLength = length;
				prototype = abbreviatedID;
			}
		}
		return prototype;
	}
	public void setNode(XACMLTreeNode treeNode)
	{
		if(!(treeNode instanceof AbstractPolicyNode))
			{throw new IllegalArgumentException("AbstractPolicy Editor can only edit AbstractPolicyNodes");}
		final AbstractPolicyNode node = (AbstractPolicyNode)treeNode;
		super.setNode(node);
		final CombiningAlgorithm algorithm = node.getCombiningAlgorithm();
		final String abbreviatedID = abbrev.getAbbreviatedCombiningID(algorithm.getIdentifier());
		setSelectedItem(abbreviatedID);
	}
	public void pushChanges()
	{
		super.pushChanges();
		final CombiningAlgorithm algorithm = getAlgorithm();
		if(algorithm != null)
			{((AbstractPolicyNode)node).setCombiningAlgorithm(algorithm);}
	}
}
