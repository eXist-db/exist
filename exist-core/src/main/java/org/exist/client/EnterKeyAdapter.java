/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2013 The eXist-db Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.client;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import javax.swing.AbstractButton;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.JComboBox;

/**
 * The class EnterKeyAdapter listens for VK_ENTER key events 
 * for buttons, JPasswordFields, JTextFields and JComboBoxes,
 * whereby it sends doClick() to the affected or specified source.
 *
 * @author <a href="mailto:ljo@exist-db.org">ljo</a>
 */
public class EnterKeyAdapter extends KeyAdapter {
    private AbstractButton button;

    /**
     * Creates a new <code>EnterKeyAdapter</code> instance.
     *
     */
    public EnterKeyAdapter() {
        super();
    }

    /**
     * Creates a new <code>EnterKeyAdapter</code> instance.
     *
     * @param button an <code>AbstractButton</code> value
     */
    public EnterKeyAdapter(final AbstractButton button) {
        super();
        this.button = button;
    }
    
    public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER && e.getSource() instanceof AbstractButton) {
            ((AbstractButton) e.getSource()).doClick();
        } else if (e.getKeyCode() == KeyEvent.VK_ENTER && (e.getSource() instanceof JPasswordField || e.getSource() instanceof JTextField || e.getSource() instanceof JComboBox)) {
            if (button != null) {
                button.doClick();
            }
        }
    }
}
