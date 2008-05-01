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
 * $Id:$
 */
package org.exist.client;

import org.exist.collections.CollectionConfigurationManager;
import org.exist.storage.DBBroker;
import org.exist.xmldb.IndexQueryService;
import org.exist.xmldb.XmldbURI;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.ArrayList;

/**
 * Dialog for viewing and editing Indexes in the Admin Client 
 * 
 * @author Adam Retter <adam.retter@devon.gov.uk>
 * @serial 2006-03-12
 * @version 1.0
 */
class IndexDialog extends JFrame {

    private static final String[] CONFIG_TYPE = {
        "qname",
        "path"
    };
    
    private static final String[] FULLTEXT_INDEX_ACTIONS = {
		"include",
		"exclude"
	};
	
	private static final String[] INDEX_TYPES = {
		"xs:boolean",
		"xs:integer",
		"xs:dateTime",
		"xs:string"
	};
	
	private CollectionXConf cx = null;
	
	private JComboBox cmbCollections;
	
	private JCheckBox chkDefaultAll;
	private JCheckBox chkAlphanum;
	private JCheckBox chkAttributes;
	
	
	private JTextField txtXPath;
	private JComboBox cmbxsType;
	private JTable tblFullTextIndexes;
	private FullTextIndexTableModel fulltextIndexModel;
	private JTable tblRangeIndexes;
	private RangeIndexTableModel rangeIndexModel;
	
	private InteractiveClient client;
	
	
	public IndexDialog(String title, InteractiveClient client) 
	{
		super(title);
		this.client = client;
		
		//capture the frame's close event
		WindowListener windowListener = new WindowAdapter()
		{
			public void windowClosing (WindowEvent e)
			{
                saveChanges(true);
				
				IndexDialog.this.setVisible(false);
				IndexDialog.this.dispose();
			}
		};
		this.addWindowListener(windowListener);
		
		//draw the GUI
		setupComponents();
		
		//Get the indexes for the root collection
		actionGetIndexes(DBBroker.ROOT_COLLECTION);
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
		c.weightx = 0;
		c.weighty = 0;
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
            	//TODO : use XmldbURIs !
            	if(alAllCollections.get(i).toString().indexOf(CollectionConfigurationManager.CONFIG_COLLECTION)  == -1)
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
        		
        		saveChanges(true);
        		
        		JComboBox cb = (JComboBox)e.getSource();
   				actionGetIndexes(cb.getSelectedItem().toString());
    		}
        });
        c.gridx = 1;
		c.gridy = 0;
		c.gridwidth = 1;
		c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1;
		c.weighty = 0;
        grid.setConstraints(cmbCollections, c);
        getContentPane().add(cmbCollections);

        //Panel to hold controls relating to the FullText Index
		JPanel panelFullTextIndex = new JPanel();
		panelFullTextIndex.setBorder(new TitledBorder("Full Text Index"));
		GridBagLayout panelFullTextIndexGrid = new GridBagLayout();
		panelFullTextIndex.setLayout(panelFullTextIndexGrid);
		
		
		//fulltext default all checkbox
		chkDefaultAll = new JCheckBox("Default All");
		chkDefaultAll.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e)
			{
				cx.setFullTextIndexDefaultAll(chkDefaultAll.isSelected());
			}
		});
		c.gridx = 0;
		c.gridy = 0;
		c.gridwidth = 1;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.NONE;
		c.weightx = 0;
		c.weighty = 0;
		panelFullTextIndexGrid.setConstraints(chkDefaultAll, c);
		panelFullTextIndex.add(chkDefaultAll);
        
		//fulltext alphanumeric checkbox
		chkAlphanum = new JCheckBox("Alphanumeric");
		chkAlphanum.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e)
			{
				cx.setFullTextIndexAlphanum(chkAlphanum.isSelected());
			}
		});
		c.gridx = 0;
		c.gridy = 1;
		c.gridwidth = 1;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.NONE;
		c.weightx = 0;
		c.weighty = 0;
		panelFullTextIndexGrid.setConstraints(chkAlphanum, c);
		panelFullTextIndex.add(chkAlphanum);

		//fulltext attributes checkbox
		chkAttributes = new JCheckBox("Attributes");
		chkAttributes.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e)
			{
				cx.setFullTextIndexAttributes(chkAttributes.isSelected());
			}
		});
		c.gridx = 0;
		c.gridy = 2;
		c.gridwidth = 1;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.NONE;
		c.weightx = 0;
		c.weighty = 0;
		panelFullTextIndexGrid.setConstraints(chkAttributes, c);
		panelFullTextIndex.add(chkAttributes);
		
        //Table to hold the FullText Indexes with Sroll bar
		fulltextIndexModel = new FullTextIndexTableModel();
        tblFullTextIndexes = new JTable(fulltextIndexModel);
        tblFullTextIndexes.setAutoResizeMode(JTable.AUTO_RESIZE_NEXT_COLUMN);
        tblFullTextIndexes.setRowHeight(20);
        tblFullTextIndexes.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        TableColumn colAction = tblFullTextIndexes.getColumnModel().getColumn(2);
        colAction.setCellEditor(new ComboBoxCellEditor(FULLTEXT_INDEX_ACTIONS));
        colAction.setCellRenderer(new ComboBoxCellRenderer(FULLTEXT_INDEX_ACTIONS));
        colAction = tblFullTextIndexes.getColumnModel().getColumn(0);
        colAction.setCellEditor(new ComboBoxCellEditor(CONFIG_TYPE));
        colAction.setCellRenderer(new ComboBoxCellRenderer(CONFIG_TYPE));
        JScrollPane scrollFullTextIndexes = new JScrollPane(tblFullTextIndexes);
		scrollFullTextIndexes.setPreferredSize(new Dimension(250, 150));
		c.gridx = 0;
		c.gridy = 3;
		c.gridwidth = 2;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.BOTH;
		c.weightx = 1;
		c.weighty = 1;
		panelFullTextIndexGrid.setConstraints(scrollFullTextIndexes, c);
		panelFullTextIndex.add(scrollFullTextIndexes);
		
		//Toolbar with add/delete buttons for FullText Index
		Box fulltextIndexToolbarBox = Box.createHorizontalBox();
		//add button
		JButton btnAddFullTextIndex = new JButton("Add");
		btnAddFullTextIndex.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e)
			{
				actionAddFullTextIndex();
			}
		});
		fulltextIndexToolbarBox.add(btnAddFullTextIndex);
		//delete button
		JButton btnDeleteFullTextIndex = new JButton("Delete");
		btnDeleteFullTextIndex.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e)
			{
				actionDeleteFullTextIndex();
			}
		});
		fulltextIndexToolbarBox.add(btnDeleteFullTextIndex);
		c.gridx = 0;
		c.gridy = 4;
		c.gridwidth = 2;
		c.anchor = GridBagConstraints.CENTER;
		c.fill = GridBagConstraints.BOTH;
		c.weightx = 1;
		c.weighty = 0;
		panelFullTextIndexGrid.setConstraints(fulltextIndexToolbarBox, c);
		panelFullTextIndex.add(fulltextIndexToolbarBox);
		
		//add fulltext panel to content frame
		c.gridx = 0;
		c.gridy = 1;
		c.gridwidth = 2;
		c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
		c.weightx = 1;
		c.weighty = 1F / 3F;
	    grid.setConstraints(panelFullTextIndex, c);
		getContentPane().add(panelFullTextIndex);
		
		
        //Panel to hold controls relating to the Range Indexes
		JPanel panelRangeIndexes = new JPanel();
		panelRangeIndexes.setBorder(new TitledBorder("Range Indexes"));
		GridBagLayout panelRangeIndexesGrid = new GridBagLayout();
		panelRangeIndexes.setLayout(panelRangeIndexesGrid);
        
        //Table to hold the Range Indexes with Sroll bar
		rangeIndexModel = new RangeIndexTableModel();
        tblRangeIndexes = new JTable(rangeIndexModel);
        tblRangeIndexes.setAutoResizeMode(JTable.AUTO_RESIZE_NEXT_COLUMN);
        tblRangeIndexes.setRowHeight(20);
        tblRangeIndexes.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        TableColumn colxsType = tblRangeIndexes.getColumnModel().getColumn(2);
        colxsType.setCellEditor(new ComboBoxCellEditor(INDEX_TYPES));
        colxsType.setCellRenderer(new ComboBoxCellRenderer(INDEX_TYPES));
        colxsType = tblRangeIndexes.getColumnModel().getColumn(0);
        colxsType.setCellEditor(new ComboBoxCellEditor(CONFIG_TYPE));
        colxsType.setCellRenderer(new ComboBoxCellRenderer(CONFIG_TYPE));
        JScrollPane scrollRangeIndexes = new JScrollPane(tblRangeIndexes);
		scrollRangeIndexes.setPreferredSize(new Dimension(350, 150));
		c.gridx = 0;
		c.gridy = 0;
		c.gridwidth = 2;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.BOTH;
		c.weightx = 1;
		c.weighty = 1;
		panelRangeIndexesGrid.setConstraints(scrollRangeIndexes, c);
		panelRangeIndexes.add(scrollRangeIndexes);
        
		//Toolbar with add/delete buttons for Range Index
		Box rangeIndexToolbarBox = Box.createHorizontalBox();
		//add button
		JButton btnAddRangeIndex = new JButton("Add");
		btnAddRangeIndex.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e)
			{
				actionAddRangeIndex();
			}
		});
		rangeIndexToolbarBox.add(btnAddRangeIndex);
		//delete button
		JButton btnDeleteRangeIndex = new JButton("Delete");
		btnDeleteRangeIndex.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e)
			{
				actionDeleteRangeIndex();
			}
		});
		rangeIndexToolbarBox.add(btnDeleteRangeIndex);
		c.gridx = 0;
		c.gridy = 1;
		c.gridwidth = 2;
		c.anchor = GridBagConstraints.CENTER;
		c.fill = GridBagConstraints.BOTH;
		c.weightx = 0;
		c.weighty = 0;
		panelRangeIndexesGrid.setConstraints(rangeIndexToolbarBox, c);
		panelRangeIndexes.add(rangeIndexToolbarBox);

		//add range index panel to content frame
		c.gridx = 0;
		c.gridy = 2;
		c.gridwidth = 2;
		c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
		c.weightx = 1;
		c.weighty = 1F / 3F;
	    grid.setConstraints(panelRangeIndexes, c);
		getContentPane().add(panelRangeIndexes);

        Box mainBtnBox = Box.createHorizontalBox();
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                IndexDialog.this.setVisible(false);
				IndexDialog.this.dispose();
            }
        });
        JButton saveBtn = new JButton("Save");
        saveBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                saveChanges(false);
                IndexDialog.this.setVisible(false);
				IndexDialog.this.dispose();
            }
        });
        mainBtnBox.add(saveBtn);
        mainBtnBox.add(cancelBtn);
        
        c.gridx = 0;
		c.gridy = 3;
		c.gridwidth = 2;
		c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
		c.weightx = 0;
		c.weighty = 0;
	    grid.setConstraints(mainBtnBox, c);
		getContentPane().add(mainBtnBox);

        pack();
	}

	//if changes have been made, allows the user to save them
	private void saveChanges(boolean ask)
	{
        //the collection has been changed
		if(cx.hasChanged())
		{
            boolean doSave = true;
            if (ask) {
                //ask the user if they would like to save the changes
                int result = JOptionPane.showConfirmDialog(getContentPane(), "The configuration for the collection has changed, would you like to save the changes?", "Save Changes", JOptionPane.YES_NO_OPTION);
                doSave = result == JOptionPane.YES_OPTION;
            }
			
            if(doSave)
			{
				//save the collection.xconf changes
				if(cx.Save())
				{
					//save ok, reindex?
					int result = JOptionPane.showConfirmDialog(getContentPane(), "Your changes have been saved, but will not take effect until the collection is reindexed!\n Would you like to reindex " + cmbCollections.getSelectedItem() + " and sub-collections now?", "Reindex", JOptionPane.YES_NO_OPTION);
					
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

	private void actionAddFullTextIndex()
	{
		fulltextIndexModel.addRow();
	}
	
	private void actionDeleteFullTextIndex()
	{
		int iSelectedRow = tblFullTextIndexes.getSelectedRow();
		if(iSelectedRow > -1 )
		{
			fulltextIndexModel.removeRow(iSelectedRow);
		}
	}
	
	private void actionAddRangeIndex()
	{
		rangeIndexModel.addRow();
	}
	
	private void actionDeleteRangeIndex()
	{
		int iSelectedRow = tblRangeIndexes.getSelectedRow();
		if(iSelectedRow > -1 )
		{
			rangeIndexModel.removeRow(iSelectedRow);
		}
	}
	
	//Displays the indexes when a collection is selection
	private void actionGetIndexes(String collectionName)
	{
		try
		{
			cx = new CollectionXConf(collectionName, client);
			
			chkDefaultAll.setSelected(cx.getFullTextIndexDefaultAll());
			chkAlphanum.setSelected(cx.getFullTextIndexAlphanum());
			chkAttributes.setSelected(cx.getFullTextIndexAttributes());
			fulltextIndexModel.fireTableDataChanged();
			rangeIndexModel.fireTableDataChanged();
		}
		catch(XMLDBException xe)
		{
			//TODO: CONSIDER whether CollectionXConf Should throw xmldb exception at all?
		}
		
	}
	
	public class ComboBoxCellRenderer extends JComboBox implements TableCellRenderer
	{
        public ComboBoxCellRenderer(String[] items)
        {
            super(items);
        }
    
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column)
        {
            if(isSelected)
            {
                setForeground(table.getSelectionForeground());
                super.setBackground(table.getSelectionBackground());
            }
            else
            {
                setForeground(table.getForeground());
                setBackground(table.getBackground());
            }
    
            // Select the current value
            setSelectedItem(value);
            return this;
        }
    }
    
	
    public class ComboBoxCellEditor extends DefaultCellEditor
    {
        public ComboBoxCellEditor(String[] items)
        {
            super(new JComboBox(items));
        }
    }
	
    class FullTextIndexTableModel extends AbstractTableModel
	{	
		private final String[] columnNames = new String[] { "Type", "QName/Path", "Action" };

		public FullTextIndexTableModel()
		{
			super();
			fireTableDataChanged();
        }
		
		/* (non-Javadoc)
		* @see javax.swing.table.TableModel#isCellEditable()
		*/
		public void setValueAt(Object aValue, int rowIndex, int columnIndex)
		{
            System.out.println(columnIndex + "->" + aValue);
            switch (columnIndex)
			{
                case 0:
                    cx.updateFullTextIndex(rowIndex, aValue.toString(), null, null);
                    break;
                case 1:		/* XPath */
					cx.updateFullTextIndex(rowIndex, null, aValue.toString(), null);
					break;
				case 2 :	/* action */
					cx.updateFullTextIndex(rowIndex, null, null, aValue.toString());
					break;
				default :
					break;
			}
			
			fireTableCellUpdated(rowIndex, columnIndex);
		}
		
		public void removeRow(int rowIndex)
		{
			cx.deleteFullTextIndex(rowIndex);
			fireTableRowsDeleted(rowIndex, rowIndex);
		}
		
		public void addRow()
		{	
			cx.addFullTextIndex(CollectionXConf.TYPE_QNAME, "", CollectionXConf.ACTION_INCLUDE);
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
				return cx != null ? cx.getFullTextPathCount() : 0;
		}

        /* (non-Javadoc)
		 * @see javax.swing.table.TableModel#getValueAt(int, int)
		 */
		public Object getValueAt(int rowIndex, int columnIndex)
		{
			switch (columnIndex)
			{
                case 0 :
                    return cx.getFullTextIndexType(rowIndex);
                case 1 :	/* XPath */
					return cx.getFullTextIndexPath(rowIndex);
				case 2 :	/* action */
					return cx.getFullTextIndexPathAction(rowIndex);
				default :
					return null;
			}
		}
	}
    
	class RangeIndexTableModel extends AbstractTableModel
	{	
		private final String[] columnNames = new String[] { "Type", "XPath", "xsType" };

		public RangeIndexTableModel()
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
                case 0:
                    cx.updateRangeIndex(rowIndex, aValue.toString(), null, null);
                    break;
                case 1:		/* XPath */
					cx.updateRangeIndex(rowIndex, null, aValue.toString(), null);
					break;
				case 2 :	/* xsType */
					cx.updateRangeIndex(rowIndex, null, null, aValue.toString());
					break;
				default :
					break;
			}
			
			fireTableCellUpdated(rowIndex, columnIndex);
		}
		
		public void removeRow(int rowIndex)
		{
			cx.deleteRangeIndex(rowIndex);
			fireTableRowsDeleted(rowIndex, rowIndex);
		}
		
		public void addRow()
		{			
			cx.addRangeIndex(CollectionXConf.TYPE_QNAME, "", "xs:string");
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
			return cx != null ? cx.getRangeIndexCount() : 0;
		}

		/* (non-Javadoc)
		 * @see javax.swing.table.TableModel#getValueAt(int, int)
		 */
		public Object getValueAt(int rowIndex, int columnIndex)
		{
			switch (columnIndex)
			{
                case 0 :
                    return cx.getRangeIndex(rowIndex).getType();
                case 1 :	/* XPath */
					return cx.getRangeIndex(rowIndex).getXPath();
				case 2 :	/* xsType */
					return cx.getRangeIndex(rowIndex).getxsType();
				default :
					return null;
			}
		}
	}
}
