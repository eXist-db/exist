/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2020 The eXist-db Project
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
 */
package org.exist.client.tristatecheckbox;

import javax.swing.*;
import javax.swing.plaf.metal.MetalIconFactory;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * See <a href="https://stackoverflow.com/questions/1263323/tristate-checkboxes-in-java">Tristate Checkboxes in Java</a>
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class TristateCheckBox extends JCheckBox implements Icon, ActionListener {

    static final boolean INDETERMINATE_AS_SELECTED = true;  //consider INDETERMINATE as selected ?
    static final Icon icon = MetalIconFactory.getCheckBoxIcon();

    public TristateCheckBox() { this(""); }

    public TristateCheckBox(final String text) {
        this(text, TristateState.DESELECTED);
    }

    public TristateCheckBox(final String text, final TristateState state) {
        /* tri-state checkbox has 3 selection states:
         * 0 unselected
         * 1 mid-state selection
         * 2 fully selected
         */
        super(text, state == TristateState.SELECTED);

        switch (state) {
            case SELECTED: setSelected(true);
            case INDETERMINATE:
            case DESELECTED:
                putClientProperty("SelectionState", state);
                break;
            default:
                throw new IllegalArgumentException();
        }
        addActionListener(this);
        setIcon(this);
    }

    @Override
    public boolean isSelected() {
        if (INDETERMINATE_AS_SELECTED && (getSelectionState() != TristateState.DESELECTED)) {
            return true;
        } else {
            return super.isSelected();
        }
    }

    public TristateState getSelectionState() {
        return (getClientProperty("SelectionState") != null ? (TristateState) getClientProperty("SelectionState") :
                super.isSelected() ? TristateState.SELECTED : TristateState.DESELECTED);
    }

    public void setSelectionState(final TristateState state) {
        switch (state) {
            case SELECTED: setSelected(true);
                break;
            case INDETERMINATE:
            case DESELECTED: setSelected(false);
                break;
            default:
                throw new IllegalArgumentException();
        }
        putClientProperty("SelectionState", state);
    }

    @Override
    public void paintIcon(final Component c, final Graphics g, final int x, final int y) {
        icon.paintIcon(c, g, x, y);
        if (getSelectionState() != TristateState.INDETERMINATE) {
            return;
        }

        final int w = getIconWidth();
        final int h = getIconHeight();
        g.setColor(c.isEnabled() ? new Color(51, 51, 51) : new Color(122, 138, 153));
        g.fillRect(x+4, y+4, w-8, h-8);

        if (!c.isEnabled()) {
            return;
        }
        g.setColor(new Color(81, 81, 81));
        g.drawRect(x+4, y+4, w-9, h-9);
    }

    @Override
    public int getIconWidth() {
        return icon.getIconWidth();
    }

    @Override
    public int getIconHeight() {
        return icon.getIconHeight();
    }

    public void actionPerformed(final ActionEvent e) {
        final TristateCheckBox tcb = (TristateCheckBox) e.getSource();
        if (tcb.getSelectionState() == TristateState.DESELECTED) {
            tcb.setSelected(false);
        }

        tcb.putClientProperty("SelectionState", tcb.getSelectionState() == TristateState.SELECTED ? TristateState.DESELECTED :
                tcb.getSelectionState().next());

//        // test
//        System.out.println(">>>>IS SELECTED: "+tcb.isSelected());
//        System.out.println(">>>>IN MID STATE: "+(tcb.getSelectionState() == TristateState.INDETERMINATE));
    }
}
