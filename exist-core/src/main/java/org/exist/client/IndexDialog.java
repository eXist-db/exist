/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.client;

import org.exist.collections.CollectionConfigurationManager;
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
 * @author <a href="mailto:adam.retter@devon.gov.uk">Adam Retter</a>
 * @serial 2006-03-12
 * @version 1.0
 */
class IndexDialog extends JFrame {

	private static final long serialVersionUID = 1L;

	private static final String[] CONFIG_TYPE = {
        "qname",
        "path"
    };
    
	private static final String[] INDEX_TYPES = {
		"xs:boolean",
		"xs:integer",
		"xs:dateTime",
		"xs:string"
	};
	
	private CollectionXConf cx = null;
	
	private JComboBox cmbCollections;
	
	private JTable tblRangeIndexes;
	private RangeIndexTableModel rangeIndexModel;
	
	private InteractiveClient client;
	
	
	public IndexDialog(String title, InteractiveClient client) 
	{
		super(title);
		this.client = client;
        this.setIconImage(InteractiveClient.getExistIcon(getClass()).getImage());		
		//capture the frame's close event
		final WindowListener windowListener = new WindowAdapter()
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
		actionGetIndexes(XmldbURI.ROOT_COLLECTION);
	}

	private void setupComponents()
	{
		//Dialog Content Panel
		final GridBagLayout grid = new GridBagLayout();
		getContentPane().setLayout(grid);
		
		//Constraints for Layout
		final GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(2, 2, 2, 2);

		//collection label
		final JLabel label = new JLabel("Collection");
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
		final ArrayList alCollections = new ArrayList();
        try
        {
            final Collection root = client.getCollection(XmldbURI.ROOT_COLLECTION);
            final ArrayList alAllCollections = getCollections(root, new ArrayList());
            for(int i = 0; i < alAllCollections.size(); i++)
            {
            	//TODO : use XmldbURIs !
            	if(alAllCollections.get(i).toString().contains(CollectionConfigurationManager.CONFIG_COLLECTION))
            	{
            		alCollections.add(alAllCollections.get(i));
            	}
            }
        }
        catch (final XMLDBException e)
        {
            //showErrorMessage(e.getMessage(), e);
            return;
        }
        
        //Create a combobox listing the collections
        cmbCollections = new JComboBox(alCollections.toArray());
        cmbCollections.addActionListener(e -> {

            saveChanges(true);

            final JComboBox cb = (JComboBox)e.getSource();
               actionGetIndexes(cb.getSelectedItem().toString());
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

        //Panel to hold controls relating to the Range Indexes
		final JPanel panelRangeIndexes = new JPanel();
		panelRangeIndexes.setBorder(new TitledBorder("Range Indexes"));
		final GridBagLayout panelRangeIndexesGrid = new GridBagLayout();
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
        final JScrollPane scrollRangeIndexes = new JScrollPane(tblRangeIndexes);
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
		final Box rangeIndexToolbarBox = Box.createHorizontalBox();
		//add button
		final JButton btnAddRangeIndex = new JButton("Add");
		btnAddRangeIndex.addActionListener(e -> actionAddRangeIndex());
		rangeIndexToolbarBox.add(btnAddRangeIndex);
		//delete button
		final JButton btnDeleteRangeIndex = new JButton("Delete");
		btnDeleteRangeIndex.addActionListener(e -> actionDeleteRangeIndex());
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

        final Box mainBtnBox = Box.createHorizontalBox();
        final JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> {
            IndexDialog.this.setVisible(false);
            IndexDialog.this.dispose();
        });
        final JButton saveBtn = new JButton("Save");
        saveBtn.addActionListener(e -> {
            saveChanges(false);
            IndexDialog.this.setVisible(false);
            IndexDialog.this.dispose();
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
                final int result = JOptionPane.showConfirmDialog(getContentPane(), "The configuration for the collection has changed, would you like to save the changes?", "Save Changes", JOptionPane.YES_NO_OPTION);
                doSave = result == JOptionPane.YES_OPTION;
            }
			
            if(doSave)
			{
				//save the collection.xconf changes
				if(cx.Save())
				{
					//save ok, reindex?
					final int result = JOptionPane.showConfirmDialog(getContentPane(), "Your changes have been saved, but will not take effect until the collection is reindexed!\n Would you like to reindex " + cmbCollections.getSelectedItem() + " and sub-collections now?", "Reindex", JOptionPane.YES_NO_OPTION);
					
					if(result == JOptionPane.YES_OPTION)
					{
						//reindex collection
						final Runnable reindexThread = () -> {
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
        final String[] childCollections= root.listChildCollections();
        Collection child;
		for (String childCollection : childCollections) {
			child = root.getChildCollection(childCollection);
			getCollections(child, collectionsList);
		}
        return collectionsList;
    }

	private void actionAddRangeIndex()
	{
		rangeIndexModel.addRow();
	}
	
	private void actionDeleteRangeIndex()
	{
		final int iSelectedRow = tblRangeIndexes.getSelectedRow();
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
			
			rangeIndexModel.fireTableDataChanged();
		}
		catch(final XMLDBException xe)
		{
			//TODO: CONSIDER whether CollectionXConf Should throw xmldb exception at all?
		}
		
	}
	
	public static class ComboBoxCellRenderer extends JComboBox implements TableCellRenderer
	{
		private static final long serialVersionUID = 1L;

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
    
	
    public static class ComboBoxCellEditor extends DefaultCellEditor
    {
		private static final long serialVersionUID = 1L;

		public ComboBoxCellEditor(String[] items)
        {
            super(new JComboBox(items));
        }
    }

	class RangeIndexTableModel extends AbstractTableModel
	{	
		private static final long serialVersionUID = 1L;

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
