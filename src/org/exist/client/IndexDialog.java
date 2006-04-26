/*
 * UserDialog.java - Jun 16, 2003
 * 
 * @author wolf
 */
package org.exist.client;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.util.Vector;

import javax.swing.Box;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
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
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;


/**
 * TODO: could loose the member arrays of RangeIndexTableModel, QNameIndexTableModel, and replace with calls to cx
 * 
 */

class IndexDialog extends JFrame {
	
	CollectionXConf cx = null;
	
	JTextField txtXPath;
	JComboBox cmbxsType;
	JTable tblFullTextIndexes;
	FullTextIndexTableModel fulltextIndexModel;
	JTable tblRangeIndexes;
	RangeIndexTableModel rangeIndexModel;
	JTable tblQNameIndexes;
	QNameIndexTableModel qnameIndexModel;
	
	InteractiveClient client;

	private static final String[] FULLTEXT_INDEX_ACTIONS = {"include", "exclude"};
	private static final String[] INDEX_TYPES = {"xs:boolean","xs:integer","xs:dateTime","xs:string"};
	
	public IndexDialog(String title, InteractiveClient client ) 
	{
		super(title);
		this.client = client;
		
		setupComponents();
		
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
		grid.setConstraints(label, c);
		getContentPane().add(label);
		
		//get the collections but not system collections
		Vector ourCollectionsVec = new Vector();
        try
        {
            Collection root = client.getCollection(DBBroker.ROOT_COLLECTION);
            Vector allCollectionsVec = getCollections(root, new Vector());
            for(int i = 0; i < allCollectionsVec.size(); i++)
            {
            	if(allCollectionsVec.get(i).toString().indexOf(DBBroker.SYSTEM_COLLECTION)  == -1)
            	{
            		ourCollectionsVec.add(allCollectionsVec.get(i));
            	}
            }
        }
        catch (XMLDBException e)
        {
            //showErrorMessage(e.getMessage(), e);
            return;
        }
        
        //Create a combobox listing the collections
        JComboBox combo = new JComboBox(ourCollectionsVec);
        combo.addActionListener(new ActionListener(){
        		public void actionPerformed(ActionEvent e) {
   				 JComboBox cb = (JComboBox)e.getSource();
   				 actionGetIndexes(cb.getSelectedItem().toString());
   			}
        });
        c.gridx = 1;
		c.gridy = 0;
		c.gridwidth = 1;
		c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.NONE;
        grid.setConstraints(combo, c);
        getContentPane().add(combo);

        //Panel to hold controls relating to the FullText Index
		JPanel panelFullTextIndex = new JPanel();
		panelFullTextIndex.setBorder(new TitledBorder("Full Text Index"));
		GridBagLayout panelFullTextIndexGrid = new GridBagLayout();
		panelFullTextIndex.setLayout(panelFullTextIndexGrid);
		
		//fulltext default label
		JLabel lblDefault = new JLabel("Default");
		c.gridx = 0;
		c.gridy = 0;
		c.gridwidth = 1;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.NONE;
		panelFullTextIndexGrid.setConstraints(lblDefault, c);
		panelFullTextIndex.add(lblDefault);
		
		//fulltext default combobox
		JComboBox cmbDefault = new JComboBox(new String[]{"all", "none"});
		c.gridx = 1;
		c.gridy = 0;
		c.gridwidth = 1;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.NONE;
		panelFullTextIndexGrid.setConstraints(cmbDefault, c);
		panelFullTextIndex.add(cmbDefault);
        
		//fulltext alphanumeric checkbox
		JCheckBox chkAlphanum = new JCheckBox("Alphanumeric");
		c.gridx = 0;
		c.gridy = 1;
		c.gridwidth = 1;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.NONE;
		panelFullTextIndexGrid.setConstraints(chkAlphanum, c);
		panelFullTextIndex.add(chkAlphanum);

		//fulltext attributes checkbox
		JCheckBox chkAttributes = new JCheckBox("Attributes");
		c.gridx = 0;
		c.gridy = 2;
		c.gridwidth = 1;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.NONE;
		panelFullTextIndexGrid.setConstraints(chkAttributes, c);
		panelFullTextIndex.add(chkAttributes);
		
        //Table to hold the FullText Indexes with Sroll bar
		/*fulltextIndexModel = new FullTextIndexTableModel();
        tblFullTextIndexes = new JTable(fulltextIndexModel);
        tblFullTextIndexes.setAutoResizeMode(JTable.AUTO_RESIZE_NEXT_COLUMN);
        tblFullTextIndexes.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        TableColumn colAction = tblFullTextIndexes.getColumnModel().getColumn(1);
        colAction.setCellEditor(new ComboBoxCellEditor(FULLTEXT_INDEX_ACTIONS));
        colAction.setCellRenderer(new ComboBoxCellRenderer(FULLTEXT_INDEX_ACTIONS));
        JScrollPane scrollFullTextIndexes = new JScrollPane(tblFullTextIndexes);
		scrollFullTextIndexes.setPreferredSize(new Dimension(250, 150));
		c.gridx = 0;
		c.gridy = 3;
		c.gridwidth = 2;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.BOTH;
		panelFullTextIndexGrid.setConstraints(scrollFullTextIndexes, c);
		panelFullTextIndex.add(scrollFullTextIndexes);*/
		
		//add fulltext panel to content frame
		c.gridx = 0;
		c.gridy = 1;
		c.gridwidth = 2;
		c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
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
        tblRangeIndexes.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        TableColumn colxsType = tblRangeIndexes.getColumnModel().getColumn(1);
        colxsType.setCellEditor(new ComboBoxCellEditor(INDEX_TYPES));
        colxsType.setCellRenderer(new ComboBoxCellRenderer(INDEX_TYPES));
        JScrollPane scrollRangeIndexes = new JScrollPane(tblRangeIndexes);
		scrollRangeIndexes.setPreferredSize(new Dimension(350, 150));
		c.gridx = 0;
		c.gridy = 0;
		c.gridwidth = 2;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.BOTH;
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
		panelRangeIndexesGrid.setConstraints(rangeIndexToolbarBox, c);
		panelRangeIndexes.add(rangeIndexToolbarBox);

		//add request panel to content frame
		c.gridx = 0;
		c.gridy = 2;
		c.gridwidth = 2;
		c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    grid.setConstraints(panelRangeIndexes, c);
		getContentPane().add(panelRangeIndexes);

		
		//Panel to hold controls relating to the QName Indexes
		JPanel panelQNameIndexes = new JPanel();
		panelQNameIndexes.setBorder(new TitledBorder("QName Indexes"));
		GridBagLayout panelQNameIndexesGrid = new GridBagLayout();
		panelQNameIndexes.setLayout(panelQNameIndexesGrid);
        
        //Table to hold the qname Indexes with Sroll bar
		qnameIndexModel = new QNameIndexTableModel();
        tblQNameIndexes = new JTable(qnameIndexModel);
        tblQNameIndexes.setAutoResizeMode(JTable.AUTO_RESIZE_NEXT_COLUMN);
        tblQNameIndexes.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scrollQNameIndexes = new JScrollPane(tblQNameIndexes);
		scrollQNameIndexes.setPreferredSize(new Dimension(350, 150));
		c.gridx = 0;
		c.gridy = 0;
		c.gridwidth = 2;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.BOTH;
		panelQNameIndexesGrid.setConstraints(scrollQNameIndexes, c);
		panelQNameIndexes.add(scrollQNameIndexes);
        
		//Toolbar with add/delete buttons for qname Index
		Box qnameIndexToolbarBox = Box.createHorizontalBox();
		//add button
		JButton btnAddQNameIndex = new JButton("Add");
		btnAddQNameIndex.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e)
			{
				actionAddQNameIndex();
			}
		});
		qnameIndexToolbarBox.add(btnAddQNameIndex);
		//delete button
		JButton btnDeleteQNameIndex = new JButton("Delete");
		btnDeleteQNameIndex.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e)
			{
				actionDeleteQNameIndex();
			}
		});
		qnameIndexToolbarBox.add(btnDeleteQNameIndex);
		c.gridx = 0;
		c.gridy = 1;
		c.gridwidth = 2;
		c.anchor = GridBagConstraints.CENTER;
		c.fill = GridBagConstraints.BOTH;
		panelQNameIndexesGrid.setConstraints(qnameIndexToolbarBox, c);
		panelQNameIndexes.add(qnameIndexToolbarBox);

		//add qname panel to content frame
		c.gridx = 0;
		c.gridy = 3;
		c.gridwidth = 2;
		c.anchor = GridBagConstraints.WEST;
	    c.fill = GridBagConstraints.BOTH;
	    grid.setConstraints(panelQNameIndexes, c);
		getContentPane().add(panelQNameIndexes);

		
		pack();
	}

	//THIS IS A COPY FROM ClientFrame
	//TODO: share this code between the two classes
	private Vector getCollections(Collection root, Vector collectionsList)
    throws XMLDBException {
        collectionsList.addElement(root.getName());
        String[] childCollections= root.listChildCollections();
        Collection child;
        for (int i= 0; i < childCollections.length; i++){
            child= root.getChildCollection(childCollections[i]);
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
		int iSelectedRow = tblRangeIndexes.getSelectedRow();
		if(iSelectedRow > -1 )
		{
			rangeIndexModel.removeRow(iSelectedRow);
		}
		
	}
	
	private void actionAddQNameIndex()
	{
		qnameIndexModel.addRow();
	}
	
	private void actionDeleteQNameIndex()
	{
		int iSelectedRow = tblQNameIndexes.getSelectedRow();
		if(iSelectedRow > -1 )
		{
			qnameIndexModel.removeRow(iSelectedRow);
		}
		
	}
	
	//Displays the indexes when a collection is selection
	private void actionGetIndexes(String collectionName)
	{
		try
		{
			cx = new CollectionXConf(collectionName, client);
			//fulltextIndexModel.reload();
			rangeIndexModel.reload();
			qnameIndexModel.reload();
		}
		catch(XMLDBException xe)
		{
			//TODO: CONSIDER whether CollectionXConf Should throw xmldb exception at all?
		}
		
	}
	
	private void tableSelectAction(MouseEvent ev)
	{
		int row = tblRangeIndexes.rowAtPoint(ev.getPoint());
		CollectionXConf.RangeIndex rangeindex = rangeIndexModel.rangeIndexes[row];
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
		private final String[] columnNames = new String[] { "XPath", "action" };

		public FullTextIndexTableModel()
		{
			super();
			//reload();
		}

		/*public void reload()
		{
			fireTableDataChanged();
		}*/
		
		/* (non-Javadoc)
		* @see javax.swing.table.TableModel#isCellEditable()
		*/
		public void setValueAt(Object aValue, int rowIndex, int columnIndex)
		{
			switch (columnIndex)
			{
				case 0:		/* XPath */
			//		cx.updateFullNameIndex(rowIndex, aValue.toString(), null);
//					reload();
					break;
				case 1 :	/* action */
			//		cx.updateFullNameIndex(rowIndex, null, aValue.toString());
	//				reload();
					break;
				default :
					break;
			}
		}
		
		public void removeRow(int rowIndex)
		{
			//cx.deleteFullTextIndex(rowIndex);
		//	reload();
		}
		
		public void addRow()
		{	
			//cx.addFullTextIndex("", "xs:string");
		//	reload();
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
				case 0 :	/* XPath */
					return cx.getFullTextIndexPath(rowIndex);
				case 1 :	/* action */
					return cx.getFullTextIndexPathAction(rowIndex);
				default :
					return null;
			}
		}
	}
    
	class RangeIndexTableModel extends AbstractTableModel
	{	
		private final String[] columnNames = new String[] { "XPath", "xsType" };
		private CollectionXConf.RangeIndex rangeIndexes[] = null;

		public RangeIndexTableModel()
		{
			super();
			reload();
		}

		public void reload()
		{
			if(cx != null)
			{
				if(cx.getRangeIndexes() != null)
				{
						rangeIndexes = cx.getRangeIndexes();
				}
			}
			fireTableDataChanged();
		}
		
		/* (non-Javadoc)
		* @see javax.swing.table.TableModel#isCellEditable()
		*/
		public void setValueAt(Object aValue, int rowIndex, int columnIndex)
		{
			switch (columnIndex)
			{
				case 0:		/* XPath */
					cx.updateRangeIndex(rowIndex, aValue.toString(), null);
					reload();
					break;
				case 1 :	/* xsType */
					cx.updateRangeIndex(rowIndex, null, aValue.toString());
					reload();
					break;
				default :
					break;
			}
		}
		
		public void removeRow(int rowIndex)
		{
			cx.deleteRangeIndex(rowIndex);
			reload();
			
		}
		
		public void addRow()
		{			
			cx.addRangeIndex("", "xs:string");
			reload();
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
			return rangeIndexes == null ? 0 : rangeIndexes.length;
		}

		/* (non-Javadoc)
		 * @see javax.swing.table.TableModel#getValueAt(int, int)
		 */
		public Object getValueAt(int rowIndex, int columnIndex)
		{
			switch (columnIndex)
			{
				case 0 :	/* XPath */
					return rangeIndexes[rowIndex].getXPath();
				case 1 :	/* xsType */
					return rangeIndexes[rowIndex].getxsType();
				default :
					return null;
			}
		}
	}
	
	class QNameIndexTableModel extends AbstractTableModel
	{	
		private final String[] columnNames = new String[] { "QName", "xsType" };
		private CollectionXConf.QNameIndex qnameIndexes[] = null;

		public QNameIndexTableModel()
		{
			super();
			reload();
		}

		public void reload()
		{
			if(cx != null)
			{
				if(cx.getQNameIndexes() != null)
				{
						qnameIndexes = cx.getQNameIndexes();
				}
			}
			fireTableDataChanged();
		}
		
		/* (non-Javadoc)
		* @see javax.swing.table.TableModel#isCellEditable()
		*/
		public void setValueAt(Object aValue, int rowIndex, int columnIndex)
		{
			switch (columnIndex)
			{
				case 0:		/* QName */
					cx.updateQNameIndex(rowIndex, aValue.toString(), null);
					reload();
					break;
				case 1 :	/* xsType */
					cx.updateQNameIndex(rowIndex, null, aValue.toString());
					reload();
					break;
				default :
					break;
			}
		}
		
		public void removeRow(int rowIndex)
		{
			cx.deleteQNameIndex(rowIndex);
			reload();
			
		}
		
		public void addRow()
		{	
			cx.addQNameIndex("", "xs:string");
			reload();
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
			return qnameIndexes == null ? 0 : qnameIndexes.length;
		}

		/* (non-Javadoc)
		 * @see javax.swing.table.TableModel#getValueAt(int, int)
		 */
		public Object getValueAt(int rowIndex, int columnIndex)
		{
			switch (columnIndex)
			{
				case 0 :	/* QName */
					return qnameIndexes[rowIndex].getQName();
				case 1 :	/* xsType */
					return qnameIndexes[rowIndex].getxsType();
				default :
					return null;
			}
		}
	}
}