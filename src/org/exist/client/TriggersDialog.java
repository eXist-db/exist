/*
 * eXist Open Source Native XML Database
 *
 * Copyright (C) 2001-06 Wolfgang M. Meier wolfgang@exist-db.org
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation; either version 2 of the License, or (at your
 * option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 * $Id$
 */
package org.exist.client;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.ArrayList;

import javax.swing.Box;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

import org.exist.storage.DBBroker;
import org.exist.xmldb.IndexQueryService;
import org.exist.xmldb.XmldbURI;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;

/**
 * Dialog for viewing and editing Triggers in the Admin Client 
 * 
 * @author Adam Retter <adam.retter@devon.gov.uk>
 * @serial 2006-08-06
 * @version 1.0
 */
class TriggersDialog extends JFrame {
	
	private static final String[] TRIGGER_TYPES = {
		"xs:boolean",
		"xs:integer",
		"xs:dateTime",
		"xs:string"
	};
	
	private CollectionXConf cx = null;
	
	private JComboBox cmbCollections;
	
	private JTextField txtXPath;
	private JComboBox cmbxsType;
	private JTable tblTriggers;
	private TriggersTableModel triggersModel;
	
	private InteractiveClient client;
	
	
	public TriggersDialog(String title, InteractiveClient client) 
	{
		super(title);
		this.client = client;
		
		//capture the frame's close event
		WindowListener windowListener = new WindowAdapter()
		{
			public void windowClosing (WindowEvent e)
			{
				saveChanges();
				
				TriggersDialog.this.setVisible(false);
				TriggersDialog.this.dispose();
			}
		};
		this.addWindowListener(windowListener);
		
		//draw the GUI
		setupComponents();
		
		//Get the indexes for the root collection
		actionGetTriggers(DBBroker.ROOT_COLLECTION);
	}

	private void setupComponents()
	{
		//Dialog Content Panel
		GridBagLayout grid = new GridBagLayout();
		getContentPane().setLayout(grid);
		
		//Constraints for Layout
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(2, 2, 2, 2);

		//collection label
		JLabel label = new JLabel("Collection");
		c.gridx = 0;
		c.gridy = 0;
		c.gridwidth = 1;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.NONE;
		grid.setConstraints(label, c);
		getContentPane().add(label);
		
		//get the collections but not system collections
		ArrayList alCollections = new ArrayList();
        try
        {
            Collection root = client.getCollection(DBBroker.ROOT_COLLECTION);
            ArrayList alAllCollections = getCollections(root, new ArrayList());
            for(int i = 0; i < alAllCollections.size(); i++)
            {
            	if(alAllCollections.get(i).toString().indexOf(DBBroker.CONFIG_COLLECTION)  == -1)
            	{
            		alCollections.add(alAllCollections.get(i));
            	}
            }
        }
        catch (XMLDBException e)
        {
            //showErrorMessage(e.getMessage(), e);
            return;
        }
        
        //Create a combobox listing the collections
        cmbCollections = new JComboBox(alCollections.toArray());
        cmbCollections.addActionListener(new ActionListener(){
        	public void actionPerformed(ActionEvent e)
        	{
        		
        		saveChanges();
        		
        		JComboBox cb = (JComboBox)e.getSource();
   				actionGetTriggers(cb.getSelectedItem().toString());
    		}
        });
        c.gridx = 1;
		c.gridy = 0;
		c.gridwidth = 1;
		c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.NONE;
        grid.setConstraints(cmbCollections, c);
        getContentPane().add(cmbCollections);

        //Panel to hold controls relating to the Triggers Index
		JPanel panelTriggers = new JPanel();
		panelTriggers.setBorder(new TitledBorder("Triggers"));
		GridBagLayout panelTriggersGrid = new GridBagLayout();
		panelTriggers.setLayout(panelTriggersGrid);
		
        //Table to hold the Triggers with Sroll bar
		triggersModel = new TriggersTableModel();
        tblTriggers = new JTable(triggersModel);
        tblTriggers.setAutoResizeMode(JTable.AUTO_RESIZE_NEXT_COLUMN);
        tblTriggers.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        TableColumn colAction = tblTriggers.getColumnModel().getColumn(1);
        colAction.setCellEditor(new CheckBoxCellEditor());
        colAction.setCellRenderer(new CheckBoxCellRenderer());
        JScrollPane scrollFullTextIndexes = new JScrollPane(tblTriggers);
		scrollFullTextIndexes.setPreferredSize(new Dimension(250, 150));
		c.gridx = 0;
		c.gridy = 3;
		c.gridwidth = 2;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.BOTH;
		panelTriggersGrid.setConstraints(scrollFullTextIndexes, c);
		panelTriggers.add(scrollFullTextIndexes);
		
		//Toolbar with add/delete buttons for Triggers
		Box triggersToolbarBox = Box.createHorizontalBox();
		//add button
		JButton btnAddTrigger = new JButton("Add");
		btnAddTrigger.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e)
			{
				actionAddTrigger();
			}
		});
		triggersToolbarBox.add(btnAddTrigger);
		//delete button
		JButton btnDeleteTrigger = new JButton("Delete");
		btnDeleteTrigger.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e)
			{
				actionDeleteTrigger();
			}
		});
		triggersToolbarBox.add(btnDeleteTrigger);
		c.gridx = 0;
		c.gridy = 4;
		c.gridwidth = 2;
		c.anchor = GridBagConstraints.CENTER;
		c.fill = GridBagConstraints.BOTH;
		panelTriggersGrid.setConstraints(triggersToolbarBox, c);
		panelTriggers.add(triggersToolbarBox);
		
		//add triggers panel to content frame
		c.gridx = 0;
		c.gridy = 1;
		c.gridwidth = 2;
		c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    grid.setConstraints(panelTriggers, c);
		getContentPane().add(panelTriggers);
				
		pack();
	}

	//if changes have been made, allows the user to save them
	private void saveChanges()
	{
		//the collection has been changed
		if(cx.hasChanged())
		{
			//ask the user if they would like to save the changes
			int result = JOptionPane.showConfirmDialog(getContentPane(), "The configuration for the collection has changed, would you like to save the changes?", "Save Changes", JOptionPane.YES_NO_OPTION);
			
			if(result == JOptionPane.YES_OPTION)
			{
				//save the collection.xconf changes
				if(cx.Save())
				{
					//save ok, reindex?
					result = JOptionPane.showConfirmDialog(getContentPane(), "Your changes have been saved, but will not take effect until the collection is reindexed!\n Would you like to reindex " + cmbCollections.getSelectedItem() + " and sub-collections now?", "Reindex", JOptionPane.YES_NO_OPTION);
					
					if(result == JOptionPane.YES_OPTION)
					{
						//reindex collection
						Runnable reindexThread = new Runnable()
						{
							public void run()
							{
								try
				                {
									IndexQueryService service = (IndexQueryService)client.current.getService("IndexQueryService", "1.0");
									
									ArrayList subCollections = getCollections(client.getCollection((String)cmbCollections.getSelectedItem()), new ArrayList());
									
				                    for(int i = 0; i < subCollections.size(); i++)
				                    {
				                    	service.reindexCollection(((ResourceDescriptor)subCollections.get(i)).getName());
				                    }
				                    
				                    //reindex done
				                    JOptionPane.showMessageDialog(getContentPane(), "Reindex Complete");
				                }
								catch(XMLDBException e)
								{
									//reindex failed
									JOptionPane.showMessageDialog(getContentPane(), "Reindex failed!");
								}
				            }
				        };
					}
				}
				else
				{
					//save failed
					JOptionPane.showMessageDialog(getContentPane(), "Unable to save changes!");
				}
			}
		}
	}
	
	
	//THIS IS A COPY FROM ClientFrame
	//TODO: share this code between the two classes
	private ArrayList getCollections(Collection root, ArrayList collectionsList) throws XMLDBException
    {
        collectionsList.add(new PrettyXmldbURI(XmldbURI.create(root.getName())));
        String[] childCollections= root.listChildCollections();
        Collection child;
        for(int i = 0; i < childCollections.length; i++)
        {
            child = root.getChildCollection(childCollections[i]);
            getCollections(child, collectionsList);
        }
        return collectionsList;
    }

	private void actionAddTrigger()
	{
		triggersModel.addRow();
	}
	
	private void actionDeleteTrigger()
	{
		int iSelectedRow = tblTriggers.getSelectedRow();
		if(iSelectedRow > -1 )
		{
			triggersModel.removeRow(iSelectedRow);
		}
	}
	
	//Displays the indexes when a collection is selection
	private void actionGetTriggers(String collectionName)
	{
		try
		{
			cx = new CollectionXConf(collectionName, client);
			
			triggersModel.fireTableDataChanged();
		}
		catch(XMLDBException xe)
		{
			//TODO: CONSIDER whether CollectionXConf Should throw xmldb exception at all?
		}
		
	}
	
	public class CheckBoxCellRenderer extends JCheckBox implements TableCellRenderer
	{
        public CheckBoxCellRenderer()
        {
            setHorizontalAlignment(JLabel.CENTER);
        }
    
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column)
        {
            if(isSelected)
            {
                setForeground(table.getSelectionForeground());
                //super.setBackground(table.getSelectionBackground());
                setBackground(table.getSelectionBackground());
            }
            else
            {
                setForeground(table.getForeground());
                setBackground(table.getBackground());
            }
    
            // Set the state
            setSelected((value != null && ((Boolean) value).booleanValue()));
            return this;
        }
    }
    
	
    public class CheckBoxCellEditor extends DefaultCellEditor
    {
        public CheckBoxCellEditor()
        {
            super(new JCheckBox());
        }
    }
	
    class TriggersTableModel extends AbstractTableModel
	{	
		private final String[] columnNames = new String[] { "class", "Store Document", "Update Document", "Remove Document", "Create Collection", "Rename Collection", "Delete Collection" };

		public TriggersTableModel()
		{
			super();
			fireTableDataChanged();
		}
		
		/* (non-Javadoc)
		* @see javax.swing.table.TableModel#isCellEditable()
		*/
		public void setValueAt(Object aValue, int rowIndex, int columnIndex)
		{
			switch (columnIndex)
			{
				/* class */
				case 0:
					//cx.updateTrigger(rowIndex, aValue.toString(), null);					
					break;
				
				/* events */
				case 1 :	//store document
				case 2 :	//update document
				case 3 :	//remove document
				case 4 :	//create collection
				case 5 :	//rename collection
				case 6 :	//delete collection
					//cx.updateTrigger(rowIndex, null, aValue.toString());
					break;
				default :
					break;
			}
			
			fireTableCellUpdated(rowIndex, columnIndex);
		}
		
		public void removeRow(int rowIndex)
		{
			//cx.deleteTrigger(rowIndex);
			fireTableRowsDeleted(rowIndex, rowIndex);
		}
		
		public void addRow()
		{	
			//cx.addTrigger("", "xs:string");
			fireTableRowsInserted(getRowCount(), getRowCount() + 1);
		}
		
		/* (non-Javadoc)
		* @see javax.swing.table.TableModel#isCellEditable()
		*/
		public boolean isCellEditable(int rowIndex, int columnIndex)
		{
			return true;
		}
		
		/* (non-Javadoc)
		* @see javax.swing.table.TableModel#getColumnCount()
		*/
		public int getColumnCount()
		{
			return columnNames.length;
		}

		/* (non-Javadoc)
		 * @see javax.swing.table.TableModel#getColumnName(int)
		 */
		public String getColumnName(int column)
		{
			return columnNames[column];
		}

		/* (non-Javadoc)
		 * @see javax.swing.table.TableModel#getRowCount()
		 */
		public int getRowCount()
		{
				//return cx != null ? cx.getTriggerCount() : 0;
				return 0;
		}

		/* (non-Javadoc)
		 * @see javax.swing.table.TableModel#getValueAt(int, int)
		 */
		public Object getValueAt(int rowIndex, int columnIndex)
		{
			switch (columnIndex)
			{
				case 0 :	/* XPath */
					//return cx.Trigger(rowIndex);
					return null;
				case 1 :	/* action */
					//return cx.getTrigger(rowIndex);
					return null;
				default :
					return null;
			}
		}
	}
}