/*
 * eXist Open Source Native XML Database
 *
 * Copyright (C) 2001-09 Wolfgang M. Meier wolfgang@exist-db.org
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation; either version 2 of the License, or (at your
 * option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 * $Id$
 */
package org.exist.client;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import org.jedit.syntax.JEditTextArea;

import org.jedit.syntax.DefaultInputHandler;
import org.jedit.syntax.InputHandler;

/**
 * A class to extend {@link org.jedit.syntax.DefaultInputHandler} to be a little
 * more Mac friendly. This class doesn't pretend to be a robust cross-platform
 * implementation of key bindings, but it is an incremental improvement over
 * what came before it. To see just how involved cross-platform keyboard
 * handling can become, check out <a href="http://jedit.org/">jEdit</a> from
 * which the jEdit-syntax libraries were derived many years ago. Ideally, I
 * suppose, someone should incorporate jEdit's much more robust solution back
 * into eXist, but that's a pretty extensive overhaul.
 */
public class ClientInputHandler extends DefaultInputHandler {
	private boolean runningOnMac = System.getProperty("mrj.version") != null;

	/* Listeners for actions not already defined in InputHandler */

	public static final ActionListener SELECT_ALL = new select_all();
	public static final ActionListener CLIP_COPY = new clip_copy();
	public static final ActionListener CLIP_PASTE = new clip_paste();
	public static final ActionListener CLIP_CUT = new clip_cut();

	/**
	 * Sets up the default key bindings.
	 */
	public void addDefaultKeyBindings() {
		if (runningOnMac) {
			/* Bindings Mac users are accustomed to */
			addKeyBinding("BACK_SPACE", BACKSPACE);
			addKeyBinding("M+BACK_SPACE", BACKSPACE_WORD);
			addKeyBinding("DELETE", DELETE);
			addKeyBinding("M+DELETE", DELETE_WORD);

			addKeyBinding("ENTER", INSERT_BREAK);
			addKeyBinding("TAB", INSERT_TAB);

			addKeyBinding("HOME", DOCUMENT_HOME);
			addKeyBinding("END", DOCUMENT_END);
			addKeyBinding("S+HOME", SELECT_DOC_HOME);
			addKeyBinding("S+END", SELECT_DOC_END);

			addKeyBinding("M+A", SELECT_ALL);
			addKeyBinding("S+HOME", SELECT_HOME);
			addKeyBinding("S+END", SELECT_END);

			addKeyBinding("PAGE_UP", PREV_PAGE);
			addKeyBinding("PAGE_DOWN", NEXT_PAGE);
			addKeyBinding("S+PAGE_UP", SELECT_PREV_PAGE);
			addKeyBinding("S+PAGE_DOWN", SELECT_NEXT_PAGE);

			addKeyBinding("LEFT", PREV_CHAR);
			addKeyBinding("S+LEFT", SELECT_PREV_CHAR);
			addKeyBinding("A+LEFT", PREV_WORD);
			addKeyBinding("AS+LEFT", SELECT_PREV_WORD);
			addKeyBinding("RIGHT", NEXT_CHAR);
			addKeyBinding("S+RIGHT", SELECT_NEXT_CHAR);
			addKeyBinding("A+RIGHT", NEXT_WORD);
			addKeyBinding("AS+RIGHT", SELECT_NEXT_WORD);
			addKeyBinding("UP", PREV_LINE);
			addKeyBinding("S+UP", SELECT_PREV_LINE);
			addKeyBinding("DOWN", NEXT_LINE);
			addKeyBinding("S+DOWN", SELECT_NEXT_LINE);

			addKeyBinding("A+ENTER", REPEAT);

			addKeyBinding("M+C", CLIP_COPY);
			addKeyBinding("M+V", CLIP_PASTE);
			addKeyBinding("M+X", CLIP_CUT);
		} else {
			/* Bindings Windows users and others are accustomed to */
			super.addDefaultKeyBindings();

			/* Plus a few extra DefaultInputHandler didn't include */
			addKeyBinding("C+A", SELECT_ALL);
			addKeyBinding("C+C", CLIP_COPY);
			addKeyBinding("C+V", CLIP_PASTE);
			addKeyBinding("C+X", CLIP_CUT);
		}
	}

	public void keyTyped(KeyEvent evt) {
		if (runningOnMac) {
			/*
			 * Keys pressed with the command key shouldn't generate text.
			 */
			int modifiers = evt.getModifiers();
			char c = evt.getKeyChar();

			/*
			 * Default input handler filters out events with the ALT (option)
			 * key, but those are associated with valid characters on the Mac.
			 * This won't work in the general case, but it should get things
			 * working for many people for whom this was broken before.
			 */
			if (c != KeyEvent.CHAR_UNDEFINED
					&& (modifiers & KeyEvent.ALT_MASK) != 0) {
				executeAction(INSERT_CHAR, evt.getSource(), String.valueOf(c));

			} else if ((modifiers & KeyEvent.META_MASK) == 0) {
				super.keyTyped(evt);
			}
		} else {
			super.keyTyped(evt);
		}
	}

	public static class select_all implements ActionListener {
		public void actionPerformed(ActionEvent evt) {
			JEditTextArea textArea = getTextArea(evt);
			textArea.selectAll();
		}
	}

	public static class clip_copy implements ActionListener {
		public void actionPerformed(ActionEvent evt) {
			JEditTextArea textArea = getTextArea(evt);
			textArea.copy();
		}
	}

	public static class clip_paste implements ActionListener {
		public void actionPerformed(ActionEvent evt) {
			JEditTextArea textArea = getTextArea(evt);
			textArea.paste();
		}
	}

	public static class clip_cut implements ActionListener {
		public void actionPerformed(ActionEvent evt) {
			JEditTextArea textArea = getTextArea(evt);
			textArea.cut();
		}
	}

}
