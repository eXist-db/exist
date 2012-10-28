package org.exist.client.security;

import javax.swing.AbstractSpinnerModel;
import org.exist.security.Permission;

/**
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class UmaskSpinnerModel extends AbstractSpinnerModel {
    
    //private int umask = Permission.DEFAULT_UMASK;
    private int umask = 0765;
    public final static int OCTAL_RADIX = 8;
    
    @Override
    public Object getValue() {
        return intToOctalUmask(umask);
    }

    @Override
    public void setValue(Object value) {
        if ((value == null) || !(value instanceof String)) {
	    throw new IllegalArgumentException("illegal value");
	}
        
        final int otherUmask = octalUmaskToInt((String)value);
        
	if(otherUmask != umask) {
            umask = otherUmask;
	    fireStateChanged();
	}
    }

    @Override
    public Object getNextValue() {
        final String result;
        if(umask < 0777) {
            result = intToOctalUmask(nextUmask(umask));
        } else {
            result = "0777";
        }
        return result;
    }

    @Override
    public Object getPreviousValue() {
        final String result;
        if(umask > 0) {
            result = intToOctalUmask(prevUmask(umask));
        } else {
            result = "0000";
        }
        return result;
    }
    
    private int prevUmask(int umask) {
        if(umask == 0070) {
            return 0007;
        } else if(umask == 0700) {
            return 0077;
        } else {
            return umask - 01;
        }
    }
    
    private int nextUmask(int umask) {
        if(umask == 0007) {
            return 0010;
        } else if(umask == 0070) {
            return 0100;
        } else {
            return umask + 01;
        }
    }
    
    private int octalUmaskToInt(final String octalUmask) {
        return Integer.parseInt(octalUmask, OCTAL_RADIX);
    }
    
    private String intToOctalUmask(final int umask) {
        return String.format("%4s", Integer.toString(umask, OCTAL_RADIX)).replace(' ', '0');
    }
}
