package org.exist.client.security;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.InputVerifier;
import javax.swing.JComponent;
import javax.swing.JTextField;

/**
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class RegExpInputVerifier extends InputVerifier {

    final Matcher matcher;
    
    public RegExpInputVerifier(final Pattern pattern) {
        this.matcher = pattern.matcher("");
    }
    
    @Override
    public boolean verify(final JComponent input) {
        if(input instanceof JTextField) {
            matcher.reset(((JTextField)input).getText());
            return matcher.matches();
        }
        
        return false;
    }   
}
