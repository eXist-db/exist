package org.exist.client.security;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.DocumentFilter;

/**
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class UmaskDocumentFilter extends DocumentFilter {

    final static Pattern ptnUmask = Pattern.compile("0?[0-7]{1,3}");
    final Matcher mtcUmask = ptnUmask.matcher("");
    
    @Override
    public void remove(FilterBypass fb, int offset, int length) throws BadLocationException {
        
        final Document doc = fb.getDocument();
        final String original = doc.getText(0, doc.getLength());
        
        if(isValidUmask(removeFromString(original, offset, length))) {
            super.remove(fb, offset, length);
        }
    }

    @Override
    public void insertString(FilterBypass fb, int offset, String text, AttributeSet attr) throws BadLocationException {
        
        final Document doc = fb.getDocument();
        final String original = doc.getText(0, doc.getLength());
        
        if(isValidUmask(insertIntoString(original, text, offset))) {
            super.insertString(fb, offset, text, attr);
        }
    }
    
    private String removeFromString(final String original, final int offset, final int length) {
        String newString;
        if(offset > 0) {
             newString = original.substring(0, offset+length-1);
            if(offset + length < original.length()) {
                newString += original.substring(offset + length);
            }
        } else {
            newString = original.substring(offset + length);
        }
        return newString;
    }
    
    private String insertIntoString(final String original, final String toInsert, final int offset) {
        String newString;
        if(offset > 0) {
            newString = original.substring(0, offset);
            newString += toInsert;
            if(offset < original.length()) {
                newString += original.substring(offset);
            }
        } else {
            newString = toInsert + original;
        }
        return newString;
    }

    @Override
    public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
        
        final Document doc = fb.getDocument();
        final String original = doc.getText(0, doc.getLength());
        
        String str;
        if(length > 0) {
            str = removeFromString(original, offset, length);
        } else {
            str = original;
        }
            
        str = insertIntoString(str, text, offset);
        if(isValidUmask(str)) {
            super.replace(fb, offset, length, text, attrs);
        }
    }
 
    private boolean isValidUmask(String str) {
        mtcUmask.reset(str);
        return mtcUmask.matches();
    }
}
