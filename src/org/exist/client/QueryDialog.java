/*
 * QueryDialog.java - Aug 6, 2003
 * 
 * @author wolf
 */
package org.exist.client;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Properties;
import java.util.TreeMap;
import java.util.Vector;

import javax.swing.Box;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.AbstractTableModel;
import javax.xml.transform.OutputKeys;

import org.exist.xmldb.XPathQueryServiceImpl;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceIterator;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

/**
 * @author wolf
 *
 * To change this generated comment go to 
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class QueryDialog extends JFrame {

	private Collection collection;
	private Properties properties;
	private JTextArea query;
	private QueryResultTableModel model;
	private JTable resultDocs;
	private JTextArea resultDisplay;
	private JComboBox collections = null;
	private SpinnerNumberModel count;
	private DefaultComboBoxModel history = new DefaultComboBoxModel();

	public QueryDialog(Collection collection, Properties properties) {
		this.collection = collection;
		this.properties = properties;
		setupComponents();
		pack();
	}

	private void setupComponents() {
		getContentPane().setLayout(new BorderLayout());

		JComponent qbox = createQueryBox();
		getContentPane().add(qbox, BorderLayout.NORTH);

		model = new QueryResultTableModel();
		resultDocs = new JTable(model);
		resultDocs.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		resultDocs.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 1)
					tableSelectAction(e);
			}
		});
		JScrollPane tableScroll = new JScrollPane(resultDocs);
		tableScroll.setPreferredSize(new Dimension(150, 75));

		resultDisplay = new JTextArea(20, 70);
		resultDisplay.setLineWrap(true);
		JScrollPane resultScroll = new JScrollPane(resultDisplay);

		JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
		split.add(tableScroll);
		split.add(resultScroll);
		getContentPane().add(split, BorderLayout.SOUTH);
	}

	private JComponent createQueryBox() {
		Box vbox = Box.createVerticalBox();

		Box inputVBox = Box.createVerticalBox();

		Box historyBox = Box.createHorizontalBox();
		JLabel label = new JLabel("History: ");
		historyBox.add(label);
		JComboBox historyList = new JComboBox(history);
		historyBox.add(historyList);
		
		inputVBox.add(historyBox);

		query = new JTextArea(5, 60);
		query.setEditable(true);
		query.setLineWrap(true);
		inputVBox.add(query);
		vbox.add(inputVBox);

		Box hbox = Box.createHorizontalBox();
		Vector data = new Vector();
		data.addElement("*");
		try {
			getCollections(collection, data);
		} catch (XMLDBException e) {
			ClientFrame.showErrorMessage(
				"An error occurred while retrieving collections list",
				e);
		}
		collections = new JComboBox(data);
		hbox.add(collections);

		label = new JLabel("Show max. results: ");
		hbox.add(label);

		count = new SpinnerNumberModel(100, 1, 10000, 50);
		JSpinner spinner = new JSpinner(count);
		spinner.setMaximumSize(new Dimension(160, 25));
		hbox.add(spinner);

		URL url = getClass().getResource("icons/Find24.gif");
		JButton button = new JButton("Submit", new ImageIcon(url));
		hbox.add(button);
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				doQuery();
			}
		});
		vbox.add(hbox);
		return vbox;
	}

	private Vector getCollections(
		Collection collection,
		Vector collectionsList)
		throws XMLDBException {
		collectionsList.add(collection.getName());
		String[] childCollections = collection.listChildCollections();
		Collection child;
		for (int i = 0; i < childCollections.length; i++) {
			child = collection.getChildCollection(childCollections[i]);
			getCollections(child, collectionsList);
		}
		return collectionsList;
	}

	private void doQuery() {
		String xpath = (String) query.getText();
		if (xpath.length() == 0)
			return;
		resultDisplay.setText("");
		try {
			XPathQueryServiceImpl service =
				(XPathQueryServiceImpl) collection.getService(
					"XPathQueryService",
					"1.0");
			service.setProperty(OutputKeys.INDENT, properties.getProperty("indent"));
			ResourceSet result = service.query(xpath);
			model.setResourceSet(result);
		} catch (XMLDBException e) {
			ClientFrame.showErrorMessage(
				"An exception occurred during query execution: "
					+ e.getMessage(),
				e);
		}
		history.addElement(xpath);
	}

	private void tableSelectAction(MouseEvent ev) {
		int row = resultDocs.rowAtPoint(ev.getPoint());
		resultDisplay.setText("");
		ArrayList results = model.data[row];
		XMLResource resource;
		int howmany = count.getNumber().intValue();
		int j = 0;
		for (Iterator i = results.iterator();
			i.hasNext() && j < howmany;
			j++) {
			resource = (XMLResource) i.next();
			try {
				resultDisplay.append((String) resource.getContent());
				resultDisplay.append("\n");
				resultDisplay.setCaretPosition(0);
			} catch (XMLDBException e) {
				ClientFrame.showErrorMessage(
					"An error occurred while retrieving results: "
						+ e.getMessage(),
					e);
			}
		}
	}

	class QueryResultTableModel extends AbstractTableModel {

		ArrayList data[] = null;

		public void setResourceSet(ResourceSet results) throws XMLDBException {
			TreeMap docs = new TreeMap();
			XMLResource current;
			ArrayList hits;
			for (ResourceIterator i = results.getIterator();
				i.hasMoreResources();
				) {
				current = (XMLResource) i.nextResource();
				hits = (ArrayList) docs.get(current.getDocumentId());
				if (hits == null) {
					hits = new ArrayList(10);
					docs.put(current.getDocumentId(), hits);
				}
				hits.add(current);
			}
			data = new ArrayList[docs.size()];
			int j = 0;
			for (Iterator i = docs.values().iterator(); i.hasNext(); j++) {
				hits = (ArrayList) i.next();
				data[j] = hits;
			}
			this.fireTableDataChanged();
		}

		public int getColumnCount() {
			return 2;
		}

		public int getRowCount() {
			return data == null ? 0 : data.length;
		}

		public String getColumnName(int column) {
			switch (column) {
				case 0 :
					return "Document";
				default :
					return "Hits";
			}
		}

		public Object getValueAt(int rowIndex, int columnIndex) {
			switch (columnIndex) {
				case 0 :
					try {
						return ((XMLResource) data[rowIndex].get(0))
							.getDocumentId();
					} catch (XMLDBException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				case 1 :
					return new Integer(data[rowIndex].size());
			}
			return null;
		}

	}
}
