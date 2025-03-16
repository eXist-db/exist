/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.client;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Observable;
import java.util.Observer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import org.exist.storage.ElementIndex;
import org.exist.util.ProgressIndicator;

class UploadDialog extends JFrame {

	private static final long serialVersionUID = 1L;

	JTextField currentFile;
	JTextField currentDir;
	JLabel currentSize;
	JTextArea messages;
	JProgressBar progress;
	JProgressBar byDirProgress;

	boolean cancelled = false;
	final JButton closeBtn;
	
	public UploadDialog() {
		super(Messages.getString("UploadDialog.0")); //$NON-NLS-1$
		final GridBagLayout grid = new GridBagLayout();
		getContentPane().setLayout(grid);
		final GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(5, 5, 5, 5);

		JLabel label = new JLabel(Messages.getString("UploadDialog.1")); //$NON-NLS-1$
        this.setIconImage(InteractiveClient.getExistIcon(getClass()).getImage());
		c.gridx = 0;
		c.gridy = 0;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.NONE;
		c.weightx = 0;
		grid.setConstraints(label, c);
		getContentPane().add(label);

		byDirProgress = new JProgressBar();
		byDirProgress.setStringPainted(true);
		byDirProgress.setString(Messages.getString("UploadDialog.2")); //$NON-NLS-1$
		byDirProgress.setIndeterminate(true);
		c.gridx = 1;
		c.gridy = 0;
		c.anchor = GridBagConstraints.EAST;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1;
		grid.setConstraints(byDirProgress, c);
		getContentPane().add(byDirProgress);

		label = new JLabel(Messages.getString("UploadDialog.3")); //$NON-NLS-1$
		c.gridx = 0;
		c.gridy = 1;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.NONE;
		c.weightx = 0;
		grid.setConstraints(label, c);
		getContentPane().add(label);

		currentDir = new JTextField(30);
		currentDir.setEditable(false);
		c.gridx = 1;
		c.gridy = 1;
		c.anchor = GridBagConstraints.EAST;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1;
		grid.setConstraints(currentDir, c);
		getContentPane().add(currentDir);

		label = new JLabel(Messages.getString("UploadDialog.4")); //$NON-NLS-1$
		c.gridx = 0;
		c.gridy = 2;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.NONE;
		c.weightx = 0;
		grid.setConstraints(label, c);
		getContentPane().add(label);

		currentFile = new JTextField(30);
		currentFile.setEditable(false);
		c.gridx = 1;
		c.gridy = 2;
		c.anchor = GridBagConstraints.EAST;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1;
		grid.setConstraints(currentFile, c);
		getContentPane().add(currentFile);

		label = new JLabel(Messages.getString("UploadDialog.5")); //$NON-NLS-1$
		c.gridx = 0;
		c.gridy = 3;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.NONE;
		c.weightx = 0;
		grid.setConstraints(label, c);
		getContentPane().add(label);

		currentSize = new JLabel(Messages.getString("UploadDialog.6")); //$NON-NLS-1$
		c.gridx = 1;
		c.gridy = 3;
		c.anchor = GridBagConstraints.EAST;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1;
		grid.setConstraints(currentSize, c);
		getContentPane().add(currentSize);

		final JLabel status = new JLabel(Messages.getString("UploadDialog.7")); //$NON-NLS-1$
		c.gridx = 0;
		c.gridy = 4;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.NONE;
		c.weightx = 0;
		grid.setConstraints(status, c);
		getContentPane().add(status);

		progress = new JProgressBar();
		progress.setIndeterminate(true);
		progress.setStringPainted(true);
		c.gridx = 1;
		c.gridy = 4;
		c.anchor = GridBagConstraints.EAST;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1;
		grid.setConstraints(progress, c);
		getContentPane().add(progress);

		messages = new JTextArea(5, 50);
		messages.setEditable(false);
		messages.setLineWrap(true);
		final JScrollPane scroll =
			new JScrollPane(
				messages,
				JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
				JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scroll.setBorder(BorderFactory.createTitledBorder("Messages")); //$NON-NLS-1$
		c.gridx = 0;
		c.gridy = 5;
		c.gridwidth = 2;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.BOTH;
		c.weightx = 1;
		c.weighty = 1;
		grid.setConstraints(scroll, c);
		getContentPane().add(scroll);

		closeBtn = new JButton(Messages.getString("UploadDialog.9")); //$NON-NLS-1$
		closeBtn.addActionListener(e -> {
            if (Messages.getString("UploadDialog.20").equals(closeBtn.getText())) //$NON-NLS-1$
                {setVisible(false);}
            else {
                cancelled = true;
                closeBtn.setText(Messages.getString("UploadDialog.11")); //$NON-NLS-1$
            }
        });
		c.gridx = 0;
		c.gridy = 6;
		c.gridwidth = 2;
		c.anchor = GridBagConstraints.EAST;
		c.fill = GridBagConstraints.NONE;
		c.weightx = 0;
		c.weighty = 0;
		grid.setConstraints(closeBtn, c);
		getContentPane().add(closeBtn);
		
		pack();
	}

	public Observer getObserver() {
		return new UploadProgressObserver();
	}

	public void setCurrent(String label) {
		currentFile.setText(label);
	}

	public void setCurrentDir(String dir) {
		currentDir.setText(dir);
	}

	public void setCurrentSize(long size) {
		if (size >= 1024)
			{currentSize.setText(size / 1024 + Messages.getString("UploadDialog.12"));} //$NON-NLS-1$
		else
			{currentSize.setText(String.valueOf(size));}
	}

	public void setTotalSize(long size) {
		byDirProgress.setIndeterminate(false);
		byDirProgress.setString(null);
		byDirProgress.setMinimum(0);
		byDirProgress.setValue(0);
		byDirProgress.setMaximum((int) (size / 1024));
	}

	public void setStoredSize(long count) {
		byDirProgress.setValue((int) (count / 1024));
	}

	public boolean isCancelled() {
		return cancelled;
	}
	
	public void uploadCompleted() {
		closeBtn.setText(Messages.getString("UploadDialog.13")); //$NON-NLS-1$
		progress.setIndeterminate(false);
		progress.setValue(100);
		progress.setString(Messages.getString("UploadDialog.14")); //$NON-NLS-1$
		byDirProgress.setIndeterminate(false);
		byDirProgress.setString(null);
		byDirProgress.setValue(byDirProgress.getMaximum());
	}
	
	public void showMessage(String msg) {
		messages.append(msg + Messages.getString("UploadDialog.15")); //$NON-NLS-1$
		messages.setCaretPosition(messages.getDocument().getLength());
	}

	public void reset() {
		progress.setString(Messages.getString("UploadDialog.16")); //$NON-NLS-1$
		progress.setIndeterminate(true);
	}

	class UploadProgressObserver implements Observer {

		public void update(Observable o, Object arg) {
			progress.setIndeterminate(false);
			final ProgressIndicator ind = (ProgressIndicator) arg;
			progress.setValue(ind.getPercentage());

			if (o instanceof ElementIndex)
				{progress.setString(Messages.getString("UploadDialog.18"));} //$NON-NLS-1$
			else
				{progress.setString(Messages.getString("UploadDialog.19"));} //$NON-NLS-1$
		}

	}
}
