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

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Box;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import org.exist.security.PermissionDeniedException;
import org.exist.xmldb.XmldbURI;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;

/**
 * Dialog for viewing and editing Triggers in the Admin Client 
 * 
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 * @serial 2012-11-24
 * @version 1.1
 */
class TriggersDialog extends JFrame {

    private static final long serialVersionUID = 1L;

    private CollectionXConf cx = null;

    private JComboBox cmbCollections;

    private JTable tblTriggers;
    private TriggersTableModel triggersModel;

    private InteractiveClient client;

    public TriggersDialog(final String title, final InteractiveClient client) {
        super(title);
        this.client = client;
        this.setIconImage(InteractiveClient.getExistIcon(getClass()).getImage());
        //capture the frame's close event
        final WindowListener windowListener = new WindowAdapter() {
            @Override
            public void windowClosing(final WindowEvent e) {
                saveChanges();

                TriggersDialog.this.setVisible(false);
                TriggersDialog.this.dispose();
            }
        };
        
        this.addWindowListener(windowListener);

        //draw the GUI
        setupComponents();

        //Get the indexes for the root collection
        actionGetTriggers(XmldbURI.ROOT_COLLECTION);
    }

    private void setupComponents() {
        //Dialog Content Panel
        final GridBagLayout grid = new GridBagLayout();
        getContentPane().setLayout(grid);

        //Constraints for Layout
        final GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 2, 2, 2);

        //collection label
        final JLabel label = new JLabel(Messages.getString("TriggersDialog.Collection"));
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 1;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.NONE;
        grid.setConstraints(label, c);
        getContentPane().add(label);

        //get the collections but not system collections
        final List<PrettyXmldbURI> alCollections = new ArrayList<>();
        
        try {
            final Collection root = client.getCollection(XmldbURI.ROOT_COLLECTION);
            final List<PrettyXmldbURI> alAllCollections = getCollections(root, new ArrayList<>());
            for(int i = 0; i < alAllCollections.size(); i++) {
                //TODO : use XmldbURIs !
                if(!alAllCollections.get(i).toString().contains(XmldbURI.CONFIG_COLLECTION)) {
                    alCollections.add(alAllCollections.get(i));
                }
            }
        } catch(final XMLDBException e) {
            ClientFrame.showErrorMessage(e.getMessage(), e);
            return;
        }
        
        //Create a combobox listing the collections
        cmbCollections = new JComboBox(alCollections.toArray());
        cmbCollections.addActionListener(e -> {
            saveChanges();

            final JComboBox cb = (JComboBox)e.getSource();
            actionGetTriggers(cb.getSelectedItem().toString());
        });
        
        c.gridx = 1;
        c.gridy = 0;
        c.gridwidth = 1;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        grid.setConstraints(cmbCollections, c);
        getContentPane().add(cmbCollections);

        //Panel to hold controls relating to the Triggers Index
        final JPanel panelTriggers = new JPanel();
        panelTriggers.setBorder(new TitledBorder(Messages.getString("TriggersDialog.Triggers")));
        final GridBagLayout panelTriggersGrid = new GridBagLayout();
        panelTriggers.setLayout(panelTriggersGrid);

        //Table to hold the Triggers with Sroll bar
        triggersModel = new TriggersTableModel();
        tblTriggers = new JTable(triggersModel);
        tblTriggers.setAutoResizeMode(JTable.AUTO_RESIZE_NEXT_COLUMN);
        tblTriggers.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        //Toolbar with add/delete buttons for Triggers
        final Box triggersToolbarBox = Box.createHorizontalBox();
        //add button
        final JButton btnAddTrigger = new JButton(Messages.getString("TriggersDialog.addbutton"));
        btnAddTrigger.addActionListener(e -> actionAddTrigger());
        triggersToolbarBox.add(btnAddTrigger);
        
        //delete button
        final JButton btnDeleteTrigger = new JButton(Messages.getString("TriggersDialog.deletebutton"));
        btnDeleteTrigger.addActionListener(e -> actionDeleteTrigger());
        triggersToolbarBox.add(btnDeleteTrigger);
        c.gridx = 0;
        c.gridy = 4;
        c.gridwidth = 2;
        c.anchor = GridBagConstraints.CENTER;
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 0;
        c.weighty = 0;
        panelTriggersGrid.setConstraints(triggersToolbarBox, c);
        panelTriggers.add(triggersToolbarBox);

        //add triggers panel to content frame
        c.gridx = 0;
        c.gridy = 1;
        c.gridwidth = 2;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1;
        c.weighty = 1;
        grid.setConstraints(panelTriggers, c);
        getContentPane().add(panelTriggers);

        pack();
    }

    //if changes have been made, allows the user to save them
    private void saveChanges() {
        //the collection has been changed
        if(cx.hasChanged()) {
            //ask the user if they would like to save the changes
            final int result = JOptionPane.showConfirmDialog(getContentPane(), "The configuration for the collection has changed, would you like to save the changes?", "Save Changes", JOptionPane.YES_NO_OPTION);

            if(result == JOptionPane.YES_OPTION) {
                //save the collection.xconf changes
                if(cx.Save()) {
                    //save ok
                    JOptionPane.showMessageDialog(getContentPane(), "Your changes have been saved.");
                } else {
                    //save failed
                    JOptionPane.showMessageDialog(getContentPane(), "Unable to save changes!");
                }
            }
        }
    }

    //THIS IS A COPY FROM ClientFrame
    //TODO: share this code between the two classes
    private List<PrettyXmldbURI> getCollections(final Collection root, final List<PrettyXmldbURI> collectionsList) throws XMLDBException {
        collectionsList.add(new PrettyXmldbURI(XmldbURI.create(root.getName())));
        final String[] childCollections = root.listChildCollections();
        Collection child;
        for(int i = 0; i < childCollections.length; i++) {
            try {
                child = root.getChildCollection(childCollections[i]);
            } catch(final XMLDBException xmldbe) {
                if(xmldbe.getCause() instanceof PermissionDeniedException) {
                    continue;
                } else {
                    throw xmldbe;
                }
            }
            getCollections(child, collectionsList);
        }
        return collectionsList;
    }

    private void actionAddTrigger() {
        triggersModel.addRow();
    }

    private void actionDeleteTrigger() {
        final int iSelectedRow = tblTriggers.getSelectedRow();
        if(iSelectedRow > -1 ) {
            triggersModel.removeRow(iSelectedRow);
        }
    }

    //Displays the indexes when a collection is selection
    private void actionGetTriggers(final String collectionName) {
        try {
            cx = new CollectionXConf(collectionName, client);
            triggersModel.fireTableDataChanged();
        } catch(final XMLDBException xmldbe) {
            ClientFrame.showErrorMessage(xmldbe.getMessage(), xmldbe);
        }

    }

    public static class CheckBoxCellRenderer extends JCheckBox implements TableCellRenderer {
        private static final long serialVersionUID = 1L;

        public CheckBoxCellRenderer() {
            setHorizontalAlignment(JLabel.CENTER);
        }
    
        @Override
        public Component getTableCellRendererComponent(final JTable table, final Object value, final boolean isSelected, final boolean hasFocus, final int row, final int column) {
            if(isSelected) {
                setForeground(table.getSelectionForeground());
                //super.setBackground(table.getSelectionBackground());
                setBackground(table.getSelectionBackground());
            } else {
                setForeground(table.getForeground());
                setBackground(table.getBackground());
            }
    
            // Set the state
            setSelected((value != null && ((Boolean) value).booleanValue()));
            return this;
        }
    }

    public static class CheckBoxCellEditor extends DefaultCellEditor {
        private static final long serialVersionUID = 1L;

        public CheckBoxCellEditor() {
            super(new JCheckBox());
        }
    }

    class TriggersTableModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;

        private final String[] columnNames = new String[] { "class", "Parameters" };

        public TriggersTableModel() {
            super();
            fireTableDataChanged();
        }

        /* (non-Javadoc)
        * @see javax.swing.table.TableModel#isCellEditable()
        */
        @Override
        public void setValueAt(final Object aValue, final int rowIndex, final int columnIndex) {
            String triggerClass = null;

            if(columnIndex == 0) {
                //trigger class name has been updated
                triggerClass = (String)aValue;
            }

            cx.updateTrigger(rowIndex, triggerClass, null);
            fireTableCellUpdated(rowIndex, columnIndex);
        }

        public void removeRow(final int rowIndex) {
            cx.deleteTrigger(rowIndex);
            fireTableRowsDeleted(rowIndex, rowIndex);
        }

        public void addRow() {	
            cx.addTrigger("", null);
            fireTableRowsInserted(getRowCount(), getRowCount() + 1);
            final ListSelectionModel selectionModel = tblTriggers.getSelectionModel();
            selectionModel.setSelectionInterval(getRowCount() -1, getRowCount() -1);
        }

        /* (non-Javadoc)
        * @see javax.swing.table.TableModel#isCellEditable()
        */
        @Override
        public boolean isCellEditable(final int rowIndex, final int columnIndex) {
            return true;
        }

        /* (non-Javadoc)
        * @see javax.swing.table.TableModel#getColumnCount()
        */
        @Override
        public int getColumnCount() {
            return columnNames.length;
        }

        /* (non-Javadoc)
         * @see javax.swing.table.TableModel#getColumnName(int)
         */
        @Override
        public String getColumnName(final int column) {
            return columnNames[column];
        }

        /* (non-Javadoc)
         * @see javax.swing.table.TableModel#getRowCount()
         */
        @Override
        public int getRowCount() {
            return cx != null ? cx.getTriggerCount() : 0;
        }

        /* (non-Javadoc)
         * @see javax.swing.table.TableModel#getValueAt(int, int)
         */
        @Override
        public Object getValueAt(final int rowIndex, final int columnIndex) {
            switch(columnIndex) {
                /* class */
                case 0:
                    return cx.getTrigger(rowIndex).getTriggerClass();
                
                default :
                    return null;
            }
        }
    }
}
