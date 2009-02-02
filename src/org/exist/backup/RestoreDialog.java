/*
 * RestoreDialog.java - Jun 16, 2003
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
import java.util.Observable;
import java.util.Observer;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import org.exist.storage.ElementIndex;
import org.exist.storage.TextSearchEngine;
import org.exist.util.ProgressIndicator;

public class RestoreDialog extends JDialog {

	JTextField currentCollection;
    JTextField currentBackup;
	JTextField resource;
	JTextArea messages;
	JProgressBar progress;

    Observer progressObserver = new UploadProgressObserver();
    
	/**
	 * @param owner
	 * @param title
	 * @param modal
	 * @throws java.awt.HeadlessException
	 */
	public RestoreDialog(Frame owner, String title, boolean modal) throws HeadlessException {
		super(owner, title, modal);
		setupComponents();
		setSize(new Dimension(350, 200));
		pack();
	}

	private void setupComponents() {
		GridBagLayout grid = new GridBagLayout();
		getContentPane().setLayout(grid);
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(5, 5, 5, 5);

        JLabel label = new JLabel("Backup:");
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.NONE;
        grid.setConstraints(label, c);
        getContentPane().add(label);

        currentBackup = new JTextField(50);
        currentBackup.setEditable(false);
        c.gridx = 1;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        grid.setConstraints(currentBackup, c);
        getContentPane().add(currentBackup);

		label = new JLabel("Collection:");
		c.gridx = 0;
		c.gridy = 1;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.NONE;
		grid.setConstraints(label, c);
		getContentPane().add(label);

		currentCollection = new JTextField(50);
		currentCollection.setEditable(false);
		c.gridx = 1;
		c.gridy = 1;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.HORIZONTAL;
		grid.setConstraints(currentCollection, c);
		getContentPane().add(currentCollection);

		label = new JLabel("Restoring:");
		c.gridx = 0;
		c.gridy = 2;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.NONE;
		grid.setConstraints(label, c);
		getContentPane().add(label);

		resource = new JTextField(40);
		resource.setEditable(false);
		c.gridx = 1;
		c.gridy = 2;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.HORIZONTAL;
		grid.setConstraints(resource, c);
		getContentPane().add(resource);

		label = new JLabel("Progress:");
		c.gridx = 0;
		c.gridy = 3;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.NONE;
		grid.setConstraints(label, c);
		getContentPane().add(label);

		progress = new JProgressBar();
		progress.setStringPainted(true);
		progress.setString("");
		c.gridx = 1;
		c.gridy = 3;
		c.anchor = GridBagConstraints.EAST;
		c.fill = GridBagConstraints.HORIZONTAL;
		grid.setConstraints(progress, c);
		getContentPane().add(progress);

		messages = new JTextArea(5, 50);
		messages.setEditable(false);
		JScrollPane scroll =
			new JScrollPane(
				messages,
				JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
				JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(BorderFactory.createTitledBorder("Messages"));
		c.gridx = 0;
		c.gridy = 4;
		c.gridwidth = 2;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.HORIZONTAL;
		grid.setConstraints(scroll, c);
		getContentPane().add(scroll);
	}

    public void setBackup(String backup) {
        currentBackup.setText(backup);
    }
    
	public void setCollection(String collection) {
		currentCollection.setText(collection);
	}

	public void setResource(String current) {
		resource.setText(current);
	}

	public void displayMessage(String message) {
		messages.append(message + '\n');
		messages.setCaretPosition(messages.getDocument().getLength());
	}
	
	public Observer getObserver() {
		return progressObserver;
	}

	class UploadProgressObserver implements Observer {

		int mode = 0;

		public void update(Observable o, Object arg) {
			progress.setIndeterminate(false);
			ProgressIndicator ind = (ProgressIndicator) arg;
			progress.setValue(ind.getPercentage());
			if (o instanceof TextSearchEngine)
				progress.setString("Storing words");
			else if (o instanceof ElementIndex)
				progress.setString("Storing elements");
			else
				progress.setString("Storing nodes");
		}

	}
}
