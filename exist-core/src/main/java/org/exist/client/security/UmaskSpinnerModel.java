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

import javax.swing.AbstractSpinnerModel;

/**
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class UmaskSpinnerModel extends AbstractSpinnerModel {
    
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
    
    public static int octalUmaskToInt(final String octalUmask) {
        return Integer.parseInt(octalUmask, OCTAL_RADIX);
    }
    
    public static String intToOctalUmask(final int umask) {
        return String.format("%4s", Integer.toString(umask, OCTAL_RADIX)).replace(' ', '0');
    }
}
