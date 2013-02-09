/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2012 The eXist Project
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
 *
 *  $Id$
 */
package org.exist.client;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.text.PlainDocument;

import org.jedit.syntax.JEditTextArea;
import org.jedit.syntax.SyntaxDocument;
import org.jedit.syntax.SyntaxStyle;
import org.jedit.syntax.TextAreaPainter;
import org.jedit.syntax.Token;
import org.jedit.syntax.XMLTokenMarker;

public class ClientTextArea extends JEditTextArea implements ActionListener {
	
	private static final long serialVersionUID = 1L;

	public final static String CUT = "Cut";
	public final static String COPY = "Copy";
	public final static String PASTE = "Paste";
	
	private JTextField txtPositionOutput = null;
	
	protected Font textFont = new Font("Monospaced", Font.PLAIN, 10);
	
	public ClientTextArea(boolean editable, String mode) {
		super();
		
		setFont(textFont);
		setEditable(editable);
		setPreferredSize(new Dimension(300, 200));
		
		this.addCaretListener(new CaretListener());
		
		final SyntaxDocument doc = new SyntaxDocument();
		doc.putProperty(PlainDocument.tabSizeAttribute, Integer.valueOf(4));
		setDocument(doc);
		setElectricScroll(2);
		
		final ClientInputHandler inputHandler = new ClientInputHandler();
		inputHandler.addDefaultKeyBindings();
		setInputHandler(inputHandler);
		
		popup = new JPopupMenu("Edit Menu");
		popup.add(new JMenuItem(CUT)).addActionListener(ClientInputHandler.CLIP_CUT);
		popup.add(new JMenuItem(COPY)).addActionListener(ClientInputHandler.CLIP_COPY);
		popup.add(new JMenuItem(PASTE)).addActionListener(ClientInputHandler.CLIP_PASTE);
		
		if("XML".equals(mode))
			{setTokenMarker(new XMLTokenMarker());}
		final TextAreaPainter painter = getPainter();
		final SyntaxStyle[] styles = painter.getStyles();
		styles[Token.KEYWORD1] = new SyntaxStyle(new Color(0, 102, 153), false, true);
		styles[Token.KEYWORD2] = new SyntaxStyle(new Color(0, 153, 102), false, true);
		styles[Token.KEYWORD3] = new SyntaxStyle(new Color(0, 153, 255), false, true);
		styles[Token.LITERAL1] = new SyntaxStyle(new Color(255, 0, 204), false, false);
		styles[Token.LITERAL2] = new SyntaxStyle(new Color(204, 0, 204), false, false);
		painter.setStyles(styles);
		painter.setEOLMarkersPainted(true);
		painter.setBracketHighlightEnabled(true);
	}
	
	public void setPositionOutputTextArea(JTextField txtPositionOutput)
	{
		this.txtPositionOutput = txtPositionOutput;
	}
	
	/* (non-Javadoc)
	 * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
	 */
	public void actionPerformed(ActionEvent e) {
		System.out.println("event: " + e.getActionCommand());
	}
	
	private class CaretListener implements javax.swing.event.CaretListener
	{
		public void caretUpdate(javax.swing.event.CaretEvent e)
		{
			if(txtPositionOutput != null)
			{
				final ClientTextArea txt = (ClientTextArea)e.getSource();
				txtPositionOutput.setText("Line: " + (txt.getCaretLine()+1) + " Column:" + ((txt.getCaretPosition() - txt.getLineStartOffset(txt.getCaretLine()))+1));
			}
		}
	}
}
