/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009-2011 The eXist Project
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
package org.exist.debuggee;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class DebuggeeFactory {
    
	private final static Logger LOG = LogManager.getLogger(DebuggeeFactory.class);

    private static Debuggee instance = null;

    public static Debuggee getInstance() {
        if (instance == null) {
            final String className = System.getProperty("exist.debuggee", "org.exist.debuggee.DebuggeeImpl");
            try {
                final Class<?> clazz = Class.forName(className);
                if (!Debuggee.class.isAssignableFrom(clazz)) {
                    LOG.warn("Class " + className + " does not implement interface Debuggee. Using fallback.");
                } else {
                    instance = (Debuggee) clazz.newInstance();
                }
            } catch (final ClassNotFoundException e) {
                LOG.warn("Class not found for debuggee: " + className);
            } catch (final IllegalAccessException e) {
                LOG.warn("Failed to instantiate class for debuggee: " + className);
            } catch (final InstantiationException e) {
                LOG.warn("Failed to instantiate class for debuggee: " + className);
            }
            if (instance == null)
                {instance = new DummyDebuggee();}
        }
        return instance;
    }
    
    public static void checkForDebugRequest(HttpServletRequest request, XQueryContext context) throws XPathException {
        //TODO: XDEBUG_SESSION_STOP_NO_EXEC
        //TODO: XDEBUG_SESSION_STOP

        //if get "start new debug session" request
		String xdebug = request.getParameter("XDEBUG_SESSION_START");
		if (xdebug != null) {
			context.declareVariable(Debuggee.SESSION,  xdebug);
		} else {
			//if have session
			xdebug = request.getParameter("XDEBUG_SESSION");
			if (xdebug != null) {
				context.declareVariable(Debuggee.SESSION,  xdebug);
			} else {
				//looking for session in cookies (FF XDebug Helper add-ons as example)
    			final Cookie[] cookies = request.getCookies();
    			if (cookies != null) {
        			for (int i = 0; i < cookies.length; i++) {
        				if ("XDEBUG_SESSION".equals(cookies[i].getName())) {
        					//TODO: check for value?? ("eXistDB_XDebug" ? or leave "default") -shabanovd 
        					context.declareVariable(Debuggee.SESSION, cookies[i].getValue());
            				break;
        				}
        			}
    			}
			}
		}
		
		if (context.requireDebugMode()) {
			final String idekey = request.getParameter("KEY");
			if (idekey != null)
				{context.declareVariable(Debuggee.IDEKEY,  idekey);}
		}
    }
}
