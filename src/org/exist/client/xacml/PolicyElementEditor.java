package org.exist.client.xacml;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URI;
import java.net.URISyntaxException;

import javax.swing.BorderFactory;
import javax.swing.ComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.Spring;
import javax.swing.SpringLayout;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.exist.client.ClientFrame;

import com.sun.xacml.UnknownIdentifierException;
import com.sun.xacml.combine.CombiningAlgorithm;
import com.sun.xacml.combine.StandardCombiningAlgFactory;

public abstract class PolicyElementEditor extends AbstractNodeEditor implements ActionListener, DocumentListener
{	
	protected Abbreviator abbrev;
	protected PolicyElementNode node;
	
	private JTextField idText;
	private JLabel invalidLabel;
	private JTextArea descriptionArea;
	private JLabel comboLabel;
	private JComboBox comboBox;
	
	private JPanel comp;

	
	public PolicyElementEditor()
	{
		abbrev = new Abbreviator();
		setup();
	}
	public JComponent getComponent()
	{
		return comp;
	}
	private void setup()
	{
		SpringLayout layout = new SpringLayout();
		comp = new JPanel(layout);
		comp.setBorder(BorderFactory.createEmptyBorder(6,6,6,6));
		comp.setOpaque(true);
		
		Spring constant6 = Spring.constant(6);
		Spring constant0 = Spring.constant(0);
		Spring glue12 = Spring.constant(12,12,Short.MAX_VALUE);

		JLabel label = new JLabel("ID: ");
		invalidLabel = new JLabel("Invalid URI");
		invalidLabel.setForeground(new Color(225, 25, 25));
		idText = new JTextField(19);
		idText.getDocument().addDocumentListener(this);
		idText.setMaximumSize(new Dimension(Short.MAX_VALUE, idText.getPreferredSize().height));
		comp.add(label);
		comp.add(idText);
		comp.add(invalidLabel);
		layout.putConstraint(SpringLayout.WEST, idText, constant6, SpringLayout.EAST, label);
		layout.putConstraint(SpringLayout.WEST, invalidLabel, constant6, SpringLayout.EAST, idText);
		Spring nextY = Spring.max(layout.getConstraint(SpringLayout.SOUTH, idText), layout.getConstraint(SpringLayout.SOUTH, label));
		nextY = Spring.sum(nextY, constant6);
		
		comboLabel = new JLabel();
		comboBox = new JComboBox();
		comboBox.addActionListener(this);
		comboBox.setEditable(false);
		comp.add(comboLabel);
		comp.add(comboBox);
		
		SpringLayout.Constraints constraints = layout.getConstraints(comboLabel);
		constraints.setHeight(Spring.max(constraints.getHeight(), layout.getConstraints(comboBox).getHeight()));
		layout.putConstraint(SpringLayout.NORTH, comboBox, constant0, SpringLayout.NORTH, comboLabel);
		layout.putConstraint(SpringLayout.NORTH, comboLabel, nextY, SpringLayout.NORTH, comp);
		layout.putConstraint(SpringLayout.WEST, comboBox, constant6, SpringLayout.EAST, comboLabel);

		nextY = layout.getConstraint(SpringLayout.SOUTH, comboBox);
		nextY = Spring.max(nextY, layout.getConstraint(SpringLayout.SOUTH, comboLabel));
		nextY = Spring.sum(nextY, constant6);
		Spring right = layout.getConstraint(SpringLayout.EAST, comboBox);
		
		
		JLabel descriptionLabel = new JLabel("Description");
		descriptionArea = new JTextArea(4, 35);
		descriptionArea.setWrapStyleWord(true);
		descriptionArea.setLineWrap(true);
		descriptionArea.getDocument().addDocumentListener(this);
		JScrollPane scroll = new JScrollPane(descriptionArea);
		
		comp.add(descriptionLabel);
		comp.add(scroll);
		layout.putConstraint(SpringLayout.NORTH, descriptionLabel, nextY, SpringLayout.NORTH, comp);
		layout.putConstraint(SpringLayout.WEST, scroll, constant0, SpringLayout.WEST, idText);
		Spring idTextWidth = layout.getConstraints(idText).getWidth();
		layout.getConstraints(scroll).setWidth(Spring.sum(idTextWidth, idTextWidth));
		layout.putConstraint(SpringLayout.NORTH, scroll, constant6, SpringLayout.SOUTH, descriptionLabel);
		
		right = Spring.max(right, layout.getConstraint(SpringLayout.EAST, scroll));
		nextY = Spring.sum(layout.getConstraint(SpringLayout.SOUTH, scroll), glue12);
		
		right = Spring.sum(constant6, right);
		
		layout.putConstraint(SpringLayout.SOUTH, comp, nextY, SpringLayout.NORTH, comp);
		layout.putConstraint(SpringLayout.EAST, comp, right, SpringLayout.WEST, comp);
	}

	protected abstract String getComboLabel();
	protected abstract ComboBoxModel getComboModel();
	protected abstract Object getComboPrototype();
	
	protected void setSelectedItem(Object value)
	{
		comboBox.setSelectedItem(value);
	}

	protected Object getSelectedItem()
	{
		return comboBox.getSelectedItem();
	}
	public String getDescription()
	{
		return descriptionArea.getText();
	}
	public String getIDString()
	{
		return idText.getText();
	}
	public URI getID()
	{
		String IDStr = getIDString();
		if(IDStr == null)
			return null;
		try
		{
			return new URI(IDStr);
		}
		catch (URISyntaxException e)
		{
			ClientFrame.showErrorMessage("Invalid policy ID '" + IDStr + "'", e);
			return null;
		}
	}
	
	public CombiningAlgorithm getAlgorithm()
	{
		URI algURI = abbrev.getFullCombiningURI((String)comboBox.getSelectedItem(), (node instanceof PolicyNode));
		try
		{
			return StandardCombiningAlgFactory.getInstance().createAlgorithm(algURI);
		}
		catch (UnknownIdentifierException e)
		{
			ClientFrame.showErrorMessage("Invalid rule combining algorithm '" + algURI + "'", e);
			return null;
		}
	}
	public void setNode(XACMLTreeNode treeNode)
	{
		if(!(treeNode instanceof PolicyElementNode))
			throw new IllegalArgumentException("PolicyElementEditor can only edit PolicyElementNodes");
		this.node = (PolicyElementNode)treeNode;
		if(node == null)
			setValues(null, null);
		else
			setValues(node.getId(), node.getDescription());
		
		setupCombo();
	}
	protected void setupCombo()
	{
		comboLabel.setText(getComboLabel());
		comboBox.setModel(getComboModel());
		comboBox.setPrototypeDisplayValue(getComboPrototype());
		comboBox.setMaximumSize(comboBox.getPreferredSize());
	}
	protected void setValues(URI id, String description)
	{
		idText.setText((id == null) ? "" : id.toString());
		descriptionArea.setText((description == null) ? "" : description);
	}
	public void pushChanges()
	{
		String description = descriptionArea.getText();
		node.setDescription((description.length() == 0) ? null : description);
		URI id = getId();
		if(id != null)
			node.setId(id);
	}
	public URI getId()
	{
		try
		{
			return new URI(idText.getText());
		}
		catch (URISyntaxException e)
		{
			return null;
		}
	}
	public void changedUpdate(DocumentEvent event)
	{
		documentUpdated(event);
	}
	public void insertUpdate(DocumentEvent event)
	{
		documentUpdated(event);
	}
	public void removeUpdate(DocumentEvent event)
	{
		documentUpdated(event);
	}
	protected void documentUpdated(DocumentEvent event)
	{
		URI id = getId();
		if(id == null)
		{
			idText.setForeground(Color.red);
			invalidLabel.setVisible(true);
		}
		else
		{
			idText.setForeground(null);
			invalidLabel.setVisible(false);
			node.setId(id);
		}
		fireChanged();
	}
	public void actionPerformed(ActionEvent event)
	{
		fireChanged();
	}
}
