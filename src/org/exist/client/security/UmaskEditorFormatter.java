package org.exist.client.security;

import java.text.ParseException;
import javax.swing.JFormattedTextField.AbstractFormatter;
import javax.swing.text.DocumentFilter;

/**
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class UmaskEditorFormatter extends AbstractFormatter {

    @Override
    public Object stringToValue(String text) throws ParseException {
        return (String)text;
    }

    @Override
    public String valueToString(Object value) throws ParseException {
        return (String)value;
    }

    @Override
    protected DocumentFilter getDocumentFilter() {
        return new UmaskDocumentFilter();
    }
    
}
