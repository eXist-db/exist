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

import com.evolvedbinary.j8fu.tuple.Tuple2;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.util.function.Function;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;

/**
 * Editor for a Boolean using a TristateCheckBox
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class TristateCheckboxTableCellEditor<T> extends AbstractCellEditor implements TableCellEditor {

    private final Function<T, Tuple2<String, TristateState>> valueStateFn;
    private final Function<Tuple2<String, TristateState>, T> stateValueFn;
    private T current;

    public TristateCheckboxTableCellEditor(final Function<T, Tuple2<String, TristateState>> valueStateFn, final Function<Tuple2<String, TristateState>, T> stateValueFn) {
        super();
        this.valueStateFn = valueStateFn;
        this.stateValueFn = stateValueFn;
    }
    
    @Override
    public Object getCellEditorValue() {
        return current;
    }

    @Override
    public Component getTableCellEditorComponent(final JTable table, final Object value, final boolean isSelected, final int row, final int column) {
        final T typedValue = (T)value;
        final Tuple2<String, TristateState> state = valueStateFn.apply(typedValue);

        final TristateCheckBox chkBox = new TristateCheckBox(state._1, state._2);
        
        chkBox.setHorizontalAlignment(SwingConstants.LEFT);
        chkBox.setHorizontalTextPosition(SwingConstants.RIGHT);
        
        chkBox.addActionListener(e -> {
            current = stateValueFn.apply(Tuple(state._1, state._2.next()));
            fireEditingStopped(); //notify that editing is done!
        });
        
        return chkBox;
    }
}
