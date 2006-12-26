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
package org.exist.performance.actions;

import org.exist.performance.Connection;
import org.exist.performance.Runner;
import org.exist.performance.AbstractAction;
import org.exist.EXistException;
import org.w3c.dom.Element;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;

public class CreateCollection extends AbstractAction {

    private String parentCol;
    private String name;

    public void configure(Runner runner, Action parent, Element config) throws EXistException {
        super.configure(runner, parent, config);
        parentCol = config.getAttribute("parent");
        if (parentCol.length() == 0)
            throw new EXistException("create-collection requires an attribute 'parent'.");
        name = config.getAttribute("name");
        if (name.length() == 0)
            throw new EXistException("create-collection requires an attribute 'name'.");
    }

    public void execute(Connection connection) throws XMLDBException, EXistException {
        Collection col = connection.getCollection(parentCol);
        if (col == null)
            throw new EXistException("Collection " + parentCol + " not found.");
        CollectionManagementService mgr = (CollectionManagementService)
                col.getService("CollectionManagementService", "1.0");
        mgr.createCollection(name);
    }
}
