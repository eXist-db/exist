package org.exist.client.security;

import javax.swing.table.DefaultTableModel;

/**
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class ReadOnlyDefaultTableModel extends DefaultTableModel {

    public ReadOnlyDefaultTableModel(final String[][] data, final String[] columnNames) {
        super(data, columnNames);
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return false;
    }
    
}
