package org.exist.client.xacml;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Spring;
import javax.swing.SpringLayout;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

import org.exist.client.ClientFrame;

import com.sun.xacml.ParsingException;
import com.sun.xacml.UnknownIdentifierException;
import com.sun.xacml.attr.AttributeDesignator;
import com.sun.xacml.attr.AttributeFactory;
import com.sun.xacml.attr.AttributeValue;


public class MatchEditor extends JPanel implements ActionListener, DocumentListener
{
	private static final String EMPTY_TEXT = "Select an attribute to edit";
	private static final int MAXIMUM_BOX_WIDTH = 450;
	
	private AttributeDesignator attribute;
	private JLabel label;
	private JComboBox functionBox;
	private JComboBox valueBox;
	private Abbreviator abbrev;
	private List listeners = new ArrayList(2);
	private List attributeHandlers = new ArrayList(2);
	
	private Object currentFunction;
	private Object currentValue;
	
	private MatchEditor() {}
	public MatchEditor(Abbreviator abbrev)
	{
		this.abbrev = abbrev;
		setup();
	}
	private void setup()
	{
		SpringLayout layout = new SpringLayout();
		setLayout(layout);
		
		setOpaque(true);
		label = new JLabel(EMPTY_TEXT);
		
		functionBox = new JComboBox();
		functionBox.setEditable(false);
		functionBox.setMaximumSize(restrictWidth(functionBox.getMaximumSize()));
		
		valueBox = new JComboBox();
		valueBox.setEditable(true);
		valueBox.setMaximumSize(restrictWidth(functionBox.getMaximumSize()));
		Component comp = valueBox.getEditor().getEditorComponent();
		if(comp instanceof JTextComponent)
			((JTextComponent)comp).getDocument().addDocumentListener(this);
		
		setBoxesVisible(false);
		
		add(label);
		add(functionBox);
		add(valueBox);
		
		Spring constant6 = Spring.constant(6);
		layout.putConstraint(SpringLayout.NORTH, label, constant6, SpringLayout.NORTH, this);
		layout.putConstraint(SpringLayout.NORTH, functionBox, constant6, SpringLayout.NORTH, this);
		layout.putConstraint(SpringLayout.NORTH, valueBox, constant6, SpringLayout.NORTH, this);
		Spring bottom = layout.getConstraint(SpringLayout.SOUTH, label);
		bottom = Spring.max(bottom, layout.getConstraint(SpringLayout.SOUTH, functionBox));
		bottom = Spring.max(bottom, layout.getConstraint(SpringLayout.SOUTH, valueBox));
		layout.putConstraint(SpringLayout.SOUTH, this, bottom, SpringLayout.NORTH, this);
		
		layout.putConstraint(SpringLayout.WEST, functionBox, constant6, SpringLayout.EAST, label);
		layout.putConstraint(SpringLayout.WEST, valueBox, constant6, SpringLayout.EAST, functionBox);
		layout.putConstraint(SpringLayout.EAST, this, Spring.constant(6,6,Short.MAX_VALUE), SpringLayout.EAST, valueBox);
	}
	public void setMatch(AttributeDesignator attribute, URI functionId, AttributeValue value)
	{
		valueBox.removeActionListener(this);
		functionBox.removeActionListener(this);
		Component comp = valueBox.getEditor().getEditorComponent();
		Document doc = null;
		if(comp instanceof JTextComponent)
		{
			doc = ((JTextComponent)comp).getDocument();
			doc.removeDocumentListener(this);
		}
		
		this.attribute = attribute;
		if(attribute == null)
		{
			label.setText(EMPTY_TEXT);
			setBoxesVisible(false);
			return;
		}
		
		URI dataType = attribute.getType();
		label.setText(abbrev.getAbbreviatedId(attribute.getId()) + " (" + abbrev.getAbbreviatedType(dataType) + ")");
		
		Set targetFunctions = abbrev.getAbbreviatedTargetFunctions(dataType);
		for(Iterator it = attributeHandlers.iterator(); it.hasNext();)
			((AttributeHandler)it.next()).filterFunctions(targetFunctions, attribute);
		
		functionBox.setModel(new DefaultComboBoxModel(targetFunctions.toArray()));
		if(functionId == null)
		{
			if(functionBox.getItemCount() > 0)
				functionBox.setSelectedIndex(0);
		}
		else
			functionBox.setSelectedItem(abbrev.getAbbreviatedTargetFunctionId(functionId, dataType));
		functionBox.setMaximumSize(restrictWidth(functionBox.getPreferredSize()));

		valueBox.setEditable(true);
		Set allowedValues = new TreeSet();
		for(Iterator it = attributeHandlers.iterator(); it.hasNext();)
		{
			if(!((AttributeHandler)it.next()).getAllowedValues(allowedValues, attribute))
			{
				valueBox.setEditable(false);
				break;
			}
		}
		valueBox.setModel(new DefaultComboBoxModel(allowedValues.toArray()));
		if(valueBox.isEditable())
		{
			Dimension max = valueBox.getMaximumSize();
			max.width = MAXIMUM_BOX_WIDTH;
			valueBox.setMaximumSize(max);
		}
		else
			valueBox.setMaximumSize(restrictWidth(valueBox.getMaximumSize()));
		
		valueBox.setSelectedItem((value == null) ? null : value.encode());
		setBoxesVisible(true);
		currentValue = valueBox.getSelectedItem();
		currentFunction = functionBox.getSelectedItem();
		valueBox.addActionListener(this);
		functionBox.addActionListener(this);
		if(doc != null)
			doc.addDocumentListener(this);
	}
	
	private Dimension restrictWidth(Dimension size)
	{
		if(size.width > MAXIMUM_BOX_WIDTH)
			size.width = MAXIMUM_BOX_WIDTH;
		return size;
	}

	private void setBoxesVisible(boolean b)
	{
		functionBox.setVisible(b);
		valueBox.setVisible(b);
	}
	public URI getFunctionId()
	{
		return (currentFunction == null) ? null : abbrev.getFullFunctionId((String)currentFunction, attribute.getType());
	}
	public AttributeValue getValue()
	{
		if(attribute == null || currentValue == null)
			return null;
		
		AttributeFactory factory = AttributeFactory.getInstance();
		try
		{
			String textValue = currentValue.toString();
			if(textValue == null || textValue.length() == 0)
				return null;
			AttributeValue value = factory.createValue(attribute.getType(), textValue);
			for(Iterator it = attributeHandlers.iterator(); it.hasNext();)
				((AttributeHandler)it.next()).checkUserValue(value, attribute);
			return value;
		}
		catch(UnknownIdentifierException e)
		{
			ClientFrame.showErrorMessage("Invalid attribute type '" + attribute.getType() + "'", e);
			return null;
		}
		catch (ParsingException e)
		{
			ClientFrame.showErrorMessage("Invalid value '" + currentValue + "'", e);
			return null;
		}
	}
	public AttributeDesignator getAttribute()
	{
		return attribute;
	}
		
	public void addAttributeHandler(AttributeHandler ah)
	{
		if(ah == null)
			return;
		if(attributeHandlers == null)
			attributeHandlers = new ArrayList();
		attributeHandlers.add(ah);
	}
	public void removeAttributeHandler(AttributeHandler ah)
	{
		if(ah == null || attributeHandlers == null)
			return;
		attributeHandlers.remove(ah);
	}
	

	public void actionPerformed(ActionEvent event)
	{
		Object source = event.getSource();
		if(source == functionBox)
			currentFunction = functionBox.getSelectedItem();
		else if(source == valueBox)
			currentValue = valueBox.getSelectedItem();
		else
			return;
		fireChanged();
	}
	private void fireChanged()
	{
		ChangeEvent event = new ChangeEvent(this);
		for(Iterator it = listeners.iterator(); it.hasNext();)
			((ChangeListener)it.next()).stateChanged(event);
	}
	public void addChangeListener(ChangeListener listener)
	{
		if(listener != null)
			listeners.add(listener);
	}
	public void removeChangeListener(ChangeListener listener)
	{
		if(listeners != null)
			listeners.remove(listeners);
	}
	
	private void documentUpdated(DocumentEvent event)
	{
		Document doc = event.getDocument(); 
		try
		{
			currentValue = doc.getText(0, doc.getLength());
		}
		catch (BadLocationException e)
		{
			return;
		}
		fireChanged();
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
}
