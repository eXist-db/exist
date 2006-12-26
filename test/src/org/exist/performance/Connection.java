/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 The eXist Project
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
package org.exist.performance;

import org.w3c.dom.Element;
import org.exist.EXistException;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.DatabaseManager;

public class Connection {

    private String id;
    private String base = "xmldb:exist://localhost:8080/exist/xmlrpc";
    private String user = "guest";
    private String password = "guest";

    public Connection(Element config) throws EXistException, XMLDBException {
        id = config.getAttribute("id");
        if (id.length() == 0)
            throw new EXistException("connection element needs an id");
        base = config.getAttribute("base");
        user = config.getAttribute("user");
        password = config.getAttribute("password");
        getCollection("/db");
    }

    public Collection getCollection(String relativePath) throws XMLDBException {
        return DatabaseManager.getCollection(base + '/' + relativePath, user, password);
    }

    public String getId() {
        return id;
    }

    public String getBase() {
        return base;
    }

    public String getUser() {
        return user;
    }

    public String getPassword() {
        return password;
    }
}
