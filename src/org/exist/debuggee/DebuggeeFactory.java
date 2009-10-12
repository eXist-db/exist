/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009 The eXist Project
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
 *  $Id:$
 */
package org.exist.debuggee;

import org.apache.log4j.Logger;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class DebuggeeFactory {
    
	private final static Logger LOG = Logger.getLogger(DebuggeeFactory.class);

    private static Debuggee instance = null;

    public static Debuggee getInstance() {
        if (instance == null) {
            String className = System.getProperty("exist.debuggee", "org.exist.debuggee.DebuggeeImpl");
            try {
                Class clazz = Class.forName(className);
                if (!Debuggee.class.isAssignableFrom(clazz)) {
                    LOG.warn("Class " + className + " does not implement interface Debuggee. Using fallback.");
                } else {
                    instance = (Debuggee) clazz.newInstance();
                }
            } catch (ClassNotFoundException e) {
                LOG.warn("Class not found for debuggee: " + className);
            } catch (IllegalAccessException e) {
                LOG.warn("Failed to instantiate class for debuggee: " + className);
            } catch (InstantiationException e) {
                LOG.warn("Failed to instantiate class for debuggee: " + className);
            }
            if (instance == null)
                instance = new DummyDebuggee();
        }
        return instance;
    }
}
