package org.exist.backup;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.HeadlessException;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.Vector;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.exist.client.Messages;

import org.exist.storage.DBBroker;

import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;

public class CreateBackupDialog extends JPanel {

	JComboBox collections;
	JTextField directory;
	String uri;
	String user;
	String passwd;

	public CreateBackupDialog(String uri, String user, String passwd, String backupDir)
		throws HeadlessException {
		super(false);
		this.uri = uri;
		this.user = user;
		this.passwd = passwd;
		setupComponents(backupDir);
		setSize(new Dimension(350, 200));
	}

	private void setupComponents(String backupDir) {
		GridBagLayout grid = new GridBagLayout();
		setLayout(grid);
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(5, 5, 5, 5);
                
                
		JLabel label = new JLabel( Messages.getString("CreateBackupDialog.1") );
		c.gridx = 0;
		c.gridy = 0;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.NONE;
		grid.setConstraints(label, c);
		add(label);

		Vector v = getAllCollections();
		collections = new JComboBox(v);
		c.gridx = 1;
		c.gridy = 0;
		c.gridwidth = 2;
		c.anchor = GridBagConstraints.EAST;
		c.fill = GridBagConstraints.HORIZONTAL;
		grid.setConstraints(collections, c);
		add(collections);

                
		label = new JLabel( Messages.getString("CreateBackupDialog.2") );
		c.gridx = 0;
		c.gridy = 1;
		c.gridwidth = 1;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.NONE;
		grid.setConstraints(label, c);
		add(label);

		directory = new JTextField(backupDir, 40);
		c.gridx = 1;
		c.gridy = 1;
		c.anchor = GridBagConstraints.EAST;
		c.fill = GridBagConstraints.HORIZONTAL;
		grid.setConstraints(directory, c);
		add(directory);

                
		JButton select = new JButton( Messages.getString("CreateBackupDialog.3") );
		select.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionSelect();
			}
		});
		c.gridx = 2;
		c.gridy = 1;
		c.anchor = GridBagConstraints.EAST;
		c.fill = GridBagConstraints.NONE;
		grid.setConstraints(select, c);
                
                select.setToolTipText( Messages.getString("CreateBackupDialog.4") );
		add(select);
	}
        
    public class MyBackupContentsFilter extends javax.swing.filechooser.FileFilter {
            public boolean accept(File f) {
                if (f.getName().toLowerCase().equals("__contents__.xml"))
                    return true;
                if (f.isDirectory())
                    return true;
                return false;
            }
            
            public String getDescription() {
                return "__contents__.xml files"; 
            }
    }
    
    public class MyZipFilter extends javax.swing.filechooser.FileFilter {
            public boolean accept(File f) {
                if (f.getName().toLowerCase().endsWith(".zip"))
                    return true;
                if (f.isDirectory())
                    return true;
                return false;
            }
            
            public String getDescription() {
                return "Zip files";
            }
            
    }

	private void actionSelect() {
		JFileChooser chooser = new JFileChooser();
		chooser.setMultiSelectionEnabled(false);
		chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
                chooser.addChoosableFileFilter(new MyBackupContentsFilter());
                chooser.addChoosableFileFilter(new MyZipFilter());
                chooser.setSelectedFile(new File("eXist-backup.zip"));
		chooser.setCurrentDirectory(null);
               
		if (chooser.showDialog(this, Messages.getString("CreateBackupDialog.5"))
			== JFileChooser.APPROVE_OPTION) {
			File f = chooser.getSelectedFile();
			if (f.exists()) {
				if (JOptionPane
					.showConfirmDialog(
						this,
						Messages.getString("CreateBackupDialog.6a") + " "+ f.getAbsolutePath() + " "+ Messages.getString("CreateBackupDialog.6b"),
						Messages.getString("CreateBackupDialog.6c"),
						JOptionPane.YES_NO_OPTION)
					== JOptionPane.NO_OPTION)
					return;
			}
			directory.setText(f.getAbsolutePath());
		}
	}

	private Vector getAllCollections() {
		Vector list = new Vector();
		try {
			Collection root = DatabaseManager.getCollection(uri + DBBroker.ROOT_COLLECTION, user, passwd);
			getAllCollections(root, list);
		} catch (XMLDBException e) {
			e.printStackTrace();
		}
		return list;
	}

	private void getAllCollections(Collection collection, Vector collections)
		throws XMLDBException {
		collections.add(collection.getName());
		String[] childCollections = collection.listChildCollections();
		Collection child;
		for (int i = 0; i < childCollections.length; i++) {
			child = collection.getChildCollection(childCollections[i]);
			getAllCollections(child, collections);
		}
	}

	public String getCollection() {
		return (String) collections.getSelectedItem();
	}

	public String getBackupDir() {
		return directory.getText();
	}
}
