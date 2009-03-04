/*
 * BackupDialog.java - Jun 16, 2003
 * 
 * @author wolf
 */
package org.exist.backup;

import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.HeadlessException;
import java.awt.Insets;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;

public class BackupDialog extends JDialog {

	JTextField currentCollection;
	JTextField currentFile;
	JProgressBar progress;
	
	public BackupDialog() throws HeadlessException {
		super();
		setupComponents();
	}

	/**
	 * @param owner
	 * @param modal
	 * @throws java.awt.HeadlessException
	 */
	public BackupDialog(Frame owner, boolean modal) throws HeadlessException {
		super(owner, "Backup", modal);
		setupComponents();
		pack();
	}

	private void setupComponents() {
		GridBagLayout grid = new GridBagLayout();
		getContentPane().setLayout(grid);
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(5, 5, 5, 5);

		JLabel label = new JLabel("Collection:");
		c.gridx = 0;
		c.gridy = 0;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.NONE;
		grid.setConstraints(label, c);
		getContentPane().add(label);

		currentCollection = new JTextField(40);
		currentCollection.setEditable(false);
		c.gridx = 1;
		c.gridy = 0;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.HORIZONTAL;
		grid.setConstraints(currentCollection, c);
		getContentPane().add(currentCollection);

		label = new JLabel("Storing file:");
		c.gridx = 0;
		c.gridy = 1;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.NONE;
		grid.setConstraints(label, c);
		getContentPane().add(label);

		currentFile = new JTextField(40);
		currentFile.setEditable(false);
		c.gridx = 1;
		c.gridy = 1;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.HORIZONTAL;
		grid.setConstraints(currentFile, c);
		getContentPane().add(currentFile);
		
		label = new JLabel("Collection Progress:");
		c.gridx = 0;
		c.gridy = 2;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.NONE;
		grid.setConstraints(label, c);
		getContentPane().add(label);
		
		progress = new JProgressBar();
		progress.setIndeterminate(false);
		progress.setStringPainted(true);
		progress.setMinimumSize(new Dimension(200, 30));
		c.gridx = 1;
		c.gridy = 2;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.HORIZONTAL;
		grid.setConstraints(progress, c);
		getContentPane().add(progress);
	}
	
	public void setCollection(String collection) {
		currentCollection.setText(collection);
	}
	
	public void setResource(String resource) {
		currentFile.setText(resource);
	}
	
	public void setResourceCount(int count) {
		progress.setMaximum(count);
	}
	
	public void setProgress(int count) {
		progress.setValue(count);
	}
}
