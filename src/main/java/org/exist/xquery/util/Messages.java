/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.util;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Reads error messages from a {@link java.util.ResourceBundle} and
 * provides shorthand methods to format the error message using the
 * message arguments passed.
 * 
 * @author wolf
 */
public class Messages {
    
    /**
     * The base name of the messages file.
     */
    public static final String BASE_NAME = "org.exist.xquery.util.messages";
    
    public static String getMessage(String messageId) {
        return formatMessage(messageId, new Object[0]);
    }
    
    public static String getMessage(String messageId, Object arg0) {
        return formatMessage(messageId, new Object[] { arg0 });
    }
    
    public static String getMessage(String messageId, Object arg0, Object arg1) {
        return formatMessage(messageId, new Object[] { arg0, arg1 });
    }
    
    public static String getMessage(String messageId, Object arg0, Object arg1, Object arg2) {
        return formatMessage(messageId, new Object[] { arg0, arg1, arg2 });
    }
    
    public static String getMessage(String messageId, Object arg0, Object arg1, Object arg2, Object arg3) {
        return formatMessage(messageId, new Object[] { arg0, arg1, arg2, arg3 });
    }
    
    public static String formatMessage(String messageId, Object[] args) {
        for (int i = 0; i < args.length; i++) {
            if (args[i] != null)
                {args[i] = args[i].toString();}
            else
                {args[i] = "";}
        }
        final ResourceBundle bundle = getBundle(Locale.getDefault());
        final String message = bundle.getString(messageId);

        return MessageFormat.format(message, args);
    }
    
    private static ResourceBundle getBundle(Locale locale) {
        try {
            return ResourceBundle.getBundle(BASE_NAME, locale);
        } catch (final MissingResourceException e) {
            return ResourceBundle.getBundle(BASE_NAME);
        }
    }
}
