/*
 * ClientFrame.java - Mar 31, 2003
 * 
 * @author wolf
 */
package org.exist.client;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.HeadlessException;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.LinkedList;
import java.util.TreeSet;

import javax.swing.Box;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Keymap;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.TextAction;

import org.xml.sax.ContentHandler;

public class ClientFrame extends JFrame {

	public final static int MAX_DISPLAY_LENGTH = 512000;
	public final static int MAX_HISTORY = 50;

	private final static SimpleAttributeSet promptAttrs =
		new SimpleAttributeSet();
	private final static SimpleAttributeSet defaultAttrs =
		new SimpleAttributeSet();
	
	{
		StyleConstants.setForeground(promptAttrs, Color.blue);
		StyleConstants.setBold(promptAttrs, true);
		StyleConstants.setForeground(defaultAttrs, Color.black);
	}
	
	private int lastPosition = 0;
	private int currentHistory = 0;
	private DefaultStyledDocument doc;
		
	private JTextPane text;
	private JComboBox historyCombo;
	private InteractiveClient client;
	private String path = null;
	private ProcessThread process = new ProcessThread();
	private LinkedList history = new LinkedList();
	private TreeSet historyItems = new TreeSet();
	private PrettyPrinter printer;
	
	/**
	 * @throws java.awt.HeadlessException
	 */
	public ClientFrame(InteractiveClient client, String path) throws HeadlessException {
		super("eXist Client Shell");
		this.path = path;
		this.client = client;
		doc = new DefaultStyledDocument();
		text = new JTextPane(doc);
		printer = new PrettyPrinter(doc);
		JScrollPane scroll = new JScrollPane(text);
		Box hbox = Box.createHorizontalBox();
		JLabel histLabel = new JLabel("History:");
		hbox.add(histLabel);
		historyCombo = new JComboBox();
		historyCombo.addItemListener(new HistorySelectedAction());
		hbox.add(historyCombo);

		KeyStroke enter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0);
		KeyStroke up = KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0);
		KeyStroke down = KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0);
		Keymap map = text.getKeymap();
		EnterAction enterAction = new EnterAction("enter");
		BackHistoryAction backAction = new BackHistoryAction("back");
		ForwardHistoryAction forwardAction = new ForwardHistoryAction("forward");
		map.addActionForKeyStroke(enter, enterAction);
		map.addActionForKeyStroke(up, backAction);
		map.addActionForKeyStroke(down, forwardAction);

		getContentPane().add(hbox, BorderLayout.NORTH);
		getContentPane().add(scroll, BorderLayout.CENTER);
		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent ev) {
				close();
			}
		});
		pack();

		process.start();
		text.requestFocus();
	}

	public void setPath(String currentPath) {
		path = currentPath;
	}

	protected void displayPrompt() {
		try {
			int pos = doc.getEndPosition().getOffset() - 1;
			doc.insertString(pos, "exist:", promptAttrs);
			pos += 6;
			doc.insertString(pos, path + '>', promptAttrs);
			pos += path.length() + 1;
			doc.insertString(pos++, " ", defaultAttrs);
			text.setCaretPosition(pos);
			lastPosition = doc.getEndPosition().getOffset() - 1;
		} catch (BadLocationException e) {
		}
	}

	protected void display(String message) {
		try {
			int pos = doc.getLength();
			if (pos > MAX_DISPLAY_LENGTH) {
				doc.remove(0, MAX_DISPLAY_LENGTH);
				pos = doc.getLength();
			}
			doc.insertString(pos, message, defaultAttrs);
			text.setCaretPosition(doc.getLength());
			lastPosition = doc.getLength();
		} catch (BadLocationException e) {
		}
	}

	protected void setEditable(boolean enabled) {
		text.setEditable(enabled);
		text.setVisible(enabled);
	}
	
	private void close() {
		setVisible(false);
		dispose();
		process.terminate();
		System.exit(0);
	}

	private void actionFinished() {
		if (!process.getStatus())
			close();
		displayPrompt();
	}

	protected ContentHandler getContentHandler() {
		return printer;
	}
	
	class EnterAction extends TextAction {

		public EnterAction(String name) {
			super(name);
		}

		public void actionPerformed(ActionEvent ev) {
			int end = doc.getEndPosition().getOffset();
			try {
				String command = doc.getText(lastPosition, end - lastPosition - 1);
				doc.insertString(doc.getEndPosition().getOffset() - 1, "\n", defaultAttrs);
				process.setAction(command);
				if (!historyItems.contains(command)) {
					historyCombo.addItem(command);
					historyItems.add(command);
				}
				history.addLast(command);
				if (history.size() == MAX_HISTORY)
					history.removeFirst();
				currentHistory = history.size();
			} catch (BadLocationException e) {
				e.printStackTrace();
			}
		}
	}

	class BackHistoryAction extends TextAction {

		public BackHistoryAction(String name) {
			super(name);
		}

		public void actionPerformed(ActionEvent e) {
			if (history.size() == 0 || currentHistory == 0)
				return;
			String item = (String) history.get(--currentHistory);
			try {
				if (doc.getEndPosition().getOffset() > lastPosition)
					doc.remove(lastPosition, doc.getEndPosition().getOffset() - lastPosition - 1);
				doc.insertString(lastPosition, item, defaultAttrs);
			} catch (BadLocationException e1) {
				e1.printStackTrace();
			}
		}

	}

	class ForwardHistoryAction extends TextAction {

		public ForwardHistoryAction(String name) {
			super(name);
		}

		public void actionPerformed(ActionEvent e) {
			if (history.size() == 0 || currentHistory == history.size() - 1)
				return;
			String item = (String) history.get(++currentHistory);
			try {
				if (doc.getEndPosition().getOffset() > lastPosition)
					doc.remove(lastPosition, doc.getEndPosition().getOffset() - lastPosition - 1);
				doc.insertString(lastPosition, item, defaultAttrs);
			} catch (BadLocationException e1) {
				e1.printStackTrace();
			}
		}

	}

	class HistorySelectedAction implements ItemListener {

		public void itemStateChanged(ItemEvent e) {
			String item = e.getItem().toString();
			try {
				if (doc.getEndPosition().getOffset() > lastPosition)
					doc.remove(lastPosition, doc.getEndPosition().getOffset() - lastPosition - 1);
				doc.insertString(lastPosition, item, defaultAttrs);
			} catch (BadLocationException e1) {
				e1.printStackTrace();
			}
			text.requestFocus();
		}
	}
	
	class ProcessThread extends Thread {

		private String action = null;
		private boolean terminate = false;
		private boolean status = false;

		public ProcessThread() {
			super();
		}

		synchronized public void setAction(String action) {
			while (this.action != null) {
				try {
					wait();
				} catch (InterruptedException e) {
				}
			}
			this.action = action;
			notify();
		}

		synchronized public void terminate() {
			terminate = true;
			notify();
		}

		synchronized public boolean getStatus() {
			return status;
		}

		public boolean isReady() {
			return action == null;
		}

		/* (non-Javadoc)
		 * @see java.lang.Runnable#run()
		 */
		public void run() {
			while (!terminate) {
				while (action == null)
					try {
						synchronized (this) {
							wait();
						}
					} catch (InterruptedException e) {
					}
				status = client.process(action);
				synchronized (this) {
					action = null;
					actionFinished();
					notify();
				}
			}
		}

	}
}
