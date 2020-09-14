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
package org.exist.client.tristatecheckbox;

import com.evolvedbinary.j8fu.tuple.Tuple2;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.UIResource;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.function.Function;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class TristateCheckBoxTableCellRenderer<T> extends TristateCheckBox
        implements TableCellRenderer, UIResource {

    private static final Border noFocusBorder = new EmptyBorder(1, 1, 1, 1);
    private final Function<T, Tuple2<String, TristateState>> valueStateFn;

    public TristateCheckBoxTableCellRenderer(final Function<T, Tuple2<String, TristateState>> valueStateFn) {
        super(null);
        setHorizontalAlignment(SwingConstants.LEFT);
        setBorderPainted(true);
        setHorizontalTextPosition(SwingConstants.RIGHT);
        this.valueStateFn = valueStateFn;
    }

    @Override
    public Component getTableCellRendererComponent(final JTable table, final Object value,
            final boolean isSelected, final boolean hasFocus, final int row, final int column) {

        if (isSelected) {
            setForeground(table.getSelectionForeground());
            super.setBackground(table.getSelectionBackground());
        } else {
            setForeground(table.getForeground());
            setBackground(table.getBackground());
        }

        //set selected/indeterminate
        final Tuple2<String, TristateState> state = valueStateFn.apply((T)value);
        setSelectionState(state._2);

        //set label (if present)
        if (state._1 != null) {
            setText(state._1);
        }

        if (hasFocus) {
            setBorder(UIManager.getBorder("Table.focusCellHighlightBorder"));
        } else {
            setBorder(noFocusBorder);
        }

        return this;
    }
}
