package org.exist.client.xacml;

import java.net.URI;
import java.util.Iterator;
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
		boolean isPolicy = node instanceof PolicyNode;
		DefaultComboBoxModel model = new DefaultComboBoxModel();
		Set algorithms = StandardCombiningAlgFactory.getFactory().getStandardAlgorithms();
		for(Iterator it = algorithms.iterator(); it.hasNext();)
		{
			CombiningAlgorithm algorithm = (CombiningAlgorithm)it.next();
			if(isPolicy)
			{
				if(algorithm instanceof RuleCombiningAlgorithm)
				{
					String abbreviatedID = abbrev.getAbbreviatedCombiningID(algorithm.getIdentifier());
					model.addElement(abbreviatedID);
				}
			}
			else if(algorithm instanceof PolicyCombiningAlgorithm)
			{
				String abbreviatedID = abbrev.getAbbreviatedCombiningID(algorithm.getIdentifier());
				model.addElement(abbreviatedID);
			}
		}
		return model;
	}
	protected Object getComboPrototype()
	{
		Object prototype = "";
		int maxLength = -1;
		Set algorithms = StandardCombiningAlgFactory.getFactory().getStandardAlgorithms();
		for(Iterator it = algorithms.iterator(); it.hasNext();)
		{
			CombiningAlgorithm algorithm = (CombiningAlgorithm)it.next();
			URI ID = algorithm.getIdentifier();
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
			throw new IllegalArgumentException("AbstractPolicy Editor can only edit AbstractPolicyNodes");
		AbstractPolicyNode node = (AbstractPolicyNode)treeNode;
		super.setNode(node);
		CombiningAlgorithm algorithm = node.getCombiningAlgorithm();
		String abbreviatedID = abbrev.getAbbreviatedCombiningID(algorithm.getIdentifier());
		setSelectedItem(abbreviatedID);
	}
	public void pushChanges()
	{
		super.pushChanges();
		CombiningAlgorithm algorithm = getAlgorithm();
		if(algorithm != null)
			((AbstractPolicyNode)node).setCombiningAlgorithm(algorithm);
	}
}
