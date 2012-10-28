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
