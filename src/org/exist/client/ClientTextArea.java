/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
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
import javax.swing.text.PlainDocument;

import org.jedit.syntax.InputHandler;
import org.jedit.syntax.JEditTextArea;
import org.jedit.syntax.SyntaxDocument;
import org.jedit.syntax.SyntaxStyle;
import org.jedit.syntax.TextAreaPainter;
import org.jedit.syntax.Token;
import org.jedit.syntax.XMLTokenMarker;

public class ClientTextArea extends JEditTextArea implements ActionListener {
	
	public final static String CUT = "Cut";
	public final static String COPY = "Copy";
	public final static String PASTE = "Paste";
	
	protected Font textFont = new Font("Monospaced", Font.PLAIN, 12);
	
	public ClientTextArea(boolean editable, String mode) {
		super();
		
		setFont(textFont);
		setEditable(editable);
		setPreferredSize(new Dimension(300, 200));
		
		SyntaxDocument doc = new SyntaxDocument();
		doc.putProperty(PlainDocument.tabSizeAttribute, new Integer(4));
		setDocument(doc);
		setElectricScroll(2);
		
		popup = new JPopupMenu("Edit Menu");
		popup.add(new JMenuItem(CUT)).addActionListener(this);
		popup.add(new JMenuItem(COPY)).addActionListener(this);
		popup.add(new JMenuItem(PASTE)).addActionListener(this);
		
		if(mode.equals("XML"))
			setTokenMarker(new XMLTokenMarker());
		TextAreaPainter painter = getPainter();
		SyntaxStyle[] styles = painter.getStyles();
		styles[Token.KEYWORD1] = new SyntaxStyle(new Color(0, 102, 153), false, true);
		styles[Token.KEYWORD2] = new SyntaxStyle(new Color(0, 153, 102), false, true);
		styles[Token.KEYWORD3] = new SyntaxStyle(new Color(0, 153, 255), false, true);
		styles[Token.LITERAL1] = new SyntaxStyle(new Color(255, 0, 204), false, false);
		styles[Token.LITERAL2] = new SyntaxStyle(new Color(204, 0, 204), false, false);
		painter.setStyles(styles);
		painter.setEOLMarkersPainted(true);
		painter.setBracketHighlightEnabled(true);
		
		InputHandler inputHandler = getInputHandler();
		inputHandler.addKeyBinding("C+c",
				new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						copy();
					}
		});
		inputHandler.addKeyBinding("C+v",
				new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				paste();
			}
		});
	}
	
	/* (non-Javadoc)
	 * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
	 */
	public void actionPerformed(ActionEvent e) {
		String cmd = e.getActionCommand();
		if(cmd.equals(CUT))
			cut();
		else if(cmd.equals(COPY))
			copy();
		else if(cmd.equals(PASTE))
			paste();
	}
}
