/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010 The eXist Project
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
 * $Id$
 */
package org.exist.http;

import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

/**
 * Created by IntelliJ IDEA.
 * User: lcahlander
 * Date: Jul 27, 2010
 * Time: 10:31:47 PM
 * To change this template use File | Settings | File Templates.
 */
public class SessionCountListener implements HttpSessionListener {

    private static long activeSessions = 0;
    
    public void sessionCreated(HttpSessionEvent httpSessionEvent) {
        activeSessions++;
    }

    public void sessionDestroyed(HttpSessionEvent httpSessionEvent) {
        if (activeSessions > 0)
            {activeSessions--;}
    }

    public static long getActiveSessions() {
        return activeSessions;
    }
}
