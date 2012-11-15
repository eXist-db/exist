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
package org.exist.client.security;

import javax.swing.JFormattedTextField;
import javax.swing.JSpinner;
import javax.swing.JSpinner.DefaultEditor;
import javax.swing.JTextField;
import javax.swing.text.DefaultFormatterFactory;

/**
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class UmaskEditor extends DefaultEditor {
    
    private static final long serialVersionUID = 1531848918506511061L;

    public UmaskEditor(final JSpinner jSpinner) {
        super(jSpinner);
        
        final UmaskEditorFormatter umaskEditorFormatter = new UmaskEditorFormatter();
        final DefaultFormatterFactory factory = new DefaultFormatterFactory(umaskEditorFormatter);
        final JFormattedTextField ftf = getTextField();
        ftf.setEditable(true);
        ftf.setFormatterFactory(factory);
        ftf.setHorizontalAlignment(JTextField.RIGHT);
        ftf.setColumns(4);
    }
}
