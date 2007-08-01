package org.exist.client.xacml;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.net.URI;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import com.sun.xacml.Target;
import com.sun.xacml.attr.AttributeDesignator;
import com.sun.xacml.attr.AttributeValue;

public class TargetEditor extends AbstractNodeEditor implements ChangeListener, ListSelectionListener
{
	private Abbreviator abbrev;

	private JTabbedPane tabbed;
	
	private JTable subjectTargetTable;
	private JTable actionTargetTable;
	//XACML 2.0:
	//private JTable environmentTargetModel;
	private JTable resourceTargetTable;
	private MatchEditor matchEditor;
	
	private JPanel comp;

	private TargetNode node;
	
	private TargetEditor() {}
	public TargetEditor(DatabaseInterface dbInterface)
	{
		abbrev = new Abbreviator();
		setup(dbInterface);
	}
	private void setup(DatabaseInterface dbInterface)
	{
		comp = new JPanel(new BorderLayout());
		comp.setOpaque(true);
		comp.setBorder(BorderFactory.createEmptyBorder(6,6,6,6));

		matchEditor = new MatchEditor(abbrev);
		matchEditor.addChangeListener(this);
		comp.add(matchEditor, BorderLayout.NORTH);
		setupHandlers(matchEditor, dbInterface);
		
		tabbed = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
		comp.add(tabbed, BorderLayout.CENTER);
		subjectTargetTable = createTargetPanel(AttributeDesignator.SUBJECT_TARGET, tabbed);
		//XACML 2.0:
		//environmentTargetTable = createTargetPanel(AttributeDesignator.ENVIRONMENT_TARGET, tabbed);
		resourceTargetTable = createTargetPanel(AttributeDesignator.RESOURCE_TARGET, tabbed);
		actionTargetTable = createTargetPanel(AttributeDesignator.ACTION_TARGET, tabbed);
		comp.add(tabbed);
	}
	public JComponent getComponent()
	{
		return comp;
	}

	private void setupHandlers(MatchEditor matchEditor, DatabaseInterface dbInterface)
	{
		matchEditor.addAttributeHandler(new UserAttributeHandler(dbInterface));
		matchEditor.addAttributeHandler(new ActionAttributeHandler());
		matchEditor.addAttributeHandler(new ResourceCategoryAttributeHandler());
		matchEditor.addAttributeHandler(new ModuleAttributeHandler());
	}

	private JTable createTargetPanel(int type, JTabbedPane tabbed)
	{
		TargetTableModel tm = new TargetTableModel(type, abbrev);
		JTable table = new ResizingTable(tm);
		table.getSelectionModel().addListSelectionListener(this);
		table.getColumnModel().getSelectionModel().addListSelectionListener(this);
		table.setMinimumSize(new Dimension(300,150));
		table.setMaximumSize(new Dimension(600,500));
		table.setCellSelectionEnabled(true);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		JScrollPane scroll = new JScrollPane(table);
		tabbed.add(getLabel(type), scroll);
		return table;
	}
	private void addSelectionListeners(JTable table)
	{
		table.getSelectionModel().addListSelectionListener(this);
		table.getColumnModel().getSelectionModel().addListSelectionListener(this);
	}
	private void removeSelectionListeners(JTable table)
	{
		table.getSelectionModel().removeListSelectionListener(this);
		table.getColumnModel().getSelectionModel().removeListSelectionListener(this);
	}
	private static String getLabel(int type)
	{
		switch(type)
		{
			case AttributeDesignator.ACTION_TARGET:
				return "Action";
			case AttributeDesignator.RESOURCE_TARGET:
				return "Resource";
			case AttributeDesignator.SUBJECT_TARGET:
				return "Subject";
			//XACML 2.0:
			//case AttributeDesignator.ENVIRONMENT_TARGET:
			//	return "Environment";
			default:
				throw new IllegalArgumentException("Invalid target type");
		}
	}
	public void setNode(XACMLTreeNode treeNode)
	{
		if(!(treeNode instanceof TargetNode))
			throw new IllegalArgumentException("TargetEditor can only edit TargetNodes");
		this.node = (TargetNode)treeNode;
		Target target = node.getTarget();
		
		((TargetTableModel)subjectTargetTable.getModel()).setTarget(target == null ? null : target.getSubjects());
		((TargetTableModel)actionTargetTable.getModel()).setTarget(target == null ? null : target.getActions());
		((TargetTableModel)resourceTargetTable.getModel()).setTarget(target == null ? null : target.getResources());
		//XACML 2.0:
		//environmentTargetModel.setTarget(target.getEnvironment());
	}
	
	public void pushChanges()
	{
		if(!node.isModified(false))
			return;
		
		List subjects = ((TargetTableModel)subjectTargetTable.getModel()).createTarget();
		List resources = ((TargetTableModel)resourceTargetTable.getModel()).createTarget();
		List actions = ((TargetTableModel)actionTargetTable.getModel()).createTarget();
		//XACML 2.0:
		//List environments = ((TargetTableModel)environmentTargetTable.getModel()).createTarget();
		Target target = new Target(subjects, resources, actions);
		node.setTarget(target);
	}
	public void valueChanged(ListSelectionEvent event)
	{
		if(event.getValueIsAdjusting())
			return;
		JTable table = getCurrentTargetTable();
		if(table == null)
		{
			matchEditor.setMatch(null, null, null);
			return;
		}

		int selectedRow = table.getSelectedRow();
		int selectedColumn = table.getSelectedColumn();
		if(selectedRow < 0 || selectedColumn < 0)
		{
			matchEditor.setMatch(null, null, null);
			return;
		}
		
		TargetTableModel model = (TargetTableModel)table.getModel();
		AttributeDesignator attribute = model.getAttribute(selectedColumn);
		URI functionId = model.getFunctionId(selectedRow, selectedColumn);
		AttributeValue value = model.getValue(selectedRow, selectedColumn); 
		matchEditor.setMatch(attribute, functionId, value);
	}
	private JTable getCurrentTargetTable()
	{
		int currentTab = tabbed.getSelectedIndex();
		switch(currentTab)
		{
			case 0:
				return subjectTargetTable;
			case 1:
				return resourceTargetTable;
			case 2:
				return actionTargetTable;
			default:
				return null;
		}
	}
	public void stateChanged(ChangeEvent event)
	{
		JTable table = getCurrentTargetTable();
		if(table == null)
		{
			matchEditor.setMatch(null, null, null);
			return;
		}

		int selectedRow = table.getSelectedRow();
		int selectedColumn = table.getSelectedColumn();
		if(selectedRow < 0 || selectedColumn < 0)
		{
			matchEditor.setMatch(null, null, null);
			return;
		}
		
		URI functionId = matchEditor.getFunctionId();
		AttributeValue value = matchEditor.getValue();
		removeSelectionListeners(table);
		((TargetTableModel)table.getModel()).setValue(functionId, value, selectedRow, selectedColumn);
		table.setRowSelectionInterval(selectedRow, selectedRow);
		table.setColumnSelectionInterval(selectedColumn, selectedColumn);
		addSelectionListeners(table);
		node.setModified(true);
	}
}
